# CH020 — projekty uzupełniające i kontrakt stanów

Stan: **gotowe do przeglądu; akceptacja produktu/UX G09 pozostaje otwarta** (10.11.2026).

Pakiet obejmuje 18 projektów. [Plansza SVG](CH020_Projekty.svg) pokazuje układy 390×844 z dołączonym fontem Inter. Katalog w aplikacji: Skrzynka → Więcej → Projekty uzupełniające lub Pomoc → Otwórz katalog projektów. Dla każdego projektu można przełączyć siedem stanów. Jest to przegląd propozycji; przyciski demonstracyjne nie wykonują operacji telefonii, eksportu ani zakupu.

Źródła maszynowe: [katalog](../../android/app/src/main/assets/ch020_catalog.json), [tokeny](../../android/app/src/main/assets/ch020_tokens.json). Podstawowe plansze S01–S10 nadal mają osobne wzorce CH002. Ten pakiet ich nie zastępuje.

## Mapa projektów

| Projekt | Zadania realizacyjne | Akcje |
| --- | --- | --- |
| Menu skrzynki | CH011, CH069, CH070 | Oznacz wszystkie jako przeczytane; Przypnij / odepnij; Przenieś do kosza; Anuluj |
| Szczegóły rozmowy | CH065, CH068, CH070 | Media; Wygląd tej rozmowy; Wycisz; Wróć do wiadomości |
| Wybór karty SIM | CH021, CH024 | SIM 1; SIM 2; Zezwól na odczyt kart; Anuluj |
| Wybór daty | CH014, CH015, CH035 | Poprzedni miesiąc; Następny miesiąc; Wybierz dzień; Zatwierdź; Anuluj |
| Wybór godziny | CH014, CH015, CH036 | Godzina; Minuta; Zatwierdź; Anuluj |
| Szablony | CH034 | Wstaw; Dodaj; Edytuj; Usuń; Anuluj |
| Ustawienia Chatra HD | CH033, CH046, CH049, CH051 | Wybierz wariant; Aktywuj HD; Potwierdź link; Anuluj |
| Zablokowani | CH042 | Odblokuj; Dodaj numer; Potwierdź; Anuluj |
| Kosz | CH043, CH061 | Odzyskaj; Usuń trwale; Anuluj |
| Pomoc | CH044 | Prywatność; Ustawienia roli SMS; Wróć |
| Prywatność i zgody | CH021, CH044, CH057 | Zezwól; Otwórz ustawienia; Wybierz zdjęcia; Anuluj |
| Chatra Pro | CH040, CH045 | Kup przy dostępnej ofercie; Przywróć zakup; Wróć |
| Odbiór z linku HD | CH050, CH051, CH052 | Pobierz; Otwórz w Chatrze; Zgłoś problem |
| Galeria rozmowy | CH066, CH067 | Otwórz plik; Przejdź do wiadomości; Wróć |
| Wygląd rozmowy | CH068 | Dziedzicz globalny; Zapisz lokalny; Przywróć globalny; Anuluj |
| Gesty i menu | CH069 | Wybierz gest; Wybierz akcję; Przywróć domyślne; Anuluj |
| Nieprzeczytane | CH070 | Oznacz jako nieprzeczytane; Pierwsza nieprzeczytana; Oznacz wszystkie jako przeczytane; Wróć |
| Eksport i skrót — opcjonalne | CH071 | Wybierz zakres; Udostępnij; Dodaj skrót; Anuluj |

## Kompozycje i dostępność

Kolory poniżej są propozycjami do CH040. Jasne S01–S10 zachowują własne zamrożone wartości. Kontrast mierzony jest dla par tokenów; na gotowym szkle trzeba ponownie sprawdzić wynik po złożeniu warstw. Kontrast używa nieprzezroczystych powierzchni. Tryb ograniczenia przezroczystości wymaga powierzchni opaque.

