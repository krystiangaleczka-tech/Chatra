package app.chatra.ui.conversation

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.chatra.core.designsystem.background.Backgrounds
import app.chatra.core.designsystem.theme.ChatraType
import app.chatra.core.model.*
import app.chatra.di.AppContainer
import app.chatra.platform.ActivityPlatform
import app.chatra.ui.ChatraScreen
import app.chatra.ui.LocalChrome
import app.chatra.ui.LocalContainer
import app.chatra.ui.common.ChoiceDialog
import app.chatra.ui.common.TextAction
import app.chatra.ui.gallery.streamFile
import app.chatra.ui.gallery.suggestedFileName
import app.chatra.ui.gallery.temporaryShareFolder
import app.chatra.ui.settings.DetailCard
import java.io.OutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val EXPORT_FILE_LIMIT = 10
private const val EXPORT_BYTES_LIMIT = 250L * 1024 * 1024
private val exportDate = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm xxx")

internal fun selectedTranscript(messages: List<Message>, metadata: Boolean, zone: ZoneId): String =
    messages.joinToString("\n\n") { message ->
        val header = if (metadata) {
            val direction = if (message.direction == Direction.Incoming) "Odebrana" else "Wysłana"
            "${exportDate.format(message.timestamp.atZone(zone))} · $direction\n"
        } else ""
        header + message.body.ifBlank { if (message.attachments.isNotEmpty() || message.hd != null) "[Wiadomość z załącznikami]" else "[Pusta wiadomość]" }
    }

private class ExportRejected(message: String) : IllegalStateException(message)

private data class ExportSelection(val limit: Int, val refs: Set<MessageRef>, val metadata: Boolean, val files: Boolean)
private data class ExportPayload(val text: String, val files: List<GalleryItem>)

private suspend fun AppContainer.exportPayload(id: ConversationId, selection: ExportSelection): ExportPayload {
    if (isDemo || !smsRole.isHeld() || conversations.conversation(id).first()?.trashed != false) {
        throw ExportRejected("Rozmowa jest niedostępna.")
    }
    val rows = conversations.exportMessages(id, selection.limit)
        .filter { it.conversationId == id && it.ref in selection.refs && it.ref !is MessageRef.Local }
    if (rows.size != selection.refs.size || rows.isEmpty()) throw ExportRejected("Wybrane wiadomości zmieniły się. Wybierz je ponownie.")
    val files = if (selection.files) {
        if (rows.sumOf { it.attachments.size } > EXPORT_FILE_LIMIT) throw ExportRejected("Wybierz najwyżej $EXPORT_FILE_LIMIT załączników.")
        if (rows.any { message -> message.hd != null && message.attachments.any { it.contentUri == null } }) {
            throw ExportRejected("Najpierw pobierz wybrane załączniki HD.")
        }
        rows.flatMap { message -> message.attachments.map { attachment ->
            readableAttachment(message, attachment) ?: throw ExportRejected("Jeden z wybranych załączników jest niedostępny.")
        } }
    } else emptyList()
    return ExportPayload(selectedTranscript(rows, selection.metadata, clock.zone()), files)
}

private suspend fun writePayload(context: Context, payload: ExportPayload, raw: OutputStream, zip: Boolean) {
    val text = payload.text.toByteArray(Charsets.UTF_8)
    if (!zip) {
        raw.write(text)
        return
    }
    ZipOutputStream(raw).use { archive ->
        var total = text.size.toLong()
        val limited = object : OutputStream() {
            override fun write(value: Int) {
                check(++total <= EXPORT_BYTES_LIMIT) { "Eksport przekracza 250 MB." }
                archive.write(value)
            }
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                total += length
                check(total <= EXPORT_BYTES_LIMIT) { "Eksport przekracza 250 MB." }
                archive.write(bytes, offset, length)
            }
        }
        archive.putNextEntry(ZipEntry("rozmowa.txt"))
        archive.write(text)
        archive.closeEntry()
        payload.files.forEachIndexed { index, file ->
            archive.putNextEntry(ZipEntry("zalaczniki/${index + 1}-${suggestedFileName(file)}"))
            streamFile(context, file, limited)
            archive.closeEntry()
        }
    }
}

