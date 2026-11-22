package app.chatra.core.model

enum class MediaKind(val label: String) {
    All("Wszystkie pliki"), Images("Zdjęcia"), Videos("Filmy"), Links("Linki"), Documents("Dokumenty"), Other("Pozostałe");

    fun includes(mime: String): Boolean = this == All || this == attachmentKind(mime)
}

fun attachmentKind(mime: String): MediaKind = when {
    mime.startsWith("image/", ignoreCase = true) -> MediaKind.Images
    mime.startsWith("video/", ignoreCase = true) -> MediaKind.Videos
    mime.startsWith("text/", ignoreCase = true) ||
        (mime.startsWith("application/", ignoreCase = true) && !mime.equals("application/octet-stream", ignoreCase = true)) -> MediaKind.Documents
    else -> MediaKind.Other
}

enum class GalleryAvailability {
    Ready, AwaitingDownload, Downloading, Expired, Missing, Preview,
}

data class GalleryItem(
    val id: AttachmentId,
    val source: MessageRef,
    val mime: String,
    val uri: String,
    val label: String,
    val transport: Transport,
    val availability: GalleryAvailability = GalleryAvailability.Ready,
    val isLink: Boolean = false,
)
data class GalleryPage(val items: List<GalleryItem>, val total: Int, val nextBefore: Long?)

/** Cursor values are opaque to the caller. Only indexed files can be opened or shared. */
interface MediaGallery {
    suspend fun page(conversation: ConversationId, kind: MediaKind = MediaKind.All, before: Long? = null, limit: Int = 40): GalleryPage
    suspend fun readable(id: AttachmentId): GalleryItem?
    suspend fun readable(item: GalleryItem): GalleryItem? = readable(item.id)?.takeIf { it.source == item.source }
}

private val webLink = Regex("""https?://[^\s<>\p{Cntrl}]{1,2048}""", RegexOption.IGNORE_CASE)

/** Plain links are never prefetched. Keep the exact source message for each occurrence. */
fun galleryLinks(body: String, source: MessageRef, transport: Transport): List<GalleryItem> =
    webLink.findAll(body).mapNotNull { match ->
        val value = match.value.trimEnd('.', ',', ';', '!', '?', ')', ']', '}', '"', '\'')
        val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return@mapNotNull null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null) return@mapNotNull null
        GalleryItem(AttachmentId("link:$source:${match.range.first}"), source, "text/uri-list",
            value, value, transport, isLink = true)
    }.distinctBy { it.uri }.toList()

data class PendingMms(val id: String, val state: String, val description: String)
interface MmsDownloads {
    val pending: kotlinx.coroutines.flow.StateFlow<List<PendingMms>>
    suspend fun retryDownload(id: String)
}
