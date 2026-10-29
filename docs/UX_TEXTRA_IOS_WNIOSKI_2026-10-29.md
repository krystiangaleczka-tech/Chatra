# Chatra: wnioski UX z ekranów Textry i porównanie z Wiadomościami Apple

**Data:** 29 października 2026  
**Status:** uzupełnienie analityczne / propozycje do zatwierdzenia, nie ukończona implementacja  
**Dotyczy:** aplikacji Chatra, przede wszystkim Android SMS/MMS oraz opcjonalnego Chatra HD.  
**Powiązania:** [AUDYT.md](AUDYT.md), [Chatra_Dokumentacja_Android.md](implementacja_android/Chatra_Dokumentacja_Android.md), [Chatra_Backlog_Android.md](implementacja_android/Chatra_Backlog_Android.md).

## 1. Cel, materiał i granice

Celem jest wybrać najlepsze zachowania Textry ze **czterech zrzutów udostępnionych przez właściciela projektu** i sprawdzić, czy Wiadomości Apple oferują czytelniejszą lub skuteczniejszą metodę. Nie kopiujemy oprawy graficznej ani ekranów 1:1. Przenosimy idee do **istniejącego projektu Chatra — Liquid Glass**.

Zrzuty pokazują:
1. widok rozmowy: nagłówek kontaktu, historia dymków, kompozytor wiadomości, przycisk załączników i klawiatura;
2. szczegóły rozmowy: akcje wyciszania, przypinania i usuwania, przycisk połączenia, personalizacja, licznik mediów, uczestnik;
3. menu dodatkowe szczegółów: czarna lista, wysyłka e-mailem, skrót na ekran główny;
4. listę rozmów i menu główne: ustawienia, zatrzymanie/pauza, oznaczenie wszystkich jako przeczytane, udostępnienie, nowości.

**Prywatność:** dokument nie publikuje zrzutów, numerów telefonów ani treści prywatnych rozmów. Wnioski dotyczą wyłącznie widocznych kontrolek, a nie jakości dostarczania lub szybkości działania aplikacji.

**Ważne:** w repozytorium już istnieją: 10 plansz referencyjnych, ich kontrakt 1:1 oraz 64 zadania CH001–CH064. Ten dokument **nie zmienia tych wzorców, priorytetów ani gotowych decyzji**, tylko wskazuje rozszerzenia do przyszłego projektu brakujących podwidoków i osobną pulę propozycji. Funkcję obecną w dotychczasowej specyfikacji oznaczamy „JUŻ UJĘTA”, żeby nie zadawać zespołowi tej samej pracy dwa razy.

## 2. Zasada wyboru: Textra funkcjonalnie, Apple ergonomicznie, Chatra wizualnie

- **Textra:** szybkość dotarcia do funkcji i kontrola per-konwersacja. Zrzuty potwierdzają *obecność wejść do funkcji*, nie pełne zachowanie ani niezawodność.
- **Apple Messages (przewodnik iOS 27):** skupienie akcji wokół osoby lub grupy, filtrowane wyszukiwanie, czytelna galeria załączników, przewidywalne gesty, stan nieprzeczytanych, skondensowane prezentowanie zdjęć.
- **Chatra:** zachowuje własną estetykę Liquid Glass, rozmiary i kompozycję 10 wzorców. Dodatkowe akcje nie mogą zasłaniać dymków, klawiatury, przycisków krytycznych ani informacji o SIM i sposobie wysłania.
- Interfejs nie może sugerować funkcji protokołu, których kanał nie obsługuje. **Lokalna personalizacja wyglądu nie jest „wspólnym tłem” po stronie odbiorcy SMS**.

## 3. Macierz funkcji i decyzji

Legenda: **P0** – kluczowe zachowanie klienta wiadomości; **P1** – istotne rozszerzenie; **P2** – opcjonalne. Oznaczenia są **propozycją tego uzupełnienia**, nie zmianą priorytetów istniejącego backlogu.

