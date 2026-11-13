package app.chatra.online

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.chatra.core.database.*
import app.chatra.core.model.*
import app.chatra.local.PrivateMediaStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class HdMailboxTest {
    @Test fun duplicateSyncPartialDownloadRestartAndLostReceiptKeepOneUnreadMessage() = runBlocking {
        scenario { db, store, remote, mailbox, history, context ->
            mailbox.sync(); mailbox.sync()
            val id = remote.id
            assertEquals(1, db.hd().all().size)
            assertEquals(1, history.inbox().first().single().unreadCount)
            assertNull(remote.downloaded)
            mailbox.enqueue(id, false)
            remote.breakChunk = true
            assertFalse(mailbox.run(id))
            assertEquals(262_144L, db.hd().get(id)!!.bytes)
            assertNull(remote.downloaded)
            val reopened = HdMailbox(context, db, store, history, remote) { remote.owner }
            remote.loseReceipt = true
            assertFalse(reopened.run(id))
            assertEquals("RECEIPT", db.hd().get(id)!!.state)
            val local = HdMailbox.ids(db.hd().get(id)!!.localFiles)
            assertEquals(2, local.size)
            local.forEachIndexed { i, attachment -> assertEquals(remote.content[i].sha256(), store.get(attachment)!!.sha256) }
            // Delivery commits remotely, then the response is lost. No re-download is necessary.
            val calls = remote.offsets.size
            assertTrue(reopened.run(id))
            assertEquals(calls, remote.offsets.size)
            assertEquals("READY", db.hd().get(id)!!.state)
            assertFalse(db.hd().get(id)!!.read)
            assertEquals(1, history.messages(ConversationId("thread")).first().size)
            history.markShown(ConversationId("thread"), setOf(MessageRef.Hd(id)))
            assertTrue(db.hd().get(id)!!.read)
            assertEquals(0, history.inbox().first().single().unreadCount)
            assertTrue(remote.unmetered)
        }
    }

    @Test fun corruptedBytesNeverProduceAReceiptAndExpiredAccessStopsRetry() = runBlocking {
        scenario { db, _, remote, mailbox, _, _ ->
            mailbox.sync(); mailbox.enqueue(remote.id, true)
            remote.corrupt = true
            assertTrue(mailbox.run(remote.id))
            assertEquals("FAILED", db.hd().get(remote.id)!!.state)
            assertNull(remote.downloaded)
            assertEquals("[]", db.hd().get(remote.id)!!.localFiles)
            mailbox.enqueue(remote.id, true)
            remote.expired = true
            assertTrue(mailbox.run(remote.id))
            assertEquals("EXPIRED", db.hd().get(remote.id)!!.state)
        }
    }

    @Test fun cursorResetTrashRestorePurgeAndGalleryKeepSourceRelations() = runBlocking {
        scenario { db, store, remote, mailbox, history, _ ->
            mailbox.sync(); mailbox.enqueue(remote.id, true); assertTrue(mailbox.run(remote.id))
            val gallery = HdGallery(object : MediaGallery {
                override suspend fun page(conversation: ConversationId, kind: MediaKind, before: Long?, limit: Int) = GalleryPage(emptyList(), 0, null)
                override suspend fun readable(id: AttachmentId): GalleryItem? = null
            }, db, store) { true }
            val thread = ConversationId("thread")
            val first = gallery.page(thread, MediaKind.All, null, 1)
            assertEquals(2, first.total)
            assertEquals(MessageRef.Hd(remote.id), first.items.single().source)
            assertNotEquals(first.items.single().id, gallery.page(thread, MediaKind.All, first.nextBefore, 1).items.single().id)
            db.organization().insert(TrashEntry("trash", "thread", "HD", 1, Long.MAX_VALUE))
            db.hd().hide(listOf(TrashHdRef(remote.id, "trash")))
            assertTrue(history.messages(thread).first().isEmpty())
            assertNull(gallery.readable(first.items.single().id))
            db.organization().remove("trash")
            assertEquals(1, history.messages(thread).first().size)
            db.hd().dismiss(HdDismissed(remote.id)); db.hd().delete(remote.id)
            remote.resetCursor = true
            mailbox.sync()
            assertTrue(db.hd().all().isEmpty())
            assertEquals(0, gallery.page(thread).total)
        }
    }

    @Test fun detachingDisablesRemoteDownloadsAndKeepsVerifiedLocalHistory() = runBlocking {
        scenario { db, store, remote, mailbox, history, _ ->
            mailbox.sync(); mailbox.enqueue(remote.id, false); assertTrue(mailbox.run(remote.id))
            val complete = db.hd().get(remote.id)!!
            val waiting = newOnlineId()
            db.hd().put(complete.copy(id = waiting, localFiles = "[]", state = "QUEUED", downloadedAt = null))
            mailbox.detached(remote.owner)
            assertEquals("LOCAL", db.hd().get(remote.id)!!.state)
            assertEquals("EXPIRED", db.hd().get(waiting)!!.state)
            val files = HdMailbox.ids(complete.localFiles)
            assertEquals(files.toSet(), mailbox.protectedIds())
            files.forEach { assertNotNull(store.get(it)) }
            assertTrue(mailbox.run(waiting))
            assertTrue(db.hd().pending().isEmpty())
            assertEquals(2, history.messages(ConversationId("thread")).first().size)
        }
    }

    private suspend fun scenario(block: suspend (ChatraDatabase, AttachmentStore, Remote, HdMailbox, ConversationRepository, Context) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val root = File(app.cacheDir, "mailbox-${newOnlineId()}").apply { mkdirs() }
        val context = object : ContextWrapper(app) { override fun getNoBackupFilesDir() = root }
        val db = Room.inMemoryDatabaseBuilder(app, ChatraDatabase::class.java).build()
        val imported = mutableListOf<AttachmentId>()
        val actual = PrivateMediaStore(app)
        val store = object : AttachmentStore by actual {
            override suspend fun importUri(uri: String): StoredMedia = actual.importUri(uri).also { imported += it.id }
        }
        val base = object : ConversationRepository {
            val summary = ConversationSummary(ConversationId("thread"), listOf(Participant(Address("+12025550101"))), "Test", Instant.EPOCH,
                ConversationSummary.Preview.Text(""), 0, false)
            override fun inbox() = flowOf(emptyList<ConversationSummary>())
            override fun conversation(id: ConversationId) = flowOf(summary)
            override fun messages(id: ConversationId) = flowOf(emptyList<Message>())
            override suspend fun markRead(id: ConversationId) = Unit
            override suspend fun setPinned(id: ConversationId, pinned: Boolean) = Unit
            override suspend fun moveToTrash(id: ConversationId) = Unit
            override suspend fun conversationFor(recipients: List<Address>) = ConversationId("thread")
        }
        val remote = Remote(listOf(png(400), png(120)))
        val history = HdHistory(base, db, store) { true }
        try { block(db, store, remote, HdMailbox(context, db, store, base, remote) { remote.owner }, history, context) }
        finally {
            db.close(); root.deleteRecursively()
            imported.forEach { id ->
                File(app.filesDir, "media-index/${id.value}.json").delete()
                File(app.filesDir, "media").listFiles()?.filter { it.name.startsWith(id.value + ".") }?.forEach { it.delete() }
            }
        }
    }

    private fun png(size: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val random = java.util.Random(1937 + size.toLong())
        bitmap.setPixels(IntArray(size * size) { 0xff000000.toInt() or random.nextInt(0xffffff) }, 0, size, 0, 0, size, size)
        return java.io.ByteArrayOutputStream().use { bytes -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes); bitmap.recycle(); bytes.toByteArray() }
    }

    private class Remote(val content: List<ByteArray>) : OnlineTransport {
        val owner = newOnlineId(); val id = newOnlineId()
        val offsets = mutableListOf<Long>()
        var breakChunk = false; var loseReceipt = false; var corrupt = false; var expired = false; var resetCursor = false
        var unmetered = false; var downloaded: Long? = null
        override suspend fun forget() = Unit
        override suspend fun request(method: String, path: String, body: ByteArray, credential: String?, bearer: Boolean, headers: Map<String, String>, unmetered: Boolean): JSONObject = error("Unused")
        override suspend fun signed(installation: String, method: String, path: String, body: ByteArray, headers: Map<String, String>, unmetered: Boolean): JSONObject {
            assertEquals(owner, installation)
            if (path.startsWith("/v1/inbox")) {
                if (resetCursor && path.contains("cursor=")) { resetCursor = false; throw OnlineError(409, "cursor_expired", 0) }
                return JSONObject().put("items", JSONArray().put(JSONObject().put("envelope", id).put("kind", "envelope")))
                    .put("cursor", "1.test").put("hasMore", false)
            }
            if (expired) throw OnlineError(404, "not_found", 0)
            if (path.endsWith("/receipt")) {
                assertEquals(content.map { it.sha256() }, JSONObject(body.toString(Charsets.UTF_8)).getJSONArray("files").let { a -> List(a.length()) { a.getString(it) } })
                downloaded = downloaded ?: unixTime()
                if (loseReceipt) { loseReceipt = false; throw IOException("lost_response") }
                return JSONObject().put("downloadedAt", downloaded)
            }
            return JSONObject().put("id", id).put("sender", "+12025550101").put("recipient", "+12025550102").put("incoming", true)
                .put("createdAt", unixTime()).put("expiresAt", unixTime() + 86400).put("downloadedAt", downloaded ?: JSONObject.NULL)
                .put("files", JSONArray(content.mapIndexed { position, bytes -> JSONObject().put("position", position)
                    .put("size", bytes.size).put("mime", "image/png").put("sha256", bytes.sha256()).put("variant", "original") }))
        }
        override suspend fun download(installation: String, path: String, unmetered: Boolean): ByteArray {
            this.unmetered = unmetered
            val offset = path.substringAfter("offset=").toLong()
            if (breakChunk && offset > 0) { breakChunk = false; throw IOException("offline") }
            val position = path.substringAfter("/files/").substringBefore('/').toInt()
            offsets += offset
            return content[position].copyOfRange(offset.toInt(), minOf(offset.toInt() + 262_144, content[position].size)).also {
                if (corrupt && offset == 0L) it[it.lastIndex] = (it.last() + 1).toByte()
            }
        }
    }
}
