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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    val notices by c.notifications.value.collectAsStateWithLifecycle()
    val notificationScope = rememberCoroutineScope()
    var files by rememberSaveable { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { files = it.size }
    DetailPage("Prywatność", onBack) {
        DetailCard {
            BasicText("Historia i wyszukiwanie pozostają na telefonie. Dane nie trafiają do chmury przez samo nadanie roli SMS.", style = ChatraType.Body)
            BasicText("Chatra HD jest dobrowolną usługą wymagającą aktywacji. SMS i MMS działają niezależnie.", style = ChatraType.Preview)
            if (c.isDemo) BasicText("Demo używa przykładowych rozmów i kart. Nie odczytuje SMS ani kontaktów urządzenia i nie wykonuje zakupów.", style = ChatraType.Preview)
        }
        DetailCard {
            BasicText("Dane na telefonie", style = chatraText(17f, 600))
            BasicText("SMS i MMS zapisujemy i odczytujemy w systemowej historii telefonu. Lokalna baza Chatry zawiera referencje, szkice, kolejkę wysyłania, harmonogram, blokady oraz kosz. Wyszukiwanie przechowuje lokalną, odtwarzalną kopię treści i metadanych załączników.", style = ChatraType.Preview)
            BasicText("Wybrane pliki mają prywatne kopie w pamięci aplikacji. Kopia bez lokalizacji powstaje przez konwersję zdjęcia do JPEG bez metadanych; oryginał pozostaje zachowany. Udostępnienie pliku innej aplikacji następuje dopiero po Twojej akcji.", style = ChatraType.Preview)
            BasicText("Kopie zapasowe danych aplikacji w chmurze i systemowy transfer danych aplikacji są wyłączone. Inne aplikacje i usługi telefonu mogą niezależnie kopiować systemową historię SMS/MMS. SMS/MMS nie zapewniają szyfrowania od końca do końca przez Chatrę.", style = ChatraType.Preview)
        }
        DetailCard {
            BasicText("Usuwanie danych", style = chatraText(17f, 600))
            BasicText("Kosz ukrywa wyłącznie zapisany zakres wiadomości w Chatrze przez 30 dni. Nowy SMS w tym samym wątku pozostaje widoczny. Trwałe usunięcie z kosza usuwa zapisany zakres z systemowej historii, także dla innych klientów SMS. Nie usuwa kopii u odbiorcy ani operatora.", style = ChatraType.Preview)
            BasicText("Wyczyść dane aplikacji lub odinstaluj Chatrę w ustawieniach Androida, aby usunąć lokalne szkice, pliki, indeks, ustawienia i harmonogram. Systemowa historia SMS/MMS pozostaje. Zrób to po sprawdzeniu aktywnych wysyłek: wiadomości przekazanej operatorowi nie można cofnąć. Usunięcie danych aplikacji usuwa także możliwość odzyskania lokalnego kosza; wiadomości nadal obecne w historii systemowej pojawią się przy kolejnym imporcie.", style = ChatraType.Preview)
            if (c.platform != null) TextAction("Otwórz ustawienia aplikacji", c.platform!!::openSettings)
        }
        DetailCard {
            BasicText("Sieć, HD i diagnostyka", style = chatraText(17f, 600))
            BasicText("SMS/MMS korzystają z usług operatora i mogą podlegać opłatom.", style = ChatraType.Preview)
            if (c.hd != null) BasicText("W pilocie HD Twilio otrzymuje Twój numer na potrzeby weryfikacji. Usługa przechowuje numer routingu w postaci zaszyfrowanej do wygaśnięcia wiązania po 90 dniach. Otrzymuje wybrany numer podczas sprawdzania zgodności HD oraz wskazane pliki. Numer odbiorcy linku pozostaje w telefonie. Upload wygasa po 24 godzinach, gotowe pliki po 7 dniach, a link po 24 godzinach. Link daje dostęp każdemu, kto go posiada. Możesz odwołać dostęp i usunąć pliki na serwerze. Odpięcie usuwa dane instalacji. Zdarzenia synchronizacji i bezosobowe liczniki audytu są przechowywane do 30 dni. FCM przekazuje tylko sygnał synchronizacji. Połączenie jest szyfrowane, a serwer ma dostęp do plików; HD nie zapewnia szyfrowania od końca do końca. Pobrane kopie pozostają u odbiorcy.", style = ChatraType.Preview)
            if (c.billing != null) BasicText("Google Play obsługuje płatność. Serwer Chatry przekazuje token zakupu do Google w celu weryfikacji. Zakup nie wymaga aktywacji HD.", style = ChatraType.Preview)
            BasicText("Aplikacja nie ma automatycznego wysyłania raportów ani analityki treści. Diagnostyka lokalna obejmuje stany transportu i błędy techniczne; system Android i operator mają własne logi. Przed dobrowolnym przekazaniem raportu sprawdź, czy nie zawiera prywatnych danych.", style = ChatraType.Preview)
        }
        c.mmsDownloads?.let { downloads ->
            val pending by downloads.pending.collectAsStateWithLifecycle()
            var downloadError by remember { mutableStateOf<String?>(null) }
            if (pending.isNotEmpty()) DetailCard {
                BasicText("MMS do pobrania: ${pending.size}", style = chatraText(17f, 600))
                pending.forEach { item ->
                    BasicText(item.description, style = ChatraType.Preview)
                    TextAction("Ponów pobieranie", { notificationScope.launch { try { downloads.retryDownload(item.id); downloadError = null } catch (e: Exception) { downloadError = e.message ?: "Nie udało się ponowić pobierania." } } })
                }
                downloadError?.let { BasicText(it, style = ChatraType.Preview) }
            }
        }
        DetailCard {
            BasicText("Podgląd powiadomień", style = chatraText(17f, 600))
            BasicText(if (notices.previews) "Podgląd tekstu po odblokowaniu. Ekran blokady pokazuje neutralną treść." else "Treść wiadomości jest ukryta w powiadomieniach.", style = ChatraType.Preview)
            TextAction(if (notices.previews) "Ukryj podglądy" else "Pokaż podglądy po odblokowaniu", { notificationScope.launch { c.notifications.update { it.copy(previews = !it.previews) } } })
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
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var linkError by remember { mutableStateOf(false) }
    DetailPage("Pomoc", onBack) {
        DetailCard {
            BasicText("SMS i MMS są różnymi transportami. Osobne SMS nie ujawniają numerów innych odbiorców. Grupowy MMS pokazuje uczestników i zależy od operatorów.", style = ChatraType.Body)
            BasicText("RCS i iMessage nie są obsługiwane. Wiadomość do iPhone jest SMS lub MMS zależnie od wybranego transportu. Nie ma gwarancji potwierdzenia odczytu.", style = ChatraType.Preview)
            BasicText("Harmonogram działa na tym telefonie i potrzebuje dostępu Alarmy i przypomnienia. Wyłączony lub zatrzymany przez system telefon nie wyśle wiadomości. Po powrocie pokażemy zaległe zlecenia zgodnie z wybraną polityką.", style = ChatraType.Preview)
            BasicText("Po utracie wybranej SIM nie przełączamy wysyłki na inną kartę. Zmień kartę świadomie. Zmiana opóźnienia dotyczy nowych zleceń; nie skraca już działającego odliczania ani harmonogramu. Niepewny wynik wysyłania wymaga sprawdzenia rozmowy przed ponowieniem.", style = ChatraType.Preview)
            BasicText("Nieznany numer oznacza brak rozpoznanego kontaktu, nie spam. Blokada lokalna ukrywa rozmowę i jej powiadomienia, ale zachowuje SMS/MMS i nie blokuje połączeń. Odblokowanie w ustawieniach przywraca widoczność.", style = ChatraType.Preview)
            BasicText("Kontakt do pomocy nie został jeszcze opublikowany przez właściciela aplikacji. Nie wysyłamy raportu automatycznie.", style = ChatraType.Preview)
            TextAction("Prywatność i uprawnienia", onPrivacy)
            TextAction("Ustawienia domyślnej aplikacji SMS", c.smsRole::openSystemSettings)
            TextAction("Uprawnienia Androida — otwórz pomoc Google", {
                linkError = runCatching { uriHandler.openUri("https://support.google.com/android/answer/9431959?hl=pl") }.isFailure
            })
            BasicText("Link otwiera zewnętrzną przeglądarkę. Nie dołączamy treści rozmów ani raportu.", style = ChatraType.Preview)
            if (linkError) BasicText("Nie udało się otworzyć przeglądarki. Instrukcje wyżej są dostępne offline.", style = ChatraType.Preview)
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
