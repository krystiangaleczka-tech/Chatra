package app.chatra.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.AtomicFile
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import app.chatra.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Owned bytes survive revoked picker grants. Metadata is never in a FileProvider root. */
class PrivateMediaStore(private val context: Context) : AttachmentStore {
    private val files = File(context.filesDir, "media").apply { check(mkdirs() || isDirectory) }
    private val index = File(context.filesDir, "media-index").apply { check(mkdirs() || isDirectory) }
    private val mutex = Mutex()
    companion object { const val MAX_BYTES = 250L * 1024 * 1024 }
    private fun valid(id: AttachmentId) = require(runCatching { java.util.UUID.fromString(id.value).toString() == id.value }.getOrDefault(false))
    private fun uri(file: File) = FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString()
    private fun hash(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    override suspend fun importUri(uri: String): StoredMedia = withContext(Dispatchers.IO) {
        mutex.withLock {
            val source = Uri.parse(uri)
            require(source.scheme == "content") { "Wybierz plik przez systemowy wybór zdjęć." }
            val temp = File.createTempFile("import-", ".partial", files)
            try {
                context.contentResolver.openInputStream(source)?.use { input ->
                    temp.outputStream().use { out ->
                        val buffer = ByteArray(65536); var bytes = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer); if (n < 0) break
                            bytes += n
                            require(bytes <= MAX_BYTES) { "Plik przekracza 250 MB." }
                            if (temp.usableSpace < 1024 * 1024) throw IOException("Brak miejsca na plik.")
                            out.write(buffer, 0, n)
                        }
                        out.fd.sync()
                    }
                } ?: throw IOException("Utracono dostęp do pliku. Wybierz go ponownie.")
                register(temp)
            } finally { temp.delete() }
        }
    }
    private fun type(file: File): Pair<String, String> {
        val head = ByteArray(32); val n = file.inputStream().use { it.read(head) }
        fun ascii(start: Int, count: Int) = if (n >= start + count) String(head, start, count, Charsets.US_ASCII) else ""
        return when {
            n >= 3 && head[0] == 0xff.toByte() && head[1] == 0xd8.toByte() && head[2] == 0xff.toByte() -> "image/jpeg" to "jpg"
            n >= 8 && head.copyOfRange(0, 8).contentEquals(byteArrayOf(137.toByte(),80,78,71,13,10,26,10)) -> "image/png" to "png"
            ascii(0, 6) in listOf("GIF87a", "GIF89a") -> "image/gif" to "gif"
            ascii(0, 4) == "RIFF" && ascii(8, 4) == "WEBP" -> "image/webp" to "webp"
            ascii(4, 4) == "ftyp" && ascii(8, 4).startsWith("3g") -> "video/3gpp" to "3gp"
            ascii(4, 4) == "ftyp" && ascii(8, 4) in listOf("heic", "heix", "mif1", "hevc") -> "image/heic" to "heic"
            ascii(4, 4) == "ftyp" && ascii(8, 4) in listOf("isom", "iso2", "mp41", "mp42", "M4V ", "avc1", "qt  ") -> "video/mp4" to "mp4"
            else -> throw IllegalArgumentException("Nieobsługiwany lub uszkodzony format. Wybierz JPEG, PNG, GIF, WebP, HEIC, MP4 lub 3GP.")
        }
    }
    private fun register(temp: File, originalId: AttachmentId? = null, sanitized: Boolean = false): StoredMedia {
        require(temp.length() in 1..MAX_BYTES) { "Pusty albo zbyt duży plik." }
        val (mime, extension) = type(temp)
        if (mime.startsWith("image/") && mime != "image/heic") {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Nie można odczytać zdjęcia." }
        }
        val id = AttachmentId.new(); val target = File(files, "${id.value}.$extension")
        check(temp.renameTo(target)) { "Nie udało się zapisać pliku." }
        try {
            val item = StoredMedia(id, uri(target), mime, target.length(), hash(target), System.currentTimeMillis(), originalId, sanitized)
            AtomicFile(File(index, "${id.value}.json")).replace(JSONObject().put("name", target.name).put("mime", mime).put("bytes", item.bytes).put("sha256", item.sha256).put("createdAt", item.createdAt).put("original", originalId?.value ?: JSONObject.NULL).put("sanitized", sanitized).toString().toByteArray())
            return item
        } catch (e: Exception) { target.delete(); throw e }
    }
    override suspend fun get(id: AttachmentId): StoredMedia? = withContext(Dispatchers.IO) {
        mutex.withLock { read(id) }
    }
    private fun read(id: AttachmentId): StoredMedia? {
        valid(id)
        val meta = AtomicFile(File(index, "${id.value}.json"))
        if (!meta.baseFile.exists()) return null
        val json = JSONObject(meta.readFully().toString(Charsets.UTF_8))
        val name = json.getString("name")
        require(name.matches(Regex("${id.value}\\.[a-z0-9]+")))
        val file = File(files, name)
        if (!file.isFile || file.length() != json.getLong("bytes") || hash(file) != json.getString("sha256")) return null
        return StoredMedia(id, uri(file), json.getString("mime"), file.length(), json.getString("sha256"), json.getLong("createdAt"), json.optString("original").takeIf { it.isNotEmpty() && it != "null" }?.let(::AttachmentId), json.optBoolean("sanitized", false))
    }
    override suspend fun derive(id: AttachmentId, variant: MediaVariant, maxBytes: Long?): StoredMedia = withContext(Dispatchers.IO) {
        mutex.withLock {
            val original = read(id) ?: error("Plik jest niedostępny. Wybierz go ponownie.")
            if (variant == MediaVariant.OriginalHd) return@withLock (original.originalId?.let { read(it) ?: error("Oryginał jest niedostępny.") } ?: original)
            require(original.mime in listOf("image/jpeg", "image/png", "image/webp")) { "Kopia bez lokalizacji i kompresja obsługują JPEG, PNG i WebP. Dla filmu, GIF lub HEIC wybierz oryginał." }
            val source = context.contentResolver.openFileDescriptor(Uri.parse(original.uri), "r") ?: error("Brak pliku.")
            val exif = source.use { ExifInterface(it.fileDescriptor) }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(Uri.parse(original.uri))!!.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 4096) sample *= 2
            var bitmap = context.contentResolver.openInputStream(Uri.parse(original.uri))!!.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: error("Uszkodzone zdjęcie.")
            val matrix = Matrix().apply { if (exif.isFlipped) postScale(-1f, 1f); postRotate(exif.rotationDegrees.toFloat()) }
            if (!matrix.isIdentity) {
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated !== bitmap) bitmap.recycle()
                bitmap = rotated
            }
            val temp = File.createTempFile("derived-", ".partial", files)
            try {
                val limit = maxBytes ?: MAX_BYTES
                require(limit in 1024..MAX_BYTES)
                var quality = 92
                while (true) {
                    coroutineContext.ensureActive()
                    temp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it)) }
                    if (temp.length() <= limit) break
                    if (quality > 45) quality -= 15
                    else {
                        require(minOf(bitmap.width, bitmap.height) > 64) { "Zdjęcie nie mieści się w limicie operatora." }
                        val scaled = Bitmap.createScaledBitmap(bitmap, maxOf(1, bitmap.width * 3 / 4), maxOf(1, bitmap.height * 3 / 4), true)
                        bitmap.recycle(); bitmap = scaled
                    }
                }
                // Decode/re-encode emits pixels only: no EXIF, XMP, IPTC, comments or source GPS survive.
                register(temp, original.originalId ?: original.id, sanitized = true)
            } finally { bitmap.recycle(); temp.delete() }
        }
    }
    override suspend fun collectUnused(protectedIds: Set<AttachmentId>, olderThan: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Caller must include all saved drafts and immutable operation snapshots, including uncertain sends.
            val protected = protectedIds.toMutableSet()
            for (id in protectedIds) {
                val json = runCatching { JSONObject(AtomicFile(File(index, "${id.value}.json")).readFully().toString(Charsets.UTF_8)) }.getOrNull()
                json?.optString("original")?.takeIf { it.isNotEmpty() && it != "null" }?.let { protected += AttachmentId(it) }
            }
            val cutoff = minOf(olderThan, System.currentTimeMillis() - 86_400_000)
            index.listFiles().orEmpty().filter { it.extension == "json" }.forEach { meta ->
                val id = AttachmentId(meta.nameWithoutExtension)
                if (id !in protected && meta.lastModified() < cutoff) {
                    val json = JSONObject(meta.readText()); val name = json.getString("name")
                    if (name.matches(Regex("${id.value}\\.[a-z0-9]+"))) File(files, name).delete()
                    meta.delete()
                }
            }
            File(context.filesDir, "camera").listFiles().orEmpty().filter { it.lastModified() < cutoff }.forEach { it.delete() }
            files.listFiles().orEmpty().filter { it.extension == "partial" && it.lastModified() < cutoff }.forEach { it.delete() }
        }
    }
    override fun cameraUri(): String {
        val dir = File(context.filesDir, "camera").apply { mkdirs() }
        return uri(File.createTempFile("capture-", ".jpg", dir))
    }
}
