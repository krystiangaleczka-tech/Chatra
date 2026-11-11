package app.chatra.telephony

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Base64
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.chatra.core.database.*
import app.chatra.core.model.*
import app.chatra.platform.SystemPlatform
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Tylko izolowany emulator. Transport jest atrapą; żaden test nie wywołuje SmsManager.send*. */
@RunWith(AndroidJUnit4::class)
class SystemIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: ChatraDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var platform: SystemPlatform
    private lateinit var history: SystemHistory
    private lateinit var sim: SimCard
    private val numbers = mutableSetOf<String>()
    private val mms = mutableListOf<Uri>()
    private val files = mutableListOf<File>()
    private var held = true
    private val work = object : SmsWork { override fun schedule(id: String, deadline: Long) {}; override fun cancel(id: String) {} }
    private fun number() = ("+48999" + System.nanoTime().toString().takeLast(8)).also { numbers += it }

    @Before fun prepare() {
        assumeTrue("Test zapisuje wyłącznie fikcyjne wiadomości w emulatorze", Build.HARDWARE in listOf("ranchu", "goldfish"))
        platform = SystemPlatform(context)
        assumeTrue(platform.isHeld())
        assumeTrue(context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
        val card = context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty().firstOrNull()
        assumeTrue(card != null)
        sim = SimCard(SubscriptionId(card!!.subscriptionId), card.simSlotIndex, card.displayName.toString())
        db = Room.inMemoryDatabaseBuilder(context, ChatraDatabase::class.java).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val contacts = object : ContactsDirectory { override fun suggestions() = flowOf(emptyList<Participant>()); override suspend fun lookup(address: Address): Participant? = null }
        history = SystemHistory(context, db, platform, contacts, scope)
    }
    @After fun cleanup() = runBlocking {
        if (!::db.isInitialized) return@runBlocking
        scope.coroutineContext[Job]!!.cancelAndJoin()
        for (address in numbers) context.contentResolver.delete(Telephony.Sms.CONTENT_URI, "address=?", arrayOf(address))
        for (uri in mms) context.contentResolver.delete(uri, null, null)
        files.forEach { it.delete() }
        db.close()
    }
    private fun queue(transport: SmsTransport): PersistentSmsQueue {
        val role = object : SmsRoleGate {
            override fun isHeld() = held && platform.isHeld()
            override suspend fun capability() = SmsCapability.Available
            override suspend fun request() = RoleRequestResult.Unavailable
            override fun openSystemSettings() {}
        }
        val sims = object : SimRepository { override fun cards() = flowOf(listOf(sim)) }
        return PersistentSmsQueue(context, db, role, history, sims, PlatformSmsText(context), RoomDrafts(db), transport, work)
    }
    private fun draft(body: String, address: String) = Draft(DraftId.new(), null, listOf(Address(address)), body, RecipientMode.Single, emptyList(), sim.id, Instant.now(), subscriptionLabel = sim.label)
    private fun status(attempt: OutgoingAttempt, part: Int, kind: String = "sent") = Intent("app.chatra.SMS_STATUS", Uri.parse("chatra-callback://sms/${attempt.id}/$part/$kind/${attempt.callbackToken}"))
    private fun smsRows(address: String): List<Pair<Long, String>> {
        val out = mutableListOf<Pair<Long, String>>()
        context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf("_id", "body"), "address=?", arrayOf(address), null)!!.use { c -> while (c.moveToNext()) out += c.getLong(0) to c.getString(1) }
        return out
    }

    @Test fun providerFailureKeepsPreviouslyIndexedReferences() = runBlocking {
        scope.coroutineContext[Job]!!.cancelAndJoin()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): android.database.Cursor? = throw android.database.sqlite.SQLiteException("Symulowany błąd providera")
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        }
        provider.attachInfo(context, android.content.pm.ProviderInfo().apply { authority = "sms" })
        val resolver = ContentResolver.wrap(provider)
        val broken = object : ContextWrapper(context) { override fun getContentResolver() = resolver }
        db.dao().insertConversation(ConversationMeta("existing", 123, "123", "[\"123\"]"))
        db.dao().upsertReferences(listOf(ProviderMessageRef("SMS", 123, "existing", 1, true, false, "old")))
        val contacts = object : ContactsDirectory { override fun suggestions() = flowOf(emptyList<Participant>()); override suspend fun lookup(address: Address): Participant? = null }
        val history = SystemHistory(broken, db, platform, contacts, scope)
        withTimeout(10000) { history.historyStatus().first { it is HistoryStatus.Error } }
        assertTrue(history.diagnostic!!.startsWith("SQLiteException"))
        assertEquals(123L, db.dao().page("existing", 200).single().providerId)
    }

    @Test fun duplicateEnqueueAndWorkerReplayInvokeTransportOnceAndSentIsNotDelivered() = runBlocking {
        var calls = 0
        val queue = queue { subscription, _, _, _, _ -> assertEquals(sim.id, subscription); calls++ }
        val draft = draft("Test jednej wiadomości", number())
        val id = queue.enqueue(draft, 0)
        assertEquals(id, queue.enqueue(draft, 0))
        queue.dispatch(id.value); queue.dispatch(id.value)
        assertEquals(1, calls)
        val attempt = db.dao().attempts(id.value).single()
        queue.callback(status(attempt, 0), Activity.RESULT_OK)
        assertEquals("SENT", db.dao().operation(id.value)!!.state)
        assertEquals("Unknown", db.dao().parts(attempt.id).single().delivery)
        queue.callback(status(attempt, 0), 1)
        assertEquals("SENT", db.dao().operation(id.value)!!.state)
        assertEquals(1, smsRows(draft.recipients.single().raw).size)
    }

    @Test fun androidPendingIntentPreservesSentResultBeforeGoAsync() = runBlocking {
        val container = (context.applicationContext as app.chatra.ChatraApplication).container(false)
        val live = ChatraDatabase.open(context)
        val recipient = number()
        val conversation = container.conversations.conversationFor(listOf(Address(recipient)))
        val operation = UUID.randomUUID().toString()
        val attempt = OutgoingAttempt(UUID.randomUUID().toString(), operation, recipient, sim.id.value, sim.label, System.currentTimeMillis(), UUID.randomUUID().toString(), 1)
        try {
            live.dao().insertOperation(OutgoingOperation(operation, operation, conversation.value, JSONArray(listOf(recipient)).toString(), "callback-test", "Single", sim.id.value, sim.label, "DISPATCHING", 0, System.currentTimeMillis(), System.currentTimeMillis()))
            live.dao().recipient(OutgoingRecipient(operation, recipient, conversation.value, "DISPATCHING"))
            live.dao().attempt(attempt); live.dao().parts(listOf(OutgoingPart(attempt.id, 0)))
            val intent = status(attempt, 0).setClass(context, SmsStatusReceiver::class.java)
            android.app.PendingIntent.getBroadcast(context, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT).send(context, Activity.RESULT_OK, null)
            withTimeout(10000) { while (live.dao().parts(attempt.id).single().sent == "Pending") delay(30) }
            val part = live.dao().parts(attempt.id).single()
            assertEquals(Activity.RESULT_OK, part.resultCode)
            assertEquals("Sent", part.sent)
        } finally {
            live.openHelper.writableDatabase.execSQL("DELETE FROM OutgoingOperation WHERE id=?", arrayOf(operation))
            live.close()
        }
    }
    @Test fun multipartCallbacksOutOfOrderKeepOriginalBodyAndSeparateParts() = runBlocking {
        var sentParts = emptyList<String>()
        val queue = queue { _, _, parts, _, _ -> sentParts = parts }
        val draft = draft("a".repeat(161), number())
        val id = queue.enqueue(draft, 0); queue.dispatch(id.value)
        assertEquals(2, sentParts.size); assertEquals(draft.text, sentParts.joinToString(""))
        val attempt = db.dao().attempts(id.value).single()
        queue.callback(status(attempt, 1), Activity.RESULT_OK)
        assertEquals("DISPATCHING", db.dao().operation(id.value)!!.state)
        queue.callback(status(attempt, 0), Activity.RESULT_OK)
        assertEquals("SENT", db.dao().operation(id.value)!!.state)
        assertEquals(draft.text, smsRows(draft.recipients.single().raw).single().second)
    }
    @Test fun crashAtTransportBoundaryIsUncertainAndNeverAutomaticallyRetried() = runBlocking {
        var calls = 0
        val queue = queue { _, _, _, _, _ -> calls++; throw IllegalStateException("Symulowana utrata odpowiedzi API") }
        val id = queue.enqueue(draft("Nie ponawiaj automatycznie", number()), 0)
        queue.dispatch(id.value); queue.recover(); queue.dispatch(id.value)
        assertEquals("DISPATCH_UNCERTAIN", db.dao().operation(id.value)!!.state)
        assertEquals(1, calls); assertFalse(queue.retry(id))
    }
    @Test fun roleLossBeforeDispatchDoesNotCallTransportAndCancelRestoresDraft() = runBlocking {
        var calls = 0
        val queue = queue { _, _, _, _, _ -> calls++ }
        val original = draft("Zachowaj treść", number())
        val id = queue.enqueue(original, 10)
        assertTrue(queue.cancel(id)); assertFalse(queue.cancel(id)); queue.dispatch(id.value)
        val conversation = db.dao().operation(id.value)!!.conversationId!!
        assertEquals(original.text, RoomDrafts(db).draft(ConversationId(conversation)).first()!!.text)
        val another = queue.enqueue(draft("Utrata roli", number()), 0)
        held = false; queue.dispatch(another.value)
        assertEquals("MISSED_NEEDS_DECISION", db.dao().operation(another.value)!!.state)
        assertEquals(0, calls)
    }

    @Test fun multipartIncomingReplayAndRecoveryGapCreateOneCanonicalSms() = runBlocking {
        val sender = number()
        val key = MessageDigest.getInstance("SHA-256").digest(UUID.randomUUID().toString().toByteArray()).joinToString("") { "%02x".format(it) }
        val json = JSONObject().put("kind", "sms").put("format", "3gpp").put("subscription", sim.id.value).put("receivedAt", System.currentTimeMillis())
            .put("pdus", JSONArray(listOf(pdu(sender, "Zażółć", 1), pdu(sender, " gęślą", 2)).map { Base64.encodeToString(it, Base64.NO_WRAP) })).toString()
        val file = File(context.filesDir, "integration-incoming/$key.json").apply { parentFile!!.mkdirs() }.also { files += it }
        val incoming = IncomingSms(context, db, platform, history, file.parentFile!!)
        file.writeText(json); assertTrue(incoming.process(key))
        file.writeText(json); assertTrue(incoming.process(key))
        val receipt = db.dao().receipt(key)!!
        db.dao().receipt(receipt.copy(state = "writing", providerId = null))
        file.writeText(json); assertTrue(incoming.process(key))
        assertEquals("Zażółć gęślą", smsRows(sender).single().second)
        val summary = withTimeout(15000) { history.inbox().first { rows -> rows.any { it.participants.any { p -> p.address.raw == sender } } }.first { it.participants.any { p -> p.address.raw == sender } } }
        assertEquals(1, summary.unreadCount)
        val message = withTimeout(15000) { history.messages(summary.id).first { it.isNotEmpty() }.single() }
        history.markShown(summary.id, setOf(message.ref))
        context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf("read", "sub_id"), "address=?", arrayOf(sender), null)!!.use { c -> assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0)); assertEquals(sim.id.value, c.getInt(1)) }
    }

    @Test fun largeHistoryPagesAndReadOnlyShownMessagesPreserveConversationIdentity() = runBlocking {
        val sender = number(); val thread = Telephony.Threads.getOrCreateThreadId(context, sender); val at = System.currentTimeMillis()
        val values = (0 until 450).map { i -> ContentValues().apply { put("address", sender); put("thread_id", thread); put("body", "Historia $i"); put("date", at + i); put("read", 0); put("sub_id", sim.id.value) } }.toTypedArray()
        assertEquals(450, context.contentResolver.bulkInsert(Telephony.Sms.Inbox.CONTENT_URI, values))
        val summary = withTimeout(20000) { history.inbox().first { list -> list.any { it.unreadCount == 450 && it.participants.any { p -> p.address.raw == sender } } }.first { it.participants.any { p -> p.address.raw == sender } } }
        val initial = withTimeout(15000) { history.messages(summary.id).first { it.size == 200 } }
        assertTrue(history.hasOlder(summary.id).first())
        history.loadOlder(summary.id); withTimeout(15000) { history.messages(summary.id).first { it.size == 400 } }
        history.loadOlder(summary.id); withTimeout(15000) { history.messages(summary.id).first { it.size == 450 } }
        assertFalse(history.hasOlder(summary.id).first())
        history.markShown(summary.id, initial.takeLast(3).map { it.ref }.toSet())
        context.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf("_id"), "address=? AND read=0", arrayOf(sender), null)!!.use { assertEquals(447, it.count) }
        history.setPinned(summary.id, true); history.refresh()
        withTimeout(15000) { history.inbox().first { it.any { row -> row.id == summary.id && row.pinned && row.unreadCount == 447 } } }
        history.markRead(summary.id); history.markUnread(summary.id)
        withTimeout(15000) { history.inbox().first { it.any { row -> row.id == summary.id && row.unreadCount == 450 } } }
        Unit
    }

    @Test fun existingMmsPartsExposeStableLazyMediaReferences() = runBlocking {
        val sender = number(); val thread = Telephony.Threads.getOrCreateThreadId(context, sender)
        val uri = context.contentResolver.insert(Telephony.Mms.CONTENT_URI, ContentValues().apply { put("thread_id", thread); put("date", System.currentTimeMillis() / 1000); put("msg_box", 1); put("m_type", 132); put("read", 0) })!!.also { mms += it }
        val id = uri.lastPathSegment!!.toLong()
        context.contentResolver.insert(Uri.parse("content://mms/$id/addr"), ContentValues().apply { put("address", sender); put("type", 137); put("charset", 106) })
        val textPart = checkNotNull(context.contentResolver.insert(Uri.parse("content://mms/$id/part"), ContentValues().apply { put(Telephony.Mms.Part.CONTENT_TYPE, "text/plain"); put(Telephony.Mms.Part.TEXT, "Treść MMS"); put(Telephony.Mms.Part.CHARSET, 106) }))
        val part = context.contentResolver.insert(Uri.parse("content://mms/$id/part"), ContentValues().apply { put("ct", "image/png"); put("name", "test.png") })!!
        context.contentResolver.openOutputStream(part)!!.use { Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        history.refresh()
        val summary = try { withTimeout(15000) { history.inbox().first { rows -> rows.any { it.participants.any { p -> p.address.raw == sender } } }.first { it.participants.any { p -> p.address.raw == sender } } } }
            catch (error: Exception) { throw AssertionError("MMS summary: ${history.historyStatus().first()}, ${history.diagnostic}; refs=${db.dao().references().first()}", error) }
        var observed: List<Message> = emptyList()
        val message = try { withTimeout(15000) { history.messages(summary.id).onEach { observed = it }.first { it.any { m -> m.body == "Treść MMS" && m.attachments.isNotEmpty() } }.single { (it.ref as? MessageRef.Provider)?.ref == ProviderRef(ProviderTransport.MMS, id) } } }
            catch (error: Exception) { throw AssertionError("MMS parts: ${history.historyStatus().first()}, ${history.diagnostic}; observed=$observed; media=${db.dao().media(summary.id.value,id)}", error) }
        assertEquals("image/png", message.attachments.single().mimeType)
        assertTrue(message.attachments.single().contentUri!!.startsWith("content://mms/part/"))
        context.contentResolver.update(textPart, ContentValues().apply { put(Telephony.Mms.Part.TEXT, "Zmieniona treść MMS") }, null, null)
        val updated = withTimeout(15000) { history.messages(summary.id).first { it.any { m -> m.body == "Zmieniona treść MMS" } }.single { it.ref == message.ref } }
        assertEquals(message.attachments.single().id, updated.attachments.single().id)
    }

    private fun pdu(sender: String, body: String, part: Int): ByteArray {
        val digits = sender.removePrefix("+")
        val address = digits.chunked(2).map { pair -> ((pair.getOrNull(1)?.digitToInt() ?: 15) shl 4) or pair[0].digitToInt() }
        val payload = byteArrayOf(5, 0, 3, 42, 2, part.toByte()) + body.toByteArray(Charsets.UTF_16BE)
        return ByteArrayOutputStream().apply {
            write(byteArrayOf(0, 0x44, digits.length.toByte(), 0x91.toByte())); address.forEach(::write)
            write(byteArrayOf(0, 8, 0x62, 0x11, 0x01, 0x21, 0, 0, 0, payload.size.toByte())); write(payload)
        }.toByteArray()
    }
}