| Kompozycja | Tryb | Tło | Tekst | Pomocniczy | Akcent | Na akcencie |
| --- | --- | --- | --- | --- | --- | --- |
| Glass | Light | #EDF0F7 | #0B0D14 | #3A4054 | #0058D6 | #FFFFFF |
| Glass | Dark | #141A2A | #FFFFFF | #C7CEDF | #91BFFF | #0B0D14 |
| Mist | Light | #F2F4F8 | #0B0D14 | #3A4054 | #0058D6 | #FFFFFF |
| Mist | Dark | #1B202A | #FFFFFF | #C7CEDF | #91BFFF | #0B0D14 |
| Graphite | Light | #E9EBF0 | #0B0D14 | #3A4054 | #0058D6 | #FFFFFF |
| Graphite | Dark | #171B25 | #FFFFFF | #C7CEDF | #91BFFF | #0B0D14 |
| Contrast | Light | #FFFFFF | #0B0D14 | #3A4054 | #0058D6 | #FFFFFF |
| Contrast | Dark | #000000 | #FFFFFF | #C7CEDF | #91BFFF | #0B0D14 |

Normalna gęstość wiersza: 52 dp; compact: 48 dp. Obszar dotyku co najmniej 48×48 dp także w compact; przy dużym foncie wysokości rosną. Nie kodujemy stanu wyłącznie kolorem. Tekst ma etykietę, przełączniki i radio rolę oraz wartość; błąd i zmiana stanu trafiają do liveRegion. Kolejność fokusu i powrót opisano poniżej. Zatwierdzenie kontrastu planszy, TalkBack, gestów i zachowania fokusu na telefonie pozostaje w G09/CH060.

## Kontrakty widoków

### Menu skrzynki (`inbox-menu`)

Oznaczenie wszystkich jako przeczytane, akcje wątku i alternatywa dla gestów.

- **ready**: Dane są dostępne. Wybór nie uruchamia działania bez świadomego potwierdzenia.
- **empty**: Brak pozycji. Wyjaśnij stan i pokaż dostępną drogę powrotu lub dodania danych.
- **loading**: Wczytywanie. Zachowaj poprzedni wybór i nie pozwalaj uruchamiać tej samej operacji ponownie.
- **error**: Nie udało się wczytać lub zapisać. Zachowaj dane formularza, pokaż Powtórz i Anuluj.
- **disabled**: Działanie jest niedostępne. Podaj powód; nie symuluj skutku ani sukcesu.
- **permission**: Brak dostępu. Wyjaśnij konkretną potrzebę, pokaż Zezwól oraz opcję dalszego używania bez zgody.
- **confirmation**: Pokaż dokładny skutek i zakres. Domyślnie wybierz Anuluj. Po akceptacji blokuj drugi klik.

Akcje: Oznacz wszystkie jako przeczytane; Przypnij / odepnij; Przenieś do kosza; Anuluj.

Fokus: Tytuł panelu, następnie bieżący wybór; Opis → pola/lista → akcja główna → Anuluj; Back/Anuluj zachowuje szkic i przywraca fokus elementowi otwierającemu; Błąd i zmiana stanu: liveRegion polite; pola mają etykietę, radio/switch/slider rolę i wartość.

### Szczegóły rozmowy (`conversation`)

Kontakt lub uczestnicy, wyciszenie, galeria, lokalny wygląd i pierwsza nieprzeczytana.

- **ready**: Dane są dostępne. Wybór nie uruchamia działania bez świadomego potwierdzenia.
- **empty**: Brak pozycji. Wyjaśnij stan i pokaż dostępną drogę powrotu lub dodania danych.
- **loading**: Wczytywanie. Zachowaj poprzedni wybór i nie pozwalaj uruchamiać tej samej operacji ponownie.
- **error**: Nie udało się wczytać lub zapisać. Zachowaj dane formularza, pokaż Powtórz i Anuluj.
- **disabled**: Działanie jest niedostępne. Podaj powód; nie symuluj skutku ani sukcesu.
- **permission**: Brak dostępu. Wyjaśnij konkretną potrzebę, pokaż Zezwól oraz opcję dalszego używania bez zgody.
- **confirmation**: Pokaż dokładny skutek i zakres. Domyślnie wybierz Anuluj. Po akceptacji blokuj drugi klik.

Akcje: Media; Wygląd tej rozmowy; Wycisz; Wróć do wiadomości.

