package app.chatra.ui.extras

import android.net.Uri
import android.provider.OpenableColumns
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chatra.core.designsystem.component.pressable
import app.chatra.core.designsystem.glass.cssLinearGradient
import app.chatra.core.designsystem.icon.ChatraIcon
import app.chatra.core.designsystem.icon.ChatraIcons
import app.chatra.core.designsystem.icon.Icon
import app.chatra.core.designsystem.theme.ChatraColors
import app.chatra.core.designsystem.theme.chatraText
import app.chatra.core.model.AttachmentId
import app.chatra.core.model.MediaVariant
import app.chatra.ui.LocalContainer
import app.chatra.ui.common.PrimaryButton
import app.chatra.ui.common.RadioOption
import app.chatra.ui.common.SheetSurface
import app.chatra.ui.common.SheetTitle
import app.chatra.ui.conversation.ConversationViewModel
import app.chatra.ui.conversation.HdBadge
import app.chatra.ui.conversation.formatMegabytes
import app.chatra.ui.conversation.photosWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Wybrany element: placeholder fixture (gradient) albo URI z Photo Pickera. */
private sealed interface Picked {
    val id: AttachmentId
    data class Placeholder(override val id: AttachmentId, val from: Color, val to: Color, val bytes: Long) : Picked
    data class Media(override val id: AttachmentId, val uri: Uri, val bytes: Long) : Picked
}

private val FixturePhotos = listOf(
    Color(0xFFF3C6A8) to Color(0xFFC98E7A), Color(0xFFA9C7E8) to Color(0xFF6C8FB8),
    Color(0xFFC9D8B0) to Color(0xFF8DA674), Color(0xFFE2C7E8) to Color(0xFFA889B8),
).mapIndexed { i, (a, b) -> Picked.Placeholder(AttachmentId("a-fixture-${i + 1}"), a, b, 9_550_000L) } // razem 38,2 MB (S04)

private enum class Mode { Photo, Templates }

/** S04 Dodatki i Chatra HD (CH013). */
@Composable
fun ExtrasSheet(model: ConversationViewModel, onClose: () -> Unit, onSchedule: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val container = LocalContainer.current
    val context = LocalContext.current
    var picked by remember { mutableStateOf<List<Picked>>(if (container.isDemo) FixturePhotos else emptyList()) }
    var variant by rememberSaveable { mutableStateOf(container.sending.value.value.hdDefault) }
    var mode by rememberSaveable { mutableStateOf(Mode.Photo) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) picked = uris.map { Picked.Media(AttachmentId.new(), it, sizeOf(context, it)) }
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
    LaunchedEffect(Unit) { if (picked.isEmpty()) pick() }

    val bytes = picked.sumOf { if (it is Picked.Media) it.bytes else (it as Picked.Placeholder).bytes }
    val name = state.summary?.participants?.firstOrNull()?.displayName?.substringBefore(' ') ?: state.summary?.title.orEmpty()
    val hdCapable = state.summary?.hdCapable == true
    val n = picked.size

    SheetSurface(196.dp, "Dodaj do wiadomości") {
        // Uchwyt 38 × 5 ze wzorca: treść S04 przepełnia arkusz, więc Chrome skurczył go do 0 (flex-shrink),
        // a odstęp 14 został — golden ma tytuł 22 pod górną krawędzią treści.
        Spacer(Modifier.height(0.dp))
        SheetTitle("Dodaj do wiadomości", onClose)
        // Wiersz siatki ma 75,71: „Zaplanuj” to <a> z content-box (74 + obramowanie, skurczone przez Chrome).
        Row(Modifier.height(75.71.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile(ChatraIcons.Photo, "Zdjęcie", mode == Mode.Photo) { if (mode == Mode.Photo) pick() else mode = Mode.Photo }
            ActionTile(ChatraIcons.Camera, "Aparat", false) { pick() }
            ActionTile(ChatraIcons.Clock, "Zaplanuj", false, onSchedule)
            ActionTile(ChatraIcons.Lines, "Szablony", mode == Mode.Templates) { mode = Mode.Templates }
        }
        if (mode == Mode.Templates) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TemplatePicker { model.insertText(it); onClose() }
            }
            return@SheetSurface
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(if (n == 0) "Nic nie wybrano" else "Wybrane: $n ${photosWord(n)}", Modifier.weight(1f), style = chatraText(15f, 600))
                Box(Modifier.heightIn(min = 32.dp).semantics { role = Role.Button }.pressable(pick), contentAlignment = Alignment.Center) {
                    BasicText(if (n == 0) "Wybierz" else "Zmień", style = chatraText(15f, color = ChatraColors.Link))
                }
            }
            if (n > 0) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                picked.take(4).forEachIndexed { i, p -> Thumb(p, "Zdjęcie ${i + 1}, wybrane", Modifier.weight(1f)) }
                repeat(4 - picked.take(4).size) { Spacer(Modifier.weight(1f)) }
            }
        }
        // Warianty zajmują całe wolne miejsce (odpowiednik flex: 1 pod nimi) i przewijają się, gdy się nie mieszczą.
        // Golden ściska CTA do 20,6 dp i wypycha go poza arkusz — tu CTA zostaje 54 dp i przypięte (odstępstwo, cel dotyku).
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            BasicText("Jak wysłać?", Modifier.padding(bottom = 8.dp), style = chatraText(15f, 600))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RadioOption(variant == MediaVariant.OriginalHd, { variant = MediaVariant.OriginalHd }) {
                    OptionText(
                        "Oryginał", hd = true,
                        "${formatMegabytes(bytes)} bez kompresji, przez internet. " + if (hdCapable) "$name ma Chatra, więc zdjęcia pojawią się w rozmowie."
                        else "$name nie ma Chatry — zapytamy, czy wysłać SMS z linkiem.",
                    )
                }
                RadioOption(variant == MediaVariant.NoLocationCopy, { variant = MediaVariant.NoLocationCopy }) {
                    OptionText("Kopia bez lokalizacji", hd = false, "Usuniemy dane GPS. To już nie będzie identyczny plik.")
                }
                RadioOption(variant == MediaVariant.Mms, { variant = MediaVariant.Mms }) {
                    OptionText("MMS", hd = false, "Pomniejszona kopia w limicie operatora. Część szczegółów przepadnie.")
                }
            }
        }
        PrimaryButton(ctaText(variant, n), {
            model.sendMedia(picked.map { it.id }, bytes, variant)
            onClose()
        }, enabled = n > 0)
    }
}

