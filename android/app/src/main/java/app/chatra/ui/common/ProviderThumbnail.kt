package app.chatra.ui.common

import android.graphics.ImageDecoder
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
import app.chatra.core.designsystem.theme.ChatraType
import app.chatra.core.model.AttachmentSummary
import app.chatra.ui.LocalContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Dekodowanie dopiero po wejściu bąbelka do LazyColumn; bez kopiowania oryginału. */
@Composable
fun ProviderThumbnail(attachment: AttachmentSummary) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val bitmap by produceState<ImageBitmap?>(null, attachment.contentUri) {
        val uri = attachment.contentUri ?: return@produceState
        if (!attachment.mimeType.startsWith("image/")) return@produceState
        value = withContext(Dispatchers.IO) {
            if (!container.isDemo && !container.smsRole.isHeld()) return@withContext null
            runCatching { ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))) { decoder, info, _ ->
                val scale = minOf(1f, 384f / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }.asImageBitmap() }.getOrNull()
        }
    }
    Column(Modifier.widthIn(max = 270.dp)) {
        bitmap?.let { Image(it, attachment.label, Modifier.fillMaxWidth().height(140.dp), contentScale = ContentScale.Fit) }
        BasicText(attachment.label, style = ChatraType.Preview)
    }
}
