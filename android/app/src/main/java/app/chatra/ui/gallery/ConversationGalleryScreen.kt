package app.chatra.ui.gallery

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chatra.core.designsystem.background.Backgrounds
import app.chatra.core.designsystem.component.GlassCircleButton
import app.chatra.core.designsystem.icon.ChatraIcons
import app.chatra.core.designsystem.theme.ChatraType
import app.chatra.core.designsystem.theme.ChatraColors
import app.chatra.core.designsystem.theme.chatraText
import app.chatra.core.model.*
import app.chatra.ui.ChatraScreen
import app.chatra.ui.LocalChrome
import app.chatra.ui.common.ChoiceDialog
import app.chatra.ui.common.ProviderThumbnail
import app.chatra.ui.common.TextAction
import app.chatra.ui.settings.DetailCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class GalleryAction(val label: String) {
    Open("Otwórz"), Save("Zapisz kopię"), Share("Udostępnij"), Source("Pokaż w rozmowie"),
}

private fun GalleryItem.key(): String = "$source/$id"

@Composable
internal fun ConversationGalleryScreen(
    model: GalleryViewModel,
    gallery: MediaGallery,
    onBack: () -> Unit,
    onSource: (MessageRef) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chrome = LocalChrome.current
    val scope = rememberCoroutineScope()
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var saveKey by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    suspend fun readable(item: GalleryItem): GalleryItem = gallery.readable(item)
        ?.takeIf { it.availability == GalleryAvailability.Ready && it.uri.isNotBlank() }
        ?: error("Plik usunięto lub utracono do niego dostęp.")

    fun perform(action: suspend () -> Unit) {
        if (working) return
        working = true
        notice = null
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { notice = "Nie można wykonać tej czynności. Sprawdź dostęp do pliku lub aplikację do jego obsługi." }
            finally { working = false }
        }
    }

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val key = saveKey
        saveKey = null
        val destination = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && destination != null) perform {
            saveFile(context, destination) {
                val item = state.items.firstOrNull { it.key() == key } ?: error("Brak pliku.")
                readable(item)
            }
            notice = "Zapisano kopię pliku."
        }
    }

    ChatraScreen(Backgrounds.Settings) {
        Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = chrome.top(50.dp), bottom = chrome.bottom(16.dp)),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassCircleButton(ChatraIcons.ChevronLeft, "Wróć do szczegółów rozmowy", onBack)
                BasicText("Wspólne", Modifier.weight(1f).semantics { heading() }, style = chatraText(25f, 700))
                TextAction("Odśwież", model::refresh, enabled = !state.loading && !working)
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (kind in MediaKind.entries) {
                    val active = kind == state.kind
                    Box(Modifier.heightIn(min = 48.dp).selectable(active, enabled = !working, role = Role.Tab,
                        onClick = { notice = null; model.select(kind) }).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        BasicText(kind.label, style = chatraText(16f, if (active) 700 else 500,
                            color = if (active) ChatraColors.Link else ChatraColors.TextSecondary))
                    }
                }
            }
            state.total?.let { BasicText("Liczba pozycji: $it", Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = ChatraType.Preview) }
            notice?.let { BasicText(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = ChatraType.Preview) }
            if (working) BasicText("Przygotowywanie pliku…", style = ChatraType.Preview)
            key(state.kind) {
                val list = rememberLazyListState()
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.items, key = { it.key() }) { item -> GalleryRow(item, { selectedKey = item.key() }, !working) }
                    when {
                        state.error -> item("error") {
                            BasicText("Nie udało się wczytać galerii.", Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = ChatraType.Body)
                            TextAction("Spróbuj ponownie", model::load)
                        }
                        state.loading -> item("loading") { BasicText("Wczytywanie…", style = ChatraType.Body) }
                        state.items.isEmpty() -> item("empty") { BasicText("Brak pozycji w tej zakładce.", style = ChatraType.Body) }
                        state.next != null -> item("more") { TextAction("Wczytaj więcej", model::load, Modifier.heightIn(min = 48.dp)) }
                    }
                }
            }
        }
    }

    val item = state.items.firstOrNull { it.key() == selectedKey }
    if (item != null) {
        val options = when {
            item.availability != GalleryAvailability.Ready -> listOf(GalleryAction.Source)
            item.isLink -> listOf(GalleryAction.Open, GalleryAction.Share, GalleryAction.Source)
            else -> GalleryAction.entries
        }
        ChoiceDialog(item.label, options.map { it.label }, { index ->
            when (options[index]) {
                GalleryAction.Source -> onSource(item.source)
                GalleryAction.Open -> perform {
                    val intent = if (item.isLink) Intent(Intent.ACTION_VIEW, Uri.parse(item.uri).normalizeScheme())
                    else fileIntent(context, readable(item), share = false)
                    context.startActivity(intent)
                }
                GalleryAction.Save -> perform {
                    val verified = readable(item)
                    saveKey = item.key()
                    createDocument.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = verified.mime
                        putExtra(Intent.EXTRA_TITLE, suggestedFileName(verified))
                    })
                }
                GalleryAction.Share -> perform {
                    val intent = if (item.isLink) Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, item.uri)
                    }, "Udostępnij link") else fileIntent(context, readable(item), share = true)
                    context.startActivity(intent)
                }
            }
        }, { selectedKey = null })
    }
}

@Composable
private fun GalleryRow(item: GalleryItem, onActions: () -> Unit, enabled: Boolean) {
    val status = when (item.availability) {
        GalleryAvailability.Ready -> null
        GalleryAvailability.AwaitingDownload -> "Pobierz z wiadomości źródłowej"
        GalleryAvailability.Downloading -> "Pobieranie w toku"
        GalleryAvailability.Expired -> "Plik wygasł"
        GalleryAvailability.Missing -> "Plik niedostępny"
        GalleryAvailability.Preview -> "Pozycja demonstracyjna"
    }
    DetailCard {
        if (item.availability == GalleryAvailability.Ready && !item.isLink && attachmentKind(item.mime) in setOf(MediaKind.Images, MediaKind.Videos)) {
            ProviderThumbnail(AttachmentSummary(item.id, item.mime, item.label, item.uri), height = 108.dp, showLabel = false, showVideo = true)
        }
        BasicText(item.label, style = ChatraType.Body, maxLines = 2, overflow = TextOverflow.Ellipsis)
        BasicText(if (status == null) item.transport.name else "${item.transport.name} · $status", style = ChatraType.Preview)
        TextAction("Opcje: ${item.label}", onActions, Modifier.heightIn(min = 48.dp), enabled = enabled)
    }
}
