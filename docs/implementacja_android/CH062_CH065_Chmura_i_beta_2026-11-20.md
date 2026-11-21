# Chatra — prace w chmurze i beta, 20.11.2026

## Źródło i zakres

Punktem wyjścia jest `main` (`efc7016`, CH045–CH054). Do gałęzi roboczej
`codex/cloud-beta-readiness` włączono istniejące commity `99d8115` i `494809d`
z `codex/ch056-ch065`: optymalizację skrzynki i raport pomiarów Galaxy A15.
Ich nazwa gałęzi nie jest dowodem ukończenia CH056–CH065.

Ten etap obejmuje poprawkę CI, panel szczegółów rozmowy CH065, ochronę odczytu
zasłoniętych wiadomości i odtwarzalny pakiet beta bez usług zdalnych.
Nie zamyka odbioru telefonii, bezpieczeństwa HD ani całej bramki wydania.

## CH062 — CI i pakiet testowy

- `setup-android@v3` domyślnie instalował `tools platform-tools`. Pakiet `tools`
  nie występuje już w repozytorium SDK; to zatrzymywało instalację przed Gradle.
  Workflow instaluje teraz jawnie `platform-tools` oraz `platforms;android-37.0`.
  Workflow uruchamia się przy zmianach w PR i po pushu do `main`.
- Pipeline obejmuje domenę, fixture, design system, oba warianty aplikacji, oba
  linty, migracje Room oraz instrumentację full i nowe testy UI demo.
- Raporty i APK są artefaktami GitHub Actions. Zrzut panelu z testu jest pobierany
  z emulatora do `android/app/build/reports/ui/`.
- `scripts/ch062_emulator_checks.sh` wymaga emulatora i uruchamia full oraz demo
  w osobnych komendach Gradle. Test demo przesyła PNG do katalogu tymczasowego
  ADB. Skrypt pobiera obraz po teście, sprawdza obecność pliku i usuwa kopię
  tymczasową z emulatora.
- `scripts/ch062_build_beta.sh` buduje pełny APK debug z jawnym wyłączeniem HD,
  backendu, FCM i Play Billing, niezależnie od lokalnych właściwości Gradle.
  Wymaga czystego drzewa Git; zapisuje APK, SHA-256, SHA źródła i notatkę testową
  w ignorowanym `build/beta/`.
- Brak projektu Firebase, listy testerów i poświadczeń w tym środowisku.
  Nie wykonano dystrybucji, wdrożenia serwera ani zmiany kont dostawców.
  APK debug korzysta z lokalnego klucza debug; do kolejnych aktualizacji trzeba
  zachować ten sam podpis. Wydanie Google Play wymaga osobnego podpisu.

Budowanie lokalne: wymagania są w `android/README.md`. W katalogu głównym:

```sh
bash scripts/ch062_build_beta.sh
(cd build/beta && sha256sum -c SHA256SUMS)
```

App Distribution jest kanałem testowym. Po skonfigurowaniu własnego projektu,
aplikacji Android o właściwym package ID, grupy testerów i uwierzytelnienia CLI
można przekazać APK przez konsolę Firebase albo `firebase appdistribution:distribute`.
Nie trzeba włączać HD, Analytics ani płatnego magazynu plików.
Publiczne wydanie, staging, podpisywanie, odtwarzanie i rotacja kluczy nadal
wymagają odrębnego odbioru CH062/CH064 oraz G06–G10.

## CH065 — szczegóły kontaktu i grupy

Dotknięcie nagłówka lub przycisku informacji w S03 otwiera osobny panel w języku
Liquid Glass. S03 pozostaje zamontowane pod spodem; nie tworzymy nowego edytora
ani kopii szkicu. Trasa panelu jest zapisywana przez `Navigator.Saver`.

Panel pokazuje wszystkich uczestników i ich rzeczywiste adresy, w tym numer
niezapisany w kontaktach i nadawcę alfanumerycznego. Dostępne są:

- `ACTION_DIAL` dla poprawnych numerów, z widocznym błędem braku dialera;
- kopiowanie adresu, wyszukiwanie w konkretnej rozmowie i harmonogram;
- przypięcie i odpięcie, stan wyciszenia, 1 h / 8 h / do odwołania i odciszenie;
- wybór SIM przez istniejący model szkicu i lokalna kompozycja z resetem;
- osobna, potwierdzana blokada uczestnika i potwierdzany kosz.

Mutacje mają blokadę ponownego kliknięcia i komunikat błędu; anulowanie blokady
lub kosza nie modyfikuje danych. Wyciszenie, blokada i kosz opisują odrębne skutki.
Brak galerii CH066 jest jawnie wyjaśniony; panel nie pokazuje fikcyjnej liczby
plików ani przycisku udającego działającą galerię. Pełna personalizacja CH068,
pierwsza nieprzeczytana CH070 i fizyczny odbiór TalkBack nadal pozostają otwarte.

## Odczyt wiadomości pod panelem

`ConversationScreen` dostaje informację, czy historię zasłania jakikolwiek arkusz.
Gdy jest zasłonięta, obserwacja widocznych wierszy nie przekazuje `markShown`.
Po zamknięciu panelu odczyt jest ponownie rozliczany na podstawie rzeczywiście
widocznych wiadomości. Nie zmienia to statusów transportu ani receipt pobrania HD.

## Stan następnych zadań

| Zadanie | Stan po tym etapie | Pozostały zakres |
| --- | --- | --- |
| CH055 | Otwarty odbiór | S01–S10 oraz nowe podwidoki, G03/G09 |
| CH056–CH058 | Otwarty odbiór urządzeń i dostawców | Operatorzy, dual SIM, awarie, OTP/FCM, dwa telefony, Safari |
| CH059 | Częściowo wykonane | A15 ma pomiary skrzynki; pełne media/HD i macierz urządzeń nadal otwarte |
| CH060–CH061 | Otwarty odbiór | TalkBack, duży font, backup, odzyskanie i sprzęt |
| CH062 | Częściowo wykonane | Staging, stabilny podpis, Firebase, klucze, rollback i przekazanie |
| CH063–CH064 | Otwarte | Odbiór produktu i dystrybucja po zamknięciu zależności |
| CH065 | Panel zaimplementowany, odbiór otwarty | Galeria CH066 i fizyczny dialer/rola/TalkBack |
| CH066 | Adapter danych istnieje | Brak pełnej galerii, zakładek i odbioru 67/1000 plików |
| CH067 | Częściowo wykonane | Karta HD ma miniatury; pełny zakres MMS/HD i podgląd do odbioru |
| CH068 | Częściowo wykonane | Kompozycja/reset działają; niezależne tło, akcent i dymki otwarte |
| CH069 | Częściowo wykonane | Menu przytrzymania istnieje; gesty, cofanie i pełne akcje otwarte |
| CH070 | Częściowo wykonane | markUnread/markAllRead/kotwica działają; pierwsza nieprzeczytana i pełna regresja otwarte |

To nie jest 54 ukończonych zadań z 70. Wcześniejsze implementacje mają otwarte
bramki odbioru. CH071–CH072 pozostają opcjonalne i nie blokują pierwszego wydania.

## Ruch i środowisko produkcyjne

API statystyk GitHub `traffic/views` i `traffic/clones` zwróciło 403
`Resource not accessible by integration`. Liczby odwiedzin i klonów są nieznane.
W środowisku nie ma konfiguracji aktywnej usługi HD ani dostępu do metryk Firebase.
Nie utożsamiamy braku dostępu do metryk z zerowym ruchem lub zerową liczbą użytkowników.
Nie dodano zbierania treści wiadomości, kontaktów ani automatycznej analityki.

## Weryfikacja

