package app.chatra.mms

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.app.Activity
import android.app.PendingIntent
import android.content.*
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.AtomicFile
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import app.chatra.core.database.*
import app.chatra.core.model.*
import app.chatra.local.replace
import app.chatra.platform.SystemPlatform
import app.chatra.telephony.*
import app.chatra.mms.pdu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

interface MmsEnvironment {
    fun limit(sim: Int): Int
    fun ready(sim: Int)
}

interface MmsTransport {
    fun send(sim: Int, uri: Uri, callback: PendingIntent)
    fun download(sim: Int, location: String, uri: Uri, callback: PendingIntent)
}
class NativeMmsTransport(private val context: Context) : MmsTransport {
    private fun manager(sim: Int) = context.getSystemService(SmsManager::class.java).createForSubscriptionId(sim)
    override fun send(sim: Int, uri: Uri, callback: PendingIntent) = manager(sim).sendMultimediaMessage(context, uri, null, null, callback)
    override fun download(sim: Int, location: String, uri: Uri, callback: PendingIntent) = manager(sim).downloadMultimediaMessage(context, location, uri, null, callback)
}

/** Persisted submission boundary: neither restart nor a missing callback repeats an MMS. */
class MmsEngine(private val context: Context, private val db: ChatraDatabase, private val media: AttachmentStore,
    private val history: SystemHistory, private val platform: SystemPlatform,
    private val transport: MmsTransport = NativeMmsTransport(context),
    private val environment: MmsEnvironment? = null) : MmsDownloads {
    private val folder = File(context.filesDir, "mms-journal").apply { mkdirs() }
    private val pdus = File(context.filesDir, "mms-pdu").apply { mkdirs() }
    private val mutex = Mutex()
    private val activeOperations = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val transferLocks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private fun lock(id: String) = transferLocks.getOrPut(id) { Mutex() }
    private val dao = db.dao()
    override val pending = kotlinx.coroutines.flow.MutableStateFlow(readDownloads())
    private fun readDownloads(): List<PendingMms> = folder.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull { file ->
        val j = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return@mapNotNull null
        if (!j.optBoolean("incoming") || j.optString("phase") == "RECEIVED") return@mapNotNull null
        val phase = j.optString("phase")
        PendingMms(j.getString("id"), phase, when (phase) {
            "SUBMITTING", "RETURNED" -> "MMS jest pobierany. Jeśli pobieranie zostało przerwane, możesz ponowić je ręcznie."
            "DOWNLOADED" -> "Pobrany MMS czeka na zapis w historii."
            else -> "MMS nie został pobrany. Sprawdź dane komórkowe, aktywną SIM i ustawienia MMS operatora."
        })
    }
    override suspend fun retryDownload(id: String) {
        val j = load(id) ?: return
        require(j.optBoolean("incoming"))
        processSpool(j.getString("spool"), retry = true)
    }
    suspend fun recoverDownloads() = withContext(Dispatchers.IO) {
        for (item in pending.value) {
            val j = load(item.id) ?: continue
            if (j.optString("phase") == "DOWNLOADED") runCatching { persistDownload(j) }
        }
        File(context.filesDir, "pending-deliveries").listFiles().orEmpty().filter { it.extension == "json" }.forEach { file ->
            if (runCatching { JSONObject(file.readText()).optString("kind") == "mms-notification" }.getOrDefault(false)) MmsDownloadWorker.schedule(context, file.nameWithoutExtension)
        }
    }
    companion object {
        const val MAX_PDU = 1024 * 1024
        fun isMms(draft: Draft) = draft.attachmentIds.isNotEmpty() || draft.recipientMode == RecipientMode.GroupMms
        fun isMms(row: OutgoingOperation): Boolean = row.snapshot?.let { isMms(DraftCodec.decode(it)) } ?: (row.mode == RecipientMode.GroupMms.name)
    }
    suspend fun failureDescription(operation: String): String? = withContext(Dispatchers.IO) {
        folder.listFiles().orEmpty().filter { it.extension == "json" }.asSequence()
            .mapNotNull { runCatching { JSONObject(it.readText()) }.getOrNull() }
            .firstOrNull { it.optString("operation") == operation && it.optString("phase") in listOf("FAILED", "UNCERTAIN") }
            ?.optString("description")?.takeIf { it.isNotBlank() }
    }
    private fun failure(code: Int): String = when (code) {
        SmsManager.MMS_ERROR_INVALID_APN, SmsManager.MMS_ERROR_CONFIGURATION_ERROR -> "Sprawdź ustawienia MMS i APN operatora wybranej SIM."
        SmsManager.MMS_ERROR_NO_DATA_NETWORK -> "Brak sieci danych dla MMS. Sprawdź dane komórkowe wybranej SIM."
        SmsManager.MMS_ERROR_HTTP_FAILURE, SmsManager.MMS_ERROR_IO_ERROR, SmsManager.MMS_ERROR_RETRY -> "Operator nie potwierdził wysyłki MMS. Sprawdź status przed ponowieniem."
        else -> "Brak jednoznacznego potwierdzenia wysyłki MMS. Sprawdź status."
    }
    private fun preparationFailure(e: Exception): String = when {
        e.message?.contains("roaming", true) == true -> "Wysyłka MMS w roamingu jest wstrzymana."
        e.message?.contains("dane", true) == true -> "Włącz dane komórkowe wybranej SIM."
        e.message?.contains("limit", true) == true || e.message?.contains("mieści", true) == true -> "Pliki lub tekst przekraczają limit MMS operatora. Wybierz mniejsze pliki."
        else -> "Nie udało się przygotować MMS. Sprawdź SIM, dostęp do plików i ustawienia operatora."
    }
    private fun key(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun load(id: String): JSONObject? {
        require(id.matches(Regex("[a-f0-9]{64}")))
        val file = AtomicFile(File(folder, "$id.json"))
        return if (file.baseFile.exists()) JSONObject(file.readFully().toString(Charsets.UTF_8)) else null
    }
    private fun save(j: JSONObject) { AtomicFile(File(folder, "${j.getString("id")}.json")).replace(j.toString().toByteArray()); pending.value = readDownloads() }
    private fun uri(id: String) = FileProvider.getUriForFile(context, "${context.packageName}.files", File(pdus, "$id.pdu"))
    private fun manager(sim: Int): SmsManager {
        check(platform.isHeld() && context.smsSend(platform) && context.smsRead(platform)) { "Brak roli lub dostępu do SMS/MMS." }
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) { "Brak dostępu do kart SIM." }
        check(context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty().any { it.subscriptionId == sim }) { "Wybrana SIM jest niedostępna." }
        return context.getSystemService(SmsManager::class.java).createForSubscriptionId(sim)
    }
    private fun limits(sim: Int): Int {
        environment?.let { return it.limit(sim).coerceIn(16 * 1024, MAX_PDU) }
        val config = manager(sim).carrierConfigValues
        check(config.getBoolean(SmsManager.MMS_CONFIG_MMS_ENABLED, false)) { "Operator tej SIM nie udostępnia MMS." }
        return config.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, 300 * 1024).coerceIn(16 * 1024, MAX_PDU)
    }
    private fun dataAllowed(sim: Int) {
        environment?.let { it.ready(sim); return }
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) { "Brak dostępu do stanu sieci." }
        val phone = context.getSystemService(TelephonyManager::class.java).createForSubscriptionId(sim)
        check(phone.isDataEnabled) { "Włącz dane komórkowe dla MMS." }
        check(!phone.isNetworkRoaming) { "MMS w roamingu wymaga wysłania po powrocie lub jawnej zgody w ustawieniach operatora." }
    }
    suspend fun validate(draft: Draft) {
        require(draft.attachmentIds.size <= 20)
        require(draft.attachmentIds.isEmpty() || draft.mediaVariant == MediaVariant.Mms) { "Wybierz MMS. Transfer Chatra HD nie jest jeszcze dostępny." }
        limits(requireNotNull(draft.subscriptionId).value)
        for (id in draft.attachmentIds) check(media.get(id) != null) { "Załącznik jest niedostępny. Wybierz go ponownie." }
    }
    private suspend fun parts(draft: Draft, max: Int): PduBody {
        val body = PduBody()
        val slides = mutableListOf<String>()
        if (draft.text.isNotEmpty()) { slides += "<par dur=\"5000ms\"><text src=\"text.txt\" region=\"Text\" /></par>"; body.addPart(PduPart().apply {
            contentType = "text/plain".toByteArray(); charset = CharacterSets.UTF_8
            contentId = "text".toByteArray(); contentLocation = "text.txt".toByteArray(); data = draft.text.toByteArray()
        }) }
        val budget = max - draft.text.toByteArray().size - 8192
        require(budget > 0) { "Tekst przekracza limit MMS operatora." }
        for ((i, id) in draft.attachmentIds.withIndex()) {
            var item = media.get(id) ?: error("Załącznik jest niedostępny.")
            val perFile = budget / draft.attachmentIds.size
            if (item.mime in listOf("image/jpeg", "image/png", "image/webp")) item = media.derive(id, MediaVariant.Mms, perFile.toLong())
            require(item.mime in listOf("image/jpeg", "image/png", "image/gif", "video/mp4", "video/3gpp") && item.bytes <= perFile) { "Ten plik nie mieści się w limicie MMS. Wybierz mniejszy plik lub inny format." }
            val bytes = context.contentResolver.openInputStream(Uri.parse(item.uri))!!.use { it.readBytes() }
            require(bytes.size <= perFile)
            val name = "media-$i.${item.uri.substringAfterLast('.')}"
            val tag = if (item.mime.startsWith("video/")) "video" else "img"
            slides += "<par dur=\"5000ms\"><$tag src=\"$name\" region=\"Image\" /></par>"
            body.addPart(PduPart().apply { contentType = item.mime.toByteArray(); contentId = "media-$i".toByteArray(); contentLocation = "media-$i.${item.uri.substringAfterLast('.')}".toByteArray(); data = bytes })
        }
        val smil = "<smil><head><layout><root-layout width=\"320px\" height=\"480px\"/><region id=\"Image\" left=\"0\" top=\"0\" width=\"320px\" height=\"320px\"/><region id=\"Text\" left=\"0\" top=\"320px\" width=\"320px\" height=\"160px\"/></layout></head><body>${slides.joinToString("")}</body></smil>"
        body.addPart(0, PduPart().apply { contentType = "application/smil".toByteArray(); contentId = "smil".toByteArray(); contentLocation = "smil.xml".toByteArray(); data = smil.toByteArray() })
        return body
    }
    private fun callback(j: JSONObject): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, MmsResultReceiver::class.java).setAction("app.chatra.MMS_RESULT")
            .setData(Uri.Builder().scheme("chatra-mms").authority("result").appendPath(j.getString("id")).appendPath(j.getString("token")).build()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

    suspend fun dispatch(row: OutgoingOperation, recipients: List<OutgoingRecipient>) = withContext(Dispatchers.IO) {
        if (!activeOperations.add(row.id)) return@withContext
        try {
            val draft = DraftCodec.decode(requireNotNull(row.snapshot))
            val groups = if (draft.recipientMode == RecipientMode.GroupMms) listOf(recipients) else recipients.map { listOf(it) }
            for (group in groups) {
                val id = key("send:${row.id}:${group.joinToString { it.address }}")
                if (load(id) != null) continue // a second worker must never enter transport again.
                lock(id).withLock {
                if (load(id) != null) return@withLock
                val j = JSONObject().put("id", id).put("token", UUID.randomUUID().toString()).put("operation", row.id)
                    .put("incoming", false).put("sim", row.subscriptionId).put("recipients", JSONArray(group.map { it.address })).put("phase", "PREPARING")
                save(j)
                var submitted = false
                try {
                    val max = limits(row.subscriptionId); dataAllowed(row.subscriptionId)
                    val body = parts(draft, max)
                    val req = SendReq().apply {
                        to = group.map { EncodedStringValue(it.address) }.toTypedArray()
                        from = EncodedStringValue("insert-address-token")
                        transactionId = id.take(24).toByteArray(); date = row.createdAt / 1000; messageSize = (0 until body.partsNum).sumOf { body.getPart(it).data?.size?.toLong() ?: 0L }
                        contentType = "application/vnd.wap.multipart.related".toByteArray(); setBody(body)
                    }
                    val bytes = PduComposer(context, req).make() ?: error("Nie można przygotować MMS.")
                    require(bytes.size <= max) { "MMS przekracza limit operatora." }
                    AtomicFile(File(pdus, "$id.pdu")).replace(bytes)
                    val conversation = dao.conversation(group.first().conversationId) ?: error("Brak rozmowy.")
                    val provider = persist(body, group.map { it.address }, false, row.subscriptionId, conversation.threadId, id, req.transactionId)
                    j.put("provider", provider).put("phase", "PREPARED"); save(j)
                    group.forEach { dao.recipient(it.copy(providerId = provider, providerKind = "MMS", state = "DISPATCHING")) }
                    if (environment == null) manager(row.subscriptionId)
                    dataAllowed(row.subscriptionId)
                    row.retryOf?.let { parent ->
                        val targets = group.map { it.address }
                        if (dao.recipients(parent).any { it.address in targets && it.state != "FAILED" }) {
                            val current = dao.operation(row.id)
                            if (current != null) dao.transition(row.id, current.state, current.version, "CANCELLED")
                            j.put("phase", "CANCELLED"); save(j)
                            return@withLock
                        }
                    }
                    // Journal write precedes the API. A crash after this point is uncertain, never retryable by default.
                    j.put("phase", "SUBMITTING"); save(j); submitted = true
                    transport.send(row.subscriptionId, uri(id), callback(j))
                    j.put("phase", "RETURNED"); save(j)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (e: Exception) {
                    val certain = !submitted || e is SecurityException || e is IllegalArgumentException
                    j.put("phase", if (certain) "FAILED" else "UNCERTAIN").put("error", e.javaClass.simpleName).put("description", preparationFailure(e)); save(j)
                    setRecipients(j, if (certain) "FAILED" else "DISPATCH_UNCERTAIN")
                }
                }
            }
            overall(row.id)
        } finally { activeOperations.remove(row.id) }
    }
    private suspend fun setRecipients(j: JSONObject, state: String) {
        val op = j.optString("operation"); if (op.isBlank()) return
        val targets = j.getJSONArray("recipients"); val addresses = (0 until targets.length()).map { targets.getString(it) }
        for (r in dao.recipients(op).filter { it.address in addresses }) if (r.state !in listOf("SENT", "DELIVERED")) dao.recipient(r.copy(state = state))
    }
    private suspend fun overall(id: String) {
        val states = dao.recipients(id).map { it.state }
        val state = when {
            states.isEmpty() -> return
            states.all { it in listOf("SENT", "DELIVERED") } -> "SENT"
            "DISPATCH_UNCERTAIN" in states -> "DISPATCH_UNCERTAIN"
            states.any { it in listOf("PENDING_UNDO", "DISPATCHING") } -> "DISPATCHING"
            states.any { it in listOf("SENT", "PARTIALLY_SENT") } -> "PARTIALLY_SENT"
            else -> "FAILED"
        }
        dao.result(id, state)
    }
    suspend fun interrupted(row: OutgoingOperation) = withContext(Dispatchers.IO) {
        if (row.id in activeOperations) return@withContext
        mutex.withLock {
            val entries = folder.listFiles().orEmpty().filter { it.extension == "json" }.map { JSONObject(it.readText()) }.filter { it.optString("operation") == row.id }
            for (r in dao.recipients(row.id)) {
                val entry = entries.firstOrNull { j -> j.getJSONArray("recipients").let { a -> (0 until a.length()).any { a.getString(it) == r.address } } }
                val phase = entry?.optString("phase")
                val next = when (phase) { "SENT" -> "SENT"; null, "PREPARING", "PREPARED", "FAILED" -> "FAILED"; else -> "DISPATCH_UNCERTAIN" }
                if (r.state !in listOf("SENT", "DELIVERED")) dao.recipient(r.copy(state = next))
            }
            overall(row.id)
        }
    }
    suspend fun result(intent: Intent, code: Int) = withContext(Dispatchers.IO) {
        val path = intent.data?.takeIf { it.scheme == "chatra-mms" && it.host == "result" }?.pathSegments ?: return@withContext
        if (intent.action != "app.chatra.MMS_RESULT" || path.size != 2) return@withContext
        lock(path[0]).withLock {
            val j = runCatching { load(path[0]) }.getOrNull() ?: return@withLock
            if (j.getString("token") != path[1]) return@withLock
            if (j.getBoolean("incoming")) {
                if (j.optString("phase") == "RECEIVED") return@withLock
                if (code == Activity.RESULT_OK) {
                    j.put("phase", "DOWNLOADED"); save(j)
                    try { persistDownload(j) } catch (_: Exception) { j.put("phase", "DOWNLOAD_FAILED"); save(j) }
                } else { j.put("phase", "DOWNLOAD_FAILED").put("result", code); save(j) }
                return@withLock
            }
            if (j.getString("phase") == "SENT") return@withLock
            val response = intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA)
            val conf = response?.takeIf { it.size <= MAX_PDU }?.let { runCatching { MmsCodec.parse(it) as? SendConf }.getOrNull() }
            val success = code == Activity.RESULT_OK && (response == null || conf != null && conf.responseStatus == PduHeaders.RESPONSE_STATUS_OK && conf.transactionId.contentEquals(j.getString("id").take(24).toByteArray()))
            val certain = code in setOf(SmsManager.MMS_ERROR_INVALID_APN, SmsManager.MMS_ERROR_NO_DATA_NETWORK, SmsManager.MMS_ERROR_CONFIGURATION_ERROR) || (android.os.Build.VERSION.SDK_INT >= 33 && code == SmsManager.MMS_ERROR_INACTIVE_SUBSCRIPTION)
            j.put("phase", if (success) "SENT" else if (certain) "FAILED" else "UNCERTAIN").put("result", code); save(j)
            if (success) j.remove("description") else j.put("description", failure(code))
            save(j)
            db.withTransaction {
                setRecipients(j, if (success) "SENT" else if (certain) "FAILED" else "DISPATCH_UNCERTAIN")
                if (success) dao.retryChild(j.getString("operation"))?.let { child ->
                    if (child.state == "PENDING_UNDO") dao.transition(child.id, child.state, child.version, "CANCELLED")
                }
                overall(j.getString("operation"))
            }
            if (j.has("provider")) context.contentResolver.update(Uri.parse("content://mms/${j.getLong("provider")}"), ContentValues().apply { put("msg_box", if (success) 2 else 4) }, null, null)
            history.refresh()
        }
    }
    /** Provider rows are staged as drafts; only complete headers/parts become inbox/outbox entries. */
    private fun persist(body: PduBody, recipients: List<String>, incoming: Boolean, sim: Int, thread: Long, key: String, transaction: ByteArray?): Long {
        val resolver = context.contentResolver
        var existing: Long? = null
        resolver.query(Telephony.Mms.CONTENT_URI, arrayOf("_id"), "tr_id=? AND sub_id=?", arrayOf("chatra:$key", sim.toString()), null)?.use { if (it.moveToFirst()) existing = it.getLong(0) }
        val target = existing?.let { Uri.parse("content://mms/$it") } ?: resolver.insert(Telephony.Mms.Draft.CONTENT_URI, ContentValues().apply {
            put("thread_id", thread); put("date", System.currentTimeMillis() / 1000); put("read", if (incoming) 0 else 1); put("seen", if (incoming) 0 else 1)
            put("sub_id", sim); put("m_type", if (incoming) PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF else PduHeaders.MESSAGE_TYPE_SEND_REQ)
            put("v", PduHeaders.CURRENT_MMS_VERSION); put("ct_t", "application/vnd.wap.multipart.related"); put("tr_id", "chatra:$key")
        }) ?: error("Zapis MMS do historii nie powiódł się.")
        val id = target.lastPathSegment!!.toLong()
        // Exact owned key makes recovery idempotent even when a process died after insertion.
        resolver.delete(Uri.parse("content://mms/$id/part"), null, null)
        resolver.delete(Uri.parse("content://mms/$id/addr"), null, null)
        recipients.forEach { address -> resolver.insert(Uri.parse("content://mms/$id/addr"), ContentValues().apply { put("address", address); put("charset", CharacterSets.UTF_8); put("type", if (incoming && address == recipients.first()) PduHeaders.FROM else PduHeaders.TO) }) }
        for (i in 0 until body.partsNum) {
            val part = body.getPart(i); val type = String(part.contentType, Charsets.US_ASCII); val data = part.data ?: byteArrayOf()
            require(data.size <= MAX_PDU && type in listOf("text/plain", "application/smil", "image/jpeg", "image/png", "image/gif", "image/webp", "video/mp4", "video/3gpp")) { "Nieobsługiwana część MMS." }
            val values = ContentValues().apply { put("ct", type); put("seq", i); put("chset", part.charset); put("cl", "part-$i"); if (type in listOf("text/plain", "application/smil")) put("text", String(data, if (part.charset == CharacterSets.ISO_8859_1) Charsets.ISO_8859_1 else Charsets.UTF_8)) }
            val partUri = resolver.insert(Uri.parse("content://mms/$id/part"), values) ?: error("Nie udało się zapisać części MMS.")
            if (type !in listOf("text/plain", "application/smil")) resolver.openOutputStream(partUri)!!.use { it.write(data) }
        }
        resolver.update(target, ContentValues().apply { put("msg_box", if (incoming) 1 else 4); put("read", if (incoming) 0 else 1); put("seen", if (incoming) 0 else 1) }, null, null)
        return id
    }
    suspend fun processSpool(name: String, retry: Boolean = false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(name.matches(Regex("[a-f0-9]{64}")))
            val file = File(context.filesDir, "pending-deliveries/$name.json")
            if (!file.exists()) return@withLock
            val spool = JSONObject(file.readText())
            if (spool.optString("kind") != "mms-notification") return@withLock
            val raw = Base64.decode(spool.getJSONArray("pdus").getString(0), Base64.NO_WRAP)
            require(raw.size <= MAX_PDU)
            val notification = MmsCodec.parse(raw) as? NotificationInd ?: return@withLock
            val location = String(notification.contentLocation, Charsets.UTF_8)
            val url = Uri.parse(location)
            require(url.scheme in listOf("http", "https") && !url.host.isNullOrBlank() && url.userInfo == null && location.length <= 4096)
            val sim = spool.getInt("subscription")
            val id = key("download:$sim:$location:${notification.transactionId?.let { String(it, Charsets.UTF_8) }}")
            lock(id).withLock download@ {
            val existing = load(id)
            if (existing?.optString("phase") == "DOWNLOADED") { persistDownload(existing); file.delete(); return@download }
            if (existing != null && (!retry || existing.optString("phase") == "RECEIVED")) { if (existing.optString("phase") == "RECEIVED") file.delete(); return@download }
            if (existing != null && existing.optString("phase") in listOf("SUBMITTING", "RETURNED")) {
                require(System.currentTimeMillis() - existing.optLong("started") > 120000) { "Pobieranie trwa. Poczekaj dwie minuty przed ponowieniem." }
            }
            val j = (existing ?: JSONObject()).put("id", id).put("incoming", true).put("sim", sim).put("location", location).put("token", UUID.randomUUID().toString()).put("phase", "SUBMITTING").put("spool", name).put("started", System.currentTimeMillis())
            save(j)
            AtomicFile(File(pdus, "$id.pdu")).replace(byteArrayOf())
            try {
                if (environment == null) manager(sim)
                dataAllowed(sim)
                require(notification.messageSize in 1..MAX_PDU.toLong()) { "MMS przekracza obsługiwany limit 1 MB." }
                if (!j.has("expiry")) j.put("expiry", notification.expiry)
                require(j.optLong("expiry", Long.MAX_VALUE) > System.currentTimeMillis() / 1000) { "MMS wygasł u operatora." }
                save(j)
                transport.download(sim, location, uri(id), callback(j))
                j.put("phase", "RETURNED"); save(j)
            }
            catch (e: Exception) { j.put("phase", "DOWNLOAD_FAILED").put("error", e.javaClass.simpleName).put("description", preparationFailure(e)); save(j) }
            }
        }
    }
    @Suppress("DEPRECATION")
    private fun ownNumbers(): Set<String> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return emptySet()
        return try { context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty().map { it.number }.filter { isOutgoingNumber(it) }.map { recipientKey(it) }.toSet() }
        catch (_: SecurityException) { emptySet() }
    }
    private suspend fun persistDownload(j: JSONObject) {
        val file = File(pdus, "${j.getString("id")}.pdu")
        require(file.length() in 1..MAX_PDU)
        val received = MmsCodec.parse(file.readBytes()) as? RetrieveConf ?: error("Nieprawidłowa odpowiedź MMS.")
        require(received.retrieveStatus in listOf(0, PduHeaders.RETRIEVE_STATUS_OK))
        val from = received.from?.string?.substringBefore("/TYPE=") ?: error("Brak nadawcy MMS.")
        val others = (received.to.orEmpty().toList() + received.cc.orEmpty().toList()).map { it.string.substringBefore("/TYPE=") }
        val own = ownNumbers()
        val participants = (listOf(from) + others.filter { recipientKey(it) !in own }).filter { isOutgoingNumber(it) }.distinctBy { recipientKey(it) }
        require(participants.isNotEmpty())
        val thread = Telephony.Threads.getOrCreateThreadId(context, participants.toSet())
        persist(received.body ?: error("Pusty MMS."), participants, true, j.getInt("sim"), thread, j.getString("id"), received.transactionId)
        j.put("phase", "RECEIVED"); save(j)
        File(context.filesDir, "pending-deliveries/${j.getString("spool")}.json").delete()
        history.refresh()
    }
}