Fokus: Tytuł panelu, następnie bieżący wybór; Opis → pola/lista → akcja główna → Anuluj; Back/Anuluj zachowuje szkic i przywraca fokus elementowi otwierającemu; Błąd i zmiana stanu: liveRegion polite; pola mają etykietę, radio/switch/slider rolę i wartość.

### Wybór karty SIM (`sim`)

Szkic > rozmowa > domyślna; utraconej karty nigdy nie zastępujemy po cichu.

- **ready**: Dane są dostępne. Wybór nie uruchamia działania bez świadomego potwierdzenia.
- **empty**: Brak dostępnych kart. Wysyłanie zablokowane; można zachować szkic.
- **loading**: Wczytywanie. Zachowaj poprzedni wybór i nie pozwalaj uruchamiać tej samej operacji ponownie.
- **error**: Zapamiętana karta zniknęła. Pokaż poprzedni opis oraz dostępne karty i wymagaj nowego wyboru.
- **disabled**: Działanie jest niedostępne. Podaj powód; nie symuluj skutku ani sukcesu.
- **permission**: Brak dostępu. Wyjaśnij konkretną potrzebę, pokaż Zezwól oraz opcję dalszego używania bez zgody.
- **confirmation**: Pokaż dokładny skutek i zakres. Domyślnie wybierz Anuluj. Po akceptacji blokuj drugi klik.

Akcje: SIM 1; SIM 2; Zezwól na odczyt kart; Anuluj.

Fokus: Tytuł panelu, następnie bieżący wybór; Opis → pola/lista → akcja główna → Anuluj; Back/Anuluj zachowuje szkic i przywraca fokus elementowi otwierającemu; Błąd i zmiana stanu: liveRegion polite; pola mają etykietę, radio/switch/slider rolę i wartość.

### Wybór daty (`date`)

Kalendarz poniedziałek–niedziela z miesiącem i rokiem. Miniony dzień niedostępny.

- **ready**: Dane są dostępne. Wybór nie uruchamia działania bez świadomego potwierdzenia.
- **empty**: Brak pozycji. Wyjaśnij stan i pokaż dostępną drogę powrotu lub dodania danych.
- **loading**: Wczytywanie. Zachowaj poprzedni wybór i nie pozwalaj uruchamiać tej samej operacji ponownie.
- **error**: Wybrany termin jest w przeszłości albo godzina nie istnieje. Waliduj przed potwierdzeniem.
- **disabled**: Działanie jest niedostępne. Podaj powód; nie symuluj skutku ani sukcesu.
- **permission**: Brak dostępu. Wyjaśnij konkretną potrzebę, pokaż Zezwól oraz opcję dalszego używania bez zgody.
- **confirmation**: Pokaż dokładny skutek i zakres. Domyślnie wybierz Anuluj. Po akceptacji blokuj drugi klik.

Akcje: Poprzedni miesiąc; Następny miesiąc; Wybierz dzień; Zatwierdź; Anuluj.

Fokus: Tytuł panelu, następnie bieżący wybór; Opis → pola/lista → akcja główna → Anuluj; Back/Anuluj zachowuje szkic i przywraca fokus elementowi otwierającemu; Błąd i zmiana stanu: liveRegion polite; pola mają etykietę, radio/switch/slider rolę i wartość.

### Wybór godziny (`time`)

Format 24 h, osobne pola godziny i minuty. Wyraźna informacja o DST przed zapisaniem.

- **ready**: Dane są dostępne. Wybór nie uruchamia działania bez świadomego potwierdzenia.
- **empty**: Brak pozycji. Wyjaśnij stan i pokaż dostępną drogę powrotu lub dodania danych.
- **loading**: Wczytywanie. Zachowaj poprzedni wybór i nie pozwalaj uruchamiać tej samej operacji ponownie.
- **error**: Nie udało się wczytać lub zapisać. Zachowaj dane formularza, pokaż Powtórz i Anuluj.
- **disabled**: Działanie jest niedostępne. Podaj powód; nie symuluj skutku ani sukcesu.
- **permission**: Brak dostępu. Wyjaśnij konkretną potrzebę, pokaż Zezwól oraz opcję dalszego używania bez zgody.
- **confirmation**: Pokaż dokładny skutek i zakres. Domyślnie wybierz Anuluj. Po akceptacji blokuj drugi klik.