| Obszar | Textra: obserwacja na zrzutach | Apple Messages: porównanie | Wymaganie lub decyzja dla Chatry | Status / orientacyjny priorytet |
| --- | --- | --- | --- | --- |
| Szczegóły rozmowy | Osobny ekran z kontaktem i czynnościami | Wejście przez nazwę/awatar w nagłówku, podział na załączniki i ustawienia | Jedna karta kontaktu/grupy: połączenie telefoniczne, szukanie, wyciszenie, przypięcie, media, wygląd, prywatność, blokowanie; akcje destrukcyjne niżej | ROZSZERZYĆ widok wskazany w S03 i CH020 · P0 |
| Lista i przypinanie | Widoczny przycisk pinezki | Przypięte konwersacje w wydzielonym obszarze; długie przytrzymanie | Zachować model przypięć z S02; umożliwić zmianę kolejności w obrębie przypiętych, nie kopiować wielkich okręgów Apple na siłę | JUŻ UJĘTA S02/CH011 · P0 |
| Wyciszenie | Ikona wyciszenia | Szybkie ukrywanie alertów gestem | Wyciszenie rozmowy z czytelnym statusem; opcje np. 1 h / 8 h / bezterminowo jako propozycja UX; odpowiednie zachowanie kanałów powiadomień Androida | ROZSZERZYĆ S03/CH030 · P0 |
| Media konwersacji | Wiersz „Media” i liczba elementów | Zakładki Zdjęcia, Łącza, Dokumenty oraz widok elementów w kontekście rozmowy | Ekran „Wspólne”: zdjęcia, filmy, linki, dokumenty/pozostałe; siatka miniaturek, filtrowanie, przejście do źródłowej wiadomości, zapisz/udostępnij; liczba z realnych danych | NOWY podwidok oparty na CH031/CH020 · P1 |
| Kilka zdjęć | Na ekranie rozmowy osobne treści, bez potwierdzenia algorytmu galerii | 2–3 zdjęcia jako kolaż, 4+ jako stos | Zgrupować *zestaw załączników jednej wiadomości*, bez mieszania różnych wiadomości lub dat; podgląd pełnych plików | NOWE usprawnienie renderera wiadomości · P1 |
| Wyszukiwanie | Brak zaawansowanego UI na pokazanych ekranach | Filtry osoby/rozmowy, rodzaju treści i słowa kluczowego; połączone kryteria | Zachować projekt S07, dodać w obrębie rozmowy szybkie „Szukaj”, jasne łączenie kryteriów, wynik prowadzi do konkretnego dymka; filtr SIM tylko gdy dane istnieją | JUŻ UJĘTA S07/CH038/CH039, rozszerzyć na rozmowę · P0 |
| Nieprzeczytane | Globalne „Wszystko przeczytano!” | Zaznacz jako nieprzeczytane; „Read All”; przeskok do pierwszej nieprzeczytanej | Akcja „Oznacz wszystkie jako przeczytane” z potwierdzeniem dla większego zakresu; oznaczanie jako nieprzeczytane i skok do pierwszej zaległej; nie oznaczać przed pokazaniem treści | ROZSZERZYĆ S02/CH023 · P1 |
| Personalizacja | „Personalizuj tę konwersację” | Tła dynamiczne i fotograficzne w iMessage (od iOS 26) | Motyw globalny + lokalne nadpisanie koloru, dymków, tła i dźwięku; podgląd na żywo, reset do globalnego; kontrola kontrastu i redukcji przezroczystości | ROZSZERZYĆ S09/CH040 · P1 |
| Tło u odbiorcy | Nie wynika ze zrzutów | W iMessage tło może być widoczne u pozostałych uczestników | W SMS/MMS **tylko lokalne**. Wspólne tło jedynie dla przyszłego własnego protokołu po uzgodnieniu, bez sugerowania, że działa w SMS | DECYZJA bezpieczeństwa UX · P1 |
| Gesty i menu | Większość działań schowana w menu | Swipe i długie przytrzymanie, zależnie od akcji | Gesty dla najczęstszych działań, z alternatywą w menu, możliwością cofnięcia i ustawienia działania przesunięć; bez konfliktu ze swipe w historii wiadomości | NOWE usprawnienie · P1 |
| Blokowanie i spam | „Czarna lista” | Filtry nieznanych, transakcji/promocji (zależnie od regionu), oznaczenie znanego nadawcy | Osobna sekcja Nieznane / Zablokowani, nie ukrywać kodów i dostaw domyślnie; rozdzielić „zablokuj” od „usuń” i „wycisz” | JUŻ UJĘTA CH042, rozszerzyć UX · P0 |
| Połączenie do kontaktu | Duży przycisk telefonu | Szybkie działania z karty kontaktu | Uruchomić systemowy dialer z poprawnym numerem; bez budowania modułu VoIP | ROZSZERZYĆ szczegóły S03 · P1 |
| Eksport/udostępnienie | „Wyślij jako e-mail” | Udostępnianie poszczególnych załączników i wiadomości | Udostępnij zaznaczone wiadomości/załączniki przez Android Sharesheet; pełny eksport rozmowy dopiero po projekcie zakresu, redakcji i ostrzeżeń prywatności | NOWE · P2 |
| Skrót do rozmowy | „Dodaj do Ekranu głównego” | Nie jest główną przewagą Apple na analizowanych stronach | Skrót Android do konkretnego conversationId, bez numeru telefonu i treści w identyfikatorze skrótu; obsłużyć usuniętą rozmowę | NOWE · P2 |
| Harmonogram | Brak na pokazanych zrzutach, znany z wcześniejszego audytu | „Send Later” w Apple dotyczy iMessage; Apple może dostarczyć zaplanowaną wiadomość z serwera | Pozostawić istniejący harmonogram SMS zależny od warunków Androida, SIM, zasięgu i polityki opóźnień; nie obiecywać dostarczenia gdy telefon wyłączony | JUŻ UJĘTA S05/S06/CH035–CH037 · P0 |
| Grupy | Na szczegółach widoczny uczestnik; bez dowodu właściwości grup | iMessage i RCS oferują funkcje grupowe, SMS grupowy bywa osobnymi wiadomościami | Wyraźnie rozdzielić osobne SMS, MMS grupowy i ewentualne transporty internetowe; nazwa/ikona grupy może być lokalna dla SMS/MMS | JUŻ UJĘTA S08/CH032, rozszerzyć nazewnictwo · P0 |
| Reakcje/odpowiedzi cytowane | Nie wynikają z tych zrzutów | Tapback i odpowiedzi do konkretnej wiadomości | Nie udawać interoperacyjnych reakcji i cytatów w zwykłym SMS. Własne funkcje tylko tam, gdzie dany protokół to umożliwia; ewentualny cytat w SMS to nowy tekst, oznaczony jako taki | OGRANICZENIE, nie nowe zobowiązanie |
| Wiadomości audio | Brak na zrzutach | W iMessage dostępne nagrania audio, tempo odtwarzania i „Raise to Listen” | Nie rozszerzać teraz zakresu na rozmowy głosowe; nagranie jako załącznik tylko po osobnej decyzji o transporcie, uprawnieniach i formatach | POZA ZAKRESEM pierwszego wydania |

