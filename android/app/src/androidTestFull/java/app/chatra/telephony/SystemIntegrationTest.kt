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
    private fun queue(time: SmsQueueTime? = null, transport: SmsTransport): PersistentSmsQueue {
        val role = object : SmsRoleGate {
            override fun isHeld() = held && platform.isHeld()
            override suspend fun capability() = SmsCapability.Available
            override suspend fun request() = RoleRequestResult.Unavailable
            override fun openSystemSettings() {}
        }
        val sims = object : SimRepository { override fun cards() = flowOf(listOf(sim)) }
        return if (time == null) PersistentSmsQueue(context, db, role, history, sims, PlatformSmsText(context), RoomDrafts(db), transport, work)
        else PersistentSmsQueue(context, db, role, history, sims, PlatformSmsText(context), RoomDrafts(db), transport, work, time)
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

    private class FakeTime(var tick: Long = 100000, var epoch: Long = System.currentTimeMillis(), var bootId: Int = 8) : SmsQueueTime {
        override fun elapsed() = tick
        override fun wall() = epoch
        override fun boot() = bootId
    }

    @Test fun undoDeadlineIsEnforcedAtTransportAt2900Exactly3000And3100EvenAfterWallClockChange() = runBlocking {
        val time = FakeTime()
        val calls = mutableListOf<String>()
        val queue = queue(time) { _, address, _, _, _ -> calls += address }
        for (offset in listOf(2900L, 3000L, 3100L)) {
            val address = number()
            val start = time.tick
            val op = queue.enqueue(draft("Granica $offset", address), 3)
            time.tick = start + offset; time.epoch -= 86400000
            queue.dispatch(op.value)
            assertEquals("offset=$offset", if (offset < 3000) 0 else 1, calls.count { it == address })
            if (offset < 3000) assertTrue(queue.cancel(op)) else assertFalse(queue.cancel(op))
            queue.dispatch(op.value)
            assertEquals(if (offset < 3000) 0 else 1, calls.count { it == address })
        }
    }

    @Test fun concurrentCancelVersusDispatchHasOneWinnerAndDuplicateWorkersNeverResend() = runBlocking {
        val time = FakeTime()
        val calls = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()
        val queue = queue(time) { _, address, _, _, _ -> calls.computeIfAbsent(address) { java.util.concurrent.atomic.AtomicInteger() }.incrementAndGet() }
        repeat(12) {
            val address = number()
            val op = queue.enqueue(draft("Wyścig $it", address), 3)
            time.tick += 3100
            val cancelled = coroutineScope {
                val cancel = async(Dispatchers.IO) { queue.cancel(op) }
                val send1 = async(Dispatchers.IO) { queue.dispatch(op.value) }
                val send2 = async(Dispatchers.IO) { queue.dispatch(op.value) }
                send1.await(); send2.await(); cancel.await()
            }
            assertEquals(if (cancelled) 0 else 1, calls[address]?.get() ?: 0)
            assertFalse(queue.cancel(op))
        }
    }

    @Test fun restartWithinBootResumesDeadlineButDeviceRebootRequiresDecision() = runBlocking {
        val time = FakeTime()
        var calls = 0
        val queue = queue(time) { _, _, _, _, _ -> calls++ }
        val first = queue.enqueue(draft("Proces", number()), 3)
        time.tick += 2900
        val restarted = queue(time) { _, _, _, _, _ -> calls++ }
        restarted.dispatch(first.value); assertEquals(0, calls)
        time.tick += 100
        restarted.dispatch(first.value); assertEquals(1, calls)
        val secondDraft = draft("Restart urządzenia", number()).let { it.copy(recipients = it.recipients + Address(number()), recipientMode = RecipientMode.SeparateSms) }
        val second = restarted.enqueue(secondDraft, 3)
        val conversations = db.dao().recipients(second.value).map { ConversationId(it.conversationId) }
        withTimeout(15000) { history.inbox().first { rows -> conversations.all { id -> rows.any { it.id == id } } } }
        time.bootId++; time.tick = 50
        val rebooted = queue(time) { _, _, _, _, _ -> calls++ }
        rebooted.dispatch(second.value)
        assertEquals("MISSED_NEEDS_DECISION", db.dao().operation(second.value)!!.state)
        withTimeout(15000) { history.inbox().first { rows -> conversations.all { id -> rows.any { it.id == id } } } }
        assertEquals(1, calls)
        assertNotNull(rebooted.copyToDraft(second))
    }

    @Test fun draftsRoundTripAllFieldsRejectStaleWritesAndPreserveNewTextWhenUndoRestoresSnapshot() = runBlocking {
        val drafts = RoomDrafts(db)
        val conv = history.conversationFor(listOf(Address(number())))
        val rich = draft("Pełny szkic", number()).copy(conversationId = conv, attachmentIds = listOf(AttachmentId("file-1"), AttachmentId("file-2")),
            attachmentBytes = 12345, mediaVariant = MediaVariant.NoLocationCopy, revision = 5, recipientNames = listOf("Kontakt"), recipientInput = "kolejny")
        drafts.save(rich); drafts.save(rich.copy(text = "stary zapis", revision = 4))
        assertEquals(rich, RoomDrafts(db).find(rich.id))
        val sendable = rich.copy(attachmentIds = emptyList(), attachmentBytes = 0, mediaVariant = null, recipientInput = "", revision = 6)
        val queue = queue { _, _, _, _, _ -> fail("Cancelled operation sent") }
        drafts.save(sendable.copy(text = "Nowsza wersja przed przyjęciem", revision = 7))
        val op = queue.enqueue(sendable, 3)
        drafts.save(sendable.copy(text = "spóźniony zapis", revision = 7))
        assertNull(drafts.find(sendable.id))
        val newer = sendable.copy(id = DraftId.new(), text = "Nowy tekst", revision = 1)
        drafts.save(newer)
        assertTrue(queue.cancel(op)); assertFalse(queue.cancel(op)); queue.dispatch(op.value)
        val all = drafts.all().first()
        assertTrue(all.any { it.text == "Nowsza wersja przed przyjęciem" && it.id != sendable.id })
        assertTrue(all.any { it.id == newer.id && it.text == "Nowy tekst" })
        val restored = all.single { it.text == sendable.text }
        assertEquals(sendable.copy(id = restored.id, updatedAt = restored.updatedAt), restored)
        assertEquals("CANCELLED", db.dao().operation(op.value)!!.state)
    }

    @Test fun recoveryDistinguishesPreparedFromSubmittingAndReturnedWithoutGuessingFromOutbox() = runBlocking {
        val queue = queue { _, _, _, _, _ -> }
        val ids = listOf("PREPARED", "SUBMITTING", "RETURNED").map { phase ->
            val id = queue.enqueue(draft(phase, number()), 0); queue.dispatch(id.value)
            db.dao().phase(db.dao().attempts(id.value).single().id, phase)
            phase to id
        }
        val restarted = queue { _, _, _, _, _ -> fail("Recovery must never resend") }
        restarted.awaitReady()
        for ((phase, id) in ids) {
            assertEquals(if (phase == "PREPARED") "FAILED" else "DISPATCH_UNCERTAIN", db.dao().operation(id.value)!!.state)
            if (phase != "PREPARED") assertFalse(restarted.retry(id))
        }
    }

    @Test fun explicitRetryUsesOnlyCertainFailuresAndSelectedLiveSimWithOneChild() = runBlocking {
        val time = FakeTime()
        var calls = 0
        val queue = queue(time) { _, _, _, _, _ -> calls++ }
        val id = queue.enqueue(draft("Ponów pewną porażkę", number()), 0); queue.dispatch(id.value)
        val attempt = db.dao().attempts(id.value).single()
        queue.callback(status(attempt, 0), android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE)
        assertEquals("FAILED", db.dao().operation(id.value)!!.state)
        try { queue.retry(id, SubscriptionId(Int.MAX_VALUE)); fail("Missing SIM accepted") } catch (_: IllegalStateException) { }
        assertNull(db.dao().retryChild(id.value))
        assertTrue(queue.retry(id, sim.id)); assertFalse(queue.retry(id, sim.id))
        val child = db.dao().retryChild(id.value)!!
        assertEquals(sim.id.value, child.subscriptionId)
        assertEquals("FAILED", db.dao().operation(id.value)!!.state)
        queue.dispatch(child.id); assertEquals(1, calls)
        time.tick += 3000
        queue.dispatch(child.id); queue.dispatch(child.id); assertEquals(2, calls)
    }

    @Test fun multipartFailureWaitsForAllPartsAndPartialOrGenericFailureCannotRetry() = runBlocking {
        val queue = queue { _, _, _, _, _ -> }
        val id = queue.enqueue(draft("a".repeat(200), number()), 0); queue.dispatch(id.value)
        val attempt = db.dao().attempts(id.value).single(); assertEquals(2, attempt.partCount)
        queue.callback(status(attempt, 0), android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE)
        assertEquals("DISPATCHING", db.dao().operation(id.value)!!.state)
        queue.callback(status(attempt, 1), Activity.RESULT_OK)
        assertEquals("PARTIALLY_SENT", db.dao().operation(id.value)!!.state)
        assertFalse(queue.retry(id)); assertNotNull(queue.copyToDraft(id))
        val unknown = queue.enqueue(draft("Niepewny modem", number()), 0); queue.dispatch(unknown.value)
        queue.callback(status(db.dao().attempts(unknown.value).single(), 0), android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE)
        assertEquals("DISPATCH_UNCERTAIN", db.dao().operation(unknown.value)!!.state)
        assertFalse(queue.retry(unknown))
    }

    @Test fun latePositiveCallbackCancelsPendingRetryAndDeliveredDoesNotRegress() = runBlocking {
        val time = FakeTime()
        var calls = 0
        val queue = queue(time) { _, _, _, _, _ -> calls++ }
        val id = queue.enqueue(draft("Późny callback", number()), 0); queue.dispatch(id.value)
        val attempt = db.dao().attempts(id.value).single()
        queue.callback(status(attempt, 0), android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE)
        assertTrue(queue.retry(id))
        val child = db.dao().retryChild(id.value)!!
        queue.callback(status(attempt, 0), Activity.RESULT_OK)
        assertEquals("CANCELLED", db.dao().operation(child.id)!!.state)
        time.tick += 3000; queue.dispatch(child.id); assertEquals(1, calls)
        db.dao().delivered(attempt.id, 0, "Delivered")
        queue.callback(status(attempt, 0), Activity.RESULT_OK)
        queue.callback(status(attempt, 0), android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE)
        assertEquals("DELIVERED", db.dao().operation(id.value)!!.state)
    }

    @Test fun reconcileRequiresExactOwnedProviderEvidenceAndMissingDeliveryStaysSent() = runBlocking {
        val queue = queue { _, _, _, _, _ -> }
        val d = draft("Porównywalny rekord", number())
        val id = queue.enqueue(d, 0); queue.dispatch(id.value); queue.interrupted(id.value)
        val attempt = db.dao().attempts(id.value).single()
        val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, attempt.providerId!!)
        context.contentResolver.update(uri, ContentValues().apply { put("type", 2); put("status", 32); put("body", "Inny rekord") }, null, null)
        queue.reconcile(id); assertEquals("DISPATCH_UNCERTAIN", db.dao().operation(id.value)!!.state)
        context.contentResolver.update(uri, ContentValues().apply { put("body", d.text) }, null, null)
        queue.reconcile(id); assertEquals("SENT", db.dao().operation(id.value)!!.state)
        assertEquals("Unknown", db.dao().parts(attempt.id).single().delivery)
        context.contentResolver.update(uri, ContentValues().apply { put("status", 0) }, null, null)
        queue.reconcile(id); assertEquals("DELIVERED", db.dao().operation(id.value)!!.state)
        queue.callback(status(attempt, 0), android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE)
        assertEquals("DELIVERED", db.dao().operation(id.value)!!.state)
    }

    @Test fun mixedRecipientsRetryOnlyDefinitelyFailedAddressesAndDeliveryFailureNeverAllowsResend() = runBlocking {
        val time = FakeTime()
        val queue = queue(time) { _, _, _, _, _ -> }
        val recipients = listOf(number(), number(), number())
        val id = queue.enqueue(draft("Trzech odbiorców", recipients.first()).copy(recipients = recipients.map(::Address), recipientMode = RecipientMode.SeparateSms), 0)
        queue.dispatch(id.value)
        val attempts = db.dao().attempts(id.value).associateBy { it.address }
        queue.callback(status(attempts.getValue(recipients[0]), 0), Activity.RESULT_OK)
        queue.callback(status(attempts.getValue(recipients[1]), 0), android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE)
        queue.callback(status(attempts.getValue(recipients[2]), 0), Activity.RESULT_OK)
        assertEquals("PARTIALLY_SENT", db.dao().operation(id.value)!!.state)
        db.dao().delivered(attempts.getValue(recipients[0]).id, 0, "Failed")
        queue.callback(status(attempts.getValue(recipients[0]), 0), Activity.RESULT_OK)
        assertTrue(queue.retry(id))
        assertEquals(listOf(recipients[1]), db.dao().recipients(db.dao().retryChild(id.value)!!.id).map { it.address })
        assertTrue(queue.operations().first().first { it.id == id }.deliveryFailed)
    }

    @Test fun sentProofWinsEvenWhenTransportThrowsAfterPartialCallback() = runBlocking {
        lateinit var queue: PersistentSmsQueue
        queue = queue { _, _, _, _, _ ->
            runBlocking {
                val row = db.dao().operations().first().first { it.state == "DISPATCHING" }
                val attempt = db.dao().attempts(row.id).single()
                queue.callback(status(attempt, 0), Activity.RESULT_OK)
            }
            throw SecurityException("Failure after a positive callback")
        }
        val id = queue.enqueue(draft("a".repeat(200), number()), 0)
        queue.dispatch(id.value)
        assertEquals("PARTIALLY_SENT", db.dao().operation(id.value)!!.state)
        assertFalse(queue.retry(id))
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
