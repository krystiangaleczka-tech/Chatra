# CH022–CH026 — historia i rzeczywisty transport SMS

Etap implementacji z 10.11.2026, na bazie CH019–CH021 (PR #11). Akceptacja telefonii na fizycznym urządzeniu pozostaje otwarta.

## Zakres

- **CH022:** moduł `core:database`, DAO i eksport schematu Room v2; migracja v1→v2 oraz zamrożony fixture SQL v1. Provider jest kanoniczną historią: Room przechowuje typowane referencje SMS/MMS, metadane rozmów i relacje części MMS. Odczyt idzie stronami po 200 rekordów; rozmowa ładuje starsze wiadomości na żądanie. Obserwatory SMS, MMS i mms-sms odtwarzają projekcję po zmianach. Sweep następuje dopiero po kompletnym odczycie obu tabel i ponownym sprawdzeniu dostępu. Obraz MMS jest dekodowany leniwie do ograniczonej miniatury.
- **CH023:** chroniony receiver zapisuje PDU atomowo, a zadanie przyspieszone składa dostarczone przez Androida części i zapisuje SMS w providerze. Dziennik przyjęcia deduplikuje powtórzenia i uzgadnia przerwę pomiędzy zapisem providera a potwierdzeniem w Room. Zachowane są nadawca, czas nadania/odbioru i subskrypcja. Odczyt obejmuje pokazane bąbelki w stanie RESUMED; długi bąbelek wymaga pokazania co najmniej połowy obszaru listy. Long press pozwala oznaczyć jako przeczytane/nieprzeczytane i przypiąć. Menu Skrzynki obsługuje wszystkie jako przeczytane. Liczniki i zbiorcze powiadomienie wynikają z projekcji; bez zgody na powiadomienia aplikacja nadal działa.
- **CH024:** aktywne subskrypcje, obserwacja zmian i systemowej domyślnej karty. Pierwszeństwo: szkic → rozmowa → preferencja aplikacji → systemowa domyślna. Jedna dostępna karta nie wymaga dodatkowego pytania. Zapisane ID, które zniknęło, wymaga wyboru; nie przechodzimy po cichu na inną SIM. W szkicu, preferencji i operacji przechowywany jest też opis karty. Wybór w rozmowie, nowym edytorze i formularzu harmonogramu korzysta z tego samego modelu.
- **CH025:** `SmsMessage.calculateLength` i `SmsManager.divideMessage`, z liczbą jednostek ostatniej rzeczywistej części. Licznik rozróżnia GSM i Unicode; poprawia przypadki rozszerzeń GSM oraz granic emoji. Zamrożony, błędny licznik S03 pozostaje wyłącznie w fixture. Podgląd bez polskich znaków wymaga przycisku i pozwala przywrócić oryginał. Jeżeli platforma zmieniłaby tekst podczas dzielenia, wysyłka jest blokowana.
- **CH026:** trwałe operacje, odbiorcy, próby i części; unikalne zadania WorkManager, CAS stanu oraz tożsamość szkicu. Odtwarzanie starych operacji kończy się przed rozpoczęciem nowej dyspozycji. Transport sprawdza bieżącą rolę, zgody i dostępność konkretnego subscriptionId bezpośrednio przed wywołaniem. Callback ma osobny adres, indeks części i losowy token. Własny rekord outbox jest uzgadniany z sent/delivered; samo sent nie oznacza delivered. Przerwana próba pozostaje niepewna i nie jest powtarzana automatycznie.

`full` korzysta z providera i transportu SMS. Demo nadal używa fixture i nie ma uprawnień ani transportu SMS. Repozytorium nie uruchamia automatycznego CI Androida dla tych zmian.

## Testy i próby

**67 uruchomień testów JVM i 18 testów Androida (15 app + 3 baza), bez błędów i pominięć.** Build obu APK i lint obu wariantów przeszły. Wyniki, sumy APK oraz dowody: [katalog QA](CH022_CH026_Android/).

Testy JVM obejmują domenę, fixture, designsystem i modele obu wariantów. Testy Androida obejmują granice ASCII, rozszerzenia GSM, polskie znaki, emoji, SENDTO, 450 rekordów historii i strony 200/400/450, zmianę MMS w providerze, relacje części, błąd providera bez utraty indeksu, deduplikację multipart i rekoncyliację przerwy zapisu. Kolejka jest badana z kontrolowanym transportem: jedno wywołanie przy powtórnym enqueue/dispatch, callbacki poza kolejnością, sent bez delivered, awaria przy granicy API, utrata roli i anulowanie ze szkicem. Osobny test wysyła prawdziwy PendingIntent do receivera i sprawdza zachowanie resultCode przed `goAsync`.

Testy integracyjne zapisujące fikcyjne wiadomości uruchamiają się wyłącznie na emulatorze z rolą SMS i dostępem do SIM. Usuwają własne fixture. Na innym urządzeniu są pomijane, co nie stanowi zaliczenia odbioru.

```bash
cd android
./gradlew :core:model:test :core:testing:test \
  :core:designsystem:testDebugUnitTest :app:testDemoDebugUnitTest :app:testFullDebugUnitTest \
  :app:assembleDemoDebug :app:assembleFullDebug :app:assembleFullDebugAndroidTest \
  :core:database:assembleDebugAndroidTest :app:lintDemoDebug :app:lintFullDebug
# Po przygotowaniu izolowanego emulatora, roli SMS i zgód:
./gradlew :app:connectedFullDebugAndroidTest :core:database:connectedDebugAndroidTest --no-parallel
```

Na komputerze z ograniczoną pamięcią użyto `-Dorg.gradle.jvmargs='-Xmx1g -Dfile.encoding=UTF-8' --max-workers=2`. Gotowe testy uruchomiono również bezpośrednio przez `adb shell am instrument`, aby Gradle nie zajmował pamięci podczas pracy emulatora.

W działającej aplikacji odebrano SMS z konsoli AVD przy nieaktywnym procesie, sprawdzono odczyt i licznik, SENDTO z polskimi znakami, świadomą transliterację i przywrócenie oryginału. Natywny SmsManager wykonał w AVD pojedynczy SMS i dwuczęściowy SMS do numeru portu emulatora `5556`: części mają `Sent`, resultCode `-1`, odrębne raporty `Delivered`, a operacje `DELIVERED`. To test transportu emulowanego, nie sieci operatora. Potwierdzono cofnięcie roli do Messages, Start po ponownym wejściu, ponowne nadanie oraz przywrócenie Messages na końcu.

Próba natywnego adaptera wykryła błędny odczyt resultCode po `goAsync`. Wynik jest teraz przechwytywany przed odłączeniem broadcastu; regresję pokrywa prawdziwy PendingIntent wysłany do receivera w teście Androida. Pierwszy błędnie oznaczony rekord QA zachowano w dowodach; następne próby po poprawce potwierdziły prawidłowe wyniki.

## Granice etapu

Do odbioru pozostają fizyczne SMS/multipart z obu kart i eSIM, brak zasięgu, zmiana SIM i uprawnień podczas prawdziwego połączenia z modemem oraz pełne próby procesu i restartu telefonu. Emulator i kontrolowany transport nie potwierdzają zachowania operatora.

Pełna rekoncyliacja, świadome ponawianie i interfejs decyzji dla prób niepewnych należą do CH027. Most szkiców i krótkiego okna anulowania służy połączeniu z istniejącym UI; nie zamyka CH028 ani trwałego harmonogramu CH035. Odpowiedź RESPOND_VIA_MESSAGE nadal jest bezpiecznie zachowanym szkicem, a nie automatyczną wysyłką. Kontakty, powiadomienia per rozmowa, załączniki i transport MMS mają dalsze zadania CH029–CH032. Nowe MMS są zachowane jako prywatne PDU, bez udawania pobrania treści. Produkcyjny indeks wyszukiwania należy do CH038.

G03, zatwierdzenie projektów G09, odbiór fizyczny oraz pełny TalkBack pozostają otwarte.

Źródła kontraktów: [Telephony](https://developer.android.com/reference/android/provider/Telephony), [SmsManager](https://developer.android.com/reference/android/telephony/SmsManager), [SubscriptionManager](https://developer.android.com/reference/android/telephony/SubscriptionManager), [Room i migracje](https://developer.android.com/training/data-storage/room/migrating-db-versions), [BroadcastReceiver w AOSP](https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/java/android/content/BroadcastReceiver.java).

Uzupełniono też odbiór S10: wszystkie trasy na 320×640 dp i fontScale 1,3 oraz porównanie referencyjne 6,1180%. Renderer emulatora API35 różni się od fizycznego S26 API37; porównanie nie zamyka G03 ani akceptacji 1:1.
