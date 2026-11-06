# CH004 — projekt Android i APK demonstracyjne

## Audyt CH004–CH006 (06.11.2026)

Przed tą zmianą na `main` nie było wrappera Gradle, modułu aplikacji, manifestu ani APK. Scalone PR #6 i #7 dostarczały odpowiednio domenę CH005 oraz narzędzia fixture/porównywania CH006.

| Zadanie | Stan po zmianie | Co pozostaje |
| --- | --- | --- |
| CH004 | Szkielet i kompilacja demo/production, testowe release, konfiguracja podpisu i CI | Odbiór po CH003 i decyzjach G02/G03; brak podpisanego wydania produkcyjnego |
| CH005 | Kontrakty domeny istnieją; 31 asercji JVM przechodzi przez Gradle | Adaptery Room/Telephony i rzeczywista wysyłka są kolejnymi zadaniami |
| CH006 | 10 fixture/500 elementów, osiem markerów, deterministyczny generator, działająca porównywarka; katalog podpięty do listy demo | Docelowy renderer, dwa przebiegi na fizycznym Androidzie i odbiór porównania goldenów |

Lokalnie sprawdzono również `ch002_verify_reference.mjs --strict-android`, trzy komendy generatora/walidatora CH006 oraz test porównywarki na dziesięciu obrazach z jednym zmienionym pikselem. Wszystkie przeszły. Oryginalny HTML, fonty i goldeny pozostają bez zmian. Te wyniki dotyczą integralności wcześniejszych materiałów i narzędzi, nie nowego natywnego renderu.

Projekt ma przypięte wersje: JDK 21 **JDK z javac, nie samo JRE**, Gradle 8.13 (wrapper z SHA-256), AGP 8.13.2, Kotlin/Compose Compiler 2.2.21, Compose BOM 2025.10.01, Activity Compose 1.11.0. `compileSdk/targetSdk = 36`, Build Tools 36.0.0, robocze `minSdk = 31` zgodnie z G02. Jest to kompatybilny zestaw do prototypu; nie zamyka decyzji G02/G03 ani pomiarów CH003.

## Budowanie w czystym środowisku

Wymagania: JDK 21, Node 22+, Android SDK i zaakceptowane licencje SDK. Ustaw `JAVA_HOME` i `ANDROID_HOME` albo lokalne `sdk.dir` w ignorowanym `local.properties`. Nie zapisuj ścieżek tej sesji do konfiguracji projektu.

```bash
sdkmanager 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0'
./gradlew :core:model:test :app:assembleDemoDebug :app:assembleDemoRelease :app:assembleProductionRelease :app:lint
python3 scripts/ch004_verify_apks.py
adb install -r app/build/outputs/apk/demo/debug/app-demo-debug.apk
adb shell am start -n pl.chatra.demo/pl.chatra.MainActivity
```

Gradle generuje fixture CH006 i kopiuje oryginalny HTML **bajt w bajt** wyłącznie do assets wariantu `demo`. Node jest potrzebny do kompilacji demo, a nie do działania APK. `production` ma oddzielny source set i ekran informujący o stanie prac; nie zawiera referencyjnych danych ani HTML. Nie jest wydaniem gotowego komunikatora.

## Co można sprawdzić w APK

Natywna lista Compose otwiera S01–S10 w Android WebView. Polski tekst, lokalne fonty i oryginalne szkło CSS pochodzą z zachowanego bundla. Wyświetlany jest pojedynczy iframe 390×844; na mniejszym obszarze można przewijać. Powrót prowadzi do listy. Wzorce są statyczne: widoczny przycisk wysyłania, wybór SIM czy przełącznik nie realizuje funkcji telefonu. Demo nie prosi o rolę domyślnej aplikacji SMS ani uprawnienia do SMS, kontaktów czy Internetu. Żądania WebView inne niż lokalny dokument są blokowane, brak mostu JavaScript do Androida. Zmiana zewnętrznej prezentacji plansz jest wykonywana w pamięci; oryginał pozostaje bez zmian.

To kontrolny podgląd WebView przewidziany przez CH003. Nie stanowi implementacji natywnych ekranów Compose, pomiaru GPU ani zatwierdzenia technologii G03. CH006 nadal wymaga wpięcia do docelowego renderera i porównania z goldenami na fizycznym Androidzie.

## Testy na urządzeniu / emulatorze

