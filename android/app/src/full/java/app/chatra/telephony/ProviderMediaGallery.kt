package app.chatra.telephony

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import app.chatra.core.database.*
import app.chatra.core.model.*
import app.chatra.platform.SystemPlatform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProviderMediaGallery(private val context: Context, private val db: ChatraDatabase, private val role: SystemPlatform) : MediaGallery {
    private fun item(r: MediaRef) = GalleryItem(AttachmentId("mms-part:${r.partId}"), MessageRef.Provider(ProviderRef(ProviderTransport.MMS, r.messageId)), r.mimeType, r.contentUri, r.label, Transport.MMS)
    override suspend fun page(conversation: ConversationId, kind: MediaKind, before: Long?, limit: Int): GalleryPage = withContext(Dispatchers.IO) {
        check(context.smsRead(role)); require(limit in 1..100)
        if (kind == MediaKind.Links) {
            val all = db.search().galleryLinkSources(conversation.value).flatMap { row ->
                val provider = ProviderTransport.valueOf(row.kind)
                val transport = if (provider == ProviderTransport.MMS) Transport.MMS else Transport.SMS
                galleryLinks(row.body, MessageRef.Provider(ProviderRef(provider, row.providerId)), transport)
            }
            val offset = (before ?: 0).coerceIn(0, all.size.toLong()).toInt()
            val next = minOf(offset + limit, all.size)
            return@withContext GalleryPage(all.subList(offset, next).toList(), all.size, next.toLong().takeIf { next < all.size })
        }
        val filter = if (kind == MediaKind.All) "" else kind.name
        db.withTransaction {
            val rows = db.dao().galleryPage(conversation.value, filter, before, limit + 1)
            GalleryPage(rows.take(limit).map(::item), db.dao().galleryCount(conversation.value, filter), if (rows.size > limit) rows[limit - 1].partId else null)
        }
    }
    override suspend fun readable(id: AttachmentId): GalleryItem? = withContext(Dispatchers.IO) {
        if (!context.smsRead(role)) return@withContext null
        val part = id.value.removePrefix("mms-part:").toLongOrNull()?.let { db.dao().mediaPart(it) } ?: return@withContext null
        if (db.organization().hidden().first().any { it.kind == "MMS" && it.providerId == part.messageId }) return@withContext null
        // URI must be exactly the provider's indexed part, not a caller-supplied authority/path.
        if (part.contentUri != "content://mms/part/${part.partId}") return@withContext null
        try { context.contentResolver.openFileDescriptor(Uri.parse(part.contentUri), "r")?.use { item(part) } }
        catch (_: Exception) { null }
    }
}
