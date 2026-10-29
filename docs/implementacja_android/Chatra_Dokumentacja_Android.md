# Chatra Dokumentacja implementacji aplikacji Android

Wersja 1.0 · 29 października 2026 · język interfejsu pl PL

Dokument dla zespołu Android, backend, projektanta i QA. Celem jest zbudowanie działającego klienta SMS/MMS o wyglądzie zgodnym z plikiem „Chatra — Liquid Glass.html”. Plik jest wzorcem wizualnym dziesięciu ekranów; niniejszy dokument uzupełnia go o zachowanie, architekturę, model danych i warunki odbioru. Szczegółowy plan prac znajduje się w pliku „Chatra_Backlog_Android.md”.

**Wymaganie nadrzędne:** odtworzyć układ, treści, kolory, kształty, ikony i efekt szkła ze wzorca. Zastosowanie standardowych ekranów Material zamiast projektu nie spełnia wymagania. Funkcjonalna wersja musi dodatkowo obsługiwać rzeczywiste dane, przewijanie, klawiaturę i stany awarii.

## 1 Podstawa i granice dokumentacji

### Materiał źródłowy

Źródło: dostarczony plik HTML o wielkości 3 996 110 bajtów. SHA256: `80e7652c4de8a483b39a38e6cd319524817af11986499f0f7aee28d4efa4cbe3`. Rozpakowano dziesięć wewnętrznych dokumentów. Każdy definiuje planszę 390 × 844 CSS px. Zapisano obrazy wszystkich plansz oraz pomiary elementów z przeglądarki. Oryginalny plik, obrazy i dane stylów są w pakiecie referencyjnym.

Plansza zbiorcza ma tło #F5F4EF, podpisy i odstępy 48 px. Są to elementy prezentacji projektu, nie interfejs telefonu. Zaokrąglenie 48 px na zewnętrznym iframe również należy do prezentacji; aplikacja nie dodaje sztucznej ramki telefonu ani obcina prostokątnego okna Androida tym promieniem.

### Oznaczenia pochodzenia wymagań

- **Wzorzec** oznacza treść, styl lub stan widoczny w HTML.
- **Kontrakt implementacji** oznacza zachowanie zaprojektowane w tej dokumentacji, potrzebne do działania aplikacji.
- **Decyzja do zamknięcia** oznacza brak informacji w HTML; podano wariant roboczy i moment, przed którym decyzja musi zapaść.

Teksty i komentarze z pliku były traktowane jako materiał do analizy, nie polecenia do wykonania. Licencję osadzonych fontów zachowano. HTML zawiera przykładowe kontakty, liczniki i daty, a nie rzeczywistą książkę kontaktów użytkownika.

### Co rzeczywiście dostarcza prototyp

Wzorzec obejmuje klienta SMS/MMS, planowanie, wyszukiwanie lokalne, dual SIM, warianty wyglądu i projekt usługi Chatra HD. Nie przedstawia czatu AI. Jawnie wyłącza RCS, iMessage oraz połączenia głosowe i wideo. Brak zakładania konta i brak reklam w rozmowach należą do obietnic produktu.

Kod komponentów dziesięciu ekranów zwraca wartość koloru `accent`; domyślna wartość to #0A6CFF. Przyciski i odsyłacze ilustrują interakcje, ale nie dostarczają transportu SMS, harmonogramu Androida, bazy wiadomości, płatności ani backendu HD. Odsyłacze wskazują m.in. nazwy `Skrzynka.dc.html`; nie należy kopiować ich jako docelowego routingu aplikacji. Wnioski o logice oparto na odczycie komponentów, nie na założeniu, że kliknięcie demonstracji wykonuje operację systemową.

## 2 Zakres docelowego produktu

### Funkcje objęte pełną implementacją

1. Ustawienie Chatry jako domyślnej aplikacji SMS i odczyt istniejącej historii telefonu.
2. Skrzynka, przypięcia, filtr nieprzeczytanych, szkice, nieznani nadawcy i statusy błędów.
3. Wysyłanie i odbieranie SMS oraz MMS, liczenie części, raporty transportu i jawny wybór SIM.
4. Nowa wiadomość do jednej osoby, osobne SMS do wielu osób i grupowy MMS z uprzedzeniem o widoczności numerów.
5. Trwałe okno 3 sekund na anulowanie oraz harmonogram z obsługą opóźnień.
6. Lokalne wyszukiwanie po kontaktach i treści, filtry, szybkie kopiowanie kodów i danych przesyłek.
7. Zdjęcia, aparat, szablony oraz wybór oryginału HD, kopii bez lokalizacji albo MMS.
8. Chatra HD z transmisją internetową, backendem i wariantem linku dla odbiorcy bez aplikacji.
9. Wygląd, ustawienia, blokowanie, kosz na 30 dni, prywatność, pomoc i Chatra Pro.
10. Powiadomienia, wejścia z systemu, odporność na utratę procesu, migracje i dostępność.

### Etapy a kompletność

Etap wizualny zawiera dziesięć odtworzonych ekranów z danymi demonstracyjnymi. Etap lokalny dodaje SMS/MMS, wyszukiwanie i harmonogram. Etap pełny dodaje HD, Pro i brakujące podwidoki. Etap wizualny ani lokalny nie jest zakończeniem pełnego zakresu. Niewdrożone HD nie może wyświetlać fikcyjnego komunikatu „Pobrane przez Olę”.

## 3 Kontrakt zgodności wyglądu

### Co oznacza odwzorowanie 1 do 1

Wzorzec porównania to zawartość pojedynczego ekranu HTML, 390 × 844 jednostek, ze stałymi danymi i stanem. W trybie testowym Androida przyjmujemy 390 × 844 dp obszaru projektu, fontScale 1.0, jasny motyw, język polski, stały zegar i wyłączone animacje. Fizyczny rozmiar zrzutu zależy od density; obrazy porównuje się po kontrolowanym przeliczeniu do tej samej siatki. Nie utożsamiać CSS px z fizycznym pikselem każdego telefonu.

Zgodność obejmuje geometrię, łamanie tekstów, kolejność warstw, ostrość treści nad szkłem, tło, światło na krawędziach i stany zaznaczenia. Każdy widoczny element musi mieć odpowiednik. Nie wolno zastępować własnych ikon przypadkowymi ikonami systemowymi, zmieniać proporcji dolnej nawigacji ani usuwać etykiet dla wygody implementacji.

### Typografia i referencyjny renderer

HTML deklaruje `-apple-system, BlinkMacSystemFont, 'SF Pro Text', Inter, system-ui, sans-serif`. Oznacza to różny wybór fontu zależnie od systemu. Osadzone pliki Inter nie wymuszają użycia Inter, gdy wcześniejszy font jest dostępny. Załączone zrzuty wykonano w Chrome na macOS; są dokumentacją dostarczonego pliku, a nie potwierdzeniem natywnego renderu Androida.

**Decyzja G01:** roboczo użyć Inter na Androidzie, z wagami 400, 500, 600, 700 i 800. Przed zamrożeniem testów CH002 wyrenderować oryginalny HTML na referencyjnym Androidzie i ustalić font faktycznie użyty przez przeglądarkę. Osobno wykonać podgląd przy wymuszonym Inter, jeśli potrzebna jest ocena różnicy względem macOS. Nie uznawać zamiany fontu za zgodność bez porównania. Jeżeli wymagana jest dokładnie typografia z renderu macOS, pozyskanie odpowiedniego fontu i praw do jego dystrybucji staje się osobnym warunkiem, nie domyślną częścią eksportu.

W Compose ustawić jawne metryki tekstu, wysokości linii, odstępy liter, brak dodatkowego font paddingu i zasady przycinania. Zmierzyć baselines, polskie znaki, cyfry i pozycję wielokropka. Nie mapować wszystkich napisów na standardowe style Material. Pliki WOFF2 z pakietu są zasobami web; zasób Android TTF/OTF musi odpowiadać tym samym metrykom. Zweryfikować pokrycie polskich znaków i zachować OFL.

### Proponowane kryteria pomiaru

Poniższe progi są kontraktem projektu, nie gwarancją identycznego rasteryzowania HTML i Compose. Przed wdrożeniem uzgodnić raport CH002 z właścicielem produktu.