## 4. Projekt zachowań, bez naruszania obecnych 10 plansz

### 4.1 Karta kontaktu / grupy — nowy podwidok

1. Otwierana stuknięciem w awatar/nazwę w S03 (nie przez zasłaniający ekran przycisk).
2. Górna sekcja: avatar/nazwa, numer lub lista uczestników, akcje **Zadzwoń / Szukaj / Wycisz / Więcej**. Jeżeli nadawca jest alfanumeryczny i nieobsługiwany jako adres połączenia, opcja telefonu jest niedostępna.
3. Sekcja „Wspólne” z miniaturami ostatnich załączników i wejściem do pełnej galerii.
4. Sekcja „Ta rozmowa”: przypięcie, wygląd, powiadomienia, wybór preferowanej SIM, ewentualnie lokalna nazwa grupy.
5. „Prywatność i działania”: eksport, blokowanie, usuwanie. Rozdzielić akcje odwracalne i destrukcyjne; do usuwania stosować reguły istniejącego kosza.
6. Akcje widoczne mają stan dostępności: zablokowany nadawca, brak karty SIM, brak dialera, brak uprawnień.

### 4.2 Galeria rozmowy — nowy podwidok

- Wspólne zdjęcia/wideo pokazują podgląd siatkowy, linki listę z domeną i opisem, pliki typ i rozmiar.
- Dla każdej pozycji: **Otwórz**, **Pokaż w rozmowie**, **Zapisz**, **Udostępnij**, opcjonalnie **Usuń** z rozróżnieniem usunięcia wiadomości/załącznika.
- Zestaw kilku obrazów w *jednej* wiadomości: wizualny kolaż/stos; otwarcie pokazuje wszystkie w poprawnej kolejności.
- Załącznik pobrany przez Chatra HD i załącznik MMS mają czytelny stan i źródło; nie mieszać pobrania z potwierdzeniem odczytu SMS.
- Brak zasobów, utracony URI, załącznik usunięty i nieudany transfer muszą mieć poprawny stan pusty/błędu.

