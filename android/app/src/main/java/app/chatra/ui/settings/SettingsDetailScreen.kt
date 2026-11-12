package app.chatra.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chatra.core.designsystem.background.Backgrounds
import app.chatra.core.designsystem.component.*
import app.chatra.core.designsystem.icon.ChatraIcons
import app.chatra.core.designsystem.theme.*
import app.chatra.core.model.*
import app.chatra.navigation.Route
import app.chatra.platform.*
import app.chatra.ui.*
import app.chatra.ui.common.*
import kotlinx.coroutines.launch

@Composable
fun DetailPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val chrome = LocalChrome.current
    ChatraScreen(Backgrounds.Settings) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = chrome.top(50.dp), bottom = chrome.bottom(24.dp)), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                GlassCircleButton(ChatraIcons.ChevronLeft, "Wróć", onBack)
                BasicText(title, Modifier.weight(1f).semantics { heading() }, style = chatraText(25f, 700))
            }
            content()
        }
    }
}

@Composable
fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(GlassStyles.Card, RoundedCornerShape(24.dp), Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun SettingsDetailScreen(kind: Route.SettingsDetail.Kind, onBack: () -> Unit, onInbox: () -> Unit) {
    val c = LocalContainer.current
    val prefs by c.sending.value.collectAsStateWithLifecycle()
    val sims by c.sims.cards().collectAsStateWithLifecycle(emptyList())
    val blocked by c.organization.blocked.collectAsStateWithLifecycle()
    val trash by c.organization.trash.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<app.chatra.di.TrashPreview?>(null) }
    var address by remember { mutableStateOf("") }
    fun action(block: suspend () -> Unit) { scope.launch { try { block(); error = null } catch (e: Exception) { error = e.message ?: "Operacja nie powiodła się. Spróbuj ponownie." } } }
    confirmation?.let { row -> ChoiceDialog("Trwale usunąć wiadomości z kosza? Tej operacji nie można cofnąć.", listOf("Usuń trwale"), { action { c.organization.purge(row.entryId) } }, { confirmation = null }) }
    val title = when (kind) {
        Route.SettingsDetail.Kind.Sims -> "Karty SIM"
        Route.SettingsDetail.Kind.Delay -> "Opóźnienie wysłania"
        Route.SettingsDetail.Kind.Hd -> "Chatra HD"
        Route.SettingsDetail.Kind.Unknown -> "Nieznani nadawcy"
        Route.SettingsDetail.Kind.Blocked -> "Zablokowani"
        Route.SettingsDetail.Kind.Trash -> "Kosz"
        Route.SettingsDetail.Kind.Pro -> "Chatra Pro"
    }
    DetailPage(title, onBack) {
        error?.let { BasicText(it, style = chatraText(14f, color = ChatraColors.ErrorText)) }
        when (kind) {
            Route.SettingsDetail.Kind.Sims -> {
                PermissionCard(AccessFeature.Sims)
                DetailCard {
                    BasicText("Wybór zapisany w szkicu ma pierwszeństwo. Po utracie karty poprosimy o ponowny wybór.", style = ChatraType.Preview)
                    if (sims.none { it.available }) BasicText("Brak dostępnych kart SIM.", style = ChatraType.Body)
                    else {
                        ChoiceRow(if (c.isDemo) "Pytaj przy wysyłaniu" else "Użyj domyślnej karty systemu", prefs.defaultSubscriptionId == null) { scope.launch { c.sending.update { it.copy(defaultSubscriptionId = null, defaultSubscriptionLabel = null) } } }
                        sims.filter { it.available }.forEach { card -> ChoiceRow(card.label, prefs.defaultSubscriptionId == card.id) { scope.launch { c.sending.update { it.copy(defaultSubscriptionId = card.id, defaultSubscriptionLabel = card.label) } } } }
                    }
                    if (prefs.defaultSubscriptionId != null && sims.none { it.id == prefs.defaultSubscriptionId }) BasicText("Poprzednia karta: ${prefs.defaultSubscriptionLabel ?: prefs.defaultSubscriptionId?.value}. Wybierz dostępną kartę ponownie.", style = ChatraType.Preview)
                }
            }
            Route.SettingsDetail.Kind.Delay -> DetailCard {
                BasicText("Po tym czasie zlecenie przechodzi do wysyłania. Wiadomości przekazanej operatorowi nie można cofnąć.", style = ChatraType.Preview)
                SendingPrefs.UNDO_OPTIONS.forEach { seconds -> ChoiceRow(if (seconds == 0) "Bez opóźnienia" else "$seconds s", prefs.undoSeconds == seconds) { scope.launch { c.sending.update { it.copy(undoSeconds = seconds) } } } }
            }
            Route.SettingsDetail.Kind.Hd -> DetailCard {
                BasicText("Chatra HD wymaga osobnej aktywacji i serwera. W tej wersji nie wysyła plików przez internet. Wybór poniżej ustala domyślny wariant formularza.", style = ChatraType.Preview)
                listOf("Oryginał HD", "Kopia bez lokalizacji", "MMS").forEachIndexed { i, label ->
                    val variant = listOf(MediaVariant.OriginalHd, MediaVariant.NoLocationCopy, MediaVariant.Mms)[i]
                    ChoiceRow(label, prefs.hdDefault == variant) { scope.launch { c.sending.update { it.copy(hdDefault = variant) } } }
                }
                BasicText("HD i wysyłka linku są obecnie niedostępne. Ta preferencja nie jest zgodą na przesłanie pliku ani wysłanie linku.", style = ChatraType.Preview)
                ChoiceRow("Proponuj pytanie o link", prefs.askBeforeLink) { scope.launch { c.sending.update { it.copy(askBeforeLink = true) } } }
                ChoiceRow("Nie proponuj linku", !prefs.askBeforeLink) { scope.launch { c.sending.update { it.copy(askBeforeLink = false) } } }
            }
            Route.SettingsDetail.Kind.Unknown -> DetailCard {
                BasicText("Nieznany numer pozostaje w Skrzynce z oznaczeniem. Brak kontaktu nie oznacza spamu. Nie blokujemy ani nie usuwamy takich wiadomości automatycznie.", style = ChatraType.Body)
                TextAction("Przejdź do Skrzynki", onInbox)
            }
            Route.SettingsDetail.Kind.Blocked -> DetailCard {
                BasicText("Blokada działa tylko w Chatrze: ukrywa rozmowy ze Skrzynki i wyłącza ich powiadomienia. Historia pozostaje na telefonie. Nie blokuje połączeń ani odbioru SMS u operatora.", style = ChatraType.Preview)
                if (c.organization.editable && !c.isDemo) {
                    androidx.compose.foundation.text.BasicTextField(address, { address = it }, Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = "Numer lub nazwa nadawcy do zablokowania" }, textStyle = ChatraType.Body,
                        decorationBox = { inner -> Column { if (address.isEmpty()) BasicText("Numer lub nazwa nadawcy", style = ChatraType.Preview); inner() } })
                    TextAction("Zablokuj lokalnie", { action { c.organization.block(Address(address.trim())); address = "" } }, enabled = address.isNotBlank())
                }
                if (blocked.isEmpty()) BasicText("Brak zablokowanych numerów.", style = ChatraType.Body)
                blocked.forEach { row ->
                    BasicText(row.label, style = ChatraType.Body)
                    if (c.organization.editable) TextAction("Odblokuj ${row.label}", { action { c.organization.unblock(row.id) } })
                }
            }
            Route.SettingsDetail.Kind.Trash -> DetailCard {
                BasicText(if (c.isDemo) "Kosz demonstracyjny." else "Kosz ukrywa wybrane wiadomości tylko w Chatrze. Inne aplikacje SMS mogą je nadal pokazywać. Odzyskasz je przez 30 dni. Potem usuniemy ten zakres z historii systemowej przy odzyskaniu dostępu. Nowe wiadomości pozostają w Skrzynce.", style = ChatraType.Preview)
                if (trash.isEmpty()) BasicText("Kosz jest pusty.", style = ChatraType.Body)
                trash.forEach { row ->
                    BasicText(row.title, style = ChatraType.Body)
                    row.expiresAt?.let { at -> BasicText(if (row.purging) "Usuwanie rozpoczęte — ponów po błędzie lub utracie dostępu." else "Odzyskiwanie do: ${java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}", style = ChatraType.Preview) }
                    if (c.organization.editable) {
                        TextAction("Odzyskaj", { action { c.organization.restoreEntry(row.entryId) } }, enabled = !row.purging)
                        if (!c.isDemo) TextAction("Usuń trwale…", { confirmation = row })
                    }
                }
            }
            Route.SettingsDetail.Kind.Pro -> DetailCard {
                BasicText(proSummary(c.proOffer, c.isDemo), style = ChatraType.Body)
                BasicText("SMS/MMS, wyszukiwanie, harmonogram i dostępność pozostają darmowe. Katalog dodatków Pro i cena wymagają potwierdzonej oferty sklepu.", style = ChatraType.Preview)
                BasicText(if (c.isDemo) "W demo nie wykonujemy zakupów." else "Zakup i przywrócenie zakupu są teraz niedostępne.", style = ChatraType.Preview)
                TextAction("Wróć do ustawień", onBack)
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Radio(selected)
        BasicText(label, style = ChatraType.Body)
    }
}

@Composable
fun PermissionCard(feature: AccessFeature) {
    val platform = LocalContainer.current.platform ?: return
    val changes by platform.changes.collectAsStateWithLifecycle()
    val allowed = remember(changes, feature) { platform.allowed(feature) }
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<AccessResult?>(null) }
    DetailCard {
        BasicText(feature.title, style = chatraText(17f, 600))
        BasicText(feature.explanation, style = ChatraType.Preview)
        BasicText(if (allowed) "Dostęp przyznany" else "Dostęp nieprzyznany", style = ChatraType.Preview)
        if (!allowed) TextAction(if (result == AccessResult.SettingsRequired) "Otwórz ustawienia aplikacji" else "Zezwól", {
            if (result == AccessResult.SettingsRequired) platform.openSettings() else scope.launch { result = platform.request(feature) }
        })
        if (result == AccessResult.Denied) BasicText("Możesz korzystać z pozostałych funkcji i wrócić do tej zgody później.", style = ChatraType.Preview)
        if (result == AccessResult.RoleRequired) BasicText("Najpierw ustaw Chatrę jako domyślną aplikację SMS.", style = ChatraType.Preview)
    }
}

@Composable
fun PermissionPrompt(feature: AccessFeature) {
    val platform = LocalContainer.current.platform ?: return
    val revision by platform.changes.collectAsStateWithLifecycle()
    if (!remember(revision) { platform.allowed(feature) }) PermissionCard(feature)
}