| Obszar | Kryterium odbioru przy 390 × 844 |
| --- | --- |
| Geometria | Położenie i rozmiar kluczowych elementów różnią się najwyżej o 1 jednostkę projektu |
| Tekst | Identyczna treść i liczba linii; baseline do 1 jednostki; bez przyciętych znaków |
| Kolory płaskie | Różnica kanału RGB do 3 z 255 na wnętrzu próbek, bez krawędzi i cieni |
| Efekt szkła | Właściwy obszar tła, maska i kolejność; ostry tekst; wizualna kontrola na urządzeniu |
| Raport obrazów | Overlay 50 procent i mapa różnic dla każdego ekranu; SSIM co najmniej 0.99 jako sygnał pomocniczy |
| Zakres | Wszystkie 10 plansz, bez selektywnego pomijania trudnych ekranów |

SSIM nie zastępuje oceny: duże jednolite tło może ukryć istotną różnicę małej ikony. W raporcie osobno sprawdzać nagłówek, karty, formularz, dock i obszary tekstowe. Wyjątki dotyczą wyłącznie udokumentowanych różnic rasteryzacji i systemowych pasków; brak automatycznego aktualizowania wzorców po regresji.

### Różne urządzenia i dostępność

Na szerokości 360–430 dp zachować marginesy, promienie i hierarchię, a środkowe obszary rozciągać. Na wysokości mniejszej od wzorca treść przewija się pod stałym nagłówkiem i kompozytorem. Nie skalować całego ekranu jak zdjęcia. Na dużym ekranie roboczo wyśrodkować panel o maksymalnej szerokości 480 dp; układ dwóch kolumn wymaga osobnego projektu.

HTML ukrywa nadmiar treści. W aplikacji lista rozmów, historia, ustawienia i zawartość arkuszy mają być przewijalne. Dolny padding listy musi pozwolić podnieść ostatni element ponad dock. Po otwarciu IME pole tekstowe i wysyłka pozostają widoczne. Systemowe paski i wycięcia obsługiwać przez jeden mechanizm insets, bez podwójnego paddingu. Referencyjne puste miejsce 50–60 px u góry nie jest narysowanym paskiem systemowym.

Przy większym foncie dopuszczamy zmianę wysokości wierszy; czytelność i dostęp do akcji mają pierwszeństwo przed zrzutem fontScale 1.0. Obszary dotyku co najmniej 48 dp można rozszerzyć niewidocznie, o ile nie nachodzą na siebie. Dla gęstych segmentów i małych przycisków może być potrzebne poszerzenie układu w trybie dostępności.

## 4 System wizualny odczytany z HTML

### Kolory i typografia

| Token | Wartość źródłowa | Użycie |
| --- | --- | --- |
| accent | #0A6CFF | Aktywne zakładki, wysyłka, dymki, liczniki |
| link | #0058D6 | Odsyłacze i drugorzędne akcje |
| textPrimary | #0B0D14 | Główna treść |
| textSecondary | #3A4054 | Podglądy, opisy i metadane |
| textTertiary | #2A3042 | Statusy i podpisy na szkle |
| error | #C0262D | Obramowanie nieudanej wysyłki |
| errorText | #A3141B | Komunikat i ponowienie |
| draft | #B4410F | Etykieta szkicu |
| decision | #9A3A0B | Stan wymagający decyzji |
| backgroundMain | #EDF0F7 | Baza głównych ekranów |
| backgroundConversation | #EEF1F8 | Baza rozmowy i arkuszy |

W schemacie edytora są również akcenty #5E5CE6, #0B7A52 i #C8402B. Nie ma widocznego selektora tych akcentów w dziesięciu planszach; nie dodawać go do głównego interfejsu bez projektu.

| Rola tekstu | Rozmiar i wysokość linii | Waga i szczegóły |
| --- | --- | --- |
| Nagłówek listy | 34 / 41 | 700, letter spacing −0.02 em |
| Nagłówek Wygląd | 28 / zgodnie z pomiarem | 700, −0.02 em |
| Nazwa kontaktu | 16 | 600 albo 700 dla stanu nieprzeczytanego |
| Podgląd wiadomości | 14 / 19 | 400 albo 500, maksymalnie 2 linie według wiersza |
| Dymek i treść pola | 16 / 21 dla dymku | 400 |
| Nagłówek arkusza | 17 | 700 |
| Etykieta sekcji | 13 | 600–700, wielkie litery, 0.04 em |
| Metadane | 11–13 | Wartość zależy od elementu |
| Dock | 11 | 600 aktywny, 500 pozostałe |

### Tła gradientowe

Główne tło Skrzynki, Startu i Wyglądu składa się z warstw radialnych nakładanych w kolejności CSS od pierwszej na wierzchu. Każda elipsa przechodzi od koloru do przezroczystości przy stopie 70 procent. Geometrię wyznaczać względem rozmiaru panelu, nie rozmiaru fizycznego ekranu.

| Warstwa | Promienie elipsy względem panelu | Środek | Kolor |
| --- | --- | --- | --- |
| 1 | 65 procent szerokości i 38 procent wysokości | 8 procent i 6 procent | #9CC3FF |
| 2 | 55 i 35 procent | 100 i 28 procent | #FFC2AE |
| 3 | 70 i 40 procent | 12 i 80 procent | #CDBFFF |
| 4 | 60 i 35 procent | 96 i 98 procent | #9FE3DA |

Rozmowa i arkusze: elipsy 70/40 przy 0/20 w #B9D3FF, 60/40 przy 100/55 w #FFD3C4 oraz 80/40 przy 30/100 w #D6CBFF. Ekrany Zaplanowane, Wyszukiwanie, Nowa wiadomość i Ustawienia mają odrębne pozycje gradientów; dokładne deklaracje należy pobrać z odpowiadających im plików referencyjnych. Nie używać jednego przybliżonego tła na wszystkich planszach. Elipsy CSS wymagają skalowania współrzędnych w Canvas lub shaderze; pojedynczy okrągły radialGradient nie daje tego samego wyniku.

### Szkło i geometria

| Komponent | Parametry źródłowe |
| --- | --- |
| GlassControl | Gradient 160 stopni biały alpha 0.74 → 0.44; blur 28 px; saturacja 170 procent; border 1 px biały alpha 0.85 |
| GlassControl cień | Inset 0 1 0 biały alpha 0.95 oraz 0 6 18 rgba 27 35 74 0.12 |
| GlassCard skrzynki | Biały 0.78 → 0.52; blur 28; saturacja 170; border 1 biały 0.85; radius 28 |
| GlassSheet | Gradient 170 stopni biały 0.86 → 0.70; blur 40; saturacja 180; border biały 0.95; radius 40 |
| GlassSheet cień | Inset 0 1 0 biały oraz 0 −10 40 rgba 14 18 32 0.18 |
| Dock | Wysokość 62; radius 31; padding 4; biały 0.72 → 0.42; blur 30; saturacja 180 |
| Okrągła akcja | 44 × 44; radius 22; ikona zwykle 22 × 22 |
| Nowa wiadomość | 62 × 62; radius 31; akcent; border biały 0.60 |
| Avatar listy | 46 × 46; radius 23; inicjały 17/600 |
| Dymek odebrany | Radius 20 20 20 6; padding 9 pionowo i 14 poziomo; max width 270 |
| Dymek wysłany | Radius 20 20 6 20; ten sam padding i max width |
| Pole pisania | Min height 44; radius 22; wewnętrzna akcja 36 × 36 |
| Badge nieprzeczytanych | Min width 22; height 22; radius 11; padding poziomy 6 |

Tabela podaje deklaracje CSS. HTML nie ustawia globalnego border box dla wszystkich elementów: część odsyłaczy ma rzeczywisty wymiar większy o obramowanie. Wiążący jest wynik renderowania, a nie sama deklaracja width/height. Nie dodawać globalnego resetu CSS do wzorca. Odczytane w Chrome na macOS przykłady poniżej pokazują różnicę; geometrię zależną od tekstu ponownie zamrozić w CH002.

| Element | Rzeczywisty obszar x y szerokość wysokość w CSS px |
| --- | --- |
| S02 pole wyszukiwania | 16 108 358 46 |
| S02 kontener docka | 16 754 358 64 |
| S02 aktywna zakładka | 21 760 90 52 |
| S02 akcja Nowa wiadomość | 310 754 64 64 |
| S03 nagłówek | 12 50 366 50 |
| S03 odsyłacz Wróć | 12 52 46 46 |
| S03 odsyłacz Dodatki | 12 772 46 46 |
| S03 przycisk Wyślij | 337 777 36 36 |
| S04 arkusz | 8 196 374 640 |
| S05 arkusz | 8 120 374 716 |
| S09 odsyłacz Wróć | 16 50 46 46 |

