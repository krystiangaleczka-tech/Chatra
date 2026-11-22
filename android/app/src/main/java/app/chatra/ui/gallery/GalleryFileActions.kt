package app.chatra.ui.gallery

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import app.chatra.core.model.GalleryItem
import app.chatra.local.PrivateMediaStore
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal fun suggestedFileName(item: GalleryItem): String {
    val label = item.label.substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("[\\p{Cntrl}:*?\"<>|]"), "_").trim(' ', '.').take(120).ifBlank { "Zalacznik" }
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(item.mime)
    return if (extension != null && !label.endsWith(".$extension", ignoreCase = true)) "$label.$extension" else label
}

/** Receivers only receive a temporary read grant for one Chatra-owned file. */
internal suspend fun fileIntent(context: Context, item: GalleryItem, share: Boolean): Intent = withContext(Dispatchers.IO) {
    val root = File(context.cacheDir, "gallery-share").apply { check(isDirectory || mkdirs()) }
    val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
    root.listFiles().orEmpty().filter { it.lastModified() < cutoff }.forEach { it.deleteRecursively() }
    val folder = File(root, java.util.UUID.randomUUID().toString()).apply { check(mkdir()) }
    try {
        val copy = File(folder, suggestedFileName(item))
        copy.outputStream().use { streamFile(context, item, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", copy)
        val intent = Intent(if (share) Intent.ACTION_SEND else Intent.ACTION_VIEW).apply {
            if (share) {
                type = item.mime
                putExtra(Intent.EXTRA_STREAM, uri)
            } else setDataAndType(uri, item.mime)
            clipData = ClipData.newRawUri("Załącznik Chatry", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (share) Intent.createChooser(intent, "Udostępnij plik") else intent
    } catch (e: Exception) {
        folder.deleteRecursively()
        throw e
    }
}

internal suspend fun saveFile(context: Context, destination: Uri, resolve: suspend () -> GalleryItem) = withContext(Dispatchers.IO) {
    require(destination.scheme == "content")
    val resolver = context.contentResolver
    try {
        val item = resolve()
        resolver.openOutputStream(destination, "w")?.use { streamFile(context, item, it) }
            ?: throw IOException("Brak dostępu do miejsca zapisu.")
    } catch (e: Exception) {
        runCatching { resolver.delete(destination, null, null) }
        throw e
    }
}

private suspend fun streamFile(context: Context, item: GalleryItem, output: OutputStream) {
    val uri = Uri.parse(item.uri)
    require(uri.scheme == "content" && (uri.authority == "mms" || uri.authority == "${context.packageName}.files"))
    context.contentResolver.openInputStream(uri)?.use { input ->
        val buffer = ByteArray(64 * 1024)
        var bytes = 0L
        while (true) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            bytes += count
            require(bytes <= PrivateMediaStore.MAX_BYTES) { "Plik przekracza 250 MB." }
            output.write(buffer, 0, count)
        }
        check(bytes > 0) { "Plik jest pusty." }
    } ?: throw IOException("Plik jest niedostępny.")
}
