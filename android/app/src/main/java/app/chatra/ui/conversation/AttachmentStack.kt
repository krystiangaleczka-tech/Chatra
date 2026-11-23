package app.chatra.ui.conversation

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.chatra.core.designsystem.theme.ChatraColors
import app.chatra.core.designsystem.theme.ChatraType
import app.chatra.core.model.*
import app.chatra.di.AppContainer
import app.chatra.ui.LocalContainer
import app.chatra.ui.common.ProviderThumbnail
import app.chatra.ui.common.TextAction
import app.chatra.ui.common.boundedImage
import app.chatra.ui.gallery.fileIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal suspend fun AppContainer.readableAttachment(message: Message, attachment: AttachmentSummary): GalleryItem? {
    if (isDemo || !smsRole.isHeld()) return null
    val transport = if (message.ref is MessageRef.Hd) Transport.HD else Transport.MMS
    if (message.ref is MessageRef.Local) {
        val file = media.get(attachment.id) ?: return null
        return GalleryItem(file.id, message.ref, file.mime, file.uri, attachment.label, transport)
    }
    return gallery?.readable(GalleryItem(attachment.id, message.ref, attachment.mimeType,
        attachment.contentUri.orEmpty(), attachment.label, transport))
}

@Composable
internal fun AttachmentStack(message: Message, modifier: Modifier = Modifier) {
    val files = message.attachments
    if (files.isEmpty()) return
    var selected by remember(message.ref) { mutableIntStateOf(-1) }
    val visible = files.take(3)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        visible.forEachIndexed { index, file ->
            val remaining = if (index == visible.lastIndex) files.size - visible.size else 0
            val label = if (remaining == 0) "Otwórz załącznik ${index + 1} z ${files.size}" else "Otwórz załącznik ${index + 1} i kolejne $remaining"
            Box(
                Modifier.weight(1f).height(if (visible.size == 1) 140.dp else 82.dp)
                    .clip(RoundedCornerShape(9.dp)).background(ChatraColors.Ink07)
                    .semantics { contentDescription = label }
                    .clickable(role = Role.Button) { selected = index },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(if (remaining > 0) "+$remaining" else "${index + 1}", style = ChatraType.Body)
                ProviderThumbnail(file, height = if (visible.size == 1) 140.dp else 82.dp, showLabel = false, showVideo = true)
                if (remaining > 0) BasicText("+$remaining",
                    Modifier.align(Alignment.BottomEnd).background(ChatraColors.Accent).padding(5.dp),
                    style = ChatraType.Body.copy(color = androidx.compose.ui.graphics.Color.White))
            }
        }
    }
    if (selected in files.indices) AttachmentViewer(message, selected, { selected = it }, { selected = -1 })
}

private data class AttachmentPreview(val file: GalleryItem?, val bitmap: ImageBitmap?)

@Composable
private fun AttachmentViewer(message: Message, index: Int, onIndex: (Int) -> Unit, onClose: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val attachment = message.attachments[index]
    var error by remember(attachment.id) { mutableStateOf(false) }
    val preview by produceState<AttachmentPreview?>(null, attachment, message.ref) {
        value = null
        value = try {
            val file = container.readableAttachment(message, attachment)
            val bitmap = if (file?.mime?.startsWith("image/") == true) {
                withContext(Dispatchers.IO) { boundedImage(context, Uri.parse(file.uri), 1600) }
            } else null
            AttachmentPreview(file, bitmap)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AttachmentPreview(null, null)
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(app.chatra.core.designsystem.theme.LocalAppearanceTokens.current.background).systemBarsPadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText("Załącznik ${index + 1} z ${message.attachments.size}", Modifier.weight(1f), style = ChatraType.Body)
                TextAction("Zamknij", onClose)
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val loaded = preview
                val bitmap = loaded?.bitmap
                if (bitmap != null) Image(bitmap, attachment.label, Modifier.fillMaxSize())
                else BasicText(when {
                    loaded == null -> "Wczytywanie…"
                    loaded.file == null -> "Załącznik jest niedostępny."
                    else -> "Brak podglądu. Możesz otworzyć oryginał."
                }, style = ChatraType.Body)
            }
            if (error) BasicText("Nie udało się otworzyć oryginału.", style = ChatraType.Body)
            if (preview?.file != null) TextAction("Otwórz oryginał", {
                scope.launch {
                    try {
                        val file = container.readableAttachment(message, attachment) ?: error("unavailable")
                        context.startActivity(fileIntent(context, file, share = false))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        error = true
                    }
                }
            })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextAction("Poprzedni", { onIndex(index - 1) }, enabled = index > 0)
                TextAction("Następny", { onIndex(index + 1) }, enabled = index < message.attachments.lastIndex)
            }
        }
    }
}