Kolejność renderowania szkła: scena za panelem → pobranie właściwego fragmentu → rozmycie i saturacja → maska → gradient powierzchni → wewnętrzny refleks → obramowanie → ostra treść. Współczynniki rozmycia CSS i Androida kalibrować obrazem; ta sama liczba nie gwarantuje tej samej odpowiedzi filtra.

Nie stosować `Modifier.blur` na całej karcie z tekstem. Oficjalna dokumentacja określa ten modyfikator jako rozmycie zawartości composable; odwzorowanie `backdrop-filter` wymaga osobnego tła. Proponowany renderer rejestruje warstwy pod panelem i nie zapisuje rekurencyjnie samego panelu. Każdy panel próbuje scenę zawierającą wyłącznie elementy pod nim w kolejności Z. [Blurring in Compose](https://developer.android.com/develop/ui/compose/graphics/blur/overview).

## 5 Nawigacja i wspólne zachowania

Trzy główne sekcje: Rozmowy, Zaplanowane, Ustawienia. Każda zachowuje pozycję przewijania i własny stos. Oddzielna okrągła akcja otwiera Nową wiadomość. Start pojawia się, gdy nie zakończono konfiguracji lub brakuje roli SMS. Po odmowie roli nie wyświetlać pozornej skrzynki z prywatnymi danymi.

| Punkt wejścia | Cel | Reguła powrotu |
| --- | --- | --- |
| Start po nadaniu roli | Skrzynka | Start nie zostaje pod Skrzynką w stosie |
| Wiersz rozmowy | Rozmowa z właściwym threadKey | Zachowanie filtra i scrolla listy |
| Wyszukiwarka | Wyszukiwanie | Anuluj wraca do źródła |
| Wynik wyszukiwania | Rozmowa i podświetlona wiadomość | Back wraca do wyników i zapytania |
| Plus w rozmowie | Dodatki | Zamknięcie zachowuje szkic i załączniki |
| Zaplanuj w dodatkach | Zaplanuj wysyłkę | Back do dodatków, bez utraty danych |
| Potwierdź harmonogram | Zaplanowane | Nowa pozycja widoczna i trwała |
| Wygląd w ustawieniach | Wygląd | Back do Ustawień, zmiany zapisane |
| Powiadomienie lub SENDTO | Właściwa rozmowa lub edytor | Kontrola roli i uprawnień przed danymi |

Systemowy Back najpierw chowa IME, potem najwyższy arkusz, następnie cofa ekran. Zamknięcie edytora zapisuje szkic; usunięcie szkicu to osobna akcja. Ponowne wejście przywraca tekst, odbiorców, załączniki i wybraną SIM. Nie przekazywać pełnych treści ani numerów w publicznych adresach nawigacji; przekazywać lokalne identyfikatory.

## 6 Specyfikacja dziesięciu ekranów

### S01 Start i ograniczenia

**Wzorzec:** kolumnowy układ od y 60, marginesy 20, odstęp 14; szklana ikona 60 × 60 z promieniem 19. Nazwa Chatra, opis „Spokojne SMS i MMS. Bez reklam w rozmowach i bez zakładania konta.” Sekcje „Co działa” i „Czego nie obsługuje”. Dekoracyjne koła w tle: 220 × 220 przy top 150/right−60 oraz 200 × 200 przy top 470/left−70. Główna akcja ustawia aplikację jako domyślną SMS. Dwie dodatkowe akcje prowadzą do Prywatności i Pomocy.

**Kontrakt:** CTA uruchamia systemowy dialog roli. Po sukcesie sprawdzić dostęp, rozpocząć odczyt historii i przejść do skrzynki. Odmowa pozostawia ekran z możliwością ponowienia. Historia systemowa pozostaje na urządzeniu; nie obiecywać importu RCS. Aparat i zdjęcia nie są wymagane na tym etapie. Stan braku telefonii lub niedostępnej roli musi mieć komunikat zamiast niedziałającego CTA.

**Odbiór:** zgodne teksty i hierarchia, żadnej prośby o galerię na starcie, prawidłowy sukces/odmowa/anulowanie dialogu i działające podstrony prywatności oraz pomocy. Powrót z ustawień systemowych ponownie sprawdza rolę.

### S02 Skrzynka

**Wzorzec:** padding top 52 i sides 16, odstępy 12; tytuł „Rozmowy” i akcja więcej. Pole wyszukiwania ma deklarację 44 i rzeczywistą wysokość 46, segment 40. Główna karta radius 28 zawiera „Przypięte” i „Pozostałe”. Siedem wierszy: Mama, Ola Nowak, Paczka24, Tomek Wiśniewski, nieznany numer, Kasia Zielińska, Brygada rowerowa. Pokazane są badge 2/1/1, szkic, nieudana wysyłka, kod i grupowy MMS. Dolny dock: left/right 16, bottom 26, gap 10. Pill ma rzeczywiście 284 × 62, a okrągła akcja 64 × 64, z czego 62 jest deklarowanym rozmiarem zawartości i 2 obramowaniem. Nie centrować docka według przybliżonej geometrii 286/62.

**Kontrakt:** przypięte wątki przed pozostałymi, wewnątrz grup sortowanie malejąco po aktywności. „Nieprzeczytane · 3” liczy rozmowy z nieprzeczytaną treścią, nie sumę wiadomości; dane demonstracyjne mają cztery nieprzeczytane wiadomości w trzech wątkach. Otwarty wątek oznaczać jako przeczytany po rzeczywistym pokazaniu treści. Nowy SMS aktualizuje wiersz i licznik. Brak kontaktów pokazuje nadawcę bez zgadywania nazwy. Szkic ma pierwszeństwo w podglądzie; błąd wysyłki pozostaje widoczny jako status.

**Stany:** początkowy odczyt historii z postępem, pusta skrzynka, brak nieprzeczytanych, błąd odczytu, utrata roli, brak dostępu do kontaktów. Menu więcej oraz long press dają przypnij/odepnij, oznacz przeczytanie, przenieś do kosza i blokowanie; ich projekty są uzupełnieniem, nie częścią widocznego HTML.

**Odbiór:** 1 do 1 dla fixture; ostatni wiersz osiągalny ponad dockiem; kliknięcie każdego nadawcy otwiera jego rozmowę, a nie zawsze demonstracyjną Olę. Zmiany są widoczne bez restartu.

### S03 Rozmowa

**Wzorzec:** szklany nagłówek y 50, boki 12, przyciski 44, centralny chip 48 z avatarem 38. Treść między top 108 a bottom 150, padding 14, odstępy 6. Wczoraj/Dziś, białe odebrane dymki, niebieskie wysłane, karta HD szerokości 248 z trzema miniaturami i oznaczeniem +1. Statusy „Pobrane przez Olę · SIM 1”, „Doręczono 13:12 · SIM 1 · to nie oznacza odczytu”, błąd z Ponów oraz wysyłka odroczona z Anuluj. Kompozytor left/right 12 i bottom 26; pasek SIM32, gap 8, pole 44.

**Kontrakt:** stronicowana historia w poprawnej chronologii, grupowanie po lokalnym dniu; auto scroll tylko gdy użytkownik jest przy końcu. Nowe wiadomości poza końcem dają licznik „nowe”, bez przeskakiwania czytanej historii. Wybrana SIM widoczna przed wysłaniem. Pusty tekst bez załącznika nie wysyła. Ponów rozpoczyna nową próbę po ponownej walidacji SIM; nie duplikuje pozycji w historii. Usunięcie szkicu dopiero po trwałym przyjęciu zlecenia.

**Ważna rozbieżność wzorca:** pole zawiera „Kupię kwiaty dla Ani”, lecz etykieta jest statycznie ustawiona na „21/160 · 1 SMS”. To nie jest poprawny algorytm licznika; wiadomość ma 20 znaków i zawiera „ę”. W fixture wizualnym zachować napis źródła, w działającej aplikacji liczyć rzeczywiste kodowanie i części. Nigdy nie usuwać polskich znaków bez wyraźnego wyboru użytkownika.

**Stany:** oczekiwanie na anulowanie, przekazanie do transportu, wysłano, doręczono, błąd, częściowy błąd, brak raportu, niepewne przekazanie, brak SIM, brak roli. Pobranie HD nie jest odczytem SMS. Szczegóły rozmowy obejmują uczestników, przypięcie, SIM, blokowanie i usuwanie; widok wymaga projektu.

### S04 Dodatki i Chatra HD

**Wzorzec:** przyciemnienie rgba 14 18 32 0.28 na całym ekranie. Arkusz left/right 8, top 196, bottom 8, width 374, height 640, radius 40. Uchwyt 38 × 5, padding sides 18, odstępy 14. Cztery akcje: Zdjęcie, Aparat, Zaplanuj, Szablony. Stan wybrane 4 zdjęcia, miniatury i Zmień. Trzy wykluczające się opcje: Oryginał HD, Kopia bez lokalizacji, MMS. CTA „Wyślij 4 zdjęcia w oryginale”.

**Kontrakt:** wybór przez Photo Picker, utrzymanie dostępu URI albo kopii prywatnej. Aparat przez systemowy kontrakt lub osobno uzgodniony własny podgląd. Każdy załącznik ma typ, rozmiar, hash, dostępność i kolejność. Zmiana opcji natychmiast aktualizuje opis i CTA. Oryginał zachowuje bajty, MMS tworzy pochodną, kopia bez lokalizacji usuwa dane lokalizacyjne i ma inne oznaczenie. Usunięcie GPS musi obejmować wspierane metadane zdjęć i wideo; brak obsługi formatu nie powoduje cichej wysyłki oryginału.

**Stany:** brak zaznaczenia, utracone URI, nieobsługiwany format, przekroczony limit, błąd przetwarzania, brak internetu, oczekiwanie uploadu, częściowy upload, brak Chatry u odbiorcy. Bez Chatry pytanie o wysłanie linku; bez automatycznego przełączania na płatny MMS. Galeria w fixture to gradientowe placeholdery, nie brakujące fotografie do wygenerowania.

### S05 Zaplanuj wysyłkę

**Wzorzec:** arkusz top 120, bottom 8, boki 8, height 716, radius 40. Odbiorca Ola, podgląd treści, skróty „Jutro 8:00”, „Sob. 10:00”, „Pon. 9:00”, „Inny…”. Data sobota 31 października 2026, godzina 10:00. SIM1/SIM2. Dwie opcje po spóźnieniu: pytaj albo wyślij do 1 h. Informacja o włączonym telefonie i zasięgu. CTA z datą.

**Kontrakt:** wejście kopiuje bieżący szkic, odbiorców i SIM, nie tekst przykładu. Skróty obliczane względem aktualnej daty w strefie urządzenia; daty przykładowe są tylko fixture z 29 października 2026. Data musi być przyszła. Walidacja przed zapisem i przed wysłaniem. Po zapisie trwały rekord i alarm muszą być spójne przez mechanizm rekoncyliacji. Brak zgody na dokładne alarmy ma jawną ścieżkę; nie deklarować dokładnej wysyłki, jeśli jej nie można zaplanować.

**Strefa czasu:** roboczo zapisujemy Instant, pierwotne LocalDateTime i ZoneId. Po podróży do innej strefy utrzymujemy tę samą chwilę wysłania i pokazujemy przeliczony czas oraz strefę. Zmiana zegara wymaga ponownego zaplanowania. Nieistniejąca godzina przy zmianie DST wymaga korekty; przy podwójnej godzinie interfejs wskazuje wybrane przesunięcie.

### S06 Zaplanowane

**Wzorzec:** tytuł 34/41, opis telefonu, pomarańczowa karta „Wymaga decyzji” dla Tomka, akcje Wyślij teraz/Zmień termin/usuń. Lista „Czeka · 4” zawiera Mamę, Brygadę rowerową, Olę i Marka z terminem, kartą SIM i transportem. Pod spodem ostatnio wysłana wiadomość; na dole wspólny dock.

**Kontrakt:** lista sortowana po terminie rosnąco. Wymagające decyzji są nad oczekującymi. Edycja zwiększa wersję zlecenia i unieważnia stary alarm. Usuwanie harmonogramu nie usuwa istniejącej rozmowy ani wcześniejszych wiadomości. „Wyślij teraz” korzysta z tej samej kolejki i kontroli co kompozytor. Przy utraconej SIM użytkownik musi wybrać kartę świadomie.

**Reguła spóźnienia:** domyślna polityka to pytanie. Wariant do 1 h wysyła tylko wtedy, gdy faktyczny start mieści się w oknie i wszystkie warunki są spełnione. Po 1 h lub po niepewnej próbie potrzebna decyzja. Wariant pytaj nigdy nie uruchamia zaległej wysyłki automatycznie po restarcie. Pusty harmonogram, oczekiwanie na alarm, odmowa uprawnienia i awaria wysyłki mają oddzielne komunikaty.

### S07 Wyszukiwanie

**Wzorzec:** padding 54/16, pole 44 i Anuluj. Zapytanie „paczk”, filtry Kody/Przesyłki/Załączniki/Od/Kiedy. Wybrane Przesyłki. Wyjaśnienie lokalnego wyszukiwania ignorującego wielkość liter i polskie znaki. Karta szybkich trafień z kodem 482915 i automatem KRA01M, dwa przyciski Kopiuj. Lista czterech wiadomości z wyróżnieniem fragmentów.

**Kontrakt:** indeks lokalny, opóźnienie wyszukiwania roboczo 200 ms, anulowanie poprzedniego zapytania. Normalizacja casefold i jawne mapowanie ą ć ę ł ń ó ś ź ż; sam strip combining marks nie obsłuży „ł”. Wyszukiwanie prefiksów tokenów, np. paczk → paczkę/PACZKA; nie obiecywać pełnej analizy odmiany języka. Zachować mapę pozycji w oryginalnym tekście do podświetleń. Filtry łączone AND; rodzaje treści jako pojedynczy wybór z możliwością odznaczenia. Zakres dat w lokalnej strefie, przeliczony na granice Instant.

**Szybkie trafienia:** rozpoznawanie kodu i automatu regułami lokalnymi, z podaniem nadawcy i wiadomości źródłowej. Nie traktować każdej liczby jako kodu. Kopiuj kopiuje dokładną wartość i potwierdza akcję bez ujawniania całej treści w komunikacie. W razie niepewnego rozpoznania pokazać wyłącznie wiadomości. Wynik otwiera właściwy rekord i przewija do niego. Wiadomości w koszu i od zablokowanych zgodnie z przyjętą polityką nie trafiają do głównych wyników.

### S08 Nowa wiadomość

**Wzorzec:** uchwyt, Anuluj, tytuł 17/700, chipy Mama/Ola/Tomek, pole Dodaj odbiorcę. Wybrany tryb osobnych SMS, alternatywa grupowy MMS z ostrzeżeniem o numerach. Sugerowane Kasia i Marek. Na dole SIM i „3 osobne SMS”, pole tekstu i Wyślij.

**Kontrakt:** obsłużyć wpisanie numeru, wklejenie, wybór kontaktu z kilkoma numerami, deduplikację i usuwanie chipa. Numer alfanumerycznego nadawcy może być nieodpowiadalny; nie traktować go jak numeru wyjściowego. Bez zgody na kontakty nadal można wpisać numer. Jedna osoba nie wymaga wyboru trybu. Dla wielu osób domyślnie osobne SMS. Tryb grupowy ujawnia uczestników i wymaga świadomego wyboru.

Wysyłka osobna tworzy operację nadrzędną i wynik dla każdego odbiorcy. Częściowy sukces nie ponawia wysłanych SMS. Dodanie zdjęcia w trybie osobnym wymaga jawnego wyboru osobnych MMS lub HD, nie cichej zmiany transportu. Dla szacowania kosztu pokazać liczbę odbiorców × części na numer; aplikacja nie zna taryfy i nie podaje zmyślonej ceny. Pusty tekst i brak załączników blokują wysyłkę.

### S09 Wygląd

**Wzorzec:** padding 50/16, powrót 44, tytuł 28. Podgląd 196/radius 28. Cztery kafelki wysokości 84: Szkło, Mgła, Grafit, Kontrast. Tryb Jasny/Ciemny/Auto; gęstość Standardowa/Kompaktowa; suwak 1–7 z wartością 4. Przezroczystość i kontrast wyłączone. Reset.

**Kontrakt:** zmiany stosowane od razu i utrwalane w DataStore. Reset ustawia Szkło/Jasny/Standardowa/4/przezroczystość normalna/kontrast normalny, zgodnie ze wzorcem. Auto śledzi systemowy motyw. Robocze mnożniki tekstu: 0.85, 0.90, 0.95, 1.00, 1.10, 1.20, 1.30; są propozycją do potwierdzenia, niezdefiniowaną przez HTML. Mnożnik aplikacji współdziała ze skalą systemową, nie zastępuje jej.

Pozostałe kompozycje i tryb ciemny mają w HTML tylko nazwy i małe próbki. Pełnych ekranów tych wariantów nie ma; potrzebują tokenów i odrębnego odbioru. Ograniczenie przezroczystości zamienia panele na matowe, zwiększenie kontrastu dobiera czytelne tokeny; nie reklamować tych celowych wariantów jako identycznych z domyślnym Szkłem. Dostępność pozostaje bezpłatna, także gdy Pro obejmuje dodatkowe kompozycje.

### S10 Ustawienia

**Wzorzec:** padding 52/16, tytuł 34/41. Karta Chatra Pro z jednorazową płatnością i literalnym placeholderem [CENA]. Sekcja Wysyłanie: SIM, opóźnienie 3 s, włączony licznik SMS, HD. Wygląd i porządek: Wygląd, nieznani nadawcy, zablokowani 3, kosz 4/30 dni. Prywatność i dock.

**Kontrakt:** wszystkie podsumowania wynikają ze stanu, a liczby demonstracyjne nie są stałymi. Każdy wiersz otwiera funkcjonalny podwidok. Cena pobrana z oferty sklepu, sformatowana dla regionu; brak oferty pokazuje niedostępność zakupu. Nie publikować [CENA]. Zakup Pro jednorazowy, przywracanie uprawnienia i obsługa oczekiwania/odrzucenia. Zakres reguł Pro wymaga decyzji; podstawowe SMS/MMS, wyszukiwanie, harmonogram i dostępność roboczo bezpłatne.

Ustawienia SIM obejmują domyślną kartę i preferencję rozmowy; opóźnienie roboczo 0/3/5/10 sekund. Nieznani pozostają oznaczeni w skrzynce, bez automatycznego uznania za spam. Prywatność wyjaśnia lokalną historię, HD, przechowywanie i usuwanie. Pomoc potrzebuje docelowego kanału kontaktu; nie wymyślać adresu e-mail.

## 7 Architektura Androida

### Wybór technologii

Propozycja: Kotlin, Jetpack Compose, jedna Activity, ViewModel, Coroutines/Flow, Room, DataStore i wstrzykiwanie zależności. Warstwa UI otrzymuje niezmienny UiState i emituje akcje. Logika wysyłania i harmonogramu nie należy do composable ani do timera ekranu. UI, dane i logikę rozdzielamy zgodnie z [zaleceniami architektury Androida](https://developer.android.com/topic/architecture/recommendations).

**Decyzja G02:** roboczo minSdk 31, aby zawęzić implementację GPU szkła. To wybór zakresu produktu, nie wymóg SMS. Jeśli potrzebne są starsze urządzenia, CH003 ma określić koszt renderera; matowy fallback nie spełnia wymagania pełnego szkła. compileSdk/targetSdk i wersje Kotlin/AGP/Compose BOM ustalić w CH004 z aktualnych stabilnych wydań i wymagań publikacji. Nie zakładać, że target 31 jest właściwy tylko dlatego, że minSdk 31.

Compose jest rekomendacją. CH003 porównuje najtrudniejsze ekrany S02/S04 z wariantem WebView i mierzy zgodność oraz koszt renderowania. WebView może zachować CSS, ale nadal wymaga całej natywnej warstwy SMS i bezpiecznego mostu. Zmiana technologii jest dopuszczalna wyłącznie z dowodem, że spełnia kontrakt, nie przez zaakceptowanie mniej wiernego ekranu. W wariancie WebView tylko lokalna zaufana zawartość ma dostęp do mostu, a link HD z internetu nigdy go nie otrzymuje.

### Podział odpowiedzialności

| Moduł | Odpowiedzialność |
| --- | --- |
| app | Składanie zależności, Activity, wejścia systemowe, warianty demo i produkcyjny |
| core model | Encje, typowane ID, statusy, błędy domenowe |
| core designsystem | Tokeny, ikony, tła, szkło, dostępne komponenty |
| core telephony | Rola SMS, provider, subskrypcje, SMS/MMS, odbiorniki |
| core database | Room, transakcje lokalne, migracje, indeks |
| core scheduling | Kolejka, alarmy, rekoncyliacja, zegar, polityki spóźnień |
| core notifications | Kanały, akcje, deep linki i prywatność blokady ekranu |
| feature inbox conversation compose | Skrzynka, historia, nowa wiadomość i szkice |
| feature attachments hd | Media, warianty plików, upload i statusy HD |
| feature search schedule settings | Wyszukiwanie, harmonogram, ustawienia i wygląd |
| core testing | Fixture, fake transport, kontrolowany zegar, raporty obrazów |

Repository dostarcza dane jako Flow; use case waliduje i wykonuje operację. Transporty `SmsTransport`, `MmsTransport`, `HdTransport` mają wspólny kontrakt zlecenia, ale odrębne wyniki i możliwości. Błędu HD nie mapować na błąd SIM. Logi mogą zawierać losowe correlationId i kody techniczne, bez treści wiadomości, kodów odbioru i pełnych numerów.

### Źródła prawdy

Telephony Provider jest kanoniczną historią SMS/MMS. Room trzyma metadane Chatry, harmonogram, szkice, stan operacji, indeks i wiadomości HD. Indeks jest odtwarzalną projekcją, nie niezależną historią. Identyfikator SMS i MMS przechowywać razem z rodzajem providera, ponieważ te same liczby mogą istnieć w różnych tabelach. Pierwszy odczyt stronicowany; później ContentObserver i rekoncyliacja. Model odczytu i wymagane komponenty klienta wynikają z [Telephony](https://developer.android.com/reference/android/provider/Telephony).

Nie importować historii przez kasowanie ani przepisywanie istniejących rekordów. Porażka indeksowania nie może usunąć SMS. Wątki grupowe identyfikujemy ustalonym zbiorem uczestników i właściwym thread ID providera; dane HD wiążemy z lokalnym conversationId. Po zmianach z innej aplikacji odświeżamy projekcję. Po utracie roli nie wykonujemy zależnego od niej odczytu ani zapisu; po odzyskaniu przeprowadzamy ponowną synchronizację.

## 8 Integracja z systemem Android

### Rola i uprawnienia

Na API29+ użyć RoleManager: sprawdzenie dostępności i posiadania ROLE_SMS, następnie createRequestRoleIntent. Odmowa i anulowanie to normalne wyniki. Kontrola roli również przy wznowieniu aplikacji i bezpośrednio przed wysyłką. [RoleManager](https://developer.android.com/reference/android/app/role/RoleManager).

Manifest kwalifikującego się klienta obejmuje receiver SMS_DELIVER chroniony BROADCAST_SMS, receiver WAP_PUSH_DELIVER z MIME application/vnd.wap.mms-message chroniony BROADCAST_WAP_PUSH, Activity SENDTO dla sms/smsto/mms/mmsto oraz Service RESPOND_VIA_MESSAGE chroniony SEND_RESPOND_VIA_MESSAGE. Jawnie ustawić exported i walidować zewnętrzne wejścia. Ochrona komponentu `android:permission` nie jest prośbą runtime o to uprawnienie. [Wymagania Telephony](https://developer.android.com/reference/android/provider/Telephony).

| Dostęp | Moment i zachowanie po odmowie |
| --- | --- |
| Grupa SMS potrzebna klientowi | Po uzyskaniu roli; minimalny zestaw READ/SEND/RECEIVE SMS oraz RECEIVE MMS/WAP PUSH zależnie od mechanizmu platformy; ponownie sprawdzić rzeczywiste granty |
| Kontakty | Przy wyborze kontaktu; wpisanie numeru nadal działa bez zgody |
| READ PHONE STATE | Gdy wymagane do listy subskrypcji; wyjaśnienie wyboru karty, bez pobierania zbędnych identyfikatorów |
| Powiadomienia | W kontekście odbioru i decyzji o harmonogramie na wersjach wymagających zgody |
| Zdjęcia | Photo Picker i dostęp do wybranych URI, bez szerokiego żądania galerii |
| Aparat | Tylko jeśli własny podgląd go wymaga; systemowy kontrakt ma osobną ocenę |
| Dokładne alarmy | Specjalny dostęp przy planowaniu, osobna obsługa cofnięcia |
| Internet i stan sieci | Dla HD oraz potrzebnych integracji, bez blokowania SMS przy braku internetu |
| Odbiór restartu | Odtworzenie przyszłych harmonogramów po BOOT COMPLETED; dane po odblokowaniu zgodnie z wybranym modelem |

Rozdzielić nadanie roli i aktualny stan grantów; nie wyświetlać zbędnych dialogów, jeżeli platforma przyznała dostęp. Klient musi spełnić zasady sklepu i deklarować użycie uprawnień SMS jako podstawową funkcję. Po utracie roli zatrzymać zależne przetwarzanie i automatyczną wysyłkę. [Zasady Google Play dla SMS](https://support.google.com/googleplay/android-developer/answer/10208820). Ostateczny audyt zasad powtórzyć przed publikacją.

### SMS i dual SIM

Tworzyć SmsManager dla wybranego subscriptionId; tekst dzielić platformowym algorytmem, a wiadomości wieloczęściowe wysyłać jako multipart. Callback przekazania i callback doręczenia oznaczają różne etapy; raport doręczenia może nie nadejść. MMS sendMultimediaMessage przyjmuje URI poprawnie zbudowanego PDU, nie dowolne URI JPG. [SmsManager](https://developer.android.com/reference/android/telephony/SmsManager).

Kontrakt Chatry dodaje trwały SendOperation, Attempt i status każdej części oraz odbiorcy. Etykieta „SIM 1” nie identyfikuje trwale karty. Zapisywać subscriptionId, migawkę nazwy, opcjonalną preferencję rozmowy i stan dostępności; po zmianie SIM wymagać potwierdzenia zamiast cichego przełączenia. Nie ufać, że numer telefonu można odczytać z SIM. Wcześniejsza preferencja rozmowy nie nadpisuje świadomego wyboru w szkicu.

Licznik rozróżnia znaki widoczne, jednostki kodowania i części. Znaki rozszerzone GSM7 mogą zajmować dwie jednostki, Unicode zmienia pojemność, a emoji wymagają szczególnych testów. Wynik bazować na platformie i udokumentować prezentację, zamiast stosować samo length/160. Opcja transliteracji tworzy podgląd i wymaga zgody; przechować oryginalny szkic.

### MMS i media

Dedykowany adapter buduje/odczytuje PDU, części tekstowe, obrazy, miniatury i nagłówki grupy. Podczas odbioru zapisuje i pobiera zgodnie z dostępną rolą; obsługuje APN, brak danych mobilnych, timeout, roaming i brak wsparcia operatora. Limity pobierać dla aktywnej subskrypcji, nie zakładać jednego rozmiaru. [CarrierConfigManager](https://developer.android.com/reference/android/telephony/CarrierConfigManager).

Przed kompresją pokazać, że MMS jest kopią. Kompresja nie modyfikuje oryginału. Dla wideo i grup ustalić granice w CH001/CH032. Transport ma działać na fizycznych urządzeniach operatorów objętych wsparciem; testy na emulatorze nie stanowią dowodu dostarczenia MMS. Import i odbiór wspieranych MMS muszą zachować tekst, uczestników i dostęp do części również po restarcie.

### Powiadomienia i wejścia z systemu

Kanały robocze: nowe wiadomości, problemy wysyłki, harmonogram wymagający decyzji i postęp HD. Powiadomienia wiadomości grupować według conversationId, otwierać właściwy wątek i anulować po oznaczeniu przeczytania. Szybka odpowiedź korzysta z tego samego use case i SIM co ekran, ma kontrolę blokady urządzenia i nie ujawnia treści bez ustawienia użytkownika. SENDTO obsługuje numer i opcjonalny szkic; nie wysyła automatycznie. Załączniki udostępniane z innych aplikacji walidować pod względem MIME, rozmiaru i uprawnień URI.

## 9 Trwała wysyłka i harmonogram

### Granica anulowania

Kliknięcie Wyślij utrwala operację w stanie PENDING_UNDO i zapisuje termin. UI odlicza do tego terminu, a nie do własnego nietrwałego timera. Anuluj wykonuje atomowe przejście wyłącznie, jeśli nie rozpoczęto DISPATCHING. Po skutecznym anulowaniu przywrócić edytowalny szkic. Po granicy przekazania nie obiecywać cofnięcia wiadomości.

Po upływie 3 s transport może ruszyć później z powodu ograniczeń systemu; nie może ruszyć wcześniej. Po utracie procesu w oknie anulowania roboczo zatrzymać automatyczną wysyłkę i pokazać decyzję, jeśli nie da się wiarygodnie odtworzyć granicy. Zdefiniować i przetestować to jako gwarancję produktu. Dla zegara 3 s w działającym procesie używać czasu monotonicznego, a zapis trwały wiązać z identyfikatorem uruchomienia urządzenia.

### Stany i reguły

| Stan | Znaczenie i dozwolone przejście |
| --- | --- |
| DRAFT | Edycja; przejście do PENDING UNDO albo SCHEDULED |
| PENDING UNDO | Zlecenie jeszcze nieprzekazane; anulowanie lub rozpoczęcie |
| SCHEDULED | Oczekuje na termin; edycja/anulowanie lub DUE |
| DUE | Termin osiągnięty; walidacja roli, SIM, polityki i warunków |
| DISPATCHING | Przekazywanie transportowi; drugi wykonawca nie może przejąć zadania |
| SENT | Przyjęte według callbacku transportu; nie oznacza odczytu |
| DELIVERED | Raport doręczenia, jeżeli dostępny |
| FAILED | Pewna porażka z kodem i możliwością świadomego ponowienia |
| PARTIALLY SENT | Sukces tylko części lub odbiorców; zachować szczegóły |
| MISSED NEEDS DECISION | Spóźnienie lub niespełnione warunki wymagające użytkownika |
| DISPATCH UNCERTAIN | Nie wiadomo, czy transport przyjął; bez automatycznego retry |
| CANCELLED | Nieprzekazane zlecenie anulowane, bez operacji transportu |

Nazwy stanów w kodzie mogą używać podkreśleń. Przejścia wymagają numeru wersji i porównania oczekiwanego stanu w transakcji Room. Callbacki idempotentne; nie cofają DELIVERED do SENT. Nie ma atomowej transakcji między Room a operacją telephony. Awaria po przekazaniu może zostawić niepewność; globalna gwarancja „dokładnie raz” nie jest uzasadniona. Zmniejszać ryzyko przez pojedynczego wykonawcę, identyfikator próby i rekoncyliację, a przy niepewności wymagać decyzji.

### Mechanizm wykonania

Trwały rekord + AlarmManager dla wskazanego terminu, jeżeli dokładne alarmy są dostępne. WorkManager służy do rekoncyliacji i prac odraczalnych; nie gwarantuje konkretnej minuty. Sprawdzać canScheduleExactAlarms, obsłużyć odmowę i ponownie odtworzyć alarmy po restarcie oraz zmianie dostępu. Nie przyjmować automatycznie USE_EXACT_ALARM jako uprawnienia właściwego dla klienta SMS. [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms).

Receiver alarmu wykonuje krótką walidację i przekazanie do odpowiedniego mechanizmu; nie prowadzi długiego uploadu w onReceive. Prace długie muszą spełniać aktualne wymagania usług/transferów w tle. Po BOOT_COMPLETED, aktualizacji, zmianie czasu, SIM, roli i uprawnień rekoncyliować rekordy. Telefon wyłączony, force stop, brak odblokowania po restarcie i ograniczenia producenta są osobnymi przypadkami; nie obiecywać wysyłki z wyłączonego telefonu.

## 10 Model danych i trwałość

Poniższy model jest projektem implementacji. Schematy migracji, indeksy i ograniczenia unikalności muszą powstać przed produkcyjnymi danymi. Wrażliwa baza, pliki i indeks pozostają w prywatnej przestrzeni aplikacji; roboczo wyłączone z automatycznego backupu do czasu zaprojektowania świadomego odtwarzania.

| Encja | Klucz i najważniejsze pola |
| --- | --- |
| ConversationMeta | conversationId UUID, providerThreadId opcjonalnie, participantsKey, pinnedAt, preferredSubscriptionId, trashedAt, purgeAfter, version |
| ProviderMessageRef | transport SMS lub MMS, providerId, providerUri, threadId, projectionUpdatedAt; unikalne transport plus ID |
| Draft | draftId, conversationId lub lista odbiorców, text, recipientMode, attachmentIds, subscriptionId, updatedAt |
| SendOperation | operationId UUID, transport, payloadSnapshotRef, recipientMode, state, version, undoDeadline, createdAt |
| SendRecipient | operationId plus recipientKey, address, providerRef, aggregateState |
| SendAttempt | attemptId, operationId, recipientKey, subscriptionSnapshot, startedAt, resultCode, uncertaintyReason |
| SendPart | attemptId plus partIndex, totalParts, sentStatus, deliveryStatus, callbackToken |
| ScheduledMessage | scheduleId, operationId lub snapshot, instant, localDateTime, zoneId, latenessPolicy, version, nextAlarmToken |
| Attachment | id, privateUri, sourceUri opcjonalne, MIME, bytes, SHA256, variant, locationSanitization, accessState |
| HdEnvelope | messageId, conversationId, senderBinding, recipientBindings, transferIds, downloadedAt, expiry, dedupeKey |
| SearchDocument | providerRef albo hdMessageId, normalizedBody, originalBodyRef, tokenOffsets, timestamp, senderKey, classifiers |
| BlockRule | normalizedAddress, scope lokalny lub systemowy, createdAt, reason opcjonalnie |
| Template | templateId, title, body, sortOrder, updatedAt |
| Preferences | theme, mode, density, textStep, reduceTransparency, increaseContrast, undoSeconds, hdDefaults |

Provider ID może zmienić się po odtworzeniu historii; nie traktować go jako globalnej tożsamości poza instalacją. Indeks usuwa rekordy po twardym usunięciu i ukrywa kosz. Pliki tymczasowe mają limit rozmiaru i czyszczenie, ale nie usuwać pliku potrzebnego aktywnej próbie. Migracja przerwana awarią musi być odtwarzalna; bez destructiveMigration dla danych użytkownika. Bezpośrednio po czystej instalacji nie przejmować automatycznie starych harmonogramów z niezaufanego backupu.

## 11 Chatra HD i kontrakt backendu

### Granica lokalnej aplikacji

Wzorzec obiecuje oryginalne zdjęcia i filmy przez internet, rozpoznanie odbiorcy Chatra oraz status pobrania. Nie zawiera serwera, endpointów ani protokołu bezpieczeństwa. Pełne HD wymaga osobnej usługi, magazynu plików i strony odbioru linku. SMS/MMS nie zależy od dostępności backendu Chatry.

**Propozycja robocza:** brak konta z hasłem; lokalna instalacja z kluczem w Keystore i świadome włączenie HD. Powiązanie numeru dopiero po weryfikacji jego posiadania, ze zgodą na odkrywalność. To nadal rejestracja instalacji i numeru, co trzeba jasno wyjaśnić w prywatności. Numer odczytany z SIM nie dowodzi posiadania. Weryfikacja, koszty dostawcy i odzyskanie po utracie telefonu są decyzjami G06.

Nie przesyłać całej książki kontaktów. Capability lookup obejmuje tylko adresowanego odbiorcę lub jawnie wybrane kontakty, z ograniczeniami częstotliwości i ochroną przed enumeracją. Proste haszowanie numerów nie daje anonimowości wobec słownika wszystkich numerów.

### Proponowane operacje API

Nazwy ścieżek są projektem, nie istniejącymi endpointami. Szczegóły w OpenAPI w CH047. Operacje biznesowe są uwierzytelnione. Wyjątkami są bootstrap rejestracji instalacji z dowodem posiadania klucza i limitami oraz celowo dostępna przez sekretny link strona odbioru. Po bootstrapie dalsze wywołania korzystają z sesji instalacji. Identyfikatory losowe; autoryzacja na obiekcie, nie wyłącznie w interfejsie.

| Operacja | Żądanie i odpowiedź | Wymóg |
| --- | --- | --- |
| POST /v1/installations | Publiczny klucz instalacji → installationId i krótka sesja | Dowód posiadania klucza, rotacja |
| POST /v1/number-challenges | Numer → challengeId i expiry | Limity, brak wycieku istnienia odbiorcy |
| POST /v1/number-verifications | challengeId i kod → potwierdzone powiązanie | Liczba prób, wygaśnięcie, odwołanie |
| POST /v1/capabilities/lookup | Wybrany odbiorca → hd lub unknown | Opt in, brak publicznej listy numerów |
| POST /v1/transfers | Manifest plików, warianty, hashe → transferId i plan uploadu | Limity, quota, Idempotency Key |
| Upload chunków i finalize | Rozmiar, offset/checksum → postęp albo gotowość | Wznawianie, integralność, wygasanie URL |
| POST /v1/envelopes | Odbiorcy i zatwierdzone transfery → messageId | Deduplikacja i outbox serwera |
| GET /v1/envelopes | Cursor → własne nowe koperty i następny cursor | Brak dostępu do cudzej historii |
| POST /v1/receipts | messageId i etap download → potwierdzenie | Podpisany odbiorca, brak udawanego odczytu |
| POST /v1/share-links | transferId → jednorazowo zwracany sekret linku | Zgoda nadawcy, expiry i revoke |
| DELETE transfer/link/binding | Własny identyfikator → wynik usunięcia | Utrata dostępu i harmonogram usuwania kopii |

Wszędzie błędy maszynowe, correlationId i retryAfter. Klient rozróżnia 401/403/404/409/413/429 i 5xx; po ponowieniu z tym samym kluczem nie powstaje drugi transfer ani druga wiadomość. Powiadomienie push to sygnał pobrania, nie kanoniczna treść. Payload bez tekstu SMS i bez sekretów linku. SMS z linkiem jest osobnym, świadomie zatwierdzonym zleceniem i może zużywać części operatora.

### Pliki i prywatność

Oryginał musi przejść porównanie SHA256 i liczby bajtów po stronie odbiorcy. Miniatury to osobne pochodne. Wariant bez lokalizacji ma nowy hash i jawny status oczyszczenia. Link jest sekretem dostępu: nie logować go, zapewnić revoke, expiry, brak indeksowania i brak zewnętrznych skryptów śledzących na stronie odbioru. Wygasły link nie ujawnia metadanych prywatnego pliku.

**Decyzja G07:** przed backendem określić limity liczby/rozmiaru plików, TTL, lokalizację przechowywania, usuwanie backupów, limit pobrań, ochronę przed nadużyciami i koszty. Roboczo nie podawać w UI liczb niepotwierdzonych przez produkt. Zastosować TLS i kontrolę dostępu; E2EE wymaga osobnego uzgodnionego protokołu, dystrybucji kluczy oraz obsługi linków. Nie obiecywać E2EE, jeżeli wdrożono tylko szyfrowanie połączenia i magazynu.

## 12 Porządek płatności i prywatność

### Kosz

Wzorzec obiecuje odzyskiwanie rozmów przez 30 dni. Roboczy kontrakt: przeniesienie do kosza ukrywa dotychczasowe wiadomości lokalnie i zapisuje zakres wiadomości objętych operacją; nie kasuje od razu providera. Nowy przychodzący SMS po przeniesieniu tworzy widoczny wątek z nową treścią, a poprzednio ukryte wiadomości pozostają w koszu. Taki model wymaga tombstone dla zbioru wiadomości, nie samego trashedAt wątku. Model danych ConversationMeta jest skrótem; CH043 rozszerza go o TrashEntry i zakres provider refs.

Po 30 dniach twardo usunąć wyłącznie objęte wpisem rekordy i ich indeks, jeżeli aplikacja ma rolę; utrata roli wstrzymuje purge. Inna aplikacja SMS może widzieć lokalnie ukryte rekordy providera — wyjaśnić to użytkownikowi. Odzyskanie usuwa tombstone, nie duplikuje historii. Trwałe usunięcie teraz wymaga potwierdzenia i pokazania zakresu. Sprzątanie nie gwarantuje dokładnej minuty 30 dnia, ale nie może nastąpić wcześniej.

### Blokowanie

Roboczo blokada lokalna Chatry: bez powiadomień i z ukryciem w głównej skrzynce, z możliwością obejrzenia i przywrócenia. Nie usuwa automatycznie wiadomości ani nie blokuje połączeń. Jeżeli produkt wybierze blokadę systemową, wyjaśnić szersze skutki oraz użyć właściwego API; to osobna decyzja G05. Nieznany numer nie oznacza spamu. Użytkownik musi móc usunąć regułę.

### Pro

Wzorzec zakłada jednorazowy zakup, dodatkowe kompozycje i reguły porządku. Nie określa ceny, identyfikatora produktu ani konkretnych reguł. G08 musi wskazać katalog funkcji przed budową paywalla. Integracja sklepu ma pobierać cenę lokalną, potwierdzać transakcję zgodnie z aktualnym API, obsługiwać oczekujące zakupy, ponowną instalację i cofnięcie uprawnienia. Testy przez środowisko testowe sklepu; build demo nigdy nie uruchamia realnego zakupu.

### Dane i logi

Treści SMS/MMS, numery i indeks nie opuszczają telefonu w ramach zwykłego wyszukiwania. HD przesyła tylko świadomie wybrane pliki i niezbędne dane routingu. Crash reporting wymaga redakcji i wyłączenia zawartości pól, a analityka nie może zbierać treści ani kodów. Udostępnianie plików przez FileProvider z minimalnym grantem URI. Nie otwierać automatycznie linków ani nie pobierać internetowych podglądów ujawniających nadawcę/odbiorcę bez przyjętej polityki.

## 13 Wymagania jakościowe i testy

### Cele wydajnościowe

Cele robocze do pomiaru na wskazanym fizycznym urządzeniu średniej klasy: pierwsza interaktywna skrzynka do 2 s z lokalną projekcją; wynik wyszukiwania P95 do 300 ms po debounce przy 50 tys. wiadomości; płynność 60 Hz z P95 czasu klatki do 16.7 ms w ustalonym scenariuszu przewijania. Pierwsze indeksowanie jest osobnym zadaniem z postępem i nie blokuje obsługi nowych SMS. Cele są wymaganiami do sprawdzenia, nie wykonanymi benchmarkami.

Nie renderować wszystkich wiadomości naraz. Miniatury dekodować do rozmiaru wyświetlania, ograniczyć cache, wspólnie buforować statyczne gradienty. Przy ograniczeniu przezroczystości pominąć kosztowne rozmycie. Android może ograniczyć zadania tła; UI nie podaje czasu dostarczenia gwarantowanego przez operatora.

### Macierz testów

| Warstwa | Minimalne pokrycie |
| --- | --- |
| Wizualna | S01–S10, wzorzec 390/844, poszczególne komponenty, raport overlay i różnic |
| Układy | 360/390/430 dp, krótki ekran, cutout, gesty i 3 przyciski, IME, obrót, fontScale 1.3/2.0 |
| SMS | Jedno/multipart, GSM rozszerzone, polskie znaki, emoji, callbacki w innej kolejności i ich duplikaty |
| SIM | Jedna/dwie/eSIM/brak, wyjęcie karty, zmiana domyślnej, brak aktywnej subskrypcji |
| MMS | Tekst/zdjęcie/wideo/grupa, zbyt duży plik, APN, brak danych, roaming, fizyczne urządzenia i operatorzy |
| Harmonogram | Alarm, odmowa/cofnięcie dostępu, restart, brak odblokowania, force stop, DST, zmiana czasu, spóźnienie 59/60/61 min |
| Odporność | Zabicie procesu przed/po granicy transportu, ponowny callback, podwójne kliknięcie, crash migracji |
| HD | Oryginalne hashe, brak GPS, resume uploadu, odbiorca offline, brak aplikacji, expiry/revoke, autoryzacja obiektowa |
| Prywatność | Brak treści w logach/backupach/push, odmowa kontaktów, niewłaściwy URI, brak niejawnego uploadu |
| Dostępność | TalkBack, role i selected state, fokus arkusza, cele dotyku, duży tekst, kontrast |

Screenshoty są podstawą weryfikacji atrybutów wizualnych Compose, ale dopiero porównanie z HTML potwierdza zgodność z tym projektem. [Screenshot testing](https://developer.android.com/training/testing/ui-tests/screenshot). Dla blur i kompozycji GPU obowiązkowo dodatkowy test emulatora/urządzenia. Złote obrazy utrzymywać osobno dla kontrolowanych rendererów; nie maskować całych sekcji, by test przechodził.

### Odbiór końcowy

Pełny odbiór wymaga: dziesięciu zgodnych ekranów, działających akcji i uzupełnionych podwidoków, realnego odbioru/wysyłki SMS/MMS, harmonogramu po restarcie, poprawnego dual SIM, lokalnego wyszukiwania, HD end to end, poprawnej obsługi Pro oraz raportu prywatności i dostępności. Brak utraty historii i brak samoczynnego retry w stanie niepewnym są warunkami krytycznymi.

Przekazanie obejmuje kod aplikacji i backendu, konfigurację bez sekretów, migracje, OpenAPI, instrukcję budowania, konfigurację testowych operatorów/SIM, build demo z fixture, podpisany pakiet dystrybucyjny w uzgodnionym kanale oraz raporty testów. Niniejsze opracowanie jest dokumentacją do realizacji; nie zawiera gotowego APK ani nie potwierdza wykonania tych testów.

## 14 Decyzje i kolejność realizacji

| Decyzja | Wariant roboczy | Zamknięcie przed |
| --- | --- | --- |
| G01 Font i renderer odniesienia | Inter na Androidzie, porównanie oryginalnego HTML na docelowym środowisku | CH007 i zamrożenie goldenów |
| G02 Zakres Androida | minSdk 31, docelowe SDK zgodne z publikacją | CH004 |
| G03 Szkło i technologia UI | Compose z osobną sceną tła, spike S02/S04 | CH008 i pełne UI |
| G04 Harmonogram | Stały Instant, domyślnie pytaj po spóźnieniu | CH035 |
| G05 Kosz i blokada | Lokalny tombstone 30 dni, blokada lokalna | CH042/CH043 |
| G06 Tożsamość HD | Opt in instalacji i weryfikacja numeru bez konta z hasłem | CH047 |
| G07 Bezpieczeństwo i limity HD | Jawne limity/TTL, TLS, E2EE do osobnego rozstrzygnięcia | CH048–CH052 |
| G08 Pro | Jednorazowy zakup; katalog i cena ze sklepu | CH045 |
| G09 Brakujące projekty | Podwidoki w tym samym systemie szkła | Implementacja danej funkcji |
| G10 Dystrybucja i wsparcie | Google Play jako założenie, lista operatorów/urządzeń do ustalenia | Plan testów i CH064 |

Brakujące materiały nie blokują przygotowania dziesięciu ekranów i szkieletu danych. Nie wolno jednak wymyślić ceny, endpointów produkcyjnych, polityki serwera czy pełnego ciemnego motywu i przedstawić ich jako danych od użytkownika. Każda decyzja ma właściciela oraz zadanie, które ją zamyka.

Kolejność: najpierw pomiar i ryzyka techniczne; następnie system wizualny i ekrany z fixture; równolegle po gotowych kontraktach system SMS i dane; później harmonogram, wyszukiwanie i MMS; następnie HD oraz Pro; na końcu kompletna walidacja i dystrybucja. Szczegółowe zależności, nakład i kryteria są w backlogu.

## 15 Rejestr źródeł i ograniczenia analizy

Źródłem wyglądu i tekstów jest wyłącznie dostarczony HTML. Źródła Androida podlinkowano przy odpowiednich faktach; dokumentację i polityki sprawdzono 29 października 2026. Bieżące wersje bibliotek, wymagania sklepu, konfiguracja operatorów i finalne ceny muszą zostać ustalone w zadaniach realizacyjnych.

Analiza statyczna REA obejmowała wyodrębnione komponenty JS dziesięciu plansz: parent Evidence `ev_3a535b88dca4e46ca65d7bd40429864a3bbad62da2dfab86f1e2f1b2ca3d1a04`, widok `ev_b711956a090e113777ef5fa1a16c7480dc1cce1e34d8bc12d5b81b50d9400d9f`. Pokrycie całego pakietu jest częściowe, ponieważ biblioteki renderujące nie były przedmiotem pełnej analizy. Obrazy i pomiary wykonano oddzielnie po uruchomieniu pliku w przeglądarce. To nie stanowi testu telefonii ani natywnej implementacji Androida.

Pakiet referencyjny zachowuje oryginalny HTML z informacją OFL, rozpakowane szablony, style inline, ścieżki ikon SVG, osadzone fonty web, obrazy plansz i pomiary z przeglądarki. Odczytane wartości są dowodem wyglądu; zaproponowane mechanizmy Androida i backendu pozostają kontraktem do implementacji.
