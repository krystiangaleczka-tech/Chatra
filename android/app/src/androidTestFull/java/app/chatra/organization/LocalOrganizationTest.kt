package app.chatra.organization

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.chatra.core.database.*
import app.chatra.core.model.*
import app.chatra.platform.SystemPlatform
import app.chatra.telephony.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class LocalOrganizationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val platform get() = SystemPlatform(context)
    private val contacts = object : ContactsDirectory {
        override fun suggestions() = flowOf(emptyList<Participant>())
        override suspend fun lookup(address: Address): Participant? = null
    }

    @Test fun normalizedBlocksSurviveRepositoryRecreationWithoutDeletingHistory() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(context, ChatraDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val first = LocalOrganization(context, db, platform, scope, allowed = { true })
            first.block(Address("999 000 123"))
            first.block(Address("0048 999-000-123"))
            val recreated = LocalOrganization(context, db, platform, scope, allowed = { true })
            withTimeout(5000) { recreated.blocked.first { it.size == 1 } }
            assertEquals("+48999000123", recreated.blocked.value.single().id)
            recreated.unblock(recipientKey("+48 999 000 123"))
            withTimeout(5000) { recreated.blocked.first { it.isEmpty() } }
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); db.close() }
    }

    @Test fun snapshotKeepsSmsAndMmsSeparateAndRestoreDoesNotDuplicateNewMessages() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(context, ChatraDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var now = 1000L
        try {
            val org = LocalOrganization(context, db, platform, scope, now = { now }, allowed = { true })
            db.dao().insertConversation(ConversationMeta("c", 1, "123", "[\"123\"]"))
            db.dao().upsertReferences(listOf(ProviderMessageRef("SMS", 7, "c", 100, true, false, "g"), ProviderMessageRef("MMS", 7, "c", 200, true, false, "g")))
            db.dao().upsertMedia(listOf(MediaRef(99, messageId = 7, conversationId = "c", mimeType = "image/jpeg", label = "photo", contentUri = "content://mms/part/99")))
            org.addToTrash(ConversationId("c"), "Test")
            val entry = db.organization().trash().first().single()
            assertEquals(2, db.organization().refs(entry.id).size)
            assertTrue(db.dao().page("c", 100).isEmpty())
            assertEquals(0, db.dao().galleryCount("c", ""))
            db.dao().upsertReferences(listOf(ProviderMessageRef("SMS", 8, "c", 300, true, false, "g")))
            assertEquals(8L, db.dao().page("c", 100).single().providerId)
            val visible = db.dao().indices().first().single()
            assertEquals(300L, visible.lastAt)
            assertEquals(1, visible.unreadCount)
            now += 29L * 86400000
            org.restoreEntry(entry.id)
            org.restoreEntry(entry.id)
            assertEquals(3, db.dao().page("c", 100).size)
            assertEquals(1, db.dao().galleryCount("c", ""))
            assertTrue(db.organization().trash().first().isEmpty())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); db.close() }
    }

    @Test fun expiryAt30DaysWaitsForAccessAndPartialPurgeCanResumeAt31Days() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(context, ChatraDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var now = 1000L; var access = true; var fail = true
        val deleted = mutableListOf<Long>()
        try {
            val org = LocalOrganization(context, db, platform, scope, now = { now }, allowed = { access }, deleteRef = {
                if (it.providerId == 2L && fail) throw java.io.IOException("Injected provider failure")
                deleted += it.providerId
            })
            db.dao().insertConversation(ConversationMeta("c", 1, "123", "[\"123\"]"))
            db.dao().upsertReferences((1L..2).map { ProviderMessageRef("SMS", it, "c", it, true, false, "g") })
            org.addToTrash(ConversationId("c"), "Test")
            val entry = db.organization().trash().first().single()
            db.dao().upsertReferences(listOf(ProviderMessageRef("SMS", 3, "c", 3, true, false, "g")))
            now = entry.expiresAt - 1
            org.expire(); assertTrue(deleted.isEmpty())
            now = entry.expiresAt; access = false
            org.expire(); assertTrue(deleted.isEmpty())
            assertTrue(runCatching { org.restoreEntry(entry.id) }.isFailure)
            access = true
            assertTrue(runCatching { org.expire() }.isFailure)
            assertEquals(listOf(1L), deleted)
            assertTrue(db.organization().entry(entry.id)!!.purging)
            assertTrue(runCatching { org.restoreEntry(entry.id) }.isFailure)
            // Simulate the next process/worker, after a day; same persisted scope.
            now += 86400000L; fail = false
            val resumed = LocalOrganization(context, db, platform, scope, now = { now }, allowed = { true }, deleteRef = { deleted += it.providerId })
            resumed.expire()
            assertEquals(setOf(1L, 2L), deleted.toSet())
            assertFalse(3L in deleted)
            assertEquals(3L, db.dao().page("c", 100).single().providerId)
            assertTrue(db.organization().trash().first().isEmpty())
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); db.close() }
    }

    @Test fun realProviderPurgeRemovesOnlyCapturedSmsAndMmsIncludingParts() = runBlocking<Unit> {
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        check(platform.isHeld()) { "Test requires the SMS role on the emulator" }
        val db = Room.inMemoryDatabaseBuilder(context, ChatraDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val owned = mutableListOf<Uri>()
        val at = (System.currentTimeMillis() / 1000) * 1000
        val number = "+48999000124"
        val thread = Telephony.Threads.getOrCreateThreadId(context, setOf(number))
        fun sms(date: Long): Uri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, ContentValues().apply {
            put("address", number); put("body", "CH043 synthetic QA"); put("date", date); put("thread_id", thread); put("read", 1)
        })!!.also { owned += it }
        try {
            val old = sms(at)
            val mms = context.contentResolver.insert(Telephony.Mms.CONTENT_URI, ContentValues().apply {
                put("thread_id", thread); put("date", at / 1000); put("msg_box", 1); put("m_type", 132); put("read", 1)
            })!!.also { owned += it }
            val mmsId = android.content.ContentUris.parseId(mms)
            val part = context.contentResolver.insert(Uri.parse("content://mms/$mmsId/part"), ContentValues().apply { put("ct", "text/plain"); put("text", "CH043 QA part"); put("chset", 106) })!!
            val id = "qa-scope"
            db.dao().insertConversation(ConversationMeta(id, thread, recipientKey(number), "[\"$number\"]"))
            db.dao().upsertReferences(listOf(ProviderMessageRef("SMS", android.content.ContentUris.parseId(old), id, at, true, true, "g"), ProviderMessageRef("MMS", mmsId, id, at, true, true, "g")))
            val org = LocalOrganization(context, db, platform, scope)
            org.addToTrash(ConversationId(id), "QA")
            val fresh = sms(at + 1000)
            org.purge(db.organization().trash().first().single().id)
            fun count(uri: Uri) = context.contentResolver.query(uri, arrayOf("_id"), null, null, null)!!.use { it.count }
            assertEquals(0, count(old)); assertEquals(0, count(mms)); assertEquals(0, count(part))
            assertEquals(1, count(fresh))
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin(); owned.forEach { context.contentResolver.delete(it, null, null) }; db.close()
        }
    }

    @Test fun blockedIncomingSmsKeepsHistorySuppressesNotificationAndUnblockRestoresIt() = runBlocking<Unit> {
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val c = (context.applicationContext as app.chatra.ChatraApplication).container(false) as app.chatra.di.FullTelephonyContainer
        check(c.smsRole.isHeld())
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val number = "+48555" + (100000 + (java.util.UUID.randomUUID().leastSignificantBits and Long.MAX_VALUE) % 900000)
        val owned = mutableListOf<Uri>()
        var id: ConversationId? = null
        suspend fun waitFor(check: () -> Boolean) { withTimeout(15000) { while (!check()) delay(50) } }
        fun insert() {
            val thread = Telephony.Threads.getOrCreateThreadId(context, setOf(number))
            owned += context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, ContentValues().apply {
                put("thread_id", thread); put("address", number); put("body", "CH042 QA synthetic"); put("date", System.currentTimeMillis()); put("read", 0)
            })!!
        }
        try {
            insert(); c.conversations.refresh()
            id = withTimeout(15000) { c.conversations.inbox().first { rows -> rows.any { it.participants.any { p -> p.address.raw == number } } }.first { it.participants.any { p -> p.address.raw == number } }.id }
            val tag = "conversation:${id.value}"
            waitFor { manager.activeNotifications.any { it.tag == tag } }
            c.organization.block(Address(number))
            waitFor { manager.activeNotifications.none { it.tag == tag } }
            insert(); c.conversations.refresh()
            withTimeout(15000) { c.conversations.messages(id).first { it.size == 2 } }
            delay(1250)
            assertTrue(c.conversations.conversation(id).first()!!.blocked)
            assertFalse(manager.activeNotifications.any { it.tag == tag })
            c.organization.unblock(recipientKey(number))
            waitFor { manager.activeNotifications.any { it.tag == tag } }
            assertEquals(2, c.conversations.messages(id).first().size)
        } finally {
            c.organization.unblock(recipientKey(number))
            owned.forEach { context.contentResolver.delete(it, null, null) }
            id?.let { manager.cancel("conversation:${it.value}", 10) }
            c.conversations.refresh()
        }
    }

    @Test fun trashRebuildAndLaterContactRespectOriginalScopeAndUnknownIsNotSpam() = runBlocking<Unit> {
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val platform = platform
        check(platform.isHeld())
        val db = Room.inMemoryDatabaseBuilder(context, ChatraDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val people = MutableStateFlow(emptyList<Participant>())
        val directory = object : ContactsDirectory {
            override fun suggestions() = people
            override suspend fun lookup(address: Address) = people.value.firstOrNull { recipientKey(it.address.raw) == recipientKey(address.raw) }
        }
        val number = "+48999000125"
        val token = "qa" + java.util.UUID.randomUUID().toString().replace("-", "")
        val owned = mutableListOf<Uri>()
        val thread = Telephony.Threads.getOrCreateThreadId(context, setOf(number))
        fun insert() {
            owned += context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, ContentValues().apply {
                put("thread_id", thread); put("address", number); put("body", token); put("date", System.currentTimeMillis()); put("read", 1)
            })!!
        }
        try {
            val org = LocalOrganization(context, db, platform, scope)
            val history = SystemHistory(context, db, platform, directory, scope, org)
            insert(); history.refresh()
            val row = withTimeout(15000) { history.inbox().first { rows -> rows.any { it.participants.any { p -> p.address.raw == number } } }.first { it.participants.any { p -> p.address.raw == number } } }
            assertTrue(row.isUnknownSender); assertFalse(row.blocked)
            people.value = listOf(Participant(Address(number), "QA contact"))
            withTimeout(15000) { history.conversation(row.id).first { it?.isUnknownSender == false } }
            val source = app.chatra.search.ProviderSearchSource(context, db, history, directory, platform)
            val search = app.chatra.search.SystemSearch(db, source, scope, autoIndex = false)
            withTimeout(15000) { history.historyStatus().first { it == HistoryStatus.Ready } }
            search.rebuildNow()
            assertEquals(1, search.search(SearchQuery(token)).first().hits.size)
            org.addToTrash(row.id, row.title)
            search.rebuildNow()
            assertTrue(search.search(SearchQuery(token)).first().hits.isEmpty())
            insert(); history.refresh()
            withTimeout(15000) { history.messages(row.id).first { it.size == 1 } }
            withTimeout(15000) { history.historyStatus().first { it == HistoryStatus.Ready } }
            search.rebuildNow()
            assertEquals(1, search.search(SearchQuery(token)).first().hits.size)
            org.restoreEntry(db.organization().trash().first().single().id)
            search.rebuildNow()
            assertEquals(2, search.search(SearchQuery(token)).first().hits.size)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            owned.forEach { context.contentResolver.delete(it, null, null) }; db.close()
        }
    }
}