### 4.3 Szybkie wyszukiwanie

- Globalne wyszukiwanie pozostaje na S07, zgodnie z umową wzorca.
- W karcie rozmowy „Szukaj” uruchamia ten sam silnik z wypełnionym conversationId i możliwością dalszego zawężenia.
- Filtry po osobie, dacie, typie treści i tekście stosować łącznie (AND), z widoczną możliwością zdjęcia filtra.
- Wynik otwiera **konkretną wiadomość** (z wyróżnieniem), nie tylko górę wątku.
- Dla wyszukiwania obrazu po jego zawartości, OCR i semantyki AI utworzyć osobny eksperyment z ochroną prywatności; nie obiecywać jako dostępne w pierwszym wydaniu.

### 4.4 Gesty, przypięcia i nieprzeczytane

- Długie przytrzymanie wiersza: przypnij/odepnij, wycisz, przeczytane/nieprzeczytane, usuń, blokuj.
- Przesunięcie wiersza: maksymalnie 1 szybka akcja na kierunek; fabryczne mapowanie i możliwość konfiguracji zatwierdzić dopiero po teście użyteczności.
- Przypięte konwersacje zachowują hierarchię istniejącego S02; reorganizacja nie może zmienić kolejności pozostałych.
- Otwieranie historii nie powinno skakać na dół, gdy użytkownik czyta starsze wpisy. „Nowe” / „Pierwsza nieprzeczytana” musi działać po dopisaniu wiadomości.
- Wszystkie działania gestami muszą mieć odpowiednik dostępny przy pomocy dotyku i czytnika ekranu.

### 4.5 Personalizacja z przewagą nad Textrą i Apple

- Globalny preset: Szkło / Mgła / Grafit / Kontrast z S09.
- Nadpisania dla jednej rozmowy: kolor akcentu, tło (kolor/gradient/zdjęcie), wariant dymków, dźwięk/powiadomienie; każda opcja dziedziczy globalną, dopóki nie zostanie świadomie zmieniona.
- Żywy podgląd na przykładowych danych, **reset dla tej rozmowy**, bez zmiany treści wiadomości.
- Automatycznie korygować kontrast lub ostrzegać, gdy zdjęcie przeszkadza czytać; tryby wysokiego kontrastu i ograniczenia przezroczystości są ważniejsze niż dekoracja.
- Należy zachować spójność z Liquid Glass; to propozycja nowego podwidoku, nie polecenie przeróbki oryginalnych 10 ekranów.

