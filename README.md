# Chatra
A better and unique version of Textra, but mine.

## Audyt i ustalenia projektu

- [Pełny audyt w Markdown](docs/AUDYT.md)
- [Pełny audyt w PDF](docs/AUDYT.pdf)
- [UX: Textra vs Wiadomości Apple, decyzje i propozycje dla Chatry (29.10.2026)](docs/UX_TEXTRA_IOS_WNIOSKI_2026-10-29.md)

Badanie bazowe: **26 października 2026**. Uzupełnienie: **29 października 2026**.
Raport obejmuje 8 produktów, 61 źródeł i ustalenia z dalszej rozmowy.

Dokumentacja zawiera:

- porównanie Textry, Wiadomości Google, Samsung Messages i Wiadomości Apple;
- audyt wyglądu, funkcji, opinii użytkowników i potencjału rynku;
- zasady komunikacji z iPhone oraz różnice między SMS do wielu osób a grupowym MMS;
- ograniczenia jakości MMS i propozycję własnego transferu oryginalnych załączników;
- wariant SMS z linkiem dla odbiorcy bez aplikacji Chatra;
- porównanie Rust, C++, C, Zig i asemblera oraz wymagania stabilności;
- priorytety, nierozstrzygnięte decyzje i kryteria przyszłych testów.

**Zakres:** aplikacja SMS i MMS z kierunkiem własnego transferu załączników przez internet.
Połączenia głosowe i wideo pozostają poza zakresem.

**Status:** dokumentacja i koncepcja. Nie oznacza zaimplementowanej obsługi RCS,
iMessage ani transferu HD. Kotlin i Rust są rekomendacją do weryfikacji w prototypie.

Rozdziały 1-21 zawierają część bazową. Rozdziały 22-27 zawierają uzupełnienie rozmowy.
W PDF uzupełnienie zaczyna się na stronie 24.


## Dokumentacja implementacji Android

- [CH040–CH044: trwały wygląd i nadpisania rozmów, ustawienia wysyłania, lokalne blokady, kosz i pomoc](docs/implementacja_android/CH040_CH044_Wdrozenie_i_weryfikacja.md)

- [CH035–CH039: trwały harmonogram, odzyskiwanie, edycja, indeks i filtry](docs/implementacja_android/CH035_CH039_Wdrozenie_i_weryfikacja.md)
- [CH030–CH034: powiadomienia, trwałe pliki, MMS, kopie bez lokalizacji i szablony](docs/implementacja_android/CH030_CH034_Wdrozenie_i_weryfikacja.md)

- [CH029: kontakty i wielu odbiorców](docs/implementacja_android/CH029_Kontakty_i_wielu_odbiorcow.md)
- [CH022–CH026: provider SMS/MMS, Room, odbiór, SIM, licznik i transport SMS](docs/implementacja_android/CH022_CH026_Wdrozenie_i_weryfikacja.md)

- [CH019–CH021: S10, katalog projektów, rola SMS i uprawnienia](docs/implementacja_android/CH019_CH021_Wdrozenie_i_weryfikacja.md)
- [CH020: 18 projektów, stany i tokeny do akceptacji G09](docs/implementacja_android/CH020_Katalog_projektow_i_stanow.md)

- [CH016–CH018: S07–S09, wyniki testów i otwarty odbiór na telefonie](docs/implementacja_android/CH016_CH018_Wdrozenie_i_weryfikacja.md)
- [CH016–CH018: sprawdzenie demo w emulatorze i poprawki z 10.11.2026](docs/implementacja_android/CH016_CH018_Emulator_QA_2026-11-10.md)
- [CH001: kontrakt produktu, G01–G10 i macierz S01–S10](docs/implementacja_android/CH001_Kontrakt_produktu_i_rejestr_decyzji.md)
- [CH001: rejestr decyzji w JSON](docs/implementacja_android/CH001_Rejestr_decyzji.json)
- [CH002: raport integralności, render Android i zatwierdzona G01](docs/implementacja_android/CH002_Weryfikacja_wzorcow.md)
- [CH002: manifest i punkty pomiarowe](docs/implementacja_android/CH002_Pomiary_i_manifest.json)
- [CH002: skrypt walidacji](scripts/ch002_verify_reference.mjs)
- [CH006: generator fixture 10 ekranów i porównywarka renderów (częściowo)](core/testing/README.md)
- [CH005: modele i porty domeny Kotlin](core/model/README.md), [ADR](docs/implementacja_android/CH005_ADR_Modele_i_granice.md), [testy JVM](scripts/ch005_test_domain.sh)
- [CH002: golden Android (S26 Ultra, Chrome 156) i porównanie z macOS](docs/implementacja_android/CH002_Android/porownanie.json)
- [CH002: plansza macOS vs Android](docs/implementacja_android/CH002_Android/plansza_macos_vs_android.jpg)
- [CH002: przechwytywanie na Androidzie](scripts/ch002_android_capture.mjs) i [porównanie](scripts/ch002_android_compare.py)

Specyfikacja z 29 października 2026 opisuje odtworzenie 1:1 dziesięciu ekranów projektu Liquid Glass oraz docelowe zachowanie aplikacji SMS/MMS.

- [Dokumentacja implementacji w Markdown](docs/implementacja_android/Chatra_Dokumentacja_Android.md)
- [Dokumentacja implementacji w Word](docs/implementacja_android/Chatra_Dokumentacja_Android.docx)
- [72 zadania w aktualnym backlogu Markdown (70 do wydania, 2 opcjonalne)](docs/implementacja_android/Chatra_Backlog_Android.md)
- [Archiwalny eksport 64 zadań w Word (wersja 1.0)](docs/implementacja_android/Chatra_Backlog_Android.docx)
- [Aktualny plan 72 zadań i zależności w JSON](docs/implementacja_android/Chatra_Plan_zadan.json)
- [Wprowadzenie i zawartość pakietu](docs/implementacja_android/Czytaj_najpierw.md)
- [Pakiet referencyjny ekranów i stylów](docs/implementacja_android/Chatra_Pakiet_referencyjny/README.md)
- [Archiwalny pakiet ZIP (wersja 1.0, 64 zadania)](docs/implementacja_android/Chatra_Dokumentacja_i_taski.zip)

Dokumentacja obejmuje wygląd, architekturę Androida, SMS/MMS, dual SIM, harmonogram, wyszukiwanie, Chatra HD, prywatność i kryteria odbioru. Aktualny backlog Markdown i JSON (wersja 1.1) obejmuje także szczegóły rozmowy, galerię, kolaże, personalizację per rozmowa, gesty oraz nieprzeczytane; dwie pozycje dotyczą opcjonalnego eksportu i badań platform. Zadania zawierają priorytety, role, zależności, orientacyjne nakłady i weryfikację. Braki źródłowego projektu i decyzje do zamknięcia oznaczono osobno. Pakiet jest dokumentacją do realizacji i nie zawiera gotowego APK.
