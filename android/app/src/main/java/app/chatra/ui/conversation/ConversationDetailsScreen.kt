package app.chatra.ui.conversation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chatra.core.designsystem.theme.ChatraColors
import app.chatra.core.designsystem.theme.ChatraType
import app.chatra.core.designsystem.theme.chatraText
import app.chatra.core.model.*
import app.chatra.ui.LocalContainer
import app.chatra.ui.common.ChoiceDialog
import app.chatra.ui.common.SimPicker
import app.chatra.ui.common.TextAction
import app.chatra.ui.settings.DetailCard
import app.chatra.ui.settings.DetailPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class BlockRequest(val participant: Participant, val block: Boolean)

/** Keeps S03 mounted underneath, including its scroll, message anchor and unsent draft. */
@Composable
fun ConversationDetailsScreen(
    model: ConversationViewModel,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onSchedule: () -> Unit,
    onGallery: () -> Unit,
    onTrashed: () -> Unit,
) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val state by model.state.collectAsStateWithLifecycle()
    val notifications by c.notifications.value.collectAsStateWithLifecycle()
    val blocks by c.organization.blocked.collectAsStateWithLifecycle()
    val overrides = remember(c) { c.conversationAppearance?.value ?: kotlinx.coroutines.flow.MutableStateFlow(emptyMap()) }
    val threadAppearance by overrides.collectAsStateWithLifecycle()
    val history = remember(c) { c.conversations.historyStatus() }
    val historyStatus by history.collectAsStateWithLifecycle(HistoryStatus.Loading)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showSim by remember { mutableStateOf(false) }
    var showThemes by remember { mutableStateOf(false) }
    var showMute by remember { mutableStateOf(false) }
    var showTrash by remember { mutableStateOf(false) }
    var blockRequest by remember { mutableStateOf<BlockRequest?>(null) }
    var now by remember { mutableLongStateOf(c.clock.now().toEpochMilli()) }
    LaunchedEffect(c) { while (true) { now = c.clock.now().toEpochMilli(); delay(30_000) } }
    BackHandler(enabled = busy) { /* Finish the one accepted mutation before leaving. */ }

    fun mutate(failure: String, operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try { operation() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = failure }
            finally { busy = false }
        }
    }

    val summary = state.summary
    DetailPage("Szczegóły rozmowy", { if (!busy) onBack() }) {
        if (c.isDemo) BasicText("Dane demonstracyjne. Zmiany dotyczą wyłącznie podglądu.", style = ChatraType.Preview)
        error?.let { BasicText(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = chatraText(14f, color = ChatraColors.ErrorText)) }
        state.error?.let { BasicText(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = chatraText(14f, color = ChatraColors.ErrorText)) }
        if (busy) BasicText("Zapisywanie…", Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = ChatraType.Preview)
        if (summary == null) {
            DetailCard {
                BasicText(when (historyStatus) {
                    HistoryStatus.Loading -> "Wczytywanie rozmowy…"
                    HistoryStatus.PermissionRequired -> "Brak dostępu do historii. Nadaj rolę aplikacji SMS i dostęp do wiadomości."
                    is HistoryStatus.Error -> "Nie udało się wczytać rozmowy."
                    HistoryStatus.Ready -> "Ta rozmowa nie jest już dostępna."
                }, style = ChatraType.Body)
                DetailsAction("Spróbuj ponownie", { mutate("Nie udało się odświeżyć rozmowy.") { c.conversations.refresh() } }, !busy)
                DetailsAction("Wróć do wiadomości", onBack, !busy)
            }
        } else {
            val id = summary.id
            val mutedUntil = notifications.mutedUntil[id.value] ?: 0L
            val muted = mutedUntil > now
            val pro = c.billing?.state?.collectAsStateWithLifecycle()?.value?.entitled == true
            val themes = app.chatra.core.model.Composition.entries.filter { !it.pro || pro }
            DetailCard {
                BasicText(summary.title, style = chatraText(22f, 700))
                BasicText(if (summary.isGroupMms) "Grupowy MMS · ${summary.participants.size} odbiorców" else "Kontakt i numery", style = ChatraType.Preview)
                if (c.isDemo) BasicText("Połączenia są wyłączone w podglądzie demonstracyjnym.", style = ChatraType.Preview)
                if (summary.participants.isEmpty()) BasicText("Brak danych uczestników.", style = ChatraType.Preview)
                summary.participants.forEach { participant ->
                    val blocked = blocks.any { it.id == recipientKey(participant.address.raw) }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BasicText(participant.label, style = ChatraType.Body)
                        if (participant.displayName != null) BasicText(participant.address.raw, style = ChatraType.Preview)
                        if (blocked) BasicText("Zablokowany lokalnie — historia zachowana, powiadomienia wyłączone.", style = ChatraType.Preview)
                        if (isOutgoingNumber(participant.address.raw)) {
                            DetailsAction("Zadzwoń: ${participant.label}", {
                                try { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", participant.address.raw, null))) }
                                catch (_: Exception) { error = "Nie można otworzyć systemowego dialera na tym urządzeniu." }
                            }, !busy && !c.isDemo)
                        } else BasicText("Ten nadawca nie udostępnia numeru, pod który można zadzwonić lub odpowiedzieć.", style = ChatraType.Preview)
                        DetailsAction("Kopiuj adres: ${participant.label}", {
                            try { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Adres rozmówcy", participant.address.raw)) }
                            catch (_: Exception) { error = "Nie udało się skopiować adresu do schowka." }
                        }, !busy)
                        if (c.organization.editable && participant.address.raw.isNotBlank()) DetailsAction(
                            if (blocked) "Odblokuj: ${participant.label}" else "Zablokuj: ${participant.label}",
                            { blockRequest = BlockRequest(participant, !blocked) }, !busy)
                    }
                }
            }
            DetailCard {
                DetailsAction("Szukaj w tej rozmowie", onSearch, !busy)
                DetailsAction("Zaplanuj wiadomość", onSchedule, !busy && summary.participants.any { it.address.canReply })
                if (summary.participants.none { it.address.canReply }) BasicText("Do tego nadawcy nie można zaplanować odpowiedzi.", style = ChatraType.Preview)
                DetailsAction(if (summary.pinned) "Odepnij rozmowę" else "Przypnij rozmowę", {
                    mutate("Nie udało się zmienić przypięcia. Spróbuj ponownie.") { c.conversations.setPinned(id, !summary.pinned) }
                }, !busy)
                BasicText(when {
                    !muted -> "Powiadomienia włączone, jeśli pozwalają na to ustawienia telefonu."
                    mutedUntil == Long.MAX_VALUE -> "Wyciszona do odwołania."
                    else -> "Wyciszona do ${java.time.Instant.ofEpochMilli(mutedUntil).atZone(c.clock.zone()).toLocalDateTime().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm"))}."
                }, style = ChatraType.Preview)
                DetailsAction(if (muted) "Zmień wyciszenie" else "Wycisz rozmowę", { showMute = true }, !busy)
                if (muted) DetailsAction("Włącz powiadomienia", {
                    mutate("Nie udało się włączyć powiadomień. Spróbuj ponownie.") { c.notifications.update { it.copy(mutedUntil = it.mutedUntil - id.value) } }
                }, !busy)
                BasicText("Wyciszenie nie blokuje odbioru ani nie usuwa wiadomości.", style = ChatraType.Preview)
            }
            DetailCard {
                BasicText("Karta SIM: ${(state.sim as? SimChoice.Ready)?.sim?.label ?: "wymaga wyboru"}", style = ChatraType.Body)
                DetailsAction("Wybierz kartę SIM", { showSim = true }, !busy && !state.busy && state.sims.any { it.available })
                if (state.sims.none { it.available }) BasicText("Brak dostępnych kart SIM lub uprawnienia do ich odczytu.", style = ChatraType.Preview)
                BasicText("Wybór dotyczy szkicu i preferowanej karty tej rozmowy. Nie zmienia wiadomości już przekazanych do wysyłania.", style = ChatraType.Preview)
                BasicText("Wygląd: ${threadAppearance[id.value]?.composition?.label ?: "dziedziczony z ustawień globalnych"}", style = ChatraType.Body)
                DetailsAction("Wygląd tej rozmowy", { showThemes = true }, !busy && c.conversationAppearance != null)
                BasicText("Zmiany wyglądu są widoczne tylko na Twoim telefonie.", style = ChatraType.Preview)
            }
            DetailCard {
                BasicText("Wspólne media", style = chatraText(17f, 600))
                DetailsAction("Otwórz galerię", onGallery, !busy && c.gallery != null)
                if (c.gallery == null) BasicText("Galeria jest niedostępna w tej wersji.", style = ChatraType.Preview)
                DetailsAction("Wróć do wiadomości", onBack, !busy)
            }
            DetailCard {
                BasicText("Kosz ukrywa dotychczasowe wiadomości w Chatrze na 30 dni. Nowe wiadomości pozostają widoczne. Inne aplikacje SMS mogą nadal pokazywać tę historię.", style = ChatraType.Preview)
                DetailsAction("Przenieś do kosza…", { showTrash = true }, !busy && c.organization.editable)
            }

            if (showSim) SimPicker(state.sims, (state.sim as? SimChoice.Ready)?.sim?.id, model::selectSim, { showSim = false })
            if (showMute) ChoiceDialog("Wycisz rozmowę", listOf("Na godzinę", "Na 8 godzin", "Do odwołania"), { choice ->
                val until = when (choice) { 0 -> c.clock.now().plusSeconds(3600).toEpochMilli(); 1 -> c.clock.now().plusSeconds(28800).toEpochMilli(); else -> Long.MAX_VALUE }
                mutate("Nie udało się wyciszyć rozmowy. Spróbuj ponownie.") { c.notifications.update { it.copy(mutedUntil = it.mutedUntil + (id.value to until)) } }
            }, { showMute = false })
            if (showThemes) ChoiceDialog("Wygląd tej rozmowy", listOf("Użyj wyglądu globalnego") + themes.map { it.label }, { choice ->
                mutate("Nie udało się zapisać wyglądu. Spróbuj ponownie.") { c.conversationAppearance?.update { all ->
                    if (choice == 0) all - id.value else all + (id.value to (all[id.value] ?: AppearanceOverride()).copy(composition = themes[choice - 1]))
                } }
            }, { showThemes = false })
            if (showTrash) ChoiceDialog("Przenieść dotychczasowe wiadomości do kosza na 30 dni?", listOf("Przenieś do kosza"), {
                mutate("Nie udało się przenieść wiadomości do kosza. Spróbuj ponownie.") { c.conversations.moveToTrash(id); onTrashed() }
            }, { showTrash = false })
            blockRequest?.let { request ->
                ChoiceDialog(if (request.block) "Zablokować lokalnie ${request.participant.label}? Wiadomości pozostaną na telefonie, a powiadomienia będą wyłączone. Nie blokuje to połączeń ani odbioru u operatora."
                    else "Odblokować ${request.participant.label} w Chatrze?", listOf(if (request.block) "Zablokuj lokalnie" else "Odblokuj"), {
                    mutate("Nie udało się zmienić blokady. Spróbuj ponownie.") {
                        if (request.block) c.organization.block(request.participant.address) else c.organization.unblock(recipientKey(request.participant.address.raw))
                    }
                }, { blockRequest = null })
            }
        }
    }
}

@Composable
private fun DetailsAction(label: String, action: () -> Unit, enabled: Boolean = true) =
    TextAction(label, action, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled)
