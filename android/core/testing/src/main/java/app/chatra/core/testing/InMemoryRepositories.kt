package app.chatra.core.testing

import app.chatra.core.model.Address
import kotlinx.coroutines.flow.combine
import app.chatra.core.model.ScheduledMessage
import app.chatra.core.model.ScheduleId
import app.chatra.core.model.LatenessPolicy
import app.chatra.core.model.AttachmentSummary
import app.chatra.core.model.HdInfo
import app.chatra.core.model.MediaVariant
import app.chatra.core.model.Clock
import app.chatra.core.model.ContactsDirectory
import app.chatra.core.model.ConversationId
import app.chatra.core.model.ConversationRepository
import app.chatra.core.model.ConversationSummary
import app.chatra.core.model.DeliveryStatus
import app.chatra.core.model.Direction
import app.chatra.core.model.Draft
import app.chatra.core.model.DraftId
import app.chatra.core.model.DraftRepository
import app.chatra.core.model.Message
import app.chatra.core.model.MessageRef
import app.chatra.core.model.OperationId
import app.chatra.core.model.Participant
import app.chatra.core.model.RecipientMode
import app.chatra.core.model.ScheduleRepository
import app.chatra.core.model.ScheduledItem
import app.chatra.core.model.SendOperation
import app.chatra.core.model.SendQueue
import app.chatra.core.model.SendState
import app.chatra.core.model.SimCard
import app.chatra.core.model.SimRepository
import app.chatra.core.model.Transition
import app.chatra.core.model.Transport
import app.chatra.core.model.participantsKey
import app.chatra.core.model.transition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * Repozytoria demonstracyjne nad [Fixture]. Stan jest w pamięci, więc zmiany (przeczytanie,
 * przypięcie, szkic, wysyłka) są widoczne od razu. Wysyłka nie dotyka telefonii: fałszywy
 * transport przechodzi PENDING_UNDO → DISPATCHING → SENT → DELIVERED.
 */
