# Chatra — aplikacja Android

## Wymagania

- JDK 17 lub nowszy do uruchamiania Gradle (sprawdzone na JDK 25); kod kompilowany do JVM 17.
- Android SDK z platformą **android-37** (`sdkmanager "platforms;android-37.0"`) i build-tools 36+.
- `local.properties` z `sdk.dir=…` (tworzy Android Studio albo ręcznie; plik jest ignorowany przez git).

## Wersje (CH004, stan 05.11.2026)

| Składnik | Wersja |
| --- | --- |
| Gradle (wrapper) | 9.8.2 |
| Android Gradle Plugin | 9.5.0 |
| Kotlin | 2.4.21 |
| Compose BOM | 2026.09.00 |
| compileSdk / targetSdk / minSdk | 37 / 36 / 31 |

`minSdk 31` wynika z decyzji G02 (RenderEffect dla szkła). `targetSdk 36` jest świadomie o jeden poziom niżej niż compileSdk, dopóki nie przejrzymy zmian zachowania Androida 17; podniesienie należy do CH062/CH064. Wszystkie wersje są w `gradle/libs.versions.toml`.

## Warianty

| Wariant | Dane | Wysyłka i zakupy |
| --- | --- | --- |
| `demoDebug` | fixture CH006 (29.10.2026, 10 plansz) | brak prawdziwej wysyłki i zakupów |
| `fullDebug` | historia systemowa SMS/MMS i metadane Room | SMS przez SmsManager; transport MMS w CH032 |
| `fullRelease` | ten sam zakres platformowy, R8 | podpis z `keystore.properties` |

`demo` ma osobny `applicationId` (`app.chatra.demo`), więc nie koliduje z wersją pełną.

## Uruchomienie

```bash
cd android
./gradlew :app:assembleDemoDebug          # APK demonstracyjny
./gradlew :app:installDemoDebug           # instalacja na podłączonym urządzeniu
./gradlew :core:model:test :core:testing:test :core:designsystem:testDebugUnitTest :app:testDemoDebugUnitTest
./gradlew :app:lintDemoDebug               # lint wariantu demo
```

Wydanie testowe: skopiuj `keystore.properties.example` do `keystore.properties`, wskaż własny keystore poza repozytorium, potem `./gradlew :app:assembleFullRelease`. Bez tego pliku release buduje się niepodpisany.

Wariant `full` kompiluje się i od CH022–CH026 odczytuje historię oraz wysyła SMS.
Pozostaje wersją testową: nowe MMS, Chatra HD, harmonogram, produkcyjny indeks
wyszukiwania i pełne ponawianie wymagają kolejnych zadań.
[Zakres, testy i otwarty odbiór fizyczny](../docs/implementacja_android/CH022_CH026_Wdrozenie_i_weryfikacja.md).

```bash
./gradlew :app:assembleFullDebug :app:testFullDebugUnitTest :app:lintFullDebug
```

## Ekrany S07–S09 (CH016–CH018)

Wyszukiwanie jest dostępne ze Skrzynki, Nowa wiadomość z przycisku docka,
a Wygląd z pełnego S10 Ustawienia (CH019).
Demo obsługuje lokalne wyniki, szkic nowych odbiorców i ustawienia wyglądu.
Pozostałe kompozycje i tryby S09 mają oznaczony podgląd roboczy do CH020/CH040.

[Zakres i wyniki weryfikacji CH016–CH018](../docs/implementacja_android/CH016_CH018_Wdrozenie_i_weryfikacja.md),
w tym otwarty końcowy odbiór na telefonie. Plansze referencyjne:

```bash
adb shell am force-stop app.chatra.demo
adb shell am start -n app.chatra.demo/app.chatra.MainActivity --ez frame true --es screen S07
# Analogicznie S08 i S09.
```

## Moduły

Zobacz [ADR-001](../docs/implementacja_android/adr/ADR-001_Provider_i_Room.md): `core:model` (domena i kontrakty), `core:designsystem` (tokeny, Inter, ikony, tła, szkło), `app` (ekrany i warianty).

## Font

Inter 4.001 (OFL, `core/designsystem/INTER_OFL.txt`) w wagach 400–700, ten sam build co web font w pakiecie referencyjnym (G01).

## CH019–CH021

S10 pokazuje podsumowania z preferencji, aktywnych kart i liczników porządku.
Pro ma własny stan braku oferty; `[CENA]` występuje tylko w ramce fixture.
[Zakres i weryfikacja](../docs/implementacja_android/CH019_CH021_Wdrozenie_i_weryfikacja.md).
[18 projektów i kontrakty stanów CH020](../docs/implementacja_android/CH020_Katalog_projektow_i_stanow.md)
pozostają do akceptacji G09. Katalog dostępny z menu Skrzynki i Pomocy.
