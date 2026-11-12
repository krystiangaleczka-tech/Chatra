package app.chatra.telephony

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import app.chatra.core.database.ChatraDatabase
import app.chatra.core.database.ConversationMeta
import app.chatra.core.database.MediaRef
import app.chatra.core.database.ProviderMessageRef as IndexRef
import app.chatra.core.model.*
import app.chatra.platform.SystemPlatform
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.time.Instant
import java.util.UUID

internal fun Context.smsRead(platform: SmsRoleGate) = platform.isHeld() && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
internal fun Context.smsSend(platform: SmsRoleGate) = platform.isHeld() && ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
internal fun Cursor.str(name: String): String = getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }?.let(::getString).orEmpty()
internal fun Cursor.long(name: String): Long = getColumnIndex(name).takeIf { it >= 0 && !isNull(it) }?.let(::getLong) ?: 0
internal fun addresses(json: String): List<Address> = JSONArray(json).let { array -> (0 until array.length()).map { Address(array.getString(it)) } }

/** Odtwarzalna projekcja referencji. Nigdy nie kopiuje treści historii do Room. */
class SystemHistory(
    private val context: Context, private val db: ChatraDatabase, private val platform: SystemPlatform,
    private val contacts: ContactsDirectory, private val scope: CoroutineScope,
) : ConversationRepository {
    private val resolver = context.contentResolver
    internal val indexChanges = MutableStateFlow(0)
    private val dao = db.dao()
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val status = MutableStateFlow<HistoryStatus>(HistoryStatus.Loading)
    private val pages = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val mutation = Mutex()
    private var previousInbox: List<ConversationSummary> = emptyList()
    private val activeStates = setOf("PENDING_UNDO", "DISPATCHING", "DISPATCH_UNCERTAIN", "MISSED_NEEDS_DECISION")
    internal var diagnostic: String? = null
        private set
    private fun diagnostic(error: Throwable) { diagnostic = error.javaClass.simpleName + " at " + error.stackTrace.firstOrNull { it.className.startsWith("app.chatra") } }
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { requests.trySend(Unit) } }

    init {
        scope.coroutineContext[Job]?.invokeOnCompletion { resolver.unregisterContentObserver(observer) }
        scope.launch(Dispatchers.IO) {
            var observing = false
            platform.changes.collect {
                if (context.smsRead(platform) && !observing) {
                    try { resolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer); resolver.registerContentObserver(Telephony.Mms.CONTENT_URI, true, observer); resolver.registerContentObserver(Uri.parse("content://mms-sms"), true, observer); observing = true }
                    catch (_: SecurityException) { resolver.unregisterContentObserver(observer) }
                } else if (!context.smsRead(platform) && observing) { resolver.unregisterContentObserver(observer); observing = false }
                requests.trySend(Unit)
            }
        }
        scope.launch(Dispatchers.IO) { for (request in requests) reconcile() }
        scope.launch(Dispatchers.IO) { contacts.suggestions().drop(1).collect { requests.trySend(Unit) } }
    }
    override fun historyStatus(): Flow<HistoryStatus> = status
    override suspend fun refresh() { requests.send(Unit) }
    private fun requireRead() { check(context.smsRead(platform)) { "Brak roli SMS lub dostępu do historii." } }

    private suspend fun reconcile() = mutation.withLock {
        if (!context.smsRead(platform)) { status.value = HistoryStatus.PermissionRequired; return@withLock }
        status.value = HistoryStatus.Loading
        try {
            val generation = UUID.randomUUID().toString()
            val threadCache = mutableMapOf<Long, List<Address>>()
            for (kind in ProviderTransport.entries) {
                var after = -1L
                while (true) {
                    requireRead()
                    val refs = mutableListOf<IndexRef>()
                    val uri = if (kind == ProviderTransport.SMS) Telephony.Sms.CONTENT_URI else Telephony.Mms.CONTENT_URI
                    val type = if (kind == ProviderTransport.SMS) "type" else "msg_box"
                    val args = Bundle().apply {
                        putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, "_id > ? AND $type != 3")
                        putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(after.toString()))
                        putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf("_id"))
                        putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, android.content.ContentResolver.QUERY_SORT_DIRECTION_ASCENDING)
                        putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 200)
                    }
                    val columns = arrayOf("_id", "thread_id", "date", "read", type) + if (kind == ProviderTransport.SMS) arrayOf("address") else emptyArray()
                    val cursor = resolver.query(uri, columns, args, null) ?: error("Provider nie zwrócił historii.")
                    cursor.use { c ->
                        while (refs.size < 200 && c.moveToNext()) {
                            requireRead()
                            val providerId = c.long("_id"); check(providerId > after) { "Provider nie obsługuje stronicowania." }; after = providerId
                            val thread = c.long("thread_id")
                            val participants = threadCache.getOrPut(thread) { threadAddresses(thread).ifEmpty { if (kind == ProviderTransport.SMS) listOf(Address(c.str("address"))) else mmsAddresses(providerId) } }
                            val meta = ensureConversation(thread, participants)
                            refs += IndexRef(kind.name, providerId, meta.id, c.long("date") * if (kind == ProviderTransport.MMS) 1000 else 1, c.long(type) == 1L, c.long("read") != 0L, generation)
                        }
                    }
                    if (refs.isEmpty()) break
                    db.withTransaction { requireRead(); dao.upsertReferences(refs) }
                    if (kind == ProviderTransport.MMS) refs.forEach { indexMmsParts(it) }
                }
            }
            db.withTransaction { requireRead(); dao.sweep(generation) }
            status.value = HistoryStatus.Ready
            indexChanges.value++
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SecurityException) { status.value = HistoryStatus.PermissionRequired }
        catch (error: Exception) { diagnostic(error); status.value = if (context.smsRead(platform)) HistoryStatus.Error("Nie udało się odświeżyć historii. Poprzedni indeks pozostaje dostępny.") else HistoryStatus.PermissionRequired }
    }

    private fun threadAddresses(thread: Long): List<Address> {
        val uri = Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build()
        val ids = resolver.query(uri, arrayOf("recipient_ids"), "_id=?", arrayOf(thread.toString()), null)?.use { c -> if (c.moveToFirst()) c.str("recipient_ids").split(' ').mapNotNull(String::toLongOrNull) else emptyList() }.orEmpty()
        if (ids.isEmpty()) return emptyList()
        val out = mutableListOf<Address>()
        resolver.query(Uri.parse("content://mms-sms/canonical-addresses"), arrayOf("address"), "_id IN (${ids.joinToString { "?" }})", ids.map(Long::toString).toTypedArray(), null)?.use { c -> while (c.moveToNext()) out += Address(c.str("address")) }
        return out
    }
    private fun mmsAddresses(id: Long): List<Address> {
        val out = mutableListOf<Address>()
        resolver.query(Uri.parse("content://mms/$id/addr"), arrayOf("address"), null, null, null)?.use { c -> while (c.moveToNext()) c.str("address").takeIf { it.isNotBlank() && it != "insert-address-token" }?.let { out += Address(it) } }
        return out.distinctBy { it.normalized }
    }
    internal suspend fun ensureConversation(thread: Long, recipients: List<Address>): ConversationMeta {
        val key = participantsKey(recipients)
        dao.findConversation(thread, key)?.let { return it }
        val row = ConversationMeta(UUID.randomUUID().toString(), thread, key, JSONArray(recipients.map { it.raw }).toString())
        dao.insertConversation(row)
        return dao.findConversation(thread, key) ?: error("Nie udało się zapisać referencji rozmowy.")
    }
    private suspend fun indexMmsParts(ref: IndexRef) {
        requireRead()
        val parts = mutableListOf<MediaRef>()
        resolver.query(Uri.parse("content://mms/${ref.providerId}/part"), arrayOf("_id", "ct", "name", "cl"), null, null, null)?.use { c ->
            while (c.moveToNext()) if (c.str("ct").let { it.startsWith("image/") || it.startsWith("video/") || it.startsWith("audio/") || it == "application/pdf" }) {
                val id = c.long("_id")
                parts += MediaRef(id, messageId = ref.providerId, conversationId = ref.conversationId, mimeType = c.str("ct"), label = c.str("name").ifBlank { c.str("cl").ifBlank { "Załącznik MMS" } }, contentUri = "content://mms/part/$id")
            }
        } ?: error("Nie udało się odczytać części MMS.")
        db.withTransaction { requireRead(); dao.upsertMedia(parts); dao.sweepParts(ref.providerId, parts.map { it.partId }) }
    }

    internal suspend fun searchMessages(refs: List<IndexRef>): List<Message> = readMessages(refs)
    private suspend fun readMessages(refs: List<IndexRef>): List<Message> {
        requireRead()
        val out = mutableMapOf<ProviderRef, Message>()
        refs.filter { it.kind == "SMS" }.chunked(200).forEach { chunk ->
            val lookup = chunk.associateBy { it.providerId }
            resolver.query(Telephony.Sms.CONTENT_URI, arrayOf("_id", "address", "body", "date", "read", "type", "sub_id", "status"), "_id IN (${chunk.joinToString { "?" }})", chunk.map { it.providerId.toString() }.toTypedArray(), null)?.use { c ->
                while (c.moveToNext()) {
                    val ref = lookup.getValue(c.long("_id")); val key = ProviderRef(ProviderTransport.SMS, ref.providerId)
                    out[key] = Message(MessageRef.Provider(key), ConversationId(ref.conversationId), if (c.long("type") == 1L) Direction.Incoming else Direction.Outgoing, Address(c.str("address")), c.str("body"), Instant.ofEpochMilli(c.long("date")), c.long("read") != 0L,
                        c.long("sub_id").toInt().takeIf { it >= 0 }?.let(::SubscriptionId), when (c.long("type")) {
                            2L -> if (c.long("status") == 0L) DeliveryStatus.Delivered else DeliveryStatus.Sent
                            4L, 6L -> DeliveryStatus.Sending
                            5L -> DeliveryStatus.Failed
                            else -> DeliveryStatus.None
                        })
                }
            } ?: error("Nie udało się odczytać wiadomości.")
        }
        refs.filter { it.kind == "MMS" }.forEach { ref ->
            requireRead()
            val header = resolver.query(Uri.parse("content://mms/${ref.providerId}"), arrayOf("date", "read", "msg_box", "sub_id"), null, null, null)?.use { c ->
                if (!c.moveToFirst()) null else listOf(c.long("date") * 1000, c.long("read"), c.long("msg_box"), c.long("sub_id"))
            } ?: return@forEach
            var from: Address? = null
            if (header[2] == 1L) resolver.query(Uri.parse("content://mms/${ref.providerId}/addr"), arrayOf("address"), "type=?", arrayOf("137"), null)?.use { c ->
                if (c.moveToFirst()) c.str("address").takeIf { it.isNotBlank() && it != "insert-address-token" }?.let { from = Address(it) }
            }
            val body = StringBuilder()
            resolver.query(Uri.parse("content://mms/${ref.providerId}/part"), arrayOf("_id", "ct", "text"), null, null, null)?.use { c -> while (c.moveToNext()) if (c.str("ct") == "text/plain") {
                val text = c.str("text").ifEmpty { resolver.openInputStream(Uri.parse("content://mms/part/${c.long("_id")}"))?.bufferedReader()?.use { reader -> val chars = CharArray(65_536); val n = reader.read(chars); if (n > 0) String(chars, 0, n) else "" }.orEmpty() }
                if (body.isNotEmpty()) body.append('\n'); body.append(text.take((65_536 - body.length).coerceAtLeast(0)))
            } }
            val key = ProviderRef(ProviderTransport.MMS, ref.providerId)
            val media = dao.media(ref.conversationId, ref.providerId).map { AttachmentSummary(AttachmentId("mms-part:${it.partId}"), it.mimeType, it.label, it.contentUri) }
            out[key] = Message(MessageRef.Provider(key), ConversationId(ref.conversationId), if (header[2] == 1L) Direction.Incoming else Direction.Outgoing, from, body.toString(), Instant.ofEpochMilli(header[0]), header[1] != 0L, subscriptionId = header[3].toInt().takeIf { it >= 0 }?.let(::SubscriptionId), status = if (header[2] == 1L) DeliveryStatus.None else DeliveryStatus.Sent, attachments = media)
        }
        requireRead()
        return refs.mapNotNull { out[ProviderRef(ProviderTransport.valueOf(it.kind), it.providerId)] }.sortedBy { it.timestamp }
    }

    private suspend fun summaries(rows: List<app.chatra.core.database.ConversationIndex>): List<ConversationSummary> {
        if (!context.smsRead(platform)) return emptyList()
        val last = rows.mapNotNull { r -> val kind = r.lastKind; val id = r.lastId; val at = r.lastAt; if (id != null && kind != null && at != null) IndexRef(kind, id, r.meta.id, at, false, true, "") else null }
        val messages = readMessages(last).associateBy { (it.ref as MessageRef.Provider).ref }
        return rows.map { r ->
            val participants = addresses(r.meta.participants).map { contacts.lookup(it) ?: Participant(it) }
            val message = r.lastKind?.let { kind -> r.lastId?.let { id -> messages[ProviderRef(ProviderTransport.valueOf(kind), id)] } }
            ConversationSummary(ConversationId(r.meta.id), participants, participants.joinToString(", ") { it.label }.ifEmpty { "Nieznany nadawca" }, Instant.ofEpochMilli(r.lastAt ?: 0),
                ConversationSummary.Preview.Text(message?.body?.take(250)?.ifBlank { if (message.attachments.isNotEmpty()) "Załącznik MMS" else "" }.orEmpty()),
                maxOf(r.unreadCount, if (r.meta.manualUnread) 1 else 0), r.meta.pinned, isGroupMms = participants.size > 1, muted = r.meta.muted, trashed = r.meta.trashed,
                tags = if (participants.any { it.displayName == null }) setOf(ConversationSummary.Tag.UnknownNumber) else emptySet())
        }
    }
    override fun inbox(): Flow<List<ConversationSummary>> = combine(dao.indices(), platform.changes, dao.operations()) { rows, _, ops -> rows to ops }.map { (rows, ops) ->
        if (!context.smsRead(platform)) { previousInbox = emptyList(); emptyList() }
        else try {
            val visibleOperations = ops.flatMap { operation ->
                dao.recipients(operation.id).filter { recipient ->
                    operation.state in activeStates || operation.state != "CANCELLED" && recipient.state == "FAILED" && recipient.providerId == null
                }.map { it.conversationId to operation }
            }.groupBy({ it.first }, { it.second })
            summaries(rows.filter { r -> r.lastAt != null || visibleOperations.containsKey(r.meta.id) }).map { summary ->
                val pending = visibleOperations[summary.id.value]?.maxByOrNull { it.createdAt }
                if (pending != null && pending.createdAt >= summary.lastActivity.toEpochMilli()) summary.copy(lastActivity = Instant.ofEpochMilli(pending.createdAt),
                    preview = if (pending.state in listOf("FAILED", "PARTIALLY_SENT")) ConversationSummary.Preview.SendFailed else ConversationSummary.Preview.Text(pending.body.take(250))) else summary
            }.also { previousInbox = it }
        } catch (error: Exception) { if (error is CancellationException) throw error; diagnostic(error); status.value = HistoryStatus.Error("Nie udało się odczytać wiadomości. Spróbuj odświeżyć historię."); previousInbox }
    }.flowOn(Dispatchers.IO)
    override fun conversation(id: ConversationId): Flow<ConversationSummary?> = combine(dao.indices(), platform.changes) { rows, _ -> rows.filter { it.meta.id == id.value } }.map { rows -> try { summaries(rows).firstOrNull() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null } }.flowOn(Dispatchers.IO)
    override fun messages(id: ConversationId): Flow<List<Message>> = combine(combine(dao.referenceChanges(), dao.conversationMedia(id.value)) { _, _ -> Unit }, pages, platform.changes, dao.operations(), dao.conversationRecipients(id.value)) { _, limits, _, ops, links -> Triple(limits[id.value] ?: 200, ops, links) }.map { (limit, ops, links) ->
        if (!context.smsRead(platform)) emptyList() else runCatching {
            val operations = ops.associateBy { it.id }
            val indexed = readMessages(dao.page(id.value, limit))
            val mapped = indexed.map { message ->
                val provider = (message.ref as? MessageRef.Provider)?.ref
                val link = links.firstOrNull { provider?.transport?.name == it.providerKind && it.providerId == provider.providerId }
                if (link == null) message else {
                    val operation = operations[link.operationId]
                    val state = if (link.state == "DELIVERED") link.state else operation?.state?.takeIf { it in listOf("DISPATCH_UNCERTAIN", "MISSED_NEEDS_DECISION") } ?: link.state
                    message.copy(operationId = OperationId(link.operationId), status = deliveryStatus(state))
                }
            }
            val locals = links.mapNotNull { link ->
                val operation = operations[link.operationId] ?: return@mapNotNull null
                if (operation.state in listOf("CANCELLED", "SCHEDULED") || indexed.any { (it.ref as? MessageRef.Provider)?.ref == ProviderRef(ProviderTransport.valueOf(link.providerKind), link.providerId ?: -1) }) return@mapNotNull null
                if (link.providerId != null && operation.state !in activeStates) return@mapNotNull null
                Message(MessageRef.Local(OperationId(operation.id)), id, Direction.Outgoing, null, operation.body, Instant.ofEpochMilli(operation.createdAt), true, SubscriptionId(operation.subscriptionId), deliveryStatus(operation.state), OperationId(operation.id))
            }
            (mapped + locals.distinctBy { it.operationId }).sortedBy { it.timestamp }
        }.getOrElse { if (it is CancellationException) throw it; diagnostic(it); status.value = HistoryStatus.Error("Nie udało się odczytać rozmowy."); emptyList() }
    }.flowOn(Dispatchers.IO)
    private fun deliveryStatus(state: String) = when (state) {
        "PENDING_UNDO" -> DeliveryStatus.PendingUndo
        "SENT" -> DeliveryStatus.Sent
        "DELIVERED" -> DeliveryStatus.Delivered
        "FAILED" -> DeliveryStatus.Failed
        "PARTIALLY_SENT" -> DeliveryStatus.PartiallySent
        "DISPATCH_UNCERTAIN", "MISSED_NEEDS_DECISION" -> DeliveryStatus.Uncertain
        else -> DeliveryStatus.Sending
    }
    override suspend fun loadOlder(id: ConversationId) { pages.update { it + (id.value to ((it[id.value] ?: 200) + 200)) } }
    override fun hasOlder(id: ConversationId) = combine(dao.messageCount(id.value), pages) { count, limits -> count > (limits[id.value] ?: 200) }
    override fun preferredSim(id: ConversationId) = dao.meta(id.value).map { it?.preferredSim?.let(::SubscriptionId) }
    override suspend fun preferSim(id: ConversationId, sim: SubscriptionId, label: String) { dao.preferSim(id.value, sim.value, label) }
    override suspend fun setPinned(id: ConversationId, pinned: Boolean) { dao.pin(id.value, pinned) }
    override suspend fun moveToTrash(id: ConversationId) { error("Kosz i retencja wymagają CH043.") }
    override suspend fun conversationFor(recipients: List<Address>): ConversationId = withContext(Dispatchers.IO) {
        requireRead(); require(recipients.isNotEmpty() && recipients.all { isOutgoingNumber(it.raw) })
        val thread = Telephony.Threads.getOrCreateThreadId(context, recipients.map { it.raw }.toSet())
        ConversationId(ensureConversation(thread, recipients).id)
    }
    override suspend fun markRead(id: ConversationId) = changeRead(id, true, null)
    override suspend fun markAllRead() = withContext(Dispatchers.IO) {
        requireRead()
        val values = ContentValues().apply { put("read", 1); put("seen", 1) }
        for (uri in listOf(Telephony.Sms.CONTENT_URI, Telephony.Mms.CONTENT_URI)) { requireRead(); resolver.update(uri, values, "read=0", null) }
        dao.clearManualUnread()
        refresh()
    }
    override suspend fun markUnread(id: ConversationId) { changeRead(id, false, null); dao.unread(id.value, true) }
    override suspend fun markShown(id: ConversationId, refs: Set<MessageRef>) = changeRead(id, true, refs)
    private suspend fun changeRead(id: ConversationId, read: Boolean, shown: Set<MessageRef>?) = withContext(Dispatchers.IO) {
        if (!context.smsRead(platform)) return@withContext
        val meta = dao.conversation(id.value) ?: return@withContext
        val values = ContentValues().apply { put("read", if (read) 1 else 0); if (read) put("seen", 1) }
        if (shown == null) for (uri in listOf(Telephony.Sms.CONTENT_URI, Telephony.Mms.CONTENT_URI)) {
            requireRead(); resolver.update(uri, values, "thread_id=?", arrayOf(meta.threadId.toString()))
        } else {
            val refs = dao.unreadRefs(id.value).filter { MessageRef.Provider(ProviderRef(ProviderTransport.valueOf(it.kind), it.providerId)) in shown }
            if (refs.isEmpty()) {
                if (read && meta.manualUnread && shown.isNotEmpty()) dao.unread(id.value, false)
                return@withContext
            }
            for (ref in refs) { requireRead(); resolver.update(ContentUris.withAppendedId(if (ref.kind == "SMS") Telephony.Sms.CONTENT_URI else Telephony.Mms.CONTENT_URI, ref.providerId), values, "thread_id=?", arrayOf(meta.threadId.toString())) }
        }
        if (read) dao.unread(id.value, false)
        refresh()
    }
}