class InMemoryChatra(
    private val clock: Clock,
    private val scope: CoroutineScope,
    /** Zamrożony zegar (zrzuty referencyjne): okno anulowania nie upływa samo. */
    private val frozen: Boolean = false,
) : ConversationRepository, DraftRepository, SimRepository, ContactsDirectory, SendQueue, ScheduleRepository {

    private val summaries = MutableStateFlow((Fixture.inbox + Fixture.hiddenConversations).associateBy { it.id })
    private val hidden = Fixture.hiddenConversations.map { it.id }.toMutableSet()
    private val history = MutableStateFlow(Fixture.messages)
    private val drafts = MutableStateFlow(
        mapOf(
            Fixture.Ids.Tomek to draft(Fixture.Ids.Tomek, Fixture.tomek, Fixture.TomekDraft),
            Fixture.Ids.Ola to draft(Fixture.Ids.Ola, Fixture.ola, Fixture.OlaComposer),
        ),
    )
    private val ops = MutableStateFlow(
        listOf(
            fixtureOp(Fixture.OlaFailed, "Wezmę wino i deser.", SendState.FAILED, null),
            fixtureOp(Fixture.OlaPending, "Pasuje, do zobaczenia!", SendState.PENDING_UNDO, clock.now().plusSeconds(3)),
        ) + Fixture.scheduled.map { it.operation },
    )
    private val schedules = MutableStateFlow(Fixture.scheduled)

    init {
        if (!frozen) scope.launch { dispatchAfterUndo(Fixture.OlaPending) }
    }

    private fun draft(c: ConversationId, p: Participant, text: String) =
        Draft(DraftId("d-${c.value}"), c, listOf(p.address), text, RecipientMode.Single, emptyList(), Fixture.SIM1.id, clock.now())

    private fun fixtureOp(id: OperationId, body: String, state: SendState, deadline: java.time.Instant?) =
        SendOperation(id, Fixture.Ids.Ola, Transport.SMS, RecipientMode.Single, listOf(Fixture.ola.address), body, emptyList(),
            if (state == SendState.FAILED) null else Fixture.SIM1.id, state, 1, clock.now(), undoDeadline = deadline)

    // --- ConversationRepository --------------------------------------------------------------

    override fun inbox(): Flow<List<ConversationSummary>> = summaries.map { all -> all.values.filter { it.id !in hidden } }

    override fun conversation(id: ConversationId): Flow<ConversationSummary?> = summaries.map { it[id] }

    override fun messages(id: ConversationId): Flow<List<Message>> = history.map { it[id].orEmpty().sortedBy { m -> m.timestamp } }

    override suspend fun markRead(id: ConversationId) {
        history.update { all -> all + (id to all[id].orEmpty().map { it.copy(read = true) }) }
        summaries.update { all -> all[id]?.let { all + (id to it.copy(unreadCount = 0)) } ?: all }
    }

    override suspend fun setPinned(id: ConversationId, pinned: Boolean) {
        summaries.update { all -> all[id]?.let { all + (id to it.copy(pinned = pinned)) } ?: all }
    }

    override suspend fun moveToTrash(id: ConversationId) {
        summaries.update { all -> all[id]?.let { all + (id to it.copy(trashed = true)) } ?: all }
    }

    override suspend fun conversationFor(recipients: List<Address>): ConversationId {
        val key = participantsKey(recipients)
        summaries.value.values.firstOrNull { participantsKey(it.participants.map { p -> p.address }) == key }?.let {
            hidden.remove(it.id)
            return it.id
        }
        val id = ConversationId.new()
        val participants = recipients.map { a -> Fixture.participantFor(a) }
        summaries.update {
            it + (id to ConversationSummary(id, participants, participants.joinToString(", ") { p -> p.label }, clock.now(),
                ConversationSummary.Preview.Text(""), 0, false, isGroupMms = recipients.size > 1))
        }
        return id
    }

    // --- DraftRepository -----------------------------------------------------------------------

    override fun draft(conversationId: ConversationId): Flow<Draft?> = drafts.map { it[conversationId] }

    override suspend fun save(draft: Draft) {
        val c = draft.conversationId ?: return
        drafts.update { it + (c to draft) }
        refreshPreview(c)
    }

    override suspend fun delete(id: DraftId) {
        val c = drafts.value.entries.firstOrNull { it.value.id == id }?.key ?: return
        drafts.update { it - c }
        refreshPreview(c)
    }

    // --- Sim / Contacts ---------------------------------------------------------------------------

    override fun cards(): Flow<List<SimCard>> = MutableStateFlow(Fixture.sims).asStateFlow()

    override fun suggestions(): Flow<List<Participant>> = MutableStateFlow(Fixture.suggestions.map { it.participant }).asStateFlow()

    override suspend fun lookup(address: Address): Participant? = Fixture.contacts.firstOrNull { it.address.normalized == address.normalized }

    // --- SendQueue ------------------------------------------------------------------------------

    override fun operations(): Flow<List<SendOperation>> = ops.asStateFlow()

    override suspend fun enqueue(draft: Draft, undoSeconds: Int): OperationId {
        val c = draft.conversationId ?: conversationFor(draft.recipients)
        val id = OperationId.new()
        val now = clock.now()
        val transport = when {
            draft.attachmentIds.isEmpty() -> Transport.SMS
            draft.mediaVariant == MediaVariant.Mms -> Transport.MMS
            else -> Transport.HD
        }
        val op = SendOperation(id, c, transport, draft.recipientMode, draft.recipients, draft.text, draft.attachmentIds,
            draft.subscriptionId, SendState.PENDING_UNDO, 1, now, undoDeadline = now.plusSeconds(undoSeconds.toLong()))
        ops.update { it + op }
        val media = draft.attachmentIds.mapIndexed { i, a -> AttachmentSummary(a, "image/jpeg", "Zdjęcie ${i + 1}") }
        history.update { all ->
            all + (c to all[c].orEmpty() + Message(MessageRef.Local(id), c, Direction.Outgoing, null, draft.text, now, true,
                draft.subscriptionId, DeliveryStatus.PendingUndo, id, attachments = media,
                hd = if (media.isNotEmpty() && draft.mediaVariant == MediaVariant.OriginalHd) HdInfo(media.size, draft.attachmentBytes) else null))
        }
        drafts.update { it - c }
        summaries.update { all -> all[c]?.let { all + (c to it.copy(lastActivity = now, preview = ConversationSummary.Preview.Text(draft.text))) } ?: all }
        if (!frozen) scope.launch { dispatchAfterUndo(id) }
        return id
    }

    override suspend fun cancel(id: OperationId): Boolean {
        val op = ops.value.firstOrNull { it.id == id } ?: return false
        val result = op.transition(op.state, op.version, SendState.CANCELLED)
        if (result !is Transition.Applied || !op.state.isCancellable) return false
        replace(result.operation)
        // Po anulowaniu przywracamy edytowalny szkic (specyfikacja 9, granica anulowania).
        op.conversationId?.let { c ->
            history.update { all -> all + (c to all[c].orEmpty().filterNot { it.operationId == id }) }
            drafts.update { it + (c to Draft(DraftId.new(), c, op.recipients, op.body, op.recipientMode, op.attachmentIds, op.subscriptionId, clock.now())) }
            refreshPreview(c)
        }
        return true
    }

    override suspend fun retry(id: OperationId): Boolean {
        val op = ops.value.firstOrNull { it.id == id } ?: return false
        if (op.state != SendState.FAILED && op.state != SendState.DISPATCH_UNCERTAIN) return false
        val result = op.transition(op.state, op.version, SendState.DISPATCHING) { it.copy(subscriptionId = it.subscriptionId ?: Fixture.SIM1.id) }
        if (result !is Transition.Applied) return false
        replace(result.operation)
        setStatus(op, DeliveryStatus.Sending)
        if (!frozen) scope.launch { completeDispatch(id) }
        return true
    }

    private suspend fun dispatchAfterUndo(id: OperationId) {
        val op = ops.value.firstOrNull { it.id == id } ?: return
        val deadline = op.undoDeadline ?: return
        delay(Duration.between(clock.now(), deadline).toMillis().coerceAtLeast(0))
        val current = ops.value.firstOrNull { it.id == id } ?: return
        val started = current.transition(SendState.PENDING_UNDO, current.version, SendState.DISPATCHING)
        if (started !is Transition.Applied) return // anulowane w międzyczasie
        replace(started.operation)
        setStatus(current, DeliveryStatus.Sending)
        completeDispatch(id)
    }

    private suspend fun completeDispatch(id: OperationId) {
        delay(600)
        advance(id, SendState.SENT, DeliveryStatus.Sent)
        delay(1200)
        advance(id, SendState.DELIVERED, DeliveryStatus.Delivered)
    }

    private fun advance(id: OperationId, state: SendState, status: DeliveryStatus) {
        val op = ops.value.firstOrNull { it.id == id } ?: return
        val r = op.transition(op.state, op.version, state)
        if (r is Transition.Applied) {
            replace(r.operation)
            setStatus(op, status)
        }
    }

    private fun replace(op: SendOperation) = ops.update { list -> list.map { if (it.id == op.id) op else it } }

    private fun setStatus(op: SendOperation, status: DeliveryStatus) {
        val c = op.conversationId ?: return
        history.update { all ->
            all + (c to all[c].orEmpty().map {
                if (it.operationId == op.id) it.copy(status = status, subscriptionId = it.subscriptionId ?: op.subscriptionId) else it
            })
        }
    }

    private fun refreshPreview(c: ConversationId) {
        val d = drafts.value[c]
        summaries.update { all ->
            val s = all[c] ?: return@update all
            val preview = when {
                d != null && d.text.isNotBlank() -> ConversationSummary.Preview.Draft(d.text)
                s.preview is ConversationSummary.Preview.Draft ->
                    history.value[c].orEmpty().lastOrNull()?.let { ConversationSummary.Preview.Text(it.body) } ?: ConversationSummary.Preview.Text("")
                else -> s.preview
            }
            all + (c to s.copy(preview = preview))
        }
    }

    // --- ScheduleRepository ------------------------------------------------------------------------

    override fun scheduled(): Flow<List<ScheduledItem>> = combine(schedules, ops) { list, current ->
        list.map { f -> ScheduledItem(f.schedule, current.firstOrNull { it.id == f.operation.id } ?: f.operation, f.title) }
    }

    override suspend fun schedule(
        draft: Draft,
        instant: java.time.Instant,
        local: java.time.LocalDateTime,
        zone: java.time.ZoneId,
        policy: LatenessPolicy,
    ): ScheduleId {
        val c = draft.conversationId ?: conversationFor(draft.recipients)
        val op = SendOperation(OperationId.new(), c, Transport.SMS, draft.recipientMode, draft.recipients, draft.text, draft.attachmentIds,
            draft.subscriptionId, SendState.SCHEDULED, 1, clock.now())
        val schedule = ScheduledMessage(ScheduleId.new(), op.id, instant, local, zone, policy, 1)
        ops.update { it + op }
        schedules.update { it + Fixture.ScheduledFixture(schedule, op, summaries.value[c]?.title ?: draft.recipients.joinToString { a -> a.raw }) }
        drafts.update { it - c }
        return schedule.id
    }
}

/** Uczestnicy znani fixture (kontakty demonstracyjne, bez kontaktów urządzenia). */
val Fixture.contacts: List<Participant>
    get() = listOf(mama, ola, tomek, kasia, marek, kuba) + brygada

fun Fixture.participantFor(address: Address): Participant =
    contacts.firstOrNull { it.address.normalized == address.normalized } ?: Participant(address)
