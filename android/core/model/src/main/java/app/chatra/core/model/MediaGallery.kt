package app.chatra.core.model

enum class MediaKind { All, Images, Videos }
data class GalleryItem(val id: AttachmentId, val source: MessageRef, val mime: String, val uri: String, val label: String, val transport: Transport)
data class GalleryPage(val items: List<GalleryItem>, val total: Int, val nextBefore: Long?)

/** CH031 data port for the future CH066 UI. Only indexed attachments can be opened/shared. */
interface MediaGallery {
    suspend fun page(conversation: ConversationId, kind: MediaKind = MediaKind.All, before: Long? = null, limit: Int = 40): GalleryPage
    suspend fun readable(id: AttachmentId): GalleryItem?
}

data class PendingMms(val id: String, val state: String, val description: String)
interface MmsDownloads {
    val pending: kotlinx.coroutines.flow.StateFlow<List<PendingMms>>
    suspend fun retryDownload(id: String)
}