private suspend fun exportShareIntent(context: Context, payload: ExportPayload, zip: Boolean): Intent = withContext(Dispatchers.IO) {
    val intent = Intent(Intent.ACTION_SEND)
    val bytes = payload.text.toByteArray(Charsets.UTF_8)
    if (!zip && bytes.size <= 48_000) {
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, payload.text)
    } else {
        val folder = temporaryShareFolder(context)
        try {
            val file = folder.resolve(if (zip) "rozmowa-chatra.zip" else "rozmowa-chatra.txt")
            file.outputStream().use { writePayload(context, payload, it, zip) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            intent.type = if (zip) "application/zip" else "text/plain"
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.clipData = ClipData.newRawUri("Eksport Chatry", uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            folder.deleteRecursively()
            throw e
        }
    }
    Intent.createChooser(intent, "Udostępnij zaznaczone wiadomości")
}

@Composable
internal fun ConversationExportScreen(id: ConversationId, onBack: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chrome = LocalChrome.current
    var limit by remember { mutableIntStateOf(20) }
    var rows by remember { mutableStateOf<List<Message>?>(null) }
    var picked by remember { mutableStateOf(emptySet<MessageRef>()) }
    var files by remember { mutableStateOf(false) }
    var metadata by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var pendingDocument by remember { mutableStateOf<ExportSelection?>(null) }

    fun action(operation: suspend () -> Unit) {
        busy = true
        status = null
        scope.launch {
            try { operation() }
            catch (e: CancellationException) { throw e }
            catch (e: ExportRejected) { status = e.message }
            catch (_: Exception) { status = "Operacja się nie powiodła. Sprawdź dostęp i rozmiar wybranych plików." }
            finally { busy = false }
        }
    }

    LaunchedEffect(id, limit) {
        status = null
        rows = null
        picked = emptySet()
        try {
            check(!container.isDemo && container.smsRole.isHeld() && container.conversations.conversation(id).first() != null)
            rows = container.conversations.exportMessages(id, limit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            status = "Rozmowa lub historia jest niedostępna."
        }
    }

    val saveDocument: (Uri?, Boolean) -> Unit = { uri, zip ->
        val selection = pendingDocument
        pendingDocument = null
        if (uri == null) busy = false
        else action {
            val resolver = context.contentResolver
            require(uri.scheme == "content")
            try {
                val snapshot = checkNotNull(selection)
                check(snapshot.files == zip)
                val payload = container.exportPayload(id, snapshot)
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "w")?.use { writePayload(context, payload, it, zip) } ?: error("no output")
                }
                status = "Plik zapisany."
            } catch (e: Exception) {
                withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) { runCatching { resolver.delete(uri, null, null) } }
                throw e
            }
        }
    }
    val textDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { saveDocument(it, false) }
    val zipDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { saveDocument(it, true) }
    val actionsEnabled = !busy && picked.isNotEmpty()
    val count = picked.size
    ChatraScreen(Backgrounds.Settings) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = chrome.top(50.dp), bottom = chrome.bottom(24.dp))) {
            item("options") {
                DetailCard {
                    TextAction("Wróć", onBack, enabled = !busy)
                    BasicText("Udostępnianie i eksport", style = ChatraType.SheetTitle)
                    TextAction("Zakres · ostatnie $limit", { question = "Zakres" }, enabled = !busy)
                    BasicText("Zaznacz wiadomości poniżej. W razie potrzeby zmień zakres.", style = ChatraType.Preview)
                    if (!rows.isNullOrEmpty()) {
                        val allPicked = count == rows?.size
                        TextAction(if (allPicked) "Wyczyść zaznaczenie" else "Zaznacz cały zakres", {
                            picked = if (allPicked) emptySet() else rows.orEmpty().map { it.ref }.toSet()
                        }, enabled = !busy)
                    }
                    ExportOption("Dołącz datę i kierunek", metadata, !busy) { metadata = it }
                    ExportOption("Dołącz pliki zaznaczonych wiadomości do ZIP", files, !busy) { files = it }
                    TextAction("Udostępnij zaznaczone ($count)", { question = "Udostępnianie" }, enabled = actionsEnabled)
                    TextAction("Zapisz zaznaczone ($count)", { question = "Zapis" }, enabled = actionsEnabled)
                    TextAction("Dodaj skrót na ekranie głównym", { question = "Skrót" }, enabled = !busy && rows != null)
                    status?.let { BasicText(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = ChatraType.Body) }
                    if (busy) BasicText("Przygotowywanie…", style = ChatraType.Body)
                }
            }
            if (rows?.isEmpty() == true) item("empty") { BasicText("Brak wiadomości w tym zakresie.", style = ChatraType.Preview) }
            items(rows.orEmpty().asReversed(), key = { it.ref.toString() }) { message ->
                val checked = message.ref in picked
                val date = exportDate.format(message.timestamp.atZone(container.clock.zone()))
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .toggleable(checked, enabled = !busy, role = Role.Checkbox) {
                        picked = if (it) picked + message.ref else picked - message.ref
                    }.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText(if (checked) "☑" else "☐", style = ChatraType.Body)
                    Column(Modifier.weight(1f)) {
                        BasicText(date, style = ChatraType.Preview)
                        BasicText(message.body.ifBlank { "Wiadomość multimedialna" }, maxLines = 3, overflow = TextOverflow.Ellipsis, style = ChatraType.Body)
                        if (message.attachments.isNotEmpty() || message.hd != null) {
                            BasicText("Załączniki: ${message.hd?.itemCount ?: message.attachments.size}", style = ChatraType.Preview)
                        }
                    }
                }
            }
        }
    }
    val selection = ExportSelection(limit, picked, metadata, files)
    when (question) {
        "Zakres" -> ChoiceDialog("Ile ostatnich wiadomości pokazać?", listOf("20", "100", "500"), {
            limit = listOf(20, 100, 500)[it]
        }, { question = null })
        "Udostępnianie" -> ChoiceDialog("Udostępnić $count zaznaczonych wiadomości?", listOf("Wybierz aplikację"), {
            action { context.startActivity(exportShareIntent(context, container.exportPayload(id, selection), selection.files)) }
        }, { question = null })
        "Zapis" -> ChoiceDialog("Zapisać $count zaznaczonych wiadomości?", listOf("Wybierz miejsce zapisu"), {
            pendingDocument = selection
            busy = true
            try {
                if (files) zipDocument.launch("rozmowa-chatra.zip") else textDocument.launch("rozmowa-chatra.txt")
            } catch (_: Exception) {
                pendingDocument = null
                busy = false
                status = "Nie udało się otworzyć wyboru miejsca zapisu."
            }
        }, { question = null })
        "Skrót" -> ChoiceDialog("Dodać skrót „Rozmowa w Chatrze” do ekranu głównego?", listOf("Poproś system o dodanie"), {
            action {
                check(container.conversations.conversation(id).first() != null)
                status = if ((container.platform as? ActivityPlatform)?.pinConversation(id) == true) "Potwierdź dodanie skrótu w systemie."
                    else "Ekran główny nie obsługuje przypinania skrótów."
            }
        }, { question = null })
    }
}

@Composable
private fun ExportOption(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(if (checked) "☑" else "☐", style = ChatraType.Body)
        BasicText(label, style = ChatraType.Body)
    }
}