Akcje: Godzina; Minuta; Zatwierdź; Anuluj.

Fokus: Tytuł panelu, następnie bieżący wybór; Opis → pola/lista → akcja główna → Anuluj; Back/Anuluj zachowuje szkic i przywraca fokus elementowi otwierającemu; Błąd i zmiana stanu: liveRegion polite; pola mają etykietę, radio/switch/slider rolę i wartość.

### Szablony (`templates`)

Lista, podgląd i wstawienie do bieżącego szkicu. Dodawanie/edycja/usuwanie z potwierdzeniem.

- **ready**: Dane są dostępne. Wybór nie uruchamia działania bez świadomego potwierdzenia.
- **empty**: Brak pozycji. Wyjaśnij stan i pokaż dostępną drogę powrotu lub dodania danych.
- **loading**: Wczytywanie. Zachowaj poprzedni wybór i nie pozwalaj uruchamiać tej samej operacji ponownie.
- **error**: Nie udało się wczytać lub zapisać. Zachowaj dane formularza, pokaż Powtórz i Anuluj.
- **disabled**: Działanie jest niedostępne. Podaj powód; nie symuluj skutku ani sukcesu.
- **permission**: Brak dostępu. Wyjaśnij konkretną potrzebę, pokaż Zezwól oraz opcję dalszego używania bez zgody.
- **confirmation**: Pokaż dokładny skutek i zakres. Domyślnie wybierz Anuluj. Po akceptacji blokuj drugi klik.

Akcje: Wstaw; Dodaj; Edytuj; Usuń; Anuluj.

Fokus: Tytuł panelu, następnie bieżący wybór; Opis → pola/lista → akcja główna → Anuluj; Back/Anuluj zachowuje szkic i przywraca fokus elementowi otwierającemu; Błąd i zmiana stanu: liveRegion polite; pola mają etykietę, radio/switch/slider rolę i wartość.

### Ustawienia Chatra HD (`hd`)

Oryginał, kopia bez GPS, MMS; link tylko po zgodzie, aktywacja i limity z backendu.

- **ready**: Dane są dostępne. Wybór nie uruchamia działania bez świadomego potwierdzenia.
- **empty**: Brak pozycji. Wyjaśnij stan i pokaż dostępną drogę powrotu lub dodania danych.
- **loading**: Wczytywanie. Zachowaj poprzedni wybór i nie pozwalaj uruchamiać tej samej operacji ponownie.
- **error**: Nie udało się wczytać lub zapisać. Zachowaj dane formularza, pokaż Powtórz i Anuluj.
- **disabled**: Działanie jest niedostępne. Podaj powód; nie symuluj skutku ani sukcesu.
- **permission**: Brak dostępu. Wyjaśnij konkretną potrzebę, pokaż Zezwól oraz opcję dalszego używania bez zgody.
- **confirmation**: Pokaż dokładny skutek i zakres. Domyślnie wybierz Anuluj. Po akceptacji blokuj drugi klik.

Akcje: Wybierz wariant; Aktywuj HD; Potwierdź link; Anuluj.

Fokus: Tytuł panelu, następnie bieżący wybór; Opis → pola/lista → akcja główna → Anuluj; Back/Anuluj zachowuje szkic i przywraca fokus elementowi otwierającemu; Błąd i zmiana stanu: liveRegion polite; pola mają etykietę, radio/switch/slider rolę i wartość.

### Zablokowani (`blocked`)

Lokalne ukrycie i brak powiadomień; bez blokowania połączeń i kasowania historii.

- **ready**: Dane są dostępne. Wybór nie uruchamia działania bez świadomego potwierdzenia.
- **empty**: Brak pozycji. Wyjaśnij stan i pokaż dostępną drogę powrotu lub dodania danych.
- **loading**: Wczytywanie. Zachowaj poprzedni wybór i nie pozwalaj uruchamiać tej samej operacji ponownie.
- **error**: Nie udało się wczytać lub zapisać. Zachowaj dane formularza, pokaż Powtórz i Anuluj.
- **disabled**: Działanie jest niedostępne. Podaj powód; nie symuluj skutku ani sukcesu.
- **permission**: Brak dostępu. Wyjaśnij konkretną potrzebę, pokaż Zezwól oraz opcję dalszego używania bez zgody.
- **confirmation**: Pokaż dokładny skutek i zakres. Domyślnie wybierz Anuluj. Po akceptacji blokuj drugi klik.

