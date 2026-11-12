package app.chatra.telephony

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import app.chatra.core.database.*
import app.chatra.core.model.*
import app.chatra.platform.SystemPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProviderMediaGallery(private val context: Context, private val db: ChatraDatabase, private val role: SystemPlatform) : MediaGallery {
    private fun item(r: MediaRef) = GalleryItem(AttachmentId("mms-part:${r.partId}"), MessageRef.Provider(ProviderRef(ProviderTransport.MMS, r.messageId)), r.mimeType, r.contentUri, r.label, Transport.MMS)
    override suspend fun page(conversation: ConversationId, kind: MediaKind, before: Long?, limit: Int): GalleryPage = withContext(Dispatchers.IO) {
        check(context.smsRead(role)); require(limit in 1..100)
        val filter = when (kind) { MediaKind.All -> ""; MediaKind.Images -> "image/%"; MediaKind.Videos -> "video/%" }
        db.withTransaction {
            val rows = db.dao().galleryPage(conversation.value, filter, before, limit + 1)
            GalleryPage(rows.take(limit).map(::item), db.dao().galleryCount(conversation.value, filter), if (rows.size > limit) rows[limit - 1].partId else null)
        }
    }
    override suspend fun readable(id: AttachmentId): GalleryItem? = withContext(Dispatchers.IO) {
        if (!context.smsRead(role)) return@withContext null
        val part = id.value.removePrefix("mms-part:").toLongOrNull()?.let { db.dao().mediaPart(it) } ?: return@withContext null
        // URI must be exactly the provider's indexed part, not a caller-supplied authority/path.
        if (part.contentUri != "content://mms/part/${part.partId}") return@withContext null
        try { context.contentResolver.openFileDescriptor(Uri.parse(part.contentUri), "r")?.use { item(part) } }
        catch (_: Exception) { null }
    }
}
