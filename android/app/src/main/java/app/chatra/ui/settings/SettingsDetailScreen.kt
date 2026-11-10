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
        when (kind) {
            Route.SettingsDetail.Kind.Sims -> {
                PermissionCard(AccessFeature.Sims)
                DetailCard {
                    BasicText("Wybór zapisany w szkicu ma pierwszeństwo. Po utracie karty poprosimy o ponowny wybór.", style = ChatraType.Preview)
                    if (sims.none { it.available }) BasicText("Brak dostępnych kart SIM.", style = ChatraType.Body)
                    else {
                        ChoiceRow("Pytaj przy wysyłaniu", prefs.defaultSubscriptionId == null) { scope.launch { c.sending.update { it.copy(defaultSubscriptionId = null) } } }
                        sims.filter { it.available }.forEach { card -> ChoiceRow(card.label, prefs.defaultSubscriptionId == card.id) { scope.launch { c.sending.update { it.copy(defaultSubscriptionId = card.id) } } } }
                    }
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
                BasicText("Przy odbiorcy bez Chatry zapytamy o link; bez Twojej zgody nie zmienimy transportu.", style = ChatraType.Preview)
            }
            Route.SettingsDetail.Kind.Unknown -> DetailCard {
                BasicText("Nieznany numer pozostaje w Skrzynce z oznaczeniem. Brak kontaktu nie oznacza spamu. Nie blokujemy ani nie usuwamy takich wiadomości automatycznie.", style = ChatraType.Body)
                TextAction("Przejdź do Skrzynki", onInbox)
            }
            Route.SettingsDetail.Kind.Blocked -> DetailCard {
                BasicText(if (c.isDemo) "Przykładowa lista demo. Przywracanie zmienia tylko dane demonstracyjne." else "Lokalne blokady będą dostępne w kolejnej wersji. Nie blokujemy połączeń telefonicznych.", style = ChatraType.Preview)
                if (blocked.isEmpty()) BasicText("Brak zablokowanych numerów.", style = ChatraType.Body)
                blocked.forEach { row ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        BasicText(row.label, Modifier.weight(1f), style = ChatraType.Body)
                        if (c.organization.editable) TextAction("Odblokuj", { c.organization.unblock(row.id) })
                    }
                }
            }
            Route.SettingsDetail.Kind.Trash -> DetailCard {
                BasicText(if (c.isDemo) "Przykładowe rozmowy w koszu demo. Odzyskanie zmienia licznik. Nie usuwamy danych z telefonu." else "Odzyskiwanie i retencja kosza nie są jeszcze dostępne w tej wersji.", style = ChatraType.Preview)
                if (trash.isEmpty()) BasicText("Kosz jest pusty.", style = ChatraType.Body)
                trash.forEach { row ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        BasicText(row.title, Modifier.weight(1f), style = ChatraType.Body)
                        if (c.organization.editable) TextAction("Odzyskaj", { c.organization.restore(row.id) })
                    }
                }
                BasicText("Docelowa retencja: 30 dni. Nowa wiadomość od ukrytego nadawcy nie będzie usuwana.", style = ChatraType.Preview)
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
