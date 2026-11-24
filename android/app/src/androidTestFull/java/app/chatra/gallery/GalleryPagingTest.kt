package app.chatra.gallery

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.chatra.core.database.*
import app.chatra.core.model.*
import app.chatra.local.PrivateMediaStore
import app.chatra.online.HdGallery
import app.chatra.platform.SystemPlatform
import app.chatra.telephony.ProviderMediaGallery
import app.chatra.telephony.smsRead
import app.chatra.ui.gallery.fileIntent
import app.chatra.ui.gallery.saveFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class GalleryPagingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun pagesKeepAll67And1000ItemsAcrossHdAndMmsBoundary() = runBlocking<Unit> {
        val platform = SystemPlatform(context)
        assumeTrue(context.smsRead(platform))
        val db = Room.inMemoryDatabaseBuilder(context, ChatraDatabase::class.java).build()
        try {
            val gallery = HdGallery(ProviderMediaGallery(context, db, platform), db, PrivateMediaStore(context)) { true }
            for ((index, count) in listOf(67, 1000).withIndex()) {
                val id = ConversationId("gallery-$count")
                val messageId = 800_000L + index
                db.dao().insertConversation(ConversationMeta(id.value, messageId, "", "[]"))
                db.dao().upsertReferences(listOf(ProviderMessageRef("MMS", messageId, id.value, 1, true, false, "qa")))
                val parts = List(count - 2) { i ->
                    val partId = (index + 1) * 10_000L + i
                    val mime = listOf("image/png", "video/mp4", "application/pdf", "audio/ogg")[i % 4]
                    MediaRef(partId, messageId = messageId, conversationId = id.value, mimeType = mime,
                        label = "Plik QA $i", contentUri = "content://mms/part/$partId")
                }
                db.dao().upsertMedia(parts)
                db.hd().put(HdRecord("hd-$count", "qa", id.value, "", "", true, 2, Long.MAX_VALUE,
                    """[{"mime":"image/jpeg"},{"mime":"image/png"}]"""))
                val durationsMicros = mutableListOf<Long>()
                var cursor: Long? = null
                val seen = mutableListOf<GalleryItem>()
                do {
                    val started = android.os.SystemClock.elapsedRealtimeNanos()
                    val page = gallery.page(id, MediaKind.All, cursor, if (seen.isEmpty()) 2 else 40)
                    durationsMicros += (android.os.SystemClock.elapsedRealtimeNanos() - started) / 1000
                    assertEquals(count, page.total)
                    assertTrue(page.items.isNotEmpty())
                    seen += page.items
                    cursor = page.nextBefore
                    assertTrue("Paging must terminate", seen.size <= count)
                } while (cursor != null)
                val sorted = durationsMicros.sorted()
                val p95 = sorted[kotlin.math.ceil(sorted.size * .95).toInt() - 1]
                android.util.Log.i("ChatraGalleryQA", "items=$count pages=${sorted.size} first_us=${durationsMicros.first()} p95_us=$p95")
                assertEquals(count, seen.map { it.id }.distinct().size)
                assertEquals(List(2) { GalleryAvailability.AwaitingDownload }, seen.take(2).map { it.availability })
                assertEquals(parts.asReversed().map { "mms-part:${it.partId}" }, seen.drop(2).map { it.id.value })
                assertEquals(MessageRef.Provider(ProviderRef(ProviderTransport.MMS, messageId)), seen.last().source)
                assertNull(gallery.readable(seen.first().id))
                assertNull(gallery.readable(seen.last().id)) // Indexed attachment whose bytes do not exist.
                assertEquals(parts.count { it.mimeType == "application/pdf" }, gallery.page(id, MediaKind.Documents).total)
                assertEquals(parts.count { it.mimeType == "audio/ogg" }, gallery.page(id, MediaKind.Other).total)
                assertEquals(parts.count { it.mimeType == "image/png" } + 2, gallery.page(id, MediaKind.Images).total)
            }
        } finally { db.close() }
    }

    @Test fun linkTabUsesOnlyVisibleIndexedMessagesFromThisThread() = runBlocking<Unit> {
        val platform = SystemPlatform(context)
        assumeTrue(context.smsRead(platform))
        val db = Room.inMemoryDatabaseBuilder(context, ChatraDatabase::class.java).build()
        try {
            for ((i, id) in listOf("one", "two").withIndex()) {
                val ref = (i + 1).toLong()
                db.dao().insertConversation(ConversationMeta(id, ref, "", "[]"))
                db.dao().upsertReferences(listOf(ProviderMessageRef("SMS", ref, id, 20, true, false, "qa")))
                db.search().documents(listOf(SearchDocument(ref * 2, "SMS", ref, id,
                    "Zobacz (https://example.org/$id). https://example.org/$id https://spoof.invalid@evil.invalid/",
                    id, "", 20, null, "[]", false, null, null, "qa", "qa", true)))
            }
            val gallery = ProviderMediaGallery(context, db, platform)
            val item = gallery.page(ConversationId("one"), MediaKind.Links).items.single()
            assertEquals("https://example.org/one", item.uri)
            assertEquals(MessageRef.Provider(ProviderRef(ProviderTransport.SMS, 1)), item.source)
            db.organization().insert(TrashEntry("trash-one", "one", "one", 1, Long.MAX_VALUE))
            db.organization().hide(listOf(TrashRef("SMS", 1, "trash-one", 20)))
            assertEquals(0, gallery.page(ConversationId("one"), MediaKind.Links).total)
            assertEquals(1, gallery.page(ConversationId("two"), MediaKind.Links).total)
        } finally { db.close() }
    }

    @Test fun sharesheetGrantsReadOnlyAccessToOneCopyAndSavePreservesBytes() = runBlocking<Unit> {
        val store = PrivateMediaStore(context)
        val resolver = context.contentResolver
        val original = Uri.parse(store.cameraUri())
        val destination = Uri.parse(store.cameraUri())
        val bytes = "Plik QA".toByteArray()
        try {
            resolver.openOutputStream(original)!!.use { it.write(bytes) }
            val item = GalleryItem(AttachmentId("qa"), MessageRef.Hd("source"), "application/pdf",
                original.toString(), "Dokument", Transport.HD)
            val chooser = fileIntent(context, item, share = true)
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            val send = requireNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java))
            val copy = requireNotNull(IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java))
            try {
                assertEquals(Intent.ACTION_SEND, send.action)
                assertEquals("application/pdf", send.type)
                assertEquals("${context.packageName}.files", copy.authority)
                assertNotEquals(original, copy)
                assertEquals(copy, send.clipData?.getItemAt(0)?.uri)
                assertEquals(1, send.clipData?.itemCount)
                val grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, send.flags and grantFlags)
                assertArrayEquals(bytes, resolver.openInputStream(copy)!!.use { it.readBytes() })
                saveFile(context, destination) { item }
                assertArrayEquals(bytes, resolver.openInputStream(destination)!!.use { it.readBytes() })
            } finally {
                resolver.delete(copy, null, null)
            }
        } finally {
            resolver.delete(original, null, null)
            resolver.delete(destination, null, null)
        }
    }
}