Akcje: Odblokuj; Dodaj numer; Potwierdź; Anuluj.

Fokus: Tytuł panelu, następnie bieżący wybór; Opis → pola/lista → akcja główna → Anuluj; Back/Anuluj zachowuje szkic i przywraca fokus elementowi otwierającemu; Błąd i zmiana stanu: liveRegion polite; pola mają etykietę, radio/switch/slider rolę i wartość.

### Kosz (`trash`)

Lokalne ukrycie wiadomości na 30 dni; przywrócenie i osobne potwierdzenie trwałego usunięcia.

- **ready**: Dane są dostępne. Wybór nie uruchamia działania bez świadomego potwierdzenia.
- **empty**: Brak pozycji. Wyjaśnij stan i pokaż dostępną drogę powrotu lub dodania danych.
- **loading**: Wczytywanie. Zachowaj poprzedni wybór i nie pozwalaj uruchamiać tej samej operacji ponownie.
- **error**: Nie udało się wczytać lub zapisać. Zachowaj dane formularza, pokaż Powtórz i Anuluj.
- **disabled**: Działanie jest niedostępne. Podaj powód; nie symuluj skutku ani sukcesu.
- **permission**: Brak dostępu. Wyjaśnij konkretną potrzebę, pokaż Zezwól oraz opcję dalszego używania bez zgody.
- **confirmation**: Pokaż dokładny skutek i zakres. Domyślnie wybierz Anuluj. Po akceptacji blokuj drugi klik.

Akcje: Odzyskaj; Usuń trwale; Anuluj.

Fokus: Tytuł panelu, następnie bieżący wybór; Opis → pola/lista → akcja główna → Anuluj; Back/Anuluj zachowuje szkic i przywraca fokus elementowi otwierającemu; Błąd i zmiana stanu: liveRegion polite; pola mają etykietę, radio/switch/slider rolę i wartość.

### Pomoc (`help`)

Różnice SMS/MMS/HD, brak RCS/iMessage/rozmów, harmonogram zależny od telefonu.

- **ready**: Dane są dostępne. Wybór nie uruchamia działania bez świadomego potwierdzenia.
- **empty**: Brak pozycji. Wyjaśnij stan i pokaż dostępną drogę powrotu lub dodania danych.
- **loading**: Wczytywanie. Zachowaj poprzedni wybór i nie pozwalaj uruchamiać tej samej operacji ponownie.
- **error**: Nie udało się wczytać lub zapisać. Zachowaj dane formularza, pokaż Powtórz i Anuluj.
- **disabled**: Działanie jest niedostępne. Podaj powód; nie symuluj skutku ani sukcesu.
- **permission**: Brak dostępu. Wyjaśnij konkretną potrzebę, pokaż Zezwól oraz opcję dalszego używania bez zgody.
- **confirmation**: Pokaż dokładny skutek i zakres. Domyślnie wybierz Anuluj. Po akceptacji blokuj drugi klik.

Akcje: Prywatność; Ustawienia roli SMS; Wróć.

Fokus: Tytuł panelu, następnie bieżący wybór; Opis → pola/lista → akcja główna → Anuluj; Back/Anuluj zachowuje szkic i przywraca fokus elementowi otwierającemu; Błąd i zmiana stanu: liveRegion polite; pola mają etykietę, radio/switch/slider rolę i wartość.

### Prywatność i zgody (`privacy`)

Osobne karty: SMS, kontakty, SIM, powiadomienia, wybrane media; odmowa nie blokuje reszty.

