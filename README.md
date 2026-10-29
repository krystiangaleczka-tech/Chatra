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