## 5. Jak zrealizować inspiracje Apple bez udawania iMessage

| Kanał | Co Chatra może uczciwie prezentować | Czego nie wolno obiecywać |
| --- | --- | --- |
| SMS | Tekst, zaawansowane lokalne wyszukiwanie, lokalny motyw, nazwa kontaktu, organizacja, ewentualny cytat jako zwykły tekst | Edycja lub cofnięcie dostarczonej wiadomości u odbiorcy; reakcje iMessage; wysokiej jakości media przez zwykły SMS |
| MMS | Załączniki i rozmowy grupowe w granicach operatora; własny estetyczny podgląd | Gwarantowana wysoka jakość i rozmiar; dynamiczne grupy jak iMessage |
| Chatra HD (osobna usługa) | Własny transfer oryginału/kopii, link dla odbiorcy bez Chatry zgodnie z istniejącą specyfikacją | Dostęp do iMessage; automatyczna obecność funkcji u wszystkich kontaktów; status odczytu SMS na podstawie pobrania |
| RCS (oddzielny tor techniczny, nie część obecnego MVP Android) | Funkcje tylko przy faktycznie dostępnej i sprawdzonej integracji | „RCS włączone” wyłącznie dlatego, że telefon lub konkurencyjna aplikacja obsługuje RCS |

**Apple iMessage:** reakcje Tapback, edycja/cofanie, odpowiedzi w wątkach i wspólne tła to cechy konkretnej usługi; nie są ogólną funkcją SMS/MMS. W Apple cofnięcie/edycja wysłanej wiadomości nie obejmuje zwykłych SMS, MMS i RCS. „Send Later” w Wiadomościach Apple dotyczy iMessage; zaplanowany SMS w Chatrze ma inne ograniczenia.

**Grupowanie:** pojedyncza operacja „Wyślij do wielu” nie jest automatycznie grupowym czatem. Na Androidzie domyślnie osobne SMS do wielu, a MMS grupowy tylko po świadomym przełączeniu i ostrzeżeniu o widocznych numerach, zgodnie z S08. Jeśli w przyszłości będzie dostępny RCS, UI musi wskazywać *rzeczywisty* kanał, nie zgadywany.

## 6. Dodatkowa obserwacja techniczna: iPhone w UE, RCS na Androidzie

### 6.1 iPhone — realna ścieżka do osobnej wersji Chatry

Dokumentacja Apple potwierdza **TelephonyMessagingKit** (SMS, MMS i RCS) na iPhone. Wymaga uprawnienia **Default Carrier Messaging App**, wybrania aplikacji jako domyślnej aplikacji do wiadomości operatora oraz spełnienia warunków rynku: użytkownik ma konto zarejestrowane w UE i urządzenie znajduje się w UE. To ważna ścieżka badawcza dla polskiego wydania Chatry na iPhone. Działa tylko na iPhone, nie na iPadzie/Macu.

**Nie mylić uprawnień:**
- „Default Messaging App” (obsługa schematu komunikatorów i otwierania powiązanych linków) nie jest tym samym co
- **„Default Carrier Messaging App”** wymagane przez **TelephonyMessagingKit** do wysyłania i odbioru wiadomości operatorowych.

**Do zbadania przed decyzją o wersji iPhone:** przyznanie entitlement i proces App Review, dostępność API na wspieranych wersjach iOS, operatorzy PL, zachowanie historii sprzed ustawienia Chatry jako domyślnej, powiadomienia i raporty, przełączanie domyślnej aplikacji, grupowe RCS, współpraca z Androidem, zasady usuwania oraz brak iMessage. Nie zakładać rozwiązania tych zagadnień bez prototypu i testów.

