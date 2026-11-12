package app.chatra.ui.extras

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chatra.core.designsystem.theme.*
import app.chatra.core.model.*
import app.chatra.ui.LocalContainer
import app.chatra.ui.common.*
import app.chatra.ui.conversation.ConversationViewModel
import app.chatra.ui.settings.DetailCard
import app.chatra.ui.settings.DetailPage
import kotlinx.coroutines.launch

@Composable
fun MediaExtrasSheet(model: ConversationViewModel, onClose: () -> Unit, onSchedule: () -> Unit) {
    val c = LocalContainer.current
    val state by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var items by remember { mutableStateOf<List<StoredMedia>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var templates by rememberSaveable { mutableStateOf(false) }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var variant by rememberSaveable { mutableStateOf(state.mediaVariant ?: MediaVariant.Mms) }
    fun import(uris: List<Uri>) {
        busy = true
        scope.launch {
            try {
                val next = items.toMutableList()
                for (uri in uris) {
                    require(next.size < 20) { "Możesz wybrać do 20 plików." }
                    next += c.media.importUri(uri.toString())
                    // Persist every successful import, including when a later URI fails.
                    items = next.toList(); model.attachMedia(items, variant)
                }
                error = null
            } catch (e: Exception) { error = e.message ?: "Nie udało się skopiować pliku. Wybierz go ponownie." }
            finally { busy = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { if (it.isNotEmpty()) import(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let { import(listOf(Uri.parse(it))) }; cameraUri = null }
    LaunchedEffect(state.attachmentIds) {
        if (items.map { it.id } == state.attachmentIds) return@LaunchedEffect
        try { items = state.attachmentIds.mapNotNull { c.media.get(it) }; if (items.size != state.attachmentIds.size) error = "Część plików jest niedostępna. Usuń brakujące pliki lub wybierz je ponownie." }
        catch (_: Exception) { error = "Nie udało się odczytać wybranych plików." }
    }
    DetailPage("Dodatki", onClose) {
        DetailCard {
            TextAction("Zaplanuj wiadomość", onSchedule, enabled = !busy && !state.busy)
            TextAction(if (templates) "Zdjęcia i filmy" else "Szablony wiadomości", { templates = !templates })
            if (templates) TemplatePicker { model.insertText(it); onClose() }
            else {
                TextAction("Wybierz zdjęcia lub filmy", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }, enabled = !busy)
                TextAction("Zrób zdjęcie", { cameraUri = c.media.cameraUri(); camera.launch(Uri.parse(cameraUri)) }, enabled = !busy)
                BasicText("Wybrane: ${items.size} · ${items.sumOf { it.bytes } / 1024} KB", style = ChatraType.Preview)
                if (busy) BasicText("Kopiowanie i sprawdzanie plików…", style = ChatraType.Body)
                items.forEachIndexed { i, item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BasicText("${i + 1}. ${if (item.sanitized) "Kopia bez metadanych" else "Oryginał"} · ${item.mime} · ${item.bytes / 1024} KB", Modifier.weight(1f), style = ChatraType.Preview)
                        TextAction("Usuń", { items = items.filterNot { it.id == item.id }; scope.launch { model.attachMedia(items, variant) } }, enabled = !busy)
                        if (i > 0) TextAction("W górę", {
                            items = items.toMutableList().apply { add(i - 1, removeAt(i)) }
                            scope.launch { model.attachMedia(items, variant) }
                        }, enabled = !busy)
                    }
                    app.chatra.ui.common.ProviderThumbnail(AttachmentSummary(item.id, item.mime, "Wybrany plik ${i + 1}", item.uri))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (share in listOf(false, true)) TextAction(if (share) "Udostępnij plik" else "Otwórz plik", {
                            scope.launch {
                                try {
                                    val verified = c.media.get(item.id) ?: error("Plik jest niedostępny.")
                                    val uri = Uri.parse(verified.uri)
                                    val intent = android.content.Intent(if (share) android.content.Intent.ACTION_SEND else android.content.Intent.ACTION_VIEW).apply {
                                        if (share) { type = verified.mime; putExtra(android.content.Intent.EXTRA_STREAM, uri) } else setDataAndType(uri, verified.mime)
                                        clipData = android.content.ClipData.newUri(context.contentResolver, "Załącznik", uri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, if (share) "Udostępnij załącznik" else "Otwórz załącznik"))
                                } catch (_: Exception) { error = "Nie udało się otworzyć pliku. Sprawdź dostępność pliku i aplikacji do jego obsługi." }
                            }
                        }, enabled = !busy)
                    }
                }
                TextAction("Zachowaj oryginały", {
                    busy = true
                    scope.launch {
                        try { items = items.map { c.media.derive(it.id, MediaVariant.OriginalHd) }; variant = MediaVariant.OriginalHd; model.attachMedia(items, variant); error = null }
                        catch (e: Exception) { error = e.message ?: "Nie można odczytać oryginałów." }
                        finally { busy = false }
                    }
                }, enabled = !busy)
                TextAction("Przygotuj kopie bez lokalizacji", {
                    busy = true
                    scope.launch {
                        try {
                            val copies = items.map { c.media.derive(it.id, MediaVariant.NoLocationCopy) }
                            items = copies; variant = MediaVariant.NoLocationCopy; model.attachMedia(items, variant); error = null
                        } catch (e: Exception) { error = e.message ?: "Nie udało się przygotować kopii." }
                        finally { busy = false }
                    }
                }, enabled = !busy && items.isNotEmpty())
                TextAction("Użyj MMS", {
                    variant = MediaVariant.Mms
                    scope.launch { model.attachMedia(items, variant); onClose() }
                }, enabled = !busy && items.isNotEmpty())
                BasicText(when (variant) {
                    MediaVariant.Mms -> "Pliki są w szkicu. MMS przygotuje pomniejszone kopie w limicie wybranej SIM. Wyślij z rozmowy."
                    MediaVariant.OriginalHd -> "Oryginały pozostają niezmienione w szkicu. Wysyłanie przez Chatra HD nie jest jeszcze dostępne."
                    MediaVariant.NoLocationCopy -> "Kopie JPEG zawierają tylko piksele, bez metadanych źródła. Konwersja może zmienić jakość i rozmiar zdjęcia; oryginały są zachowane. Aby je wysłać, wybierz MMS; Chatra HD nie jest jeszcze dostępne."
                }, style = ChatraType.Preview)
            }
            error?.let { BasicText(it, style = ChatraType.Preview) }
        }
    }
}