- **ready**: Dane są dostępne. Wybór nie uruchamia działania bez świadomego potwierdzenia.
- **empty**: Brak pozycji. Wyjaśnij stan i pokaż dostępną drogę powrotu lub dodania danych.
- **loading**: Wczytywanie. Zachowaj poprzedni wybór i nie pozwalaj uruchamiać tej samej operacji ponownie.
- **error**: Nie udało się wczytać lub zapisać. Zachowaj dane formularza, pokaż Powtórz i Anuluj.
- **disabled**: Działanie jest niedostępne. Podaj powód; nie symuluj skutku ani sukcesu.
- **permission**: Brak dostępu. Wyjaśnij konkretną potrzebę, pokaż Zezwól oraz opcję dalszego używania bez zgody.
- **confirmation**: Pokaż dokładny skutek i zakres. Domyślnie wybierz Anuluj. Po akceptacji blokuj drugi klik.

Akcje: Zezwól; Otwórz ustawienia; Wybierz zdjęcia; Anuluj.

Fokus: Tytuł panelu, następnie bieżący wybór; Opis → pola/lista → akcja główna → Anuluj; Back/Anuluj zachowuje szkic i przywraca fokus elementowi otwierającemu; Błąd i zmiana stanu: liveRegion polite; pola mają etykietę, radio/switch/slider rolę i wartość.

### Chatra Pro (`pro`)

Oferta i cena tylko ze sklepu. SMS/MMS, harmonogram, wyszukiwanie i dostępność bez paywalla.

- **ready**: Dane są dostępne. Wybór nie uruchamia działania bez świadomego potwierdzenia.
- **empty**: Brak oferty sklepu: nie pokazuj ceny, Kup ani obietnicy przywrócenia.
- **loading**: Pobieranie oferty. Zakup zablokowany do zweryfikowania ceny i identyfikatora produktu.
- **error**: Sklep niedostępny lub zakup odrzucony. Bez naliczenia uprawnienia; ponowienie jest jawne.
- **disabled**: Działanie jest niedostępne. Podaj powód; nie symuluj skutku ani sukcesu.
- **permission**: Brak dostępu. Wyjaśnij konkretną potrzebę, pokaż Zezwól oraz opcję dalszego używania bez zgody.
- **confirmation**: Pokaż dokładny skutek i zakres. Domyślnie wybierz Anuluj. Po akceptacji blokuj drugi klik.

Akcje: Kup przy dostępnej ofercie; Przywróć zakup; Wróć.

Fokus: Tytuł panelu, następnie bieżący wybór; Opis → pola/lista → akcja główna → Anuluj; Back/Anuluj zachowuje szkic i przywraca fokus elementowi otwierającemu; Błąd i zmiana stanu: liveRegion polite; pola mają etykietę, radio/switch/slider rolę i wartość.

### Odbiór z linku HD (`link`)

Zaufana strona z nazwą pliku, rozmiarem, terminem ważności i pobraniem po autoryzacji.

- **ready**: Dane są dostępne. Wybór nie uruchamia działania bez świadomego potwierdzenia.
- **empty**: Link wygasł lub został cofnięty. Nie ujawniaj listy plików.
- **loading**: Wczytywanie. Zachowaj poprzedni wybór i nie pozwalaj uruchamiać tej samej operacji ponownie.
- **error**: Nie udało się wczytać lub zapisać. Zachowaj dane formularza, pokaż Powtórz i Anuluj.
- **disabled**: Limit transferu lub niewspierany plik. Pokaż rozmiar i przyczynę bez pobierania.
- **permission**: Link wymaga uprawnienia do konkretnego zasobu. Nigdy nie umieszczaj tokenu w logach.
- **confirmation**: Pokaż dokładny skutek i zakres. Domyślnie wybierz Anuluj. Po akceptacji blokuj drugi klik.

Akcje: Pobierz; Otwórz w Chatrze; Zgłoś problem.

Fokus: Tytuł panelu, następnie bieżący wybór; Opis → pola/lista → akcja główna → Anuluj; Back/Anuluj zachowuje szkic i przywraca fokus elementowi otwierającemu; Błąd i zmiana stanu: liveRegion polite; pola mają etykietę, radio/switch/slider rolę i wartość.

### Galeria rozmowy (`gallery`)

Zdjęcia i pliki pochodzą z tej rozmowy. Wybrany element prowadzi do oryginalnej wiadomości bez resetowania historii.