### 6.2 Android — API 37 nie oznacza gotowego interoperacyjnego RCS

Dokumentacja Androida opisuje **AlternativeMessageTransportService** od **API 37**, które pozwala domyślnej aplikacji SMS zadeklarować obsługę *alternatywnego transportu* przy wysyłaniu. To sygnał do technicznego spike'u dla przyszłości Chatry. **Nie jest gwarancją** dostępu do sieci RCS operatorów, rejestracji numeru w RCS, odczytu wszystkich istniejących konwersacji Google Messages ani interoperacyjności z iPhone'em. Wymaga osobnej architektury, zgód, testów i oceny kompatybilności na urządzeniach.

Obecny kontrakt Androida **nie włącza RCS do pierwszego wydania**. Ustalenie to tylko rozszerza listę rzeczy do zweryfikowania później.

## 7. Proponowane zadania UX do rozpatrzenia po CH020

Żaden z poniższych identyfikatorów nie zastępuje CH001–CH064. To **kandydaci do osobnego doprecyzowania zakresu**, nie zadania automatycznie przypisane zespołowi.

| ID | Kandydat | Powiązanie | Proponowany priorytet | Kryterium akceptacji |
| --- | --- | --- | --- | --- |
| UX01 | Podwidok szczegółów kontaktu/grupy | S03, CH012, CH020, CH029 | P0 | Wszystkie akcje mają faktyczne działanie lub wyjaśniony stan niedostępny |
| UX02 | Galeria załączników per rozmowa | CH020, CH031, CH033, CH038 | P1 | Filtry i „Pokaż w rozmowie” otwierają właściwą wiadomość |
| UX03 | Kolaż / stos zdjęć z jednej wiadomości | S03, CH012, CH031 | P1 | Kolejność, liczniki i podgląd oryginałów zgadzają się z modelem danych |
| UX04 | Gesty i działania na wierszach | S02, CH011, CH023, CH030 | P1 | Cofnięcie, czytnik ekranu i brak przypadkowego usuwania |
| UX05 | Lokalna personalizacja pojedynczej rozmowy | S09, CH018, CH040 | P1 | Dziedziczenie, podgląd, reset i kontrast bez naruszenia wzorców |
| UX06 | Nieprzeczytane + przeskok do pierwszej zaległej | S02/S03, CH022, CH023 | P1 | Stany spójne po restarcie i po otrzymaniu wiadomości |
| UX07 | Eksport / Sharesheet i skrót na pulpit | CH020, CH022, CH044 | P2 | Eksport nie ujawnia danych niezamierzonych, skrót działa po restarcie |
| UX08 | Spike wykonalności iOS UE + aktualizacja analizy RCS Android | CH001, AUDYT.md | Badanie, nie MVP | Notatka z wynikami na urządzeniach, operatorem i API; bez obietnic wdrożenia |

**Zasada wdrożenia:** najpierw aktualizować projekt i kontrakt brakujących ekranów w CH020, uzgodnić zakres, dopiero potem wdrażać. Nie wolno zastępować istniejących wzorców S01–S10 „bardziej applowskimi” ekranami bez wyraźnej decyzji właściciela projektu.

## 8. Scenariusze testowe / Definition of Done nowych zachowań

