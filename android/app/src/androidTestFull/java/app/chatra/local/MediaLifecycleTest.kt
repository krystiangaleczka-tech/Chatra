package app.chatra.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import app.chatra.core.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MediaLifecycleTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun photo(store: PrivateMediaStore, gps: Boolean = false): Uri {
        val uri = Uri.parse(store.cameraUri())
        val bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        context.contentResolver.openOutputStream(uri)!!.use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        bitmap.recycle()
        if (gps) context.contentResolver.openFileDescriptor(uri, "rw")!!.use { fd ->
            ExifInterface(fd.fileDescriptor).apply {
                setLatLong(52.2297, 21.0122)
                setAttribute(ExifInterface.TAG_USER_COMMENT, "secret-location-test")
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
        }
        return uri
    }
    @Test fun ownedCopySurvivesSourceDeletionAndStoreRestartAndDetectsTamper() = runBlocking {
        val store = PrivateMediaStore(context); val source = photo(store)
        val expected = context.contentResolver.openInputStream(source)!!.use { it.readBytes() }
        val item = store.importUri(source.toString())
        context.contentResolver.delete(source, null, null)
        val restored = PrivateMediaStore(context).get(item.id)!!
        assertEquals(item.sha256, restored.sha256); assertEquals(expected.size.toLong(), restored.bytes)
        assertArrayEquals(expected, context.contentResolver.openInputStream(Uri.parse(restored.uri))!!.use { it.readBytes() })
        context.contentResolver.openOutputStream(Uri.parse(restored.uri), "w")!!.use { it.write(byteArrayOf(1, 2, 3)) }
        assertNull(PrivateMediaStore(context).get(item.id))
    }
    @Test fun sanitizedCopyDropsAllSourceMetadataKeepsOrientationAndNeverChangesOriginal() = runBlocking {
        val store = PrivateMediaStore(context); val source = photo(store, true)
        val before = context.contentResolver.openInputStream(source)!!.use { it.readBytes() }
        // The same import path as a new draft with the NoLocationCopy sending preference.
        val copy = store.importUri(source.toString(), MediaVariant.NoLocationCopy)
        assertTrue(copy.sanitized)
        val original = store.get(copy.originalId!!)!!
        assertNotEquals(original.id, copy.id); assertNotEquals(original.sha256, copy.sha256)
        val bytes = context.contentResolver.openInputStream(Uri.parse(copy.uri))!!.use { it.readBytes() }
        val exif = ExifInterface(bytes.inputStream())
        assertNull(exif.latLong); assertNull(exif.getAttribute(ExifInterface.TAG_USER_COMMENT))
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("secret-location-test"))
        // Independently inspect JPEG segments: pixel-only re-encoding must not emit APP1 (EXIF or XMP).
        var at = 2
        while (at + 4 < bytes.size && bytes[at] == 0xff.toByte() && bytes[at + 1] != 0xda.toByte()) {
            assertNotEquals(0xe1, bytes[at + 1].toInt() and 255)
            val size = ((bytes[at + 2].toInt() and 255) shl 8) or (bytes[at + 3].toInt() and 255)
            at += 2 + size
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(32, bitmap.width); assertEquals(64, bitmap.height); bitmap.recycle()
        assertArrayEquals(before, context.contentResolver.openInputStream(Uri.parse(original.uri))!!.use { it.readBytes() })
        assertEquals(original.sha256, store.derive(original.id, MediaVariant.OriginalHd).sha256)
    }
    @Test fun badUriEmptyOrUnsupportedFileNeverPublishesAMediaItem() = runBlocking {
        val store = PrivateMediaStore(context)
        for (source in listOf("file:///etc/passwd", store.cameraUri())) {
            try { store.importUri(source); fail("invalid source accepted") } catch (_: IllegalArgumentException) { }
        }
        val source = Uri.parse(store.cameraUri())
        context.contentResolver.openOutputStream(source)!!.use { it.write("not an image".toByteArray()) }
        try { store.importUri(source.toString()); fail("invalid signature accepted") } catch (_: IllegalArgumentException) { }
        assertFalse(File(context.filesDir, "media").listFiles().orEmpty().any { it.extension == "partial" })
    }
    @Test fun templatesAndTimedMutesPersistAndLastTemplateStaysDeleted() = runBlocking {
        val repo = LocalTemplates(context)
        val previous = repo.items.value
        val id = TemplateId("qa-${java.util.UUID.randomUUID()}")
        try {
            repo.save(Template(id, "QA", "Pierwsza treść", 0))
            repo.save(Template(id, "QA edycja", "Druga treść", 0))
            assertEquals("Druga treść", LocalTemplates(context).items.value.single { it.id == id }.body)
            repo.delete(id)
            assertFalse(LocalTemplates(context).items.value.any { it.id == id })
            val prefs = LocalNotificationPrefs(context); val old = prefs.value.value
            try {
                prefs.update { NotificationPrefs(true, mapOf("qa-thread" to 1000)) }
                val restored = LocalNotificationPrefs(context).value.value
                assertTrue(restored.previews); assertTrue(restored.muted("qa-thread", 999)); assertFalse(restored.muted("qa-thread", 1000))
            } finally { prefs.update { old } }
        } finally { repo.delete(id); previous.forEach { repo.save(it) } }
    }
}