- **ready**: Dane są dostępne. Wybór nie uruchamia działania bez świadomego potwierdzenia.
- **empty**: Brak pozycji. Wyjaśnij stan i pokaż dostępną drogę powrotu lub dodania danych.
- **loading**: Wczytywanie. Zachowaj poprzedni wybór i nie pozwalaj uruchamiać tej samej operacji ponownie.
- **error**: Nie udało się wczytać lub zapisać. Zachowaj dane formularza, pokaż Powtórz i Anuluj.
- **disabled**: Działanie jest niedostępne. Podaj powód; nie symuluj skutku ani sukcesu.
- **permission**: Brak dostępu. Wyjaśnij konkretną potrzebę, pokaż Zezwól oraz opcję dalszego używania bez zgody.
- **confirmation**: Pokaż dokładny skutek i zakres. Domyślnie wybierz Anuluj. Po akceptacji blokuj drugi klik.

Akcje: Otwórz plik; Przejdź do wiadomości; Wróć.

Fokus: Tytuł panelu, następnie bieżący wybór; Opis → pola/lista → akcja główna → Anuluj; Back/Anuluj zachowuje szkic i przywraca fokus elementowi otwierającemu; Błąd i zmiana stanu: liveRegion polite; pola mają etykietę, radio/switch/slider rolę i wartość.

### Wygląd rozmowy (`local-appearance`)

Dziedziczenie globalnego motywu albo jawne lokalne nadpisanie. Reset dotyczy tylko tej rozmowy.

- **ready**: Dane są dostępne. Wybór nie uruchamia działania bez świadomego potwierdzenia.
- **empty**: Brak pozycji. Wyjaśnij stan i pokaż dostępną drogę powrotu lub dodania danych.
- **loading**: Wczytywanie. Zachowaj poprzedni wybór i nie pozwalaj uruchamiać tej samej operacji ponownie.
- **error**: Nie udało się wczytać lub zapisać. Zachowaj dane formularza, pokaż Powtórz i Anuluj.
- **disabled**: Działanie jest niedostępne. Podaj powód; nie symuluj skutku ani sukcesu.
- **permission**: Brak dostępu. Wyjaśnij konkretną potrzebę, pokaż Zezwól oraz opcję dalszego używania bez zgody.
- **confirmation**: Pokaż dokładny skutek i zakres. Domyślnie wybierz Anuluj. Po akceptacji blokuj drugi klik.

Akcje: Dziedzicz globalny; Zapisz lokalny; Przywróć globalny; Anuluj.

Fokus: Tytuł panelu, następnie bieżący wybór; Opis → pola/lista → akcja główna → Anuluj; Back/Anuluj zachowuje szkic i przywraca fokus elementowi otwierającemu; Błąd i zmiana stanu: liveRegion polite; pola mają etykietę, radio/switch/slider rolę i wartość.

### Gesty i menu (`gestures`)

Przypisania gestów z dostępną alternatywą menu. Akcje destrukcyjne wymagają potwierdzenia.

- **ready**: Dane są dostępne. Wybór nie uruchamia działania bez świadomego potwierdzenia.
- **empty**: Brak pozycji. Wyjaśnij stan i pokaż dostępną drogę powrotu lub dodania danych.
- **loading**: Wczytywanie. Zachowaj poprzedni wybór i nie pozwalaj uruchamiać tej samej operacji ponownie.
- **error**: Nie udało się wczytać lub zapisać. Zachowaj dane formularza, pokaż Powtórz i Anuluj.
- **disabled**: Działanie jest niedostępne. Podaj powód; nie symuluj skutku ani sukcesu.
- **permission**: Brak dostępu. Wyjaśnij konkretną potrzebę, pokaż Zezwól oraz opcję dalszego używania bez zgody.
- **confirmation**: Pokaż dokładny skutek i zakres. Domyślnie wybierz Anuluj. Po akceptacji blokuj drugi klik.

Akcje: Wybierz gest; Wybierz akcję; Przywróć domyślne; Anuluj.

