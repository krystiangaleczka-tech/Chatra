# Chatra
A better and unique version of Textra, but mine.

## Audyt i ustalenia projektu

- [Pełny audyt w Markdown](docs/AUDYT.md)
- [Pełny audyt w PDF](docs/AUDYT.pdf)

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
