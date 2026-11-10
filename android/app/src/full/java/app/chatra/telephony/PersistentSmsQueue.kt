package app.chatra.telephony

import android.app.Activity
import android.app.PendingIntent
import android.content.*
import android.net.Uri
import android.provider.Settings
import android.provider.Telephony
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import androidx.work.*
import app.chatra.ChatraApplication
import app.chatra.core.database.*
import app.chatra.core.model.*
import app.chatra.di.FullTelephonyContainer
import app.chatra.platform.SystemPlatform
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.json.JSONArray

class PersistentSmsQueue(
    private val context: Context, private val db: ChatraDatabase, private val platform: SmsRoleGate,
    private val history: SystemHistory, private val sims: SimRepository, private val text: PlatformSmsText,
    private val drafts: RoomDrafts,
    private val transport: SmsTransport = NativeSmsTransport(text),
    private val work: SmsWork = PlatformSmsWork(context),
) : SendQueue {
    private val dao = db.dao()
    private val startup = CoroutineScope(SupervisorJob() + Dispatchers.IO).async(start = CoroutineStart.LAZY) { recover() }
    suspend fun awaitReady() { startup.await() }
    private fun boot() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private suspend fun requireSim(id: SubscriptionId) {
        check(context.smsSend(platform) && context.smsRead(platform)) { "Brak roli SMS lub uprawnienia." }
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) { "Brak dostępu do kart SIM." }
        check(context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty().any { it.subscriptionId == id.value }) { "Karta SIM jest niedostępna. Wybierz ją ponownie." }
    }
    override fun operations() = dao.operations().map { rows -> rows.map { row -> SendOperation(OperationId(row.id), row.conversationId?.let(::ConversationId), Transport.SMS,
        RecipientMode.valueOf(row.mode), addresses(row.recipients), row.body, emptyList(), SubscriptionId(row.subscriptionId), SendState.valueOf(row.state), row.version,
        Instant.ofEpochMilli(row.createdAt), Instant.ofEpochMilli(row.undoUntil), row.bootCount.toString()) } }

    override suspend fun enqueue(draft: Draft, undoSeconds: Int): OperationId = withContext(Dispatchers.IO) {
        awaitReady()
        require(draft.isSendable && draft.text.length <= 16_384 && draft.recipients.size <= 20 && draft.recipients.all { isOutgoingNumber(it.raw) })
        require(draft.attachmentIds.isEmpty() && draft.recipientMode != RecipientMode.GroupMms) { "MMS i załączniki wymagają CH032." }
        require(undoSeconds in SendingPrefs.UNDO_OPTIONS)
        val sim = requireNotNull(draft.subscriptionId)
        requireSim(sim)
        val card = sims.cards().first().first { it.id == sim }
        val recipients = draft.recipients.distinctBy { it.normalized }.map { address -> address to history.conversationFor(listOf(address)) }
        val now = System.currentTimeMillis()
        val row = OutgoingOperation(UUID.randomUUID().toString(), draft.id.value, draft.conversationId?.value ?: recipients.singleOrNull()?.second?.value,
            JSONArray(recipients.map { it.first.raw }).toString(), draft.text, draft.recipientMode.name, sim.value, card.label, "PENDING_UNDO", 0, now, now + undoSeconds * 1000L, boot())
        val id = db.withTransaction {
            val existing = dao.operationForDraft(draft.id.value)
            if (existing != null) return@withTransaction existing.id
            check(dao.insertOperation(row) != -1L)
            recipients.forEach { (address, conversation) -> dao.recipient(OutgoingRecipient(row.id, address.raw, conversation.value, "PENDING_UNDO")) }
            dao.deleteDraft(draft.id.value)
            row.id
        }
        schedule(id)
        OperationId(id)
    }
    private suspend fun schedule(id: String) {
        val row = dao.operation(id) ?: return
        work.schedule(id, row.undoUntil)
    }
    suspend fun recover(initial: Boolean = true) {
        for (row in dao.recoverable()) {
            if (row.state == "DISPATCHING" && initial) dao.transition(row.id, row.state, row.version, "DISPATCH_UNCERTAIN")
            else if (row.state == "DISPATCHING") continue
            else if (row.bootCount != boot()) dao.transition(row.id, row.state, row.version, "MISSED_NEEDS_DECISION")
            else schedule(row.id)
        }
    }
    override suspend fun cancel(id: OperationId): Boolean = withContext(Dispatchers.IO) {
        val cancelled = db.withTransaction {
            val row = dao.operation(id.value) ?: return@withTransaction false
            if (row.state != "PENDING_UNDO" || dao.transition(row.id, row.state, row.version, "CANCELLED") != 1) return@withTransaction false
            drafts.save(Draft(DraftId(row.draftKey), row.conversationId?.let(::ConversationId), addresses(row.recipients), row.body, RecipientMode.valueOf(row.mode), emptyList(), SubscriptionId(row.subscriptionId), Instant.now(), subscriptionLabel = row.subscriptionLabel))
            true
        }
        if (cancelled) work.cancel(id.value)
        cancelled
    }
    override suspend fun retry(id: OperationId) = false // świadome ponawianie i rekoncyliacja w CH027.

    suspend fun dispatch(id: String) = withContext(Dispatchers.IO) {
        awaitReady()
        val row = dao.operation(id) ?: return@withContext
        if (row.state != "PENDING_UNDO") return@withContext
        if (row.bootCount != boot() || !context.smsSend(platform)) { dao.transition(id, row.state, row.version, "MISSED_NEEDS_DECISION"); return@withContext }
        if (dao.transition(id, row.state, row.version, "DISPATCHING") != 1) return@withContext
        for (recipient in dao.recipients(id)) {
            var enteredTransport = false
            try {
                val sim = SubscriptionId(row.subscriptionId)
                requireSim(sim)
                val parts = text.divide(row.body, sim)
                require(parts.isNotEmpty())
                require(parts.joinToString("") == row.body) { "Platforma zmieniłaby treść. Wymagany świadomy podgląd użytkownika." }
                val attempt = OutgoingAttempt(UUID.randomUUID().toString(), id, recipient.address, sim.value, row.subscriptionLabel, System.currentTimeMillis(), UUID.randomUUID().toString(), parts.size)
                db.withTransaction { dao.attempt(attempt); dao.parts(parts.indices.map { OutgoingPart(attempt.id, it) }) }
                val meta = dao.conversation(recipient.conversationId) ?: error("Brak rozmowy.")
                val values = ContentValues().apply {
                    put("address", recipient.address); put("body", row.body); put("thread_id", meta.threadId)
                    put("date", row.createdAt); put("sub_id", row.subscriptionId); put("read", 1); put("seen", 1)
                    put("type", Telephony.Sms.MESSAGE_TYPE_OUTBOX); put("status", Telephony.Sms.STATUS_PENDING)
                }
                requireSim(sim)
                val providerId = context.contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull() ?: error("Zapis providera nie powiódł się.")
                dao.recipient(recipient.copy(state = "DISPATCHING", providerId = providerId))
                val sent = ArrayList(parts.indices.map { callback(attempt, it, false) })
                val delivery = ArrayList(parts.indices.map { callback(attempt, it, true) })
                requireSim(sim)
                enteredTransport = true
                transport.send(sim, recipient.address, parts, sent, delivery)
            } catch (error: Exception) {
                val state = if (!enteredTransport || error is SecurityException || error is IllegalArgumentException) "FAILED" else "DISPATCH_UNCERTAIN"
                val latest = dao.recipients(id).first { it.address == recipient.address }
                if (latest.state !in listOf("SENT", "DELIVERED")) {
                    dao.recipient(latest.copy(state = state))
                    updateProvider(latest, row, state)
                }
            }
        }
        overall(id)
        history.refresh()
    }

    private fun callback(attempt: OutgoingAttempt, part: Int, delivered: Boolean): PendingIntent {
        val uri = Uri.Builder().scheme("chatra-callback").authority("sms").appendPath(attempt.id).appendPath(part.toString()).appendPath(if (delivered) "delivered" else "sent").appendPath(attempt.callbackToken).build()
        val intent = Intent(context, SmsStatusReceiver::class.java).setAction("app.chatra.SMS_STATUS").setData(uri)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or if (delivered) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE)
    }
    suspend fun callback(intent: Intent, resultCode: Int) {
        awaitReady()
        val uri = intent.data ?: return
        if (intent.action != "app.chatra.SMS_STATUS" || uri.scheme != "chatra-callback" || uri.host != "sms") return
        val path = uri.pathSegments
        if (path.size != 4) return
        val attempt = dao.attempt(path[0]) ?: return
        val part = path[1].toIntOrNull()?.takeIf { it in 0 until attempt.partCount } ?: return
        if (path[3] != attempt.callbackToken) return
        db.withTransaction {
            if (path[2] == "sent") dao.sent(attempt.id, part, if (resultCode == Activity.RESULT_OK) "Sent" else "Failed", resultCode)
            else if (path[2] == "delivered") {
                val pdu = intent.getByteArrayExtra("pdu") ?: return@withTransaction
                val status = SmsMessage.createFromPdu(pdu, intent.getStringExtra("format") ?: "3gpp")?.status ?: return@withTransaction
                if (status in 0..31) dao.delivered(attempt.id, part, "Delivered")
                else if (status >= 64) dao.delivered(attempt.id, part, "Failed")
            }
            val parts = dao.parts(attempt.id).map { SendPart(AttemptId(attempt.id), it.partIndex, attempt.partCount, PartStatus.valueOf(it.sent), PartDelivery.valueOf(it.delivery)) }
            val next = aggregate(parts)?.name ?: "DISPATCHING"
            val recipient = dao.recipients(attempt.operationId).firstOrNull { it.address == attempt.address } ?: return@withTransaction
            dao.recipient(recipient.copy(state = if (recipient.state == "DELIVERED") "DELIVERED" else next))
        }
        val recipient = dao.recipients(attempt.operationId).first { it.address == attempt.address }
        val row = dao.operation(attempt.operationId) ?: return
        updateProvider(recipient, row, recipient.state)
        overall(row.id)
        history.refresh()
    }
    private suspend fun overall(id: String) {
        val states = dao.recipients(id).map { it.state }
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
        if (row.state == "DISPATCHING") dao.transition(id, row.state, row.version, "DISPATCH_UNCERTAIN")
    }
    private suspend fun updateProvider(recipient: OutgoingRecipient, operation: OutgoingOperation, state: String) {
        val id = recipient.providerId ?: return
        if (!context.smsRead(platform)) return
        val type = when (state) { "SENT", "DELIVERED" -> 2; "FAILED", "PARTIALLY_SENT" -> 5; else -> 4 }
        val values = ContentValues().apply { put("type", type); put("status", if (state == "DELIVERED") 0 else if (state == "FAILED") 64 else 32) }
        try { context.contentResolver.update(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id), values, "address=? AND body=? AND sub_id=?", arrayOf(recipient.address, operation.body, operation.subscriptionId.toString())) }
        catch (_: SecurityException) { /* Rola mogła zostać cofnięta pomiędzy kontrolą i wywołaniem. */ }
    }
}

class SmsDispatchWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("operation") ?: return Result.failure()
        val queue = ((applicationContext as ChatraApplication).container(false) as FullTelephonyContainer).smsQueue
        return try { queue.dispatch(id); Result.success() }
        catch (cancelled: CancellationException) { withContext(NonCancellable) { queue.interrupted(id) }; throw cancelled }
        catch (_: Exception) { queue.interrupted(id); Result.failure() }
    }
}
class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = resultCode
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { ((context.applicationContext as ChatraApplication).container(false) as FullTelephonyContainer).smsQueue.callback(intent, result) }
            catch (_: Exception) { /* Nie zgadujemy wyniku; po restarcie DISPATCHING staje się niepewne. */ }
            finally { pending.finish() }
        }
    }
}