/** CTA zmienia się natychmiast z wariantem (kontrakt S04). */
private fun ctaText(v: MediaVariant, n: Int): String {
    val what = "$n ${photosWord(n)}"
    return when (v) {
        MediaVariant.OriginalHd -> "Wyślij $what w oryginale"
        MediaVariant.NoLocationCopy -> "Wyślij $what bez lokalizacji"
        MediaVariant.Mms -> "Wyślij $what jako MMS"
    }
}

@Composable
private fun OptionText(title: String, hd: Boolean, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(title, style = chatraText(16f, 600))
            if (hd) HdBadge(vertical = 2.dp)
        }
        BasicText(description, style = chatraText(13f, lineHeight = 18f, color = ChatraColors.TextSecondary))
    }
}

@Composable
private fun RowScope.ActionTile(icon: ChatraIcon, label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.weight(1f).height(74.dp).clip(shape)
            .background(if (selected) app.chatra.core.designsystem.theme.LocalAppearanceTokens.current.surface else ChatraColors.white(0.6f))
            .border(if (selected) 1.5.dp else 1.dp, if (selected) ChatraColors.Accent else ChatraColors.white(0.95f), shape)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .pressable(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, 24.dp, ChatraColors.TextPrimary, 1.8f)
        BasicText(label, style = chatraText(12f, 600))
    }
}

@Composable
private fun Thumb(p: Picked, label: String, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val base = modifier.height(70.dp).clip(shape).semantics { role = Role.Image; contentDescription = label }
    when (p) {
        is Picked.Placeholder -> Box(base.drawBehind { drawRect(cssLinearGradient(150f, size, p.from, p.to)) })
        is Picked.Media -> {
            val context = LocalContext.current
            val bitmap by produceState<ImageBitmap?>(null, p.uri) {
                value = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.loadThumbnail(p.uri, Size(240, 240), null).asImageBitmap() }.getOrNull()
                }
            }
            Box(base.background(ChatraColors.Ink07)) {
                bitmap?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize()) }
            }
        }
    }
}

private fun sizeOf(context: android.content.Context, uri: Uri): Long =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
        } ?: 0L
    }.getOrDefault(0L)