Fokus: Tytuł panelu, następnie bieżący wybór; Opis → pola/lista → akcja główna → Anuluj; Back/Anuluj zachowuje szkic i przywraca fokus elementowi otwierającemu; Błąd i zmiana stanu: liveRegion polite; pola mają etykietę, radio/switch/slider rolę i wartość.

### Nieprzeczytane (`unread`)

Ręczne nadpisanie i znacznik pierwszej nieprzeczytanej; odczyt dopiero po pokazaniu wiadomości.

- **ready**: Dane są dostępne. Wybór nie uruchamia działania bez świadomego potwierdzenia.
- **empty**: Brak pozycji. Wyjaśnij stan i pokaż dostępną drogę powrotu lub dodania danych.
- **loading**: Wczytywanie. Zachowaj poprzedni wybór i nie pozwalaj uruchamiać tej samej operacji ponownie.
- **error**: Nie udało się wczytać lub zapisać. Zachowaj dane formularza, pokaż Powtórz i Anuluj.
- **disabled**: Działanie jest niedostępne. Podaj powód; nie symuluj skutku ani sukcesu.
- **permission**: Brak dostępu. Wyjaśnij konkretną potrzebę, pokaż Zezwól oraz opcję dalszego używania bez zgody.
- **confirmation**: Pokaż dokładny skutek i zakres. Domyślnie wybierz Anuluj. Po akceptacji blokuj drugi klik.

Akcje: Oznacz jako nieprzeczytane; Pierwsza nieprzeczytana; Oznacz wszystkie jako przeczytane; Wróć.

Fokus: Tytuł panelu, następnie bieżący wybór; Opis → pola/lista → akcja główna → Anuluj; Back/Anuluj zachowuje szkic i przywraca fokus elementowi otwierającemu; Błąd i zmiana stanu: liveRegion polite; pola mają etykietę, radio/switch/slider rolę i wartość.

### Eksport i skrót — opcjonalne (`export`)

Wybierz zakres eksportu i aplikację docelową. Skrót prowadzi bezpośrednio do rozmowy.

- **ready**: Dane są dostępne. Wybór nie uruchamia działania bez świadomego potwierdzenia.
- **empty**: Brak pozycji. Wyjaśnij stan i pokaż dostępną drogę powrotu lub dodania danych.
- **loading**: Wczytywanie. Zachowaj poprzedni wybór i nie pozwalaj uruchamiać tej samej operacji ponownie.
- **error**: Nie udało się wczytać lub zapisać. Zachowaj dane formularza, pokaż Powtórz i Anuluj.
- **disabled**: Funkcja opcjonalna CH071. Nie blokuje wydania i nie jest włączona bez osobnego zakresu.
- **permission**: Brak dostępu. Wyjaśnij konkretną potrzebę, pokaż Zezwól oraz opcję dalszego używania bez zgody.
- **confirmation**: Pokaż dokładny skutek i zakres. Domyślnie wybierz Anuluj. Po akceptacji blokuj drugi klik.

Akcje: Wybierz zakres; Udostępnij; Dodaj skrót; Anuluj.

Fokus: Tytuł panelu, następnie bieżący wybór; Opis → pola/lista → akcja główna → Anuluj; Back/Anuluj zachowuje szkic i przywraca fokus elementowi otwierającemu; Błąd i zmiana stanu: liveRegion polite; pola mają etykietę, radio/switch/slider rolę i wartość.

## Walidacja i odbiór

```bash
python3 scripts/ch020_validate_catalog.py --render
```

Walidator sprawdza komplet 18×7 stanów, akcje, fokus, mapę zadań, status propozycji, osiem palet i kontrast tokenów co najmniej 4,5:1 oraz minimalny obszar dotyku. Nie zastępuje akceptacji wizualnej ani badania użyteczności. Planszę wyrenderowano w Chrome i obejrzano; katalog Compose jest osobnym podglądem tekstów i stanów.

Do zatwierdzenia: układy podwidoków, szczegółowe treści, projekty gestów i alternatyw, palety dark/compact, paywall oraz strona linku. Kalendarz i zegar w formularzach S05/S06 nadal używają tymczasowych pickerów systemowych do czasu wdrożenia zatwierdzonego projektu. Żaden przyszły backend ani zakup nie jest aktywowany przez katalog.
