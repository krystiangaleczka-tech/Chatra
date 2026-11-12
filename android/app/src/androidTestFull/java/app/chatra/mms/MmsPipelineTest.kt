package app.chatra.mms

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.chatra.core.database.*
import app.chatra.core.model.*
import app.chatra.local.PrivateMediaStore
import app.chatra.mms.pdu.*
import app.chatra.platform.SystemPlatform
import app.chatra.telephony.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File
import java.time.Instant

/** Real Telephony Provider + pinned codec, with transport replaced so QA cannot contact an operator. */
class MmsPipelineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: ChatraDatabase
    private lateinit var history: SystemHistory
    private lateinit var scope: CoroutineScope
    private val ownedRows = mutableListOf<Long>()
    private val journals = mutableListOf<File>()
    private val env = object : MmsEnvironment { override fun limit(sim: Int) = 300 * 1024; override fun ready(sim: Int) {} }
    @Before fun prepare() {
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        assumeTrue(SystemPlatform(context).isHeld())
        db = Room.inMemoryDatabaseBuilder(context, ChatraDatabase::class.java).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val contacts = object : ContactsDirectory { override fun suggestions() = flowOf(emptyList<Participant>()); override suspend fun lookup(address: Address): Participant? = null }
        history = SystemHistory(context, db, SystemPlatform(context), contacts, scope)
    }
    @After fun cleanup() = runBlocking {
        if (!::db.isInitialized) return@runBlocking
        scope.coroutineContext[Job]!!.cancelAndJoin()
        ownedRows.forEach { context.contentResolver.delete(Uri.parse("content://mms/$it"), null, null) }
        journals.forEach { it.delete() }
        db.close()
    }
    private fun engine(transport: MmsTransport) = MmsEngine(context, db, PrivateMediaStore(context), history, SystemPlatform(context), transport, env)
    private val noDownload = object : MmsTransport {
        override fun send(sim: Int, uri: Uri, callback: PendingIntent) {}
        override fun download(sim: Int, location: String, uri: Uri, callback: PendingIntent) { fail("unexpected download") }
    }
    private suspend fun operation(group: Boolean = true): OutgoingOperation {
        val addresses = listOf("+48555000320", "+48555000321", "+48555000322")
        val conversation = history.conversationFor(addresses.map(::Address))
        val draft = Draft(DraftId.new(), conversation, addresses.map(::Address), "Tekst grupy, bez automatycznej wysyłki", if (group) RecipientMode.GroupMms else RecipientMode.SeparateSms, emptyList(), SubscriptionId(1), Instant.now())
        val row = OutgoingOperation(OperationId.new().value, draft.id.value, conversation.value, JSONArray(addresses).toString(), draft.text, draft.recipientMode.name, 1, "QA SIM", "DISPATCHING", 1, System.currentTimeMillis(), 0, snapshot = DraftCodec.encode(draft))
        db.dao().insertOperation(row)
        addresses.forEach { db.dao().recipient(OutgoingRecipient(row.id, it, conversation.value, "DISPATCHING")) }
        return row
    }
    private fun entries(row: OutgoingOperation): List<JSONObject> = File(context.filesDir, "mms-journal").listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull { f ->
        val j = JSONObject(f.readText())
        if (j.optString("operation") != row.id) null else { journals += f; if (j.has("provider")) ownedRows += j.getLong("provider"); j }
    }
    private fun result(j: JSONObject) = Intent("app.chatra.MMS_RESULT", Uri.parse("chatra-mms://result/${j.getString("id")}/${j.getString("token")}"))
    @android.annotation.SuppressLint("MissingPermission") // The isolated emulator fixture is granted READ_PHONE_STATE by the host.
    @Test fun sharedQueueAssignsTheGroupConversationBeforeTheUndoWindow() = runBlocking {
        val info = context.getSystemService(android.telephony.SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty().first()
        val card = SimCard(SubscriptionId(info.subscriptionId), info.simSlotIndex, "QA SIM")
        val sims = object : SimRepository { override fun cards() = flowOf(listOf(card)) }
        val work = object : SmsWork { override fun schedule(id: String, deadline: Long) {}; override fun cancel(id: String) {} }
        val queue = PersistentSmsQueue(context, db, SystemPlatform(context), history, sims, PlatformSmsText(context), RoomDrafts(db), SmsTransport { _, _, _, _, _ -> fail("SMS transport called for group MMS") }, work)
        queue.mms = engine(noDownload)
        val draft = Draft(DraftId.new(), null, listOf(Address("+48555000320"), Address("+48555000321")), "Queue group QA", RecipientMode.GroupMms, emptyList(), card.id, Instant.now())
        val id = queue.enqueue(draft, 3)
        val row = db.dao().operation(id.value)!!
        assertNotNull(row.conversationId)
        assertEquals(setOf(row.conversationId), db.dao().recipients(row.id).map { it.conversationId }.toSet())
        assertEquals(Transport.MMS, queue.operations().first().single().transport)
        assertTrue(queue.cancel(id))
    }

    @Test fun groupHasOnePduOneTransportAndOneOwnedMmsProviderRow() = runBlocking {
        var calls = 0; var pdu: ByteArray? = null
        val transport = object : MmsTransport by noDownload {
            override fun send(sim: Int, uri: Uri, callback: PendingIntent) { calls++; assertEquals(1, sim); pdu = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
        }
        val engine = engine(transport); val row = operation()
        coroutineScope { List(8) { async { engine.dispatch(row, db.dao().recipients(row.id)) } }.awaitAll() }
        val parsed = PduParser(pdu!!, true).parse() as SendReq
        assertEquals(3, parsed.to.size); assertEquals(2, parsed.body.partsNum)
        assertEquals("text/plain", String(parsed.body.getPart(1).contentType))
        val journal = entries(row).single()
        assertTrue(db.dao().recipients(row.id).all { it.providerKind == "MMS" && it.providerId == journal.getLong("provider") })
        engine.dispatch(row, db.dao().recipients(row.id)); assertEquals(1, calls)
        engine.result(result(journal), Activity.RESULT_OK)
        assertEquals("SENT", db.dao().operation(row.id)!!.state)
        engine.result(result(journal), SmsManager.MMS_ERROR_UNSPECIFIED)
        assertEquals("SENT", db.dao().operation(row.id)!!.state)
        val messages = withTimeout(5000) { history.messages(ConversationId(row.conversationId!!)).first { it.any { m -> m.operationId == OperationId(row.id) } } }
        assertEquals(1, messages.count { it.operationId == OperationId(row.id) })
    }
    @Test fun uncertainSubmissionAndRestartNeverRepeatTransport() = runBlocking {
        var calls = 0
        val transport = object : MmsTransport by noDownload { override fun send(sim: Int, uri: Uri, callback: PendingIntent) { calls++; throw java.io.IOException("interrupted binder") } }
        val engine = engine(transport); val row = operation()
        engine.dispatch(row, db.dao().recipients(row.id)); entries(row)
        assertEquals("DISPATCH_UNCERTAIN", db.dao().operation(row.id)!!.state)
        engine(transport).interrupted(row); engine(transport).dispatch(row, db.dao().recipients(row.id))
        assertEquals(1, calls)
        assertEquals("DISPATCH_UNCERTAIN", db.dao().operation(row.id)!!.state)
    }
    @Test fun separateMmsTracksPartialSuccessAndRejectsBadCallbackToken() = runBlocking {
        var calls = 0
        val transport = object : MmsTransport by noDownload { override fun send(sim: Int, uri: Uri, callback: PendingIntent) { calls++; if (calls == 2) throw IllegalArgumentException("invalid request") } }
        val engine = engine(transport); val row = operation(false)
        engine.dispatch(row, db.dao().recipients(row.id))
        val journals = entries(row)
        assertEquals(3, calls)
        journals.filter { it.getString("phase") == "RETURNED" }.forEach { engine.result(result(it), Activity.RESULT_OK) }
        assertEquals("PARTIALLY_SENT", db.dao().operation(row.id)!!.state)
        assertEquals(1, db.dao().recipients(row.id).count { it.state == "FAILED" })
        val failed = journals.single { it.getString("phase") == "FAILED" }
        val bad = result(failed).apply { data = Uri.parse(data.toString().substringBeforeLast('/') + "/not-the-token") }
        engine.result(bad, Activity.RESULT_OK)
        assertEquals("PARTIALLY_SENT", db.dao().operation(row.id)!!.state)
    }
    @Test fun successWithCorruptSendConfirmationIsUncertain() = runBlocking {
        val engine = engine(noDownload); val row = operation()
        engine.dispatch(row, db.dao().recipients(row.id))
        val journal = entries(row).single()
        engine.result(result(journal).putExtra(SmsManager.EXTRA_MMS_DATA, byteArrayOf(0, 1, 2)), Activity.RESULT_OK)
        assertEquals("DISPATCH_UNCERTAIN", db.dao().operation(row.id)!!.state)
    }
    @Test fun latePositiveMmsCallbackCancelsUnsubmittedRetry() = runBlocking {
        val engine = engine(noDownload); val parent = operation()
        engine.dispatch(parent, db.dao().recipients(parent.id))
        val journal = entries(parent).single()
        engine.result(result(journal), SmsManager.MMS_ERROR_INVALID_APN)
        assertEquals("FAILED", db.dao().operation(parent.id)!!.state)
        val child = parent.copy(id = OperationId.new().value, draftKey = "retry:${parent.id}", state = "PENDING_UNDO", retryOf = parent.id)
        db.dao().insertOperation(child)
        db.dao().recipients(parent.id).forEach { db.dao().recipient(it.copy(operationId = child.id, providerId = null, state = "PENDING_UNDO")) }
        engine.result(result(journal), Activity.RESULT_OK)
        assertEquals("SENT", db.dao().operation(parent.id)!!.state)
        assertEquals("CANCELLED", db.dao().operation(child.id)!!.state)
    }
    @Test fun inboundDownloadPersistsOnceAndGalleryPagesIndexedMedia() = runBlocking {
        val transaction = java.util.UUID.randomUUID().toString()
        val location = "https://mmsc.example.invalid/$transaction"
        val suffix = (100000 + (java.util.UUID.randomUUID().leastSignificantBits and Long.MAX_VALUE) % 900000).toString()
        val notification = java.io.ByteArrayOutputStream().apply {
            write(byteArrayOf(0x8c.toByte(), 0x82.toByte(), 0x98.toByte()))
            write(transaction.toByteArray()); write(0)
            write(byteArrayOf(0x8d.toByte(), 0x92.toByte(), 0x8a.toByte(), 0x80.toByte(), 0x8e.toByte(), 2, 1, 0))
            write(byteArrayOf(0x88.toByte(), 4, 0x81.toByte(), 2, 0x0e, 0x10, 0x83.toByte()))
            write(location.toByteArray()); write(0)
        }.toByteArray()
        assertTrue(MmsCodec.parse(notification) is NotificationInd)
        val body = PduBody()
        body.addPart(PduPart().apply { contentType = "text/plain".toByteArray(); contentLocation = "text.txt".toByteArray(); charset = CharacterSets.UTF_8; data = "Przychodzący MMS QA".toByteArray() })
        val bitmap = android.graphics.Bitmap.createBitmap(20, 10, android.graphics.Bitmap.Config.ARGB_8888)
        val jpeg = java.io.ByteArrayOutputStream().apply { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, this) }.toByteArray()
        bitmap.recycle()
        repeat(2) { i -> body.addPart(PduPart().apply { contentType = "image/jpeg".toByteArray(); contentLocation = "photo-$i.jpg".toByteArray(); contentId = "photo-$i".toByteArray(); data = jpeg }) }
        val reply = PduComposer(context, SendReq().apply {
            from = EncodedStringValue("+48555$suffix"); to = arrayOf(EncodedStringValue("+48556$suffix"))
            date = System.currentTimeMillis() / 1000; contentType = "application/vnd.wap.multipart.related".toByteArray(); setBody(body)
        }).make()!!
        reply[1] = PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF.toByte()
        assertTrue(MmsCodec.parse(reply) is RetrieveConf)
        assertEquals(3, (MmsCodec.parse(reply) as RetrieveConf).body.partsNum)
        var downloads = 0
        val transport = object : MmsTransport by noDownload {
            override fun download(sim: Int, url: String, uri: Uri, callback: PendingIntent) {
                downloads++; assertEquals(location, url)
                context.contentResolver.openOutputStream(uri, "w")!!.use { it.write(reply) }
            }
        }
        val engine = engine(transport)
        val spoolKey = java.security.MessageDigest.getInstance("SHA-256").digest(transaction.toByteArray()).joinToString("") { "%02x".format(it) }
        val spool = File(context.filesDir, "pending-deliveries/$spoolKey.json").apply { parentFile!!.mkdirs() }
        spool.writeText(JSONObject().put("kind", "mms-notification").put("subscription", 1).put("pdus", JSONArray(listOf(android.util.Base64.encodeToString(notification, android.util.Base64.NO_WRAP)))).toString())
        try {
            engine.processSpool(spoolKey)
            val id = engine.pending.value.single { it.state == "RETURNED" }.id
            val journalFile = File(context.filesDir, "mms-journal/$id.json"); journals += journalFile
            val j = JSONObject(journalFile.readText())
            engine.result(result(j), Activity.RESULT_OK)
            engine.result(result(j), Activity.RESULT_OK)
            engine.processSpool(spoolKey)
            assertEquals(1, downloads); assertFalse(spool.exists())
            assertFalse(engine.pending.value.any { it.id == id })
            val ids = mutableListOf<Long>()
            context.contentResolver.query(Telephony.Mms.CONTENT_URI, arrayOf("_id", "read", "msg_box"), "tr_id=?", arrayOf("chatra:$id"), null)!!.use { c ->
                while (c.moveToNext()) { ids += c.getLong(0); ownedRows += c.getLong(0); assertEquals(0, c.getInt(1)); assertEquals(1, c.getInt(2)) }
            }
            assertEquals(1, ids.size); ownedRows += ids
            history.refresh()
            val reference = withTimeout(5000) { db.dao().references().first { refs -> refs.any { it.kind == "MMS" && it.providerId == ids.single() } }.first { it.kind == "MMS" && it.providerId == ids.single() } }
            withTimeout(5000) { db.dao().conversationMedia(reference.conversationId).first { parts -> parts.count { it.messageId == ids.single() } == 2 } }
            val gallery = ProviderMediaGallery(context, db, SystemPlatform(context))
            val first = gallery.page(ConversationId(reference.conversationId), limit = 1)
            assertEquals(2, first.total); assertEquals(1, first.items.size); assertNotNull(first.nextBefore)
            val second = gallery.page(ConversationId(reference.conversationId), before = first.nextBefore, limit = 1)
            assertEquals(1, second.items.size); assertNotEquals(first.items.first().id, second.items.first().id)
            assertNotNull(gallery.readable(first.items.first().id))
            context.contentResolver.delete(Uri.parse(first.items.first().uri), null, null)
            assertNull(gallery.readable(first.items.first().id))
        } finally { spool.delete() }
    }
    @Test(timeout = 10000) fun malformedBoundedPdusDoNotAllocateFromUntrustedLengthsOrLoop() {
        val random = java.util.Random(31034)
        repeat(1000) {
            val bytes = ByteArray(random.nextInt(1024) + 1); random.nextBytes(bytes)
            MmsCodec.parse(bytes)
        }
    }
}