1. Kontakt z jednym numerem: szczegóły pokazują prawidłową osobę, jego multimedia, działający dialer; po powrocie zachowana pozycja przewijania.
2. Kontakt z kilkoma numerami i dual SIM: połączenie i wysyłka nie wybierają po cichu złej osoby/numeru/SIM.
3. Nieznany lub alfanumeryczny nadawca: brak fałszywego przycisku połączenia; działają filtr i blokowanie bez usuwania kodów domyślnie.
4. Rozmowa z 67 załącznikami: liczba pochodzi z indeksu, filtry działają, żadna fotografia nie jest pominięta przez stronicowanie.
5. MMS z wieloma zdjęciami: zestaw pojawia się jako logiczna grupa, a tap w miniaturę otwiera właściwy plik.
6. Wyszukiwanie po tekście/kontakcie/dacie: łączenie filtrów nie pokazuje wiadomości spoza wyniku, kliknięcie trafia w konkretny dymek.
7. Otrzymanie nowej wiadomości w trakcie czytania starszych: ekran nie przeskakuje samoczynnie na koniec; przycisk „Nowe” działa.
8. Motyw indywidualny ze zdjęciem: tekst pozostaje czytelny, reset odtwarza wygląd globalny, preferencje nie są wysyłane jako część SMS.
9. Wyciszenie, przypięcie, oznaczenie przeczytania i blokada: stany utrzymują się po restarcie; akcje odwracalne są odwracalne.
10. Grupa SMS vs MMS: użytkownik rozumie, czy odpowiedzi będą widoczne wszystkim; brak podszywania się pod iMessage/RCS.
11. Brak dostępu do treści załącznika lub usunięta rozmowa: galeria i skrót na ekranie głównym pokazują kontrolowany stan pusty lub błąd, bez crasha.
12. Test dostępności: większy font, TalkBack, redukcja przezroczystości, sterowanie bez gestów, obsługa klawiatury i systemowego Back.

## 9. Źródła weryfikacyjne (dostęp 2026-10-29)

**Oficjalne Apple:**
- [Przypinanie, oznaczanie nieprzeczytanych, „Read All” i przypięte elementy](https://support.apple.com/en-ie/guide/iphone/iphe9b48b89e/27/ios/27).
- [Wyszukiwanie i łączenie filtrów w Wiadomościach](https://support.apple.com/en-ie/guide/iphone/iph17c111fb6/ios).
- [Zdjęcia, kolaże/stosy oraz udostępniane treści](https://support.apple.com/en-euro/guide/iphone/iphb66cfeaad/27/ios/27).
- [Tła rozmów iMessage](https://support.apple.com/en-ie/guide/iphone/iph605fa06e4/27/ios/27).
- [Filtry nieznanych nadawców](https://support.apple.com/en-ie/guide/iphone/iph203ab0be4/ios).
- [Grupy iMessage, RCS, SMS/MMS](https://support.apple.com/pl-pl/118236).
- [Odpowiedzi do wiadomości i gesty](https://support.apple.com/en-euro/guide/iphone/iph82fb73ba3/ios).
- [Reakcje Tapback](https://support.apple.com/en-euro/guide/iphone/iph018d3c336/27/ios/27).
- [Edycja i cofanie wysłania: ograniczenia protokołów](https://support.apple.com/en-by/guide/iphone/iphe67195653/27/ios/27).
- [Send Later w iMessage](https://support.apple.com/en-gb/guide/iphone/iph5ae9a7be6/27/ios/27).
- [Wiadomości RCS na iPhonie](https://support.apple.com/pl-pl/122195).
- [TelephonyMessagingKit, warunki UE i entitlement](https://developer.apple.com/documentation/TelephonyMessagingKit).
- [Alternatywne aplikacje domyślne](https://support.apple.com/pl-pl/121430).
- [Oddzielny Default Messaging App dla komunikatorów](https://developer.apple.com/documentation/messages/preparing-your-app-to-be-the-default-messaging-app).

**Oficjalny Android:**
- [Rola domyślnej aplikacji SMS i odbieranie SMS/MMS](https://developer.android.com/reference/android/provider/Telephony).
- [AlternativeMessageTransportService, API 37](https://developer.android.com/reference/android/service/messaging/AlternativeMessageTransportService).

**Źródło obserwacji Textry:** cztery zrzuty ekranu od właściciela projektu, udostępnione w rozmowie 2026-10-29. Zrzuty nie są publikowane ze względu na prywatne dane.
