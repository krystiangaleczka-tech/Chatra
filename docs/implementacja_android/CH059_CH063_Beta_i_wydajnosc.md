# CH059, CH062 i CH063 — wydajność i odbiór bety offline

## CH059 — historia i galeria

Historia HD obserwuje w Room tylko rekordy otwartej rozmowy. Identyczna emisja po zmianie w innym wątku nie uruchamia ponownego renderowania jego sąsiadów. Odczyt historii używa metadanych pliku i jego rozmiaru. Otwieranie i eksport poprzez galerię wciąż sprawdzają sumę oryginału, a odbiór HD sprawdza ją przed wysłaniem potwierdzenia.

Test odbioru porównuje liczbę odczytów pliku przy wyświetleniu historii i po otwarciu oryginału. Pokazuje uniknięcie pełnego odczytu wszystkich bajtów w pierwszym przypadku. Galeria mierzy czas uzyskania pierwszej strony oraz P95 stron przy 67 i 1000 pozycjach, w tym granicę między HD i MMS. Dane licznikowe i czasy bez treści pochodzą z wierszy `ChatraGalleryQA` w logach instrumentacji.

Limit 16,7 ms czasu klatki, start skrzynki i docelowy pomiar 50 tys. wiadomości dotyczą wybranego fizycznego urządzenia. Czasy emulatora służą diagnostyce i porównywaniu przypadków w tym samym przebiegu. [Cele wydajności](Chatra_Dokumentacja_Android.md)

## CH062 — zweryfikowany APK do bety

`scripts/ch062_build_beta.sh` wymaga czystego commitu oraz SDK z Android Build Tools 36.1.0. W CI podajemy sprzeczną, syntetyczną konfigurację Gradle i jawnie wyłączamy integracje podczas budowy. Skrypt sprawdza podpis i identyfikator pakietu przed przeniesieniem wyniku do `build/beta/`. Pakiet `chatra-offline-beta` zawiera APK debug, sumę SHA-256, SHA źródła, metadane pakietu, publiczny odcisk certyfikatu i notatkę dla testerów.

CI uruchamia `ch062_emulator_checks.sh --verify-beta` po zbudowaniu paczki. Emulator instaluje właśnie ten plik i sprawdza sumę oraz SHA checkoutu. Test odczytuje konfigurację z zainstalowanego APK. Weryfikuje wyłączone HD, backend, FCM i Play Billing, a potem otwiera syntetyczny SMS z systemowej historii po odłączeniu sieci. Certyfikat debug z GitHub Actions służy tej próbie. Aktualizacje w trwałym kanale testerskim wymagają tego samego klucza między buildami. Staging, podpis wydania i dystrybucja wymagają konfiguracji właściciela. [Instrukcja Android](../../android/README.md)

Próba wycofania kodu instaluje kolejno wersje QA 1, 2 i 3 z tym samym certyfikatem debug. Wersja 3 jest odbudowana z poprzedniego commitu `894b034`, dzięki czemu Android przyjmuje ją jako aktualizację. Po instalacji test sprawdza rolę SMS, historię systemową, szkic, kosz i zaplanowane zlecenie z zerem prób wysyłki. Raport zawiera commit, metadane paczki, jej sumę i wynik instalacji. Próba obejmuje ten sam schemat Room. Przed wycofaniem kodu po zmianie schematu trzeba potwierdzić, że poprzedni kod otwiera nową bazę albo przygotować kompatybilną poprawkę z kolejnym numerem wersji.

## CH063 — dowody dla funkcji

| Zakres | Zautomatyzowany dowód | Pozostały odbiór |
| --- | --- | --- |
| Beta lokalna | `BetaInstallSmokeTest` otwiera systemowy SMS offline, wyszukuje go bez polskich znaków i otwiera trafienie w rozmowie | Wysyłka SMS i MMS u operatorów na fizycznych kartach |
| Szczegóły i galeria, CH065–CH066 | `ConversationDetailsTest` oraz `GalleryPagingTest` sprawdzają szczegóły, powrót do źródła, filtry i stronicowanie | Duże pliki u operatorów, TalkBack, dialer |
| Kolaż i wygląd, CH067–CH068 | `AttachmentStackTest`, `AdaptiveConversationTest` i `PreferencesPersistenceTest` | Szybkie przewijanie dużych zdjęć i własne tło na sprzęcie |
| Akcje skrzynki i odczyt, CH069–CH070 | `AdaptiveConversationTest`, `ConversationDetailsTest` oraz `SystemIntegrationTest` | Gesty producentów i powiadomienia przy zmianie roli |
| Harmonogram i dane | `PersistentSchedulesTest` oraz próby restartu i czyszczenia danych | Zablokowany telefon i fizyczny modem |
| Usługi zdalne | Backend i aplikacja mają osobne testy HD z kontrolowanymi danymi | Staging, odbiorca linku, konfiguracja FCM i zakup Pro |
| Zgodność obrazów | Raport S01–S10 i 161 obszarów | Uzgodnienie różnic S04 i S05, renderowanie na fizycznym GPU |

Macierz obejmuje zakres dostępny w chmurze i powiązane bramki odbioru. Odbiór produktu do publicznego wydania zależy od prób fizycznych i ustaleń wizualnych.
