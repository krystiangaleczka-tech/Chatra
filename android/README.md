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
| `fullDebug` | Telephony Provider, rola SMS | prawdziwe transporty |
| `fullRelease` | jak wyżej, R8 | podpis z `keystore.properties` |

`demo` ma osobny `applicationId` (`app.chatra.demo`), więc nie koliduje z wersją pełną.

## Uruchomienie

```bash
cd android
./gradlew :app:assembleDemoDebug          # APK demonstracyjny
./gradlew :app:installDemoDebug           # instalacja na podłączonym urządzeniu
./gradlew test                            # testy JVM
```

Wydanie testowe: skopiuj `keystore.properties.example` do `keystore.properties`, wskaż własny keystore poza repozytorium, potem `./gradlew :app:assembleFullRelease`. Bez tego pliku release buduje się niepodpisany.

## Moduły

Zobacz [ADR-001](../docs/implementacja_android/adr/ADR-001_Provider_i_Room.md): `core:model` (domena i kontrakty), `core:designsystem` (tokeny, Inter, ikony, tła, szkło), `app` (ekrany i warianty).

## Font

Inter 4.001 (OFL, `core/designsystem/INTER_OFL.txt`) w wagach 400–700, ten sam build co web font w pakiecie referencyjnym (G01).
