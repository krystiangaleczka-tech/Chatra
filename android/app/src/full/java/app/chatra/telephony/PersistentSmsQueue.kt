package app.chatra.telephony

import android.Manifest
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.provider.Telephony
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import androidx.work.*
import app.chatra.ChatraApplication
import app.chatra.core.database.*
import app.chatra.core.model.*
import app.chatra.di.FullTelephonyContainer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import java.time.Instant
import java.util.UUID

interface SmsQueueTime {
    fun wall(): Long
    fun elapsed(): Long
    fun boot(): Int
}
private class AndroidSmsTime(private val context: Context) : SmsQueueTime {
    override fun wall() = System.currentTimeMillis()
    override fun elapsed() = SystemClock.elapsedRealtime()
    override fun boot() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
}

class PersistentSmsQueue(
    private val context: Context, private val db: ChatraDatabase, private val platform: SmsRoleGate,
    private val history: SystemHistory, private val sims: SimRepository, private val text: PlatformSmsText,
    private val drafts: RoomDrafts,
    private val transport: SmsTransport = NativeSmsTransport(text),
    private val work: SmsWork = PlatformSmsWork(context),
    private val time: SmsQueueTime = AndroidSmsTime(context),
) : SendQueue {
    private class RetryInvalidated : IllegalStateException()
    private val dao = db.dao()
    private val startup = CoroutineScope(SupervisorJob() + Dispatchers.IO).async(start = CoroutineStart.LAZY) { recover() }
    suspend fun awaitReady() { startup.await() }
    private suspend fun requireSim(id: SubscriptionId) {
        check(context.smsSend(platform) && context.smsRead(platform)) { "Brak roli SMS lub uprawnienia." }
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) { "Brak dostępu do kart SIM." }
        check(context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty().any { it.subscriptionId == id.value }) { "Karta SIM jest niedostępna. Wybierz ją ponownie." }
    }
    private suspend fun certainFailures(id: String): List<OutgoingRecipient> {
        val attempts = dao.attempts(id)
        return dao.recipients(id).filter { recipient ->
            if (recipient.state != "FAILED") false else {
                val attempt = attempts.lastOrNull { it.address == recipient.address }
                if (attempt == null) true
                else {
                    val parts = dao.parts(attempt.id)
                    attempt.phase != "LEGACY_UNKNOWN" && parts.size == attempt.partCount && parts.all { it.sent == "Failed" && it.delivery != "Delivered" }
                }
            }
        }
    }
    override fun operations() = dao.operations().map { rows -> rows.map { row ->
        val attempts = dao.attempts(row.id)
        SendOperation(OperationId(row.id), row.conversationId?.let(::ConversationId), Transport.SMS,
            RecipientMode.valueOf(row.mode), addresses(row.recipients), row.body, emptyList(), SubscriptionId(row.subscriptionId), SendState.valueOf(row.state), row.version,
            Instant.ofEpochMilli(row.createdAt), Instant.ofEpochMilli(row.undoUntil), row.bootCount.toString(),
            canRetry = row.state in listOf("FAILED", "PARTIALLY_SENT") && dao.retryChild(row.id) == null && certainFailures(row.id).isNotEmpty(),
            deliveryFailed = attempts.any { a -> dao.parts(a.id).any { it.delivery == "Failed" } },
            undoElapsedDeadline = row.undoElapsed, retryOf = row.retryOf?.let(::OperationId))
    } }

    override suspend fun enqueue(draft: Draft, undoSeconds: Int): OperationId = withContext(Dispatchers.IO) {
        awaitReady()
        require(draft.isSendable && draft.text.length <= 16_384 && draft.recipients.size <= 20 && draft.recipients.all { isOutgoingNumber(it.raw) })
        require(draft.attachmentIds.isEmpty() && draft.recipientMode != RecipientMode.GroupMms) { "MMS i załączniki wymagają CH032." }
        require(undoSeconds in SendingPrefs.UNDO_OPTIONS)
        val sim = requireNotNull(draft.subscriptionId)
        requireSim(sim)
        val card = sims.cards().first().first { it.id == sim }
        val recipients = draft.recipients.distinctBy { recipientKey(it.raw) }.map { it to history.conversationFor(listOf(it)) }
        val now = time.wall()
        val row = OutgoingOperation(UUID.randomUUID().toString(), draft.id.value, draft.conversationId?.value ?: recipients.singleOrNull()?.second?.value,
            JSONArray(recipients.map { it.first.raw }).toString(), draft.text, draft.recipientMode.name, sim.value, card.label, "PENDING_UNDO", 0, now, now + undoSeconds * 1000L, time.boot(),
            time.elapsed() + undoSeconds * 1000L, DraftCodec.encode(draft))
        val id = db.withTransaction {
            dao.operationForDraft(draft.id.value)?.let { return@withTransaction it.id }
            check(!drafts.isClosed(draft.id)) { "Ten szkic został już przyjęty lub zamknięty." }
            check(dao.insertOperation(row) != -1L)
            recipients.forEach { (address, conversation) -> dao.recipient(OutgoingRecipient(row.id, address.raw, conversation.value, "PENDING_UNDO")) }
            drafts.consume(draft)
            row.id
        }
        schedule(id)
        OperationId(id)
    }
    private suspend fun schedule(id: String) {
        val row = dao.operation(id) ?: return
        // WorkManager delay is merely a wake-up; dispatch checks the monotonic deadline again.
        val remaining = undoRemaining(row.undoElapsed, row.bootCount, time.boot(), time.elapsed()) ?: return
        work.schedule(id, time.wall() + remaining)
    }
    suspend fun remaining(id: String): Long? {
        val row = dao.operation(id)?.takeIf { it.state == "PENDING_UNDO" } ?: return null
        return undoRemaining(row.undoElapsed, row.bootCount, time.boot(), time.elapsed())
    }
    suspend fun recover(initial: Boolean = true) = withContext(Dispatchers.IO) {
        for (row in dao.operations().first()) {
            if (row.state == "PENDING_UNDO") {
                if (undoRemaining(row.undoElapsed, row.bootCount, time.boot(), time.elapsed()) == null)
                    dao.transition(row.id, row.state, row.version, "MISSED_NEEDS_DECISION")
                else schedule(row.id)
            } else if (row.state == "DISPATCHING" && initial) {
                interrupted(row.id)
            }
            if (row.state in listOf("DISPATCHING", "DISPATCH_UNCERTAIN", "SENT", "PARTIALLY_SENT", "FAILED")) reconcile(OperationId(row.id))
        }
    }
    override suspend fun cancel(id: OperationId): Boolean = withContext(Dispatchers.IO) {
        awaitReady()
        val cancelled = db.withTransaction {
            val row = dao.operation(id.value) ?: return@withTransaction false
            if (row.state != "PENDING_UNDO" || dao.transition(row.id, row.state, row.version, "CANCELLED") != 1) return@withTransaction false
            drafts.restore(row) // Fresh identity; current and restored drafts both survive.
            true
        }
        if (cancelled) work.cancel(id.value)
        cancelled
    }
    override suspend fun copyToDraft(id: OperationId): Draft? = withContext(Dispatchers.IO) {
        db.withTransaction { dao.operation(id.value)?.let { drafts.restore(it) } }
    }
    override suspend fun retry(id: OperationId): Boolean {
        val row = dao.operation(id.value) ?: return false
        return retry(id, SubscriptionId(row.subscriptionId))
    }
    override suspend fun retry(id: OperationId, sim: SubscriptionId): Boolean = withContext(Dispatchers.IO) {
        awaitReady()
        reconcile(id)
        requireSim(sim)
        val card = sims.cards().first().first { it.id == sim }
        val child = db.withTransaction {
            val row = dao.operation(id.value) ?: return@withTransaction null
            if (row.state !in listOf("FAILED", "PARTIALLY_SENT") || dao.retryChild(row.id) != null) return@withTransaction null
            val failed = certainFailures(row.id)
            if (failed.isEmpty()) return@withTransaction null
            val now = time.wall()
            val retry = row.copy(id = UUID.randomUUID().toString(), draftKey = "retry:${row.id}", recipients = JSONArray(failed.map { it.address }).toString(),
                subscriptionId = sim.value, subscriptionLabel = card.label, state = "PENDING_UNDO", version = 0, createdAt = now,
                undoUntil = now + 3000, undoElapsed = time.elapsed() + 3000, bootCount = time.boot(), retryOf = row.id,
                snapshot = DraftCodec.encode(drafts.snapshot(row).copy(recipients = failed.map { Address(it.address) }, subscriptionId = sim, subscriptionLabel = card.label)))
            if (dao.insertOperation(retry) == -1L) return@withTransaction null
            failed.forEach { dao.recipient(it.copy(operationId = retry.id, state = "PENDING_UNDO", providerId = null)) }
            retry.id
        } ?: return@withContext false
        schedule(child)
        true
    }

    suspend fun dispatch(id: String) = withContext(Dispatchers.IO) {
        awaitReady()
        val row = db.withTransaction {
            val row = dao.operation(id) ?: return@withTransaction null
            if (row.state != "PENDING_UNDO") return@withTransaction null
            val left = undoRemaining(row.undoElapsed, row.bootCount, time.boot(), time.elapsed())
            if (left == null || !context.smsSend(platform)) {
                dao.transition(id, row.state, row.version, "MISSED_NEEDS_DECISION"); return@withTransaction null
            }
            if (left > 0) return@withTransaction null
            val retryParent = row.retryOf
            if (retryParent != null && dao.recipients(retryParent).any { parent -> addresses(row.recipients).any { it.raw == parent.address } && parent.state != "FAILED" }) {
                dao.transition(id, row.state, row.version, "CANCELLED"); return@withTransaction null
            }
            if (dao.transition(id, row.state, row.version, "DISPATCHING") != 1) null else row
        } ?: return@withContext
        for (recipient in dao.recipients(id)) {
            var enteredTransport = false
            var attempt: OutgoingAttempt? = null
            try {
                val sim = SubscriptionId(row.subscriptionId)
                requireSim(sim)
                val parts = text.divide(row.body, sim)
                require(parts.isNotEmpty() && parts.joinToString("") == row.body) { "Platforma zmieniłaby treść." }
                attempt = OutgoingAttempt(UUID.randomUUID().toString(), id, recipient.address, sim.value, row.subscriptionLabel, time.wall(), UUID.randomUUID().toString(), parts.size, "PREPARED")
                db.withTransaction { dao.attempt(attempt); dao.parts(parts.indices.map { OutgoingPart(attempt.id, it) }) }
                val meta = dao.conversation(recipient.conversationId) ?: error("Brak rozmowy.")
                val values = ContentValues().apply {
                    put("address", recipient.address); put("body", row.body); put("thread_id", meta.threadId)
                    put("date", row.createdAt); put("sub_id", row.subscriptionId); put("read", 1); put("seen", 1)
                    put("type", Telephony.Sms.MESSAGE_TYPE_OUTBOX); put("status", Telephony.Sms.STATUS_PENDING)
                }
                requireSim(sim)
                val providerId = context.contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull() ?: error("Zapis providera nie powiódł się.")
                db.withTransaction { dao.attemptProvider(attempt.id, providerId); dao.recipient(recipient.copy(state = "DISPATCHING", providerId = providerId)) }
                val sent = ArrayList(parts.indices.map { callback(attempt, it, false) })
                val delivery = ArrayList(parts.indices.map { callback(attempt, it, true) })
                requireSim(sim)
                val allowed = db.withTransaction {
                    // A late positive callback on the parent invalidates this retry before submission.
                    val parent = row.retryOf?.let { dao.recipients(it).firstOrNull { r -> r.address == recipient.address } }
                    if (parent != null && parent.state != "FAILED") false
                    else { dao.phase(attempt.id, "SUBMITTING"); true }
                }
                if (!allowed) throw RetryInvalidated()
                enteredTransport = true
                transport.send(sim, recipient.address, parts, sent, delivery)
                dao.phase(attempt.id, "RETURNED")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                val certain = error !is RetryInvalidated && (!enteredTransport || error is SecurityException || error is IllegalArgumentException)
                db.withTransaction {
                    attempt?.let { a ->
                        dao.phase(a.id, if (certain) "NOT_SUBMITTED" else "SUBMITTING")
                        dao.parts(a.id).filter { it.sent == "Pending" }.forEach { dao.sent(a.id, it.partIndex, if (certain) "Failed" else "Unknown", 0) }
                    }
                    val latest = dao.recipients(id).first { it.address == recipient.address }
                    if (attempt != null && error !is RetryInvalidated) refreshRecipient(attempt)
                    else if (latest.state !in listOf("SENT", "DELIVERED")) dao.recipient(latest.copy(state = if (certain) "FAILED" else "DISPATCH_UNCERTAIN"))
                }
            }
        }
        db.withTransaction { overall(id) }
        publish(id)
    }

    private fun callback(attempt: OutgoingAttempt, part: Int, delivered: Boolean): PendingIntent {
        val uri = Uri.Builder().scheme("chatra-callback").authority("sms").appendPath(attempt.id).appendPath(part.toString()).appendPath(if (delivered) "delivered" else "sent").appendPath(attempt.callbackToken).build()
        return PendingIntent.getBroadcast(context, 0, Intent(context, SmsStatusReceiver::class.java).setAction("app.chatra.SMS_STATUS").setData(uri),
            PendingIntent.FLAG_UPDATE_CURRENT or if (delivered) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE)
    }
    suspend fun callback(intent: Intent, resultCode: Int) {
        awaitReady()
        val uri = intent.data ?: return
        if (intent.action != "app.chatra.SMS_STATUS" || uri.scheme != "chatra-callback" || uri.host != "sms") return
        val path = uri.pathSegments
        if (path.size != 4) return
        val attempt = dao.attempt(path[0]) ?: return
        val part = path[1].toIntOrNull()?.takeIf { it in 0 until attempt.partCount } ?: return
        if (path[3] != attempt.callbackToken || path[2] !in listOf("sent", "delivered")) return
        db.withTransaction {
            if (path[2] == "sent") dao.sent(attempt.id, part, submissionResult(resultCode).name, resultCode)
            else {
                val pdu = intent.getByteArrayExtra("pdu") ?: return@withTransaction
                val format = intent.getStringExtra("format") ?: "3gpp"
                val status = SmsMessage.createFromPdu(pdu, format)?.status ?: return@withTransaction
                // TP-ST=0 proves receipt. 1/2 and pending/reserved values do not. CDMA needs its own decoder.
                if (format == "3gpp" && status == 0) dao.delivered(attempt.id, part, "Delivered")
                else if (format == "3gpp" && status in 64..127) dao.delivered(attempt.id, part, "Failed")
            }
            refreshRecipient(attempt)
            overall(attempt.operationId)
        }
        publish(attempt.operationId)
    }
    private suspend fun refreshRecipient(attempt: OutgoingAttempt) {
        val parts = dao.parts(attempt.id).map { SendPart(AttemptId(attempt.id), it.partIndex, attempt.partCount, PartStatus.valueOf(it.sent), PartDelivery.valueOf(it.delivery)) }
        val next = aggregate(parts)?.name ?: "DISPATCHING"
        val recipient = dao.recipients(attempt.operationId).firstOrNull { it.address == attempt.address } ?: return
        val keep = recipient.state == "DELIVERED" || (recipient.state == "SENT" && next != "DELIVERED")
        dao.recipient(recipient.copy(state = if (keep) recipient.state else next))
        if (next in listOf("SENT", "DELIVERED", "PARTIALLY_SENT")) {
            dao.retryChild(attempt.operationId)?.let { child ->
                if (child.state == "PENDING_UNDO") dao.transition(child.id, child.state, child.version, "CANCELLED")
            }
        }
    }
    private suspend fun overall(id: String) {
        val states = dao.recipients(id).map { it.state }
        if (states.isEmpty()) return
        val next = when {
            states.all { it == "DELIVERED" } -> "DELIVERED"
            states.all { it in listOf("SENT", "DELIVERED") } -> "SENT"
            "DISPATCH_UNCERTAIN" in states -> "DISPATCH_UNCERTAIN"
            states.any { it in listOf("PENDING_UNDO", "DISPATCHING") } -> "DISPATCHING"
            states.any { it in listOf("SENT", "DELIVERED", "PARTIALLY_SENT") } -> "PARTIALLY_SENT"
            else -> "FAILED"
        }
        dao.result(id, next)
    }
    suspend fun interrupted(id: String) {
        val row = dao.operation(id) ?: return
        if (row.state == "DISPATCHING") db.withTransaction {
            for (recipient in dao.recipients(id)) {
                val attempt = dao.attempts(id).lastOrNull { it.address == recipient.address }
                if (attempt == null || attempt.phase in listOf("PREPARED", "NOT_SUBMITTED")) {
                    if (attempt != null) dao.parts(attempt.id).filter { it.sent == "Pending" }.forEach { dao.sent(attempt.id, it.partIndex, "Failed", 0) }
                    if (recipient.state !in listOf("SENT", "DELIVERED")) dao.recipient(recipient.copy(state = "FAILED"))
                } else {
                    dao.parts(attempt.id).filter { it.sent == "Pending" }.forEach { dao.sent(attempt.id, it.partIndex, "Unknown", 0) }
                    refreshRecipient(attempt)
                }
            }
            overall(id)
        }
    }
    /** Only positive evidence from this exact owned provider row; absence/failure is not proof of no send. */
    override suspend fun reconcile(id: OperationId) = withContext(Dispatchers.IO) {
        if (!context.smsRead(platform)) return@withContext
        val row = dao.operation(id.value) ?: return@withContext
        for (attempt in dao.attempts(row.id)) {
            if (attempt.phase == "RETURNED" && row.state in listOf("DISPATCHING", "DISPATCH_UNCERTAIN")) db.withTransaction {
                dao.parts(attempt.id).filter { it.sent == "Pending" }.forEach { dao.sent(attempt.id, it.partIndex, "Unknown", 0) }
                refreshRecipient(attempt); overall(row.id)
            }
            val provider = attempt.providerId ?: continue
            if (attempt.phase !in listOf("SUBMITTING", "RETURNED", "LEGACY_UNKNOWN")) continue
            val proof = try {
                context.contentResolver.query(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, provider), arrayOf("type", "status"),
                    "address=? AND body=? AND sub_id=? AND date=?", arrayOf(attempt.address, row.body, attempt.subscriptionId.toString(), row.createdAt.toString()), null)?.use { c ->
                    if (c.moveToFirst() && c.getInt(0) == Telephony.Sms.MESSAGE_TYPE_SENT) c.getInt(1) else null
                }
            } catch (_: RuntimeException) { null }
            if (proof != null) db.withTransaction {
                dao.parts(attempt.id).forEach { part ->
                    dao.sent(attempt.id, part.partIndex, "Sent", -1)
                    if (proof == Telephony.Sms.STATUS_COMPLETE) dao.delivered(attempt.id, part.partIndex, "Delivered")
                }
                refreshRecipient(attempt); overall(row.id)
            }
        }
        publish(row.id)
    }
    private suspend fun publish(id: String) {
        val row = dao.operation(id) ?: return
        for (recipient in dao.recipients(id)) updateProvider(recipient, row)
        history.refresh()
    }
    private fun updateProvider(recipient: OutgoingRecipient, operation: OutgoingOperation) {
        val id = recipient.providerId ?: return
        if (!context.smsRead(platform)) return
        val state = recipient.state
        val type = when (state) { "SENT", "DELIVERED" -> 2; "FAILED", "PARTIALLY_SENT" -> 5; else -> 4 }
        val values = ContentValues().apply { put("type", type); put("status", if (state == "DELIVERED") 0 else if (state == "FAILED") 64 else 32) }
        // Provider updates can arrive out of order. Never overwrite positive sent/delivered evidence.
        val guard = when (state) { "DELIVERED" -> ""; "SENT" -> " AND NOT(type=2 AND status=0)"; else -> " AND type!=2" }
        try { context.contentResolver.update(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id), values,
            "address=? AND body=? AND sub_id=? AND date=?$guard", arrayOf(recipient.address, operation.body, operation.subscriptionId.toString(), operation.createdAt.toString())) }
        catch (_: RuntimeException) { /* Provider/role can become unavailable; durable evidence remains in Room. */ }
    }
}

class SmsDispatchWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("operation") ?: return Result.failure()
        val queue = ((applicationContext as ChatraApplication).container(false) as FullTelephonyContainer).smsQueue
        return try {
            queue.awaitReady()
            while (true) { val left = queue.remaining(id) ?: break; if (left == 0L) break; delay(left.coerceAtMost(1000)) }
            queue.dispatch(id); Result.success()
        } catch (cancelled: CancellationException) { withContext(NonCancellable) { queue.interrupted(id) }; throw cancelled }
        catch (_: Exception) { queue.interrupted(id); Result.failure() }
    }
}
class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = resultCode
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { ((context.applicationContext as ChatraApplication).container(false) as FullTelephonyContainer).smsQueue.callback(intent, result) }
            catch (_: Exception) { /* Persisted evidence is reconciled after restart; never automatically resend. */ }
            finally { pending.finish() }
        }
    }
}
