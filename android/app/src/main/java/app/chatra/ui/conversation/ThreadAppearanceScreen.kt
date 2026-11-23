package app.chatra.ui.conversation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chatra.core.designsystem.theme.*
import app.chatra.core.model.*
import app.chatra.core.model.Composition
import app.chatra.ui.LocalContainer
import app.chatra.ui.common.ChoiceDialog
import app.chatra.ui.common.TextAction
import app.chatra.ui.common.boundedImage
import app.chatra.ui.settings.DetailCard
import app.chatra.ui.settings.DetailPage
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun ThreadColor?.color(): Color? = when (this) {
    ThreadColor.Blue -> Color(0xFF1558AF)
    ThreadColor.Plum -> Color(0xFF723775)
    ThreadColor.Teal -> Color(0xFF006659)
    null -> null
}

private fun wallpaperFile(context: Context, key: String): File =
    File(File(context.noBackupFilesDir, "conversation-backgrounds"), "${UUID.fromString(key)}.jpg")

@Composable
internal fun conversationBackground(style: AppearanceOverride?): DrawScope.() -> Unit {
    val tokens = LocalAppearanceTokens.current
    val context = LocalContext.current
    val background = style?.background?.takeUnless { tokens.contrast }
    val key = style?.wallpaper?.takeIf { background == ThreadBackground.Photo && !tokens.matte }
    val photo by produceState<ImageBitmap?>(null, key) {
        value = null
        if (key != null) value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(wallpaperFile(context, key).path)?.asImageBitmap() }.getOrNull()
        }
    }
    val image = photo.takeIf { key != null }
    return {
        val color = when (background) {
            ThreadBackground.Mist -> if (tokens.dark) Color(0xFF222E42) else Color(0xFFE1EAF7)
            ThreadBackground.Sand -> if (tokens.dark) Color(0xFF352A20) else Color(0xFFF3E8DA)
            ThreadBackground.Forest -> if (tokens.dark) Color(0xFF19352F) else Color(0xFFDDECE1)
            else -> null
        }
        color?.let { drawRect(it) }
        if (image != null) {
            val scale = maxOf(size.width / image.width, size.height / image.height)
            val width = (image.width * scale).toInt()
            val height = (image.height * scale).toInt()
            clipRect {
                drawImage(image, dstOffset = IntOffset((size.width.toInt() - width) / 2, (size.height.toInt() - height) / 2),
                    dstSize = IntSize(width, height))
                drawRect(tokens.background.copy(alpha = .86f))
            }
        }
    }
}

@Composable
internal fun ThreadAppearanceScreen(id: ConversationId, onBack: () -> Unit) {
    val container = LocalContainer.current
    val store = container.conversationAppearance ?: return
    val overrides by store.value.collectAsStateWithLifecycle()
    val notifications by container.notifications.value.collectAsStateWithLifecycle()
    val pro = container.billing?.state?.collectAsStateWithLifecycle()?.value?.entitled == true
    val style = overrides[id.value] ?: AppearanceOverride()
    val tokens = LocalAppearanceTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var setting by remember { mutableStateOf<String?>(null) }

    fun apply(change: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = false
        scope.launch {
            try { change() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }

    suspend fun update(change: (AppearanceOverride) -> AppearanceOverride) {
        store.update { all -> all + (id.value to change(all[id.value] ?: AppearanceOverride())) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) apply {
            val key = UUID.randomUUID().toString()
            val file = wallpaperFile(context, key)
            var saved = false
            val previous = store.value.value[id.value]?.wallpaper
            try {
                withContext(Dispatchers.IO) {
                    val bitmap = boundedImage(context, uri, 1280) ?: error("invalid wallpaper")
                    check(file.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
                    file.outputStream().use { check(bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, 85, it)) }
                }
                withContext(NonCancellable) {
                    update { it.copy(background = ThreadBackground.Photo, wallpaper = key) }
                    saved = true
                }
            } finally {
                if (saved) previous?.let { wallpaperFile(context, it).delete() } else file.delete()
            }
        }
    }

    DetailPage("Wygląd tej rozmowy", onBack) {
        DetailCard {
            BasicText("Podgląd na żywo", style = ChatraType.Body)
            val background = conversationBackground(style)
            Column(Modifier.fillMaxWidth().background(tokens.background).drawBehind(background).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText("Wiadomość odebrana", Modifier.background(tokens.surface, RoundedCornerShape(12.dp)).padding(10.dp), style = ChatraType.Body)
                BasicText("Wiadomość wysłana", Modifier.align(Alignment.End).background(tokens.outgoingBubble, RoundedCornerShape(12.dp)).padding(10.dp),
                    style = ChatraType.Body.copy(color = Color.White))
                BasicText("Wyślij", Modifier.align(Alignment.End).background(tokens.accent, RoundedCornerShape(10.dp)).padding(8.dp),
                    style = ChatraType.Body.copy(color = Color.White))
            }
            BasicText(if (style.notificationPreview ?: notifications.previews) "Powiadomienie · przykładowa treść" else "Powiadomienie · otwórz rozmowę",
                style = ChatraType.Preview)
            BasicText("Wygląd jest lokalny. Ustawienia kontrastu mają pierwszeństwo.", style = ChatraType.Preview)
            if (error) BasicText("Nie udało się zapisać ustawienia.", style = ChatraType.Body)
        }
        DetailCard {
            val labels = listOf(
                "Kompozycja" to style.composition?.label,
                "Tło" to style.background?.label,
                "Akcent" to style.accent?.label,
                "Dymki" to style.bubble?.label,
                "Podgląd w powiadomieniach" to style.notificationPreview?.let { if (it) "Pokaż" else "Ukryj" },
            )
            labels.forEach { (label, value) -> TextAction("$label · ${value ?: "globalne"}", { setting = label }, enabled = !busy) }
            TextAction("Przywróć ustawienia globalne", {
                apply {
                    store.update { it - id.value }
                    style.wallpaper?.let { wallpaperFile(context, it).delete() }
                }
            }, enabled = !busy)
        }
    }

    val inherit = "Użyj ustawienia globalnego"
    when (setting) {
        "Kompozycja" -> {
            val options = Composition.entries.filter { !it.pro || pro }
            ChoiceDialog("Kompozycja", listOf(inherit) + options.map { it.label }, { option ->
                apply { update { it.copy(composition = options.getOrNull(option - 1)) } }
            }, { setting = null })
        }
        "Tło" -> ChoiceDialog("Tło", listOf(inherit) + ThreadBackground.entries.map { it.label }, { option ->
            val selected = ThreadBackground.entries.getOrNull(option - 1)
            if (selected == ThreadBackground.Photo) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            else apply {
                update { it.copy(background = selected, wallpaper = null) }
                style.wallpaper?.let { wallpaperFile(context, it).delete() }
            }
        }, { setting = null })
        "Akcent", "Dymki" -> {
            val accent = setting == "Akcent"
            ChoiceDialog(if (accent) "Akcent" else "Dymki", listOf(inherit) + ThreadColor.entries.map { it.label }, { option ->
                val color = ThreadColor.entries.getOrNull(option - 1)
                apply { update { if (accent) it.copy(accent = color) else it.copy(bubble = color) } }
            }, { setting = null })
        }
        "Podgląd w powiadomieniach" -> ChoiceDialog("Podgląd w powiadomieniach", listOf(inherit, "Ukryj treść", "Pokaż treść"), { option ->
            apply { update { it.copy(notificationPreview = when (option) { 1 -> false; 2 -> true; else -> null }) } }
        }, { setting = null })
    }
}