Źródło APK: `2d9c97764569151db25463c9aabba75ea3de7fcc`.
Przebieg weryfikacji funkcji sprawdził `c81d7d03996be87af5284d0a8fe8ab82edc0a6b3`.
Późniejsze commity dotyczą eksportu zrzutu oraz dokumentacji.
[Wyniki maszynowe](CH062_CH065_Cloud/verification.json).

| Sprawdzenie lokalne | Wynik |
| --- | --- |
| Backend / OpenAPI | 18 testów PASS; specyfikacja zgodna z trasami |
| JVM | 93 testy, 0 błędów i pominięć |
| Kompilacja | APK demo/full oraz pakiet trzech testów UI demo |
| Lint demo/full | 0 błędów; odpowiednio 27 i 61 ostrzeżeń |
| Beta | Podpis debug poprawny; minSdk 31 / targetSdk 36 |
| Wyłączenie usług | Backend/HD/Pro wyłączone mimo przeciwnych wartości w środowisku; konfiguracja FCM pusta |
| Manifest bety | FCM auto-init i Analytics collection=false |
| Integralność APK | SHA-256 `533e6fa5a1fe719a1fea55c3f53c6164ed7a3ffb7b7ea549883a2e1af12f3a77` |

Skrypt bety sprawdzono z przeciwnymi wartościami `ORG_GRADLE_PROJECT_chatraBackendOrigin`,
`ORG_GRADLE_PROJECT_chatraHdPilot` i `ORG_GRADLE_PROJECT_chatraPlayProduct`;
jawne właściwości skryptu wygrały. Błąd preferowanej SIM ma test zachowania szkicu
i usunięcia błędu po ponownym wyborze. Lokalna maszyna nie ma `/dev/kvm`;
instrumentację wykonuje emulator GitHub Actions.

W przebiegu `44823602509` zaliczono **93 testy JVM oraz 81 testów Androida,
9 scenariuszy sprzętowych pominiętych, bez błędów** (Room 4/4, full 74 PASS + 9 skip,
UI demo 3/3). [Pierwsza poprawka CI, `9d00eda`](https://github.com/krystiangaleczka-tech/Chatra/actions/runs/44817784843)
przeszła buildy, lint i instrumentację full/Room. Nie zawierała jeszcze nowego panelu.

Przebieg `44821075023` sprawdził nowy kod: 93 JVM, Room 4/4 i pełną aplikację
83 przypadki (74 PASS, 9 scenariuszy sprzętowych pominiętych). UI demo zaliczyło
2/3. Końcowe oczekiwanie testu odczytu nie uwzględniało zachowania pozycji historii;
nowy element nie musi być widoczny od razu po zamknięciu panelu. Skorygowany test
wstawia chronologicznie nową wiadomość, sprawdza brak jej odczytu pod panelem,
jawnie przewija do jej treści i nadal wymaga odczytu konkretnego `MessageRef`.
Nie usunięto asercji. Dodatkowo odseparowano full/demo, które Gradle uruchamiał
równolegle na tym samym emulatorze. Końcowa próba tych zmian: [44823602509](https://github.com/krystiangaleczka-tech/Chatra/actions/runs/44823602509).

Testy UI demo obejmują szkic, przypięcie, wyciszenie, odczyt nowej wiadomości
pod panelem, nadawcę alfanumerycznego, uczestników grupy i anulowanie blokady/kosza.
Używają wyłącznie syntetycznych fixture, bez prawdziwych odbiorców i sieci.
Fizyczny dialer, odmowy roli, odsłuch TalkBack i pełny duży font pozostają do odbioru.

UTP usuwa dane demo przy odinstalowaniu APK. Próba zapisania zrzutu do katalogu
pobierania w przebiegu `44825686035` też się nie powiodła, ponieważ UiAutomation
uruchamia polecenia bez interpretowania operatorów powłoki. Test przekazuje teraz
PNG przez standardowe wejście programu `dd` do katalogu tymczasowego ADB.
Wyniki i artefakty tej kontroli są dostępne przy [PR #19](https://github.com/krystiangaleczka-tech/Chatra/pull/19).
