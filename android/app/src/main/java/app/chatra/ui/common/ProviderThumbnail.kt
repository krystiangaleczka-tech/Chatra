package app.chatra.ui.common

import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import app.chatra.core.designsystem.theme.ChatraType
import app.chatra.core.model.AttachmentSummary
import app.chatra.ui.LocalContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Dekodowanie dopiero po wejściu bąbelka do LazyColumn; bez kopiowania oryginału. */
@Composable
fun ProviderThumbnail(attachment: AttachmentSummary, height: Dp = 140.dp, showLabel: Boolean = true, showVideo: Boolean = false) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val bitmap by produceState<ImageBitmap?>(null, attachment.contentUri, attachment.mimeType, showVideo) {
        value = null
        val uri = attachment.contentUri ?: return@produceState
        val image = attachment.mimeType.startsWith("image/")
        if (!image && !(showVideo && attachment.mimeType.startsWith("video/"))) return@produceState
        value = withContext(Dispatchers.IO) {
            if (!container.isDemo && !container.smsRole.isHeld()) return@withContext null
            if (!image) return@withContext runCatching {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(context, Uri.parse(uri))
                    retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 384, 216)?.asImageBitmap()
                }
            }.getOrNull()
            boundedImage(context, Uri.parse(uri), 384)
        }
    }
    Column(Modifier.widthIn(max = 270.dp)) {
        bitmap?.let { Image(it, attachment.label.takeIf { showLabel }, Modifier.fillMaxWidth().height(height), contentScale = ContentScale.Fit) }
        if (showLabel) BasicText(attachment.label, style = ChatraType.Preview)
    }
}


internal fun boundedImage(context: android.content.Context, uri: Uri, maxSide: Int): ImageBitmap? =
    runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val scale = minOf(1f, maxSide.toFloat() / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }.asImageBitmap()
    }.getOrNull()
