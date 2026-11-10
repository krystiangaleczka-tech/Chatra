package app.chatra.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.chatra.core.designsystem.theme.*
import app.chatra.platform.AccessFeature
import app.chatra.ui.LocalContainer
import app.chatra.ui.common.TextAction
import app.chatra.ui.common.ChoiceDialog
import org.json.JSONObject

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    var files by rememberSaveable { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { files = it.size }
    DetailPage("Prywatność", onBack) {
        DetailCard {
            BasicText("Historia i wyszukiwanie pozostają na telefonie. Dane nie trafiają do chmury przez samo nadanie roli SMS.", style = ChatraType.Body)
            BasicText("Chatra HD jest osobnym, dobrowolnym transferem. W tej wersji nie jest aktywny. Nie obiecujemy RCS, iMessage ani połączeń.", style = ChatraType.Preview)
            if (c.isDemo) BasicText("Demo używa przykładowych rozmów i kart. Nie odczytuje SMS ani kontaktów urządzenia i nie wykonuje zakupów.", style = ChatraType.Preview)
        }
        if (c.platform != null) {
            PermissionCard(AccessFeature.Sms)
            PermissionCard(AccessFeature.Contacts)
            PermissionCard(AccessFeature.Sims)
            PermissionCard(AccessFeature.Notifications)
        }
        DetailCard {
            BasicText("Zdjęcia i filmy", style = chatraText(17f, 600))
            BasicText("Systemowy wybór udostępnia tylko wskazane pliki. Nie prosimy o całą bibliotekę zdjęć.", style = ChatraType.Preview)
            TextAction("Wybierz zdjęcia", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) })
            if (files > 0) BasicText("Wybrano: $files. Ten podgląd nie wysyła plików.", style = ChatraType.Preview)
        }
    }
}

@Composable
fun HelpScreen(onBack: () -> Unit, onPrivacy: () -> Unit, onCatalog: () -> Unit) {
    val c = LocalContainer.current
    DetailPage("Pomoc", onBack) {
        DetailCard {
            BasicText("SMS i MMS są różnymi transportami. Osobne SMS nie ujawniają numerów innych odbiorców. Grupowy MMS pokazuje uczestników i zależy od operatorów.", style = ChatraType.Body)
            BasicText("Harmonogram działa na tym telefonie. Wyłączony telefon, brak SIM i cofnięte zgody mogą wymagać decyzji po powrocie.", style = ChatraType.Preview)
            TextAction("Prywatność i uprawnienia", onPrivacy)
            TextAction("Ustawienia domyślnej aplikacji SMS", c.smsRole::openSystemSettings)
        }
        DetailCard {
            BasicText("Projekty uzupełniające", style = chatraText(17f, 600))
            BasicText("Katalog proponowanych podwidoków i stanów do przeglądu. Nie oznacza gotowych funkcji telefonii ani aktywnych zakupów.", style = ChatraType.Preview)
            TextAction("Otwórz katalog projektów", onCatalog)
        }
    }
}

@Composable
fun DesignCatalogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val designs = remember { JSONObject(context.assets.open("ch020_catalog.json").bufferedReader().use { it.readText() }).getJSONArray("designs") }
    var index by rememberSaveable { mutableIntStateOf(-1) }
    var state by rememberSaveable { mutableStateOf("ready") }
    var picker by remember { mutableStateOf(false) }
    var action by rememberSaveable { mutableStateOf<String?>(null) }
    val states = listOf("ready", "empty", "loading", "error", "disabled", "permission", "confirmation")
    val names = listOf("Gotowy", "Pusty", "Wczytywanie", "Błąd", "Niedostępny", "Brak zgody", "Potwierdzenie")
    BackHandler(enabled = index >= 0) { index = -1; action = null }
    DetailPage(if (index < 0) "Projekty uzupełniające" else designs.getJSONObject(index).getString("title"), { if (index < 0) onBack() else { index = -1; action = null } }) {
        BasicText("Propozycje do akceptacji. To dodatkowe widoki względem wzorców S01–S10.", style = ChatraType.Preview)
        if (index < 0) {
            for (i in 0 until designs.length()) DetailCard {
                val item = designs.getJSONObject(i)
                TextAction(item.getString("title"), { index = i; state = "ready"; action = null })
                BasicText(item.getString("purpose"), style = ChatraType.Preview)
            }
        } else {
            val item = designs.getJSONObject(index)
            DetailCard {
                BasicText(item.getString("purpose"), style = ChatraType.Body)
                TextAction("Stan: ${names[states.indexOf(state)]}", { picker = true })
                BasicText(item.getJSONObject("states").getString(state), style = ChatraType.Preview)
                val actions = item.getJSONArray("actions")
                for (i in 0 until actions.length()) TextAction(actions.getString(i), { action = "Przykład: ${actions.getString(i)}. Powrót zachowuje dane projektu." }, enabled = state in listOf("ready", "confirmation"))
                if (state == "error") TextAction("Spróbuj ponownie", { state = "ready"; action = null })
                TextAction("Wróć do katalogu", { index = -1; action = null })
                action?.let { BasicText(it, style = ChatraType.Preview) }
            }
            DetailCard { BasicText("Fokus: tytuł → opis → pola i lista → potwierdzenie → Anuluj. Back zachowuje szkic i przywraca fokus otwierającemu elementowi.", style = ChatraType.Preview) }
        }
    }
    if (picker) ChoiceDialog("Stan projektu", names, { state = states[it]; action = null }, { picker = false })
}