Podłącz Android 12+ z aktualnym systemowym WebView, włącz debugowanie USB. Dla emulatora uruchom wcześniej wybrany AVD. Skrypt instaluje demoDebug i otwiera wszystkie 10 ekranów; nie modyfikuje ustawień telefonu.

```bash
node scripts/ch004_smoke_android.mjs
```

Opcjonalnie podaj ścieżkę do APK i katalog raportu jako dwa argumenty. `ADB` może wskazywać plik wykonywalny. Sprawdza rzeczywisty DOM WebView, viewport iframe, załadowanie Inter i znaczniki CH006. Raport i zrzuty całej aplikacji zapisuje w `build/ch004/device`. To zrzuty z paskami sterowania demo, więc nie należy podawać ich bezpośrednio do porównywarki goldenów CH006. Zdalne DevTools są aktywne tylko w debug. Pojedynczy ekran można otworzyć komendą:

```bash
adb shell am force-stop pl.chatra.demo
adb shell am start -n pl.chatra.demo/pl.chatra.MainActivity --ei screen 3
```

## Release i podpis

Bez klucza komendy budują testowe **niepodpisane** demoRelease i productionRelease. APK demoDebug jest podpisane kluczem debug i można je zainstalować. Do release wyeksportuj zmienne opisane w `.env.example`; keystore trzymaj poza repozytorium. Nie używamy podpisu debug do release. `ch004_verify_apks.py` celowo weryfikuje domyślny build bez klucza release. W CI publikowany jest artefakt demoDebug po przejściu kompilacji i lint. Status zdalnego przebiegu jest widoczny w PR #8; wyniki lokalne podano osobno.

## Lokalna weryfikacja kompilacji (06.11.2026)

`./gradlew clean :core:model:test :app:assembleDemoDebug :app:assembleDemoRelease :app:assembleProductionRelease :app:lint` — **BUILD SUCCESSFUL**, 151 zadań wykonanych po czyszczeniu. Test domeny: **31 asercji PASS**. Lint: **No issues found**. `python3 scripts/ch004_verify_apks.py` — **PASS** dla trzech paczek: oryginalne bajty HTML i wygenerowane fixture obecne w demo, brak danych demo w produkcji (dozwolone są profile dexopt kompilatora). `apksigner verify` potwierdza podpis demoDebug; `aapt dump permissions` nie zawiera Internetu, SMS ani kontaktów. Release pozostaje niepodpisane. Nie używano fizycznego telefonu. Zdalne CI uruchomiono po wypchnięciu gałęzi; jego status należy sprawdzić w PR #8. Powyższy wynik dotyczy lokalnej kompilacji.

## Wynik testu uruchomienia — niezaliczony

Na emulatorze 37.2.13, AOSP Android 16/API 36 x86_64, bez `/dev/kvm` (TCG), system początkowo restartował `system_server` przez watchdog. Po zakończeniu startu **potwierdzono instalację APK**: `adb install --no-streaming -r ...` → **Success**, a `pm path pl.chatra.demo` zwróciło `base.apk`.

Uruchomienie `pl.chatra.demo/pl.chatra.MainActivity --ei screen 1` utworzyło proces aplikacji i załadowało systemowy WebView **com.android.webview 133.0.6943.137**. Następnie proces zakończył się **SIGTRAP**. Raport natywny wskazuje `libwebviewchromium.so`, `pc 0x249c840`, BuildId `3fed08901ee0e6620bf486e4e9e34fba0a577d62`. Nie potwierdzono dokładnej przyczyny; nie przypisujemy jej bez dowodu wyłącznie emulatorowi ani kodowi Kotlin. Na emulatorze występowały również ANR launchera i restarty usług systemowych.

**Wynik: instalacja PASS, podgląd/rzeczywisty render FAIL — 0/10 ekranów odebranych.** Brak raportu fontów i poprawnych zrzutów aplikacji. To APK do dalszej diagnostyki, nie odebrana demonstracja. Kompilacja i testy statyczne nie zastępują tego wyniku. CH004 i CH006 pozostają otwarte. Kolejna weryfikacja: uruchomiony telefon Android 12+ z aktualnym WebView lub emulator z KVM; następnie ponowne uruchomienie skryptu smoke i diagnostyka awarii, jeśli się odtworzy.

Zdalny build na GitHubie również przeszedł: [Actions 42306324634](https://github.com/krystiangaleczka-tech/Chatra/actions/runs/42306324634) — **success**, z artefaktem `chatra-demo-debug`. Ten przebieg potwierdza kompilację, lint i rozdzielenie assets, nie uruchomienie Androida.
