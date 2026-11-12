package app.chatra.scheduling

import app.chatra.core.database.*
import app.chatra.core.model.*
import app.chatra.core.model.Clock
import app.chatra.telephony.*
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.*
import java.util.UUID

interface ScheduleAlarms {
    fun boot(): Int = 0
    fun available(): Boolean
    fun register(row: ScheduledRow)
    fun cancel(row: ScheduledRow)
}
fun interface ScheduleConditions { fun problem(sim: SubscriptionId): String? }
fun interface ScheduleNotice { fun show(row: ScheduledRow, message: String); fun cancel(id: String) {} }

/** The database wins every edit/fire race; alarm registration is repaired after the commit. */
class PersistentSchedules(private val db: ChatraDatabase, private val queue: PersistentSmsQueue,
    private val drafts: RoomDrafts, private val clock: Clock, private val alarms: ScheduleAlarms,
    private val conditions: ScheduleConditions, private val notice: ScheduleNotice = ScheduleNotice { _, _ -> }) : ScheduleRepository {
    private val dao = db.dao()
    private val store = db.schedules()
    private val mutex = Mutex()
    private fun ScheduledRow.model() = ScheduledMessage(ScheduleId(id), OperationId(operationId), Instant.ofEpochMilli(instant), LocalDateTime.parse(local), ZoneId.of(zone), LatenessPolicy.valueOf(policy), version, token)
    override fun scheduled(): Flow<List<ScheduledItem>> = combine(store.rows(), queue.operations()) { rows, operations ->
        val ops = operations.associateBy { it.id.value }
        rows.mapNotNull { row -> ops[row.operationId]?.let { ScheduledItem(row.model(), it, row.title, row.error) } }
    }
    override fun lastSent(): Flow<LastScheduledSent?> = scheduled().map { rows ->
        rows.filter { it.operation.state in setOf(SendState.SENT, SendState.DELIVERED) }.maxByOrNull { it.operation.createdAt }
            ?.let { LastScheduledSent(it.title, it.operation.createdAt, it.operation.state == SendState.DELIVERED) }
    }
    private fun validate(instant: Instant, local: LocalDateTime, zone: ZoneId) {
        require(!instant.isBefore(clock.now().plus(MinLead))) { "Wybierz termin co najmniej minutę w przyszłości." }
        require(when (val resolved = resolveLocal(local, zone)) {
            is LocalTimeResolution.Exact -> resolved.instant == instant
            is LocalTimeResolution.Overlap -> instant == resolved.earlier || instant == resolved.later
            is LocalTimeResolution.Gap -> false
        }) { "Ta godzina nie istnieje lub nie odpowiada wybranej strefie." }
    }
    override suspend fun schedule(draft: Draft, instant: Instant, local: LocalDateTime, zone: ZoneId, policy: LatenessPolicy): ScheduleId = withContext(Dispatchers.IO) {
        mutex.withLock {
            validate(instant, local, zone)
            val p = queue.prepare(draft, 0)
            val row = p.row.copy(state = "SCHEDULED", undoUntil = instant.toEpochMilli(), undoElapsed = null)
            val id = db.withTransaction {
                dao.operationForDraft(draft.id.value)?.let { old ->
                    return@withTransaction store.forOperation(old.id)?.id ?: error("Ten szkic został już przyjęty do wysyłki.")
                }
                check(!drafts.isClosed(draft.id))
                check(dao.insertOperation(row) != -1L)
                p.recipients.forEach { dao.recipient(it.copy(state = "SCHEDULED")) }
                val title = draft.recipientNames.filter { it.isNotBlank() }.joinToString(", ").ifBlank { draft.recipients.joinToString(", ") { it.raw } }
                val entry = ScheduledRow(ScheduleId.new().value, row.id, instant.toEpochMilli(), local.toString(), zone.id, policy.name, 0, UUID.randomUUID().toString(), title, clock.now().toEpochMilli())
                store.insert(entry); drafts.consume(draft); entry.id
            }
            register(requireNotNull(store.row(id)))
            ScheduleId(id)
        }
    }
    private suspend fun register(row: ScheduledRow) {
        val problem = conditions.problem(SubscriptionId(requireNotNull(dao.operation(row.operationId)).subscriptionId))
        try {
            check(problem == null) { problem.orEmpty() }
            check(alarms.available()) { "Brak dostępu do dokładnych alarmów. Otwórz ustawienia." }
            alarms.register(row)
            store.alarmResult(row.id, row.version, row.token, true, null, alarms.boot())
            notice.cancel(row.id)
        } catch (e: Exception) {
            val message = e.message ?: "Nie udało się zarejestrować alarmu. Zlecenie jest zapisane i wymaga sprawdzenia."
            store.alarmResult(row.id, row.version, row.token, false, message)
            notice.show(row, message)
        }
    }
    private suspend fun missed(row: ScheduledRow, reason: String, expectedOperationVersion: Int? = null) {
        val applied = db.withTransaction {
            val current = store.row(row.id) ?: return@withTransaction false
            if (current.version != row.version || current.token != row.token) return@withTransaction false
            val op = dao.operation(row.operationId) ?: return@withTransaction false
            if (expectedOperationVersion != null && op.version != expectedOperationVersion) return@withTransaction false
            if (op.state in listOf("SCHEDULED", "PENDING_UNDO", "MISSED_NEEDS_DECISION")) {
                if (op.state != "MISSED_NEEDS_DECISION" && dao.transition(op.id, op.state, op.version, "MISSED_NEEDS_DECISION") != 1) return@withTransaction false
                dao.recipients(op.id).forEach { dao.recipient(it.copy(state = "MISSED_NEEDS_DECISION")) }
                store.alarmResult(row.id, row.version, row.token, false, reason)
                true
            } else false
        }
        if (applied) notice.show(row, reason)
    }
    /** Rechecked by the shared queue immediately before its dispatch claim, also after WorkManager delays. */
    suspend fun permit(operation: OutgoingOperation): Boolean {
        val row = store.forOperation(operation.id) ?: return true
        val current = dao.operation(operation.id) ?: return false
        if (current.version != operation.version || current.state != operation.state) return false
        val problem = conditions.problem(SubscriptionId(operation.subscriptionId))
        val reason = when {
            problem != null -> problem
            !row.manual && row.policy == LatenessPolicy.Ask.name && row.boot != alarms.boot() -> "Telefon uruchomiono ponownie. Sprawdź zaplanowaną wiadomość."
            !row.manual && !alarms.available() -> "Cofnięto dostęp do dokładnych alarmów. Wybierz dalsze działanie."
            row.model().decide(clock.now()) !is DueDecision.Send -> "Termin minął. Wybierz wysłanie teraz lub nowy termin."
            else -> null
        }
        if (reason != null) { missed(row, reason, operation.version); return false }
        return true
    }
    suspend fun fire(id: ScheduleId, version: Int, token: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val row = store.row(id.value)?.takeIf { it.version == version && it.token == token } ?: return@withLock
            val op = dao.operation(row.operationId)?.takeIf { it.state == "SCHEDULED" } ?: return@withLock
            if (clock.now().isBefore(Instant.ofEpochMilli(row.instant))) { register(row); return@withLock }
            if (!permit(op)) return@withLock
            val activated = db.withTransaction {
                val current = store.row(row.id) ?: return@withTransaction false
                if (current.version != version || current.token != token) return@withTransaction false
                if (!queue.activateScheduled(op, SubscriptionId(op.subscriptionId))) return@withTransaction false
                store.revise(row.id, version, op.id, row.instant, row.local, row.zone, row.policy, UUID.randomUUID().toString(), false) == 1
            }
            if (activated) { notice.cancel(row.id); queue.dispatch(op.id) }
        }
    }
    override suspend fun sendNow(id: ScheduleId, sim: SubscriptionId?) = sendNowVersion(id, sim, null)
    override suspend fun sendNow(id: ScheduleId, sim: SubscriptionId?, expectedVersion: Int) = sendNowVersion(id, sim, expectedVersion)
    private suspend fun sendNowVersion(id: ScheduleId, sim: SubscriptionId?, expectedVersion: Int?): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val row = store.row(id.value)?.takeIf { expectedVersion == null || it.version == expectedVersion } ?: return@withLock false
            val op = dao.operation(row.operationId) ?: return@withLock false
            val selected = sim ?: SubscriptionId(op.subscriptionId)
            conditions.problem(selected)?.let { return@withLock false }
            val now = clock.now(); val zone = ZoneId.of(row.zone)
            if (op.state in listOf("FAILED", "PARTIALLY_SENT")) {
                if (!queue.retry(OperationId(op.id), selected)) return@withLock false
                val child = dao.retryChild(op.id) ?: return@withLock false
                store.revise(row.id, row.version, child.id, now.toEpochMilli(), now.atZone(zone).toLocalDateTime().toString(), row.zone, LatenessPolicy.Ask.name, UUID.randomUUID().toString(), true)
                runCatching { alarms.cancel(row) }; return@withLock true
            }
            if (op.state !in listOf("SCHEDULED", "MISSED_NEEDS_DECISION")) return@withLock false
            val accepted = db.withTransaction {
                if (store.row(row.id)?.version != row.version) return@withTransaction false
                if (!queue.activateScheduled(op, selected)) return@withTransaction false
                store.revise(row.id, row.version, op.id, now.toEpochMilli(), now.atZone(zone).toLocalDateTime().toString(), row.zone, LatenessPolicy.Ask.name, UUID.randomUUID().toString(), true) == 1
            }
            if (accepted) { runCatching { alarms.cancel(row) }; notice.cancel(row.id); queue.wake(op.id) }
            accepted
        }
    }
    override suspend fun reschedule(id: ScheduleId, instant: Instant, local: LocalDateTime, zone: ZoneId) = rescheduleVersion(id, instant, local, zone, null)
    override suspend fun reschedule(id: ScheduleId, instant: Instant, local: LocalDateTime, zone: ZoneId, expectedVersion: Int) = rescheduleVersion(id, instant, local, zone, expectedVersion)
    private suspend fun rescheduleVersion(id: ScheduleId, instant: Instant, local: LocalDateTime, zone: ZoneId, expectedVersion: Int?): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            validate(instant, local, zone)
            val row = store.row(id.value)?.takeIf { expectedVersion == null || it.version == expectedVersion } ?: return@withLock false
            val op = dao.operation(row.operationId) ?: return@withLock false
            if (op.state !in listOf("SCHEDULED", "MISSED_NEEDS_DECISION")) return@withLock false
            val revised = db.withTransaction {
                if (store.row(row.id)?.version != row.version) return@withTransaction false
                if (dao.transition(op.id, op.state, op.version, "SCHEDULED") != 1) return@withTransaction false
                store.revise(row.id, row.version, op.id, instant.toEpochMilli(), local.toString(), zone.id, row.policy, UUID.randomUUID().toString(), false) == 1
            }
            if (revised) { runCatching { alarms.cancel(row) }; register(requireNotNull(store.row(row.id))) }
            revised
        }
    }
    override suspend fun edit(id: ScheduleId, expectedVersion: Int, draft: Draft, instant: Instant, local: LocalDateTime, zone: ZoneId, policy: LatenessPolicy): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            validate(instant, local, zone)
            val row = store.row(id.value)?.takeIf { it.version == expectedVersion } ?: return@withLock false
            val op = dao.operation(row.operationId)?.takeIf { it.state in listOf("SCHEDULED", "MISSED_NEEDS_DECISION") } ?: return@withLock false
            // Recipients/transport are immutable here; changing them creates a new message in the composer.
            require(draft.recipients.map { recipientKey(it.raw) }.toSet() == addresses(op.recipients).map { recipientKey(it.raw) }.toSet())
            val original = drafts.snapshot(op)
            require(draft.recipientMode.name == op.mode && draft.attachmentIds == original.attachmentIds) { "Zmień odbiorców i załączniki w nowej wiadomości." }
            val snapshot = original.copy(text = draft.text, subscriptionId = draft.subscriptionId, updatedAt = clock.now())
            val p = queue.prepare(snapshot, 0)
            val accepted = db.withTransaction {
                if (store.row(row.id)?.version != expectedVersion) return@withTransaction false
                if (db.schedules().editOperation(op.id, op.version, snapshot.text, DraftCodec.encode(snapshot), p.row.subscriptionId, p.row.subscriptionLabel) != 1) return@withTransaction false
                store.revise(row.id, expectedVersion, op.id, instant.toEpochMilli(), local.toString(), zone.id, policy.name, UUID.randomUUID().toString(), false) == 1
            }
            if (accepted) { runCatching { alarms.cancel(row) }; register(requireNotNull(store.row(row.id))) }
            accepted
        }
    }
    override suspend fun delete(id: ScheduleId) = deleteVersion(id, null)
    override suspend fun delete(id: ScheduleId, expectedVersion: Int) = deleteVersion(id, expectedVersion)
    private suspend fun deleteVersion(id: ScheduleId, expectedVersion: Int?): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val row = store.row(id.value)?.takeIf { expectedVersion == null || it.version == expectedVersion } ?: return@withLock false
            val deleted = db.withTransaction {
                if (store.row(row.id)?.version != row.version) return@withTransaction false
                val op = dao.operation(row.operationId) ?: return@withTransaction false
                if (op.state in listOf("DISPATCHING", "PENDING_UNDO", "SENT", "DELIVERED")) return@withTransaction false
                // Removing a schedule must retain transport evidence and late callback reconciliation.
                if (op.state in listOf("SCHEDULED", "MISSED_NEEDS_DECISION") && dao.transition(op.id, op.state, op.version, "CANCELLED") != 1) return@withTransaction false
                store.delete(row.id, row.version) == 1
            }
            if (deleted) { runCatching { alarms.cancel(row) }; notice.cancel(row.id) }
            deleted
        }
    }
    override suspend fun reconcile() { reconcile("resume") }
    suspend fun reconcile(reason: String) = withContext(Dispatchers.IO) {
        val due = mutableListOf<ScheduledRow>()
        mutex.withLock {
            for (row in store.rows().first()) {
                val op = dao.operation(row.operationId) ?: continue
                if (op.state in listOf("FAILED", "PARTIALLY_SENT")) {
                    dao.retryChild(op.id)?.let { child ->
                        val at = Instant.ofEpochMilli(child.createdAt)
                        store.revise(row.id, row.version, child.id, child.createdAt, at.atZone(ZoneId.of(row.zone)).toLocalDateTime().toString(), row.zone, LatenessPolicy.Ask.name, UUID.randomUUID().toString(), true)
                    }
                    continue
                }
                if (op.state !in listOf("SCHEDULED", "MISSED_NEEDS_DECISION")) continue
                if (clock.now().isBefore(Instant.ofEpochMilli(row.instant))) {
                    if (op.state == "SCHEDULED") register(row)
                } else if ((row.policy == LatenessPolicy.SendWithinHour.name || row.registered && row.boot == alarms.boot() && !reason.contains("BOOT", true)) && row.model().decide(clock.now()) is DueDecision.Send && conditions.problem(SubscriptionId(op.subscriptionId)) == null && alarms.available()) {
                    // The actual worker checks the deadline again, rather than trusting this wake-up.
                    if (op.state == "MISSED_NEEDS_DECISION") dao.transition(op.id, op.state, op.version, "SCHEDULED")
                    due += row
                } else missed(row, "Termin minął lub zmieniły się warunki. Wybierz dalsze działanie.")
            }
        }
        due.forEach { fire(ScheduleId(it.id), it.version, it.token) }
    }
}
