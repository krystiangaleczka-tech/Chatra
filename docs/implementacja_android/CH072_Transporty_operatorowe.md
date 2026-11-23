# CH072 — transporty operatorowe na iOS i Androidzie

Przegląd dokumentacji i SDK wykonano 23.11.2026. Wydanie Chatry zachowuje dotychczasowy transport. Dalszy prototyp ma sens dopiero po uzyskaniu dostępu do urządzeń i po potwierdzeniu ścieżki operatorskiej.

## iPhone i TelephonyMessagingKit

- API jest dostępne od iOS 26 i działa na iPhonie. W aplikacji trzeba włączyć entitlement `com.apple.developer.carrier-messaging-app`. Użytkownik wybiera aplikację jako domyślną dla wiadomości operatora. Produkcyjne użycie wymaga konta zarejestrowanego w UE oraz urządzenia znajdującego się w UE. Profil dostarczany przez Apple pozwala testować poza UE. Domyślna aplikacja do komunikacji internetowej korzysta z osobnego uprawnienia. [Framework](https://developer.apple.com/documentation/telephonymessagingkit) · [Entitlement](https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.carrier-messaging-app)
- `TelephonyMessagingSession.shared.isConfiguredForCarrierMessaging` sprawdza konfigurację. `cellularServices` i zmiany stanu usług pozwalają sprawdzić dostępność dla konkretnej linii. SMS, MMS i RCS mają odrębne API i dostępność zależną od operatora, planu oraz roli. Aplikacja przejmuje obsługę nowych wiadomości operatorowych, także kolejki z okresu, w którym aplikacja była wyłączona. To aplikacja zapisuje te wiadomości w lokalnej bazie. Publiczna dokumentacja nie wskazuje API importu istniejącej historii Wiadomości ani dostępu do iMessage. [Konfiguracja i odbiór](https://developer.apple.com/documentation/telephonymessagingkit/creating-a-carrier-messaging-app) · [Sesja](https://developer.apple.com/documentation/telephonymessagingkit/telephonymessagingsession)

| Operator w Polsce | Potwierdzenie RCS na iPhonie | Próba |
| --- | --- | --- |
| Orange | RCS nie jest wykazany w polskiej sekcji Apple | SMS i MMS dwukierunkowo, sprawdzenie dostępności RCS na danej taryfie |
| Play | RCS nie jest wykazany w polskiej sekcji Apple | Te same pomiary na osobnej karcie Play |
| Plus | RCS nie jest wykazany w polskiej sekcji Apple | Te same pomiary na osobnej karcie Plus |
| T-Mobile | RCS nie jest wykazany w polskiej sekcji Apple | Te same pomiary na osobnej karcie T-Mobile |

Weryfikacja wykazu Apple dotyczy funkcji operatora iPhone’a. Brak wpisu oznacza brak publicznego potwierdzenia w tym wykazie. Wynik dostępności w aplikacji należy rejestrować osobno dla iOS, wersji ustawień operatora, typu SIM, taryfy oraz pracy w roamingu. W CI nie ma iPhone’a, profilu Apple ani fizycznych kart. [Apple, wykaz dla Polski](https://support.apple.com/pl-pl/108048) · [Warunki RCS na iPhonie](https://support.apple.com/en-gb/122195)

## Android 17 i alternatywna wysyłka

- `android.service.messaging.AlternativeMessageTransportService` jest publiczną klasą od API 37. System może powiązać usługę domyślnej aplikacji SMS, gdy przetwarza wysyłkę SMS lub MMS. Deklaracja używa akcji `android.service.messaging.AlternativeMessageTransportService` oraz ograniczenia `android.permission.BIND_ALTERNATIVE_MESSAGE_TRANSPORT_SERVICE`. Callback przyjmuje URI rekordu wiadomości i ma 10 sekund na akceptację lub odmowę. Po akceptacji aplikacja odpowiada za własne dostarczenie i przeniesienie rekordu z OUTBOX do SENT albo FAILED. [Referencja Androida](https://developer.android.com/reference/android/service/messaging/AlternativeMessageTransportService)
- Publiczny kontrakt nie udostępnia rejestracji w Google Messages/Jibe, dostępu do historii RCS Google ani API przychodzącego RCS. Dostępność klasy potwierdza jedynie punkt rozszerzenia wysyłki. Integracja z konkretną siecią operatorską oraz interoperacyjność z iOS wymagają osobnej umowy lub udokumentowanego transportu. [Referencja pakietu](https://developer.android.com/reference/android/service/messaging/package-summary)
- `javap` na `platforms/android-37.0/android.jar` potwierdził klasę, metodę i stałe decyzji. Test `TransportApiTest` sprawdza brak klasy w API 36, obecność sygnatury w API 37 oraz brak aktywnej usługi w Chatrze. Sztuczny test nie wysyła wiadomości i nie ocenia zachowania sieci operatora.

## Ryzyko, koszt i bramki

| Ryzyko | Bramka prototypu | Wstępny koszt |
| --- | --- | --- |
| iOS. Profil, rola, start procesu w tle | Podpisany build na fizycznym iPhonie z iOS ≥26. Zmiana domyślnej aplikacji, odbiór przy zimnym starcie i utrata roli. | 2–3 osobodni po uzyskaniu profilu i sprzętu |
| Polska. Linie i historia | Dwa telefony i kontrolowane konta. Dla czterech operatorów zapisać wynik SMS, MMS i dostępność RCS. Zasiać wiadomości przed zmianą roli i rozliczyć osobno stare oraz nowe. | 3–5 osobodni po przygotowaniu kart |
| Android. Podwójna wysyłka i status | Izolowany prototyp API 37 z atrapą transportu. Sprawdzić odmowę i timeout, zgon procesu po akceptacji, idempotencję oraz dokładnie jedno przejście rekordu OUTBOX. | 2–3 osobodni po dostępie do API 37 |
| RCS. Dostęp i zgodność obu platform | Udokumentowany dostęp operatorski i próba fizyczna z dwiema niezależnymi aplikacjami. W kosztach uwzględnić integrację, testy i utrzymanie dostawcy. | Do wyceny po potwierdzeniu dostępu |

**Decyzja.** Go dla ograniczonego prototypu SMS/MMS na iPhonie po uzyskaniu profilu i urządzeń. Go dla izolowanego testu AMTS z atrapą transportu. No-go dla obietnicy RCS, iMessage, importu historii Apple lub aktywowania AMTS w wydaniu Chatry bez przejścia powyższych bramek.
