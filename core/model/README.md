# Chatra core/model (CH005)

Czysta domena Kotlin/JVM. Nie zależy od Android SDK, Compose, Room ani backendu. Jest podłączona jako moduł Gradle `:core:model`; `./gradlew :core:model:test` uruchamia istniejące 31 asercji kontraktów.

Testy kontraktów: `bash scripts/ch005_test_domain.sh` z katalogu głównego (JDK 17+, kotlinc). Skrypt tworzy tymczasowy JAR poza repo i po zakończeniu go usuwa. Nie wysyła wiadomości ani nie otwiera providera.

Główne kontrakty: `Models.kt` (identyfikatory i encje), `Ports.kt` (oddzielne porty i typy wyników), `Transitions.kt` (bezpieczne granice stanu). ADR: `docs/implementacja_android/CH005_ADR_Modele_i_granice.md`.

**Jeszcze nie ma** adapterów Room, Telephony ani testów instrumentacyjnych domeny. Projekt CH004 dostarcza APK podglądu wzorców, bez funkcji telefonii. Reguły CAS są kontraktem do zaimplementowania jako transakcje w CH022/CH026–CH028.
