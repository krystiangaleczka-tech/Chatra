# Chatra audyt aplikacji do wiadomości i koncepcja konkurenta Textry

Badanie bazowe z 26 października 2026 roku

Uzupełnienie rozmowy z 29 października 2026 roku w rozdziałach 22-27

Przygotowano dla Chrisa w projekcie Chatra

Aktualizacja zakresu: SMS i MMS pozostają podstawą, a własny transfer internetowy załączników jest kierunkiem dalszego prototypu. Połączenia głosowe i wideo są poza zakresem. Rekomendacje języków nie są zatwierdzonym wyborem stosu.

## 1 Wniosek dla projektu

**Warto sprawdzić ten pomysł, ale jako nowoczesną aplikację SMS i MMS dla konkretnej grupy odbiorców. Obecne dowody nie uzasadniają obietnicy pełnego zastąpienia Wiadomości Google.** Największe ryzyko stanowi dostęp do RCS, a nie wykonanie atrakcyjnego interfejsu. Oficjalna dokumentacja Androida wymaga dla opisanych interfejsów RCS single registration między innymi statusu aplikacji uprzywilejowanej, preinstalowanej na urządzeniu. Sam wybór aplikacji jako domyślnej do SMS nie wystarcza. [S31]

Proponowany kierunek produktu to spokojny, dopracowany komunikator SMS i MMS: świetna czytelność od pierwszego uruchomienia, wygodna obsługa na dużym telefonie, szybkie wyszukiwanie, porządek w skrzynce i personalizacja, która zachowuje spójność. Estetyka ma pomagać czytać i odpowiadać. Sama duża liczba kolorów nie tworzy trwałej przewagi.

**Najlepsze połączenie inspiracji:** kontrola nad zachowaniem aplikacji z Textry, klarowna hierarchia rozmowy z Wiadomości Apple, organizacja z Samsung Messages oraz jednoznaczne stany wiadomości i rozbudowane wyszukiwanie jako punkt odniesienia z Google. To rekomendacja autora, a nie wynik testu użytkowników.

| Decyzja | Rekomendacja |
| --- | --- |
| Pierwsza platforma | Android; wersję iPhone traktować jako osobną analizę wykonalności |
| Pierwszy odbiorca | Osoba używająca SMS, która świadomie akceptuje brak RCS |
| Główna obietnica | Czytelne wiadomości, porządek i kontrola w dopracowanym interfejsie |
| Najważniejszy warunek | Uczciwe pokazanie ograniczeń przed zmianą aplikacji domyślnej |
| Podejście do wyglądu | Dobry motyw domyślny oraz kilka spójnych wariantów |
| Podejście do AI | Funkcje opcjonalne po potwierdzeniu potrzeby; bez chatbota na ekranie głównym |
| Model biznesowy do sprawdzenia | Bez reklam w rozmowach; zakup Pro, ewentualnie płatne usługi chmurowe później |
| Ocena inwestycji | Najpierw prototyp, rozmowy i test dostarczania; potem szeroki zakres funkcji |

## 2 Zakres i sposób oceny

Opracowanie obejmuje Textra SMS, Wiadomości Google, Samsung Messages i Wiadomości Apple. Pomocniczo uwzględnia Chomp SMS, Pulse SMS, Fossify Messages oraz QUIK. Materiał składa się z oficjalnych stron produktów, aktualnych instrukcji, dokumentacji platform, publicznych kart Google Play, materiałów wizualnych oraz bezpośrednich wypowiedzi użytkowników.

Rozróżniam cztery rodzaje ustaleń:

- **Potwierdzona funkcja:** opisana przez producenta lub w dokumentacji platformy. Dostępność nadal może zależeć od regionu, operatora, modelu i wdrożenia.
- **Deklaracja producenta:** na przykład zapewnienie o bardzo dużej szybkości. Bez pomiarów nie traktuję jej jako wyniku benchmarku.
- **Sygnał użytkownika:** konkretna publiczna relacja. Nie dowodzi skali problemu ani jego przyczyny technicznej.
- **Ocena lub rekomendacja autora:** interpretacja materiałów i propozycja dla nowego produktu. Wymaga sprawdzenia z użytkownikami.

**Ograniczenie audytu:** nie wykonano instalacji konkurencyjnych aplikacji, testów na fizycznych telefonach, pomiarów baterii, inspekcji ruchu sieciowego ani reprezentatywnego badania opinii. Ocena wizualna jest przeglądem heurystycznym publicznych ekranów i opisanych przepływów. Nie przypisuję aplikacjom wymyślonych ocen szybkości, niezawodności lub bezpieczeństwa.

Porównanie Apple odnosi się do aktualnego przewodnika iOS 27 oraz instrukcji konkretnych funkcji. Wczesnych zapowiedzi z iOS 26 nie przedstawiam jako nowości z października 2026. Dostępność nowych funkcji AI należy oceniać osobno dla Polski. [S23]

## 3 Rynek i realność wejścia

### 3.1 Co rzeczywiście potwierdzają publiczne liczby

| Aplikacja | Publiczny próg pobrań Google Play | Widoczna ocena w odczycie | Interpretacja |
| --- | --- | --- | --- |
| Textra SMS | 10 mln i więcej | 4,0 w karcie en US | Istnieje duża historyczna dystrybucja niezależnego klienta [S02] |
| Wiadomości Google | 10 mld i więcej | 4,6 w karcie en US | Ogromna przewaga dystrybucji; próg nie oznacza tylu unikalnych osób [S06] |
| Chomp SMS | 10 mln i więcej | 4,2 | Dojrzały konkurent z tej samej firmy co Textra [S38] |
| Pulse SMS | 1 mln i więcej | 3,5 | Dodatkowy rynek funkcji na wiele urządzeń [S39] |
| Fossify Messages | 100 tys. i więcej | 4,1 | Istnieje także oferta bez nacisku na reklamy i chmurę [S40] |

To migawka publicznych kart, nie raport o liczbie aktywnych użytkowników. Wyniki mogą różnić się między regionami i odczytami. Strona marketingowa Textry pokazywała 4,4, a bieżąca karta sklepu 4,0; do porównania nie należy mieszać tych wartości. [S01, S02]

Nie znamy MAU Textry, retencji, liczby płacących użytkowników ani przychodów. Nie ma podstaw, aby obliczać możliwy udział nowej aplikacji przez proste przemnożenie progu pobrań przez cenę Pro.

Polski kontekst również wymaga ostrożności: UKE podaje za koniec 2025 roku 67,2 mln aktywnych kart SIM, w tym 14 mln M2M. Liczba kart nie jest liczbą osób zainteresowanych nową aplikacją. W tej analizie nie wykorzystuję nieodczytanego załącznika PDF UKE do podania wolumenu SMS. [S42]

### 3.2 Gdzie można szukać odbiorców

| Segment | Konkretny problem do rozwiązania | Atrakcyjność jako początek | Co może uniemożliwić zmianę |
| --- | --- | --- | --- |
| Użytkownicy Textry | Chcą zachować kontrolę, ale oczekują lepszego interfejsu | Wysoka do badania | RCS i przywiązanie do istniejących ustawień |
| Użytkownicy dawnych klientów Samsunga | Szukają porządku i znajomej organizacji | Średnia do wysokiej | Oczekiwanie zachowania wszystkich funkcji operatora |
| Osoby z dużą liczbą SMS usługowych | Trudno znaleźć kod, adres, termin lub numer przesyłki | Wysoka do testu zadaniowego | Wystarczają im obecne filtry i wyszukiwarka |
| Osoby ceniące wygląd i spokój | Przeszkadza im wizualny chaos i zbędne dodatki | Wysoka dla prototypu | Deklarowana sympatia nie przekłada się na zmianę aplikacji |
| Użytkownicy dwóch kart SIM | Chcą pewnie widzieć numer używany do wysyłania | Wysoka funkcjonalnie | Błędy przypisania SIM przekreślają produkt |
| Rodzinne grupy RCS | Potrzebują bezproblemowych multimediów i grup | Niska dla Android SMS MVP | Brak dostępu do wymaganego kanału |

To hipotezy segmentacji, a nie oszacowanie ich liczebności. Pierwsza rekrutacja powinna obejmować osoby faktycznie używające SMS kilka razy w tygodniu. Użytkownik, który korzysta wyłącznie z komunikatora internetowego, może ocenić wygląd, lecz niewiele powie o retencji klienta SMS.

### 3.3 Dlaczego wejście pozostaje trudne

Zmiana aplikacji do wiadomości oznacza powierzenie jej kodów logowania, kontaktów prywatnych i komunikatów usługowych. Nowy produkt konkuruje z przyzwyczajeniem i zaufaniem. Nawet bardzo atrakcyjny ekran nie zrekompensuje jednej ważnej, niewidocznej wiadomości.

Najbardziej obiecująca pozycja to produkt, który odbiorca potrafi opisać przez korzyść: szybciej znajduję potrzebne informacje, nie wysyłam z niewłaściwej karty, nie gubię zaplanowanych wiadomości. Hasło o ładniejszej Textrze pomaga rozpocząć rozmowę, ale jest za słabe jako pełne pozycjonowanie.

## 4 Textra SMS

### 4.1 Co oferuje i za co można ją cenić

Textra jest klientem SMS i MMS firmy Delicious Global. Publiczny opis potwierdza między innymi rozbudowane ustawienia rozmów, planowanie wysyłki, opóźnienie wysłania, szybkie odpowiedzi, przypinanie, kompresję multimediów oraz prezentowanie i wysyłanie reakcji. To istotnie bogatszy punkt wyjścia niż sam ekran z listą wiadomości. [S02]

Producent podaje jednorazową cenę Pro 14,99 USD i 14 dni bez reklam na start. Funkcje obu wariantów mają być takie same; zakup usuwa reklamy. Cena polska wymaga sprawdzenia na koncie Google Play w Polsce. Textra opisuje też anulowanie w oknie opóźnienia od 1 do 10 sekund i kopiowanie fragmentu tekstu. [S01]

**Wniosek autora:** jej atrakcyjność może wynikać z kumulacji małych, praktycznych udogodnień. Osoba przyzwyczajona do indywidualnych ustawień rozmów może odczuwać zmianę aplikacji jako utratę kontroli, nawet gdy nowy klient ma nowszą oprawę.

### 4.2 Audyt wizualny

Publiczne materiały eksponują kolory, kształty dymków i wiele wariantów. Moja ocena: język interfejsu odwołuje się do wcześniejszej generacji Material Design i może wyglądać mniej świeżo niż współczesne aplikacje systemowe. To ocena estetyczna; nie dowodzi, że interfejs jest nieczytelny lub nielubiany przez większość odbiorców. Materiały referencyjne: karta sklepu i oficjalne ekrany producenta. [S01, S02]

Grafiki marketingowe mogą przedstawiać starszy lub mocno spersonalizowany wariant. Nie utożsamiam ich z domyślnym wyglądem każdej bieżącej instalacji.

| Obszar | Ryzyko projektowe widoczne w sposobie prezentacji | Proponowana odpowiedź nowego produktu |
| --- | --- | --- |
| Personalizacja | Dużo parametrów może utrudniać uzyskanie spójnego efektu | Gotowe kompozycje z podglądem całego ekranu |
| Hierarchia | Mocny akcent kolorystyczny konkuruje z treścią | Neutralna baza, kolor służący orientacji |
| Ustawienia | Wartość zaawansowanych funkcji może być trudna do odkrycia | Proste ustawienia główne i rozwijane opcje zaawansowane |
| Wyszukiwanie | Skrót wymagający poznania składni obciąża początkującego | Widoczny filtr kontaktu i zakresu bez nauki komend |
| Planowanie | Ikona bez etykiety może nie ujawniać znaczenia | Jawna akcja Zaplanuj i wspólna lista zaplanowanych |
| Rozmowa | Każda dodatkowa kontrolka zabiera uwagę tekstowi | Najczęstsze akcje na wierzchu, reszta w jednym panelu |

Powyższe ryzyka trzeba sprawdzić zadaniowo. Nie twierdzę, że każda osoba ma problem z każdą wymienioną funkcją. Przeprojektowanie powinno zachować szybkość działania i pamięć mięśniową doświadczonych użytkowników.

### 4.3 Opinie i zdarzenia istotne dla projektu

W widocznych opiniach Google Play z września 2026 pojawiają się zgłoszenia reklam odtwarzających dźwięk; producent odpowiedział, a changelog 4.87 z 27 września wskazuje poprawkę tego problemu. Nie przedstawiam incydentu jako dowodu, że reklamy dźwiękowe nadal występują w bieżącej wersji. [S02, S03]

Nowsze wypowiedzi na Reddicie opisują odejście mimo wieloletniego przywiązania, problemy z grupami oraz brakujące wiadomości. Są to relacje użytkowników, nie potwierdzenie wspólnej przyczyny. [S44, S45]

**Lekcja produktowa:** reklama albo trudna do zrozumienia awaria w aplikacji do prywatnej komunikacji może niszczyć zaufanie szybciej niż w aplikacji używanej sporadycznie. Dla nowego produktu przewidywalność monetyzacji powinna być elementem doświadczenia.

### 4.4 Czego nie pomylić z funkcjami RCS

Oficjalna pomoc Textry potwierdza brak RCS. [S04] Reakcja pokazana jako emoji nie jest dowodem dostępności pełnej komunikacji RCS. Opóźniona wysyłka nie cofa wiadomości, która została już przekazana do sieci. Takie rozróżnienia powinny być zrozumiałe w języku interfejsu.

**Zachować w nowej aplikacji:** odczucie kontroli, wygodne skróty, elastyczność rozmów i dobrą obsługę tekstu. **Zaprojektować od nowa:** motyw domyślny, hierarchię ustawień, odkrywanie funkcji i komunikację ograniczeń.

## 5 Wiadomości Google

### 5.1 Rzeczywisty punkt odniesienia

Google łączy SMS i MMS z RCS, multimediami wyższej jakości, wskaźnikami pisania, potwierdzeniami odczytu, funkcjami ekspresji i integracjami. Dostępność zależy między innymi od regionu i operatora. To szeroki produkt z przewagą infrastruktury, nie tylko estetyczny klient SMS. [S07]

Nie należy przedstawiać go jako aplikacji pozbawionej personalizacji. Bieżąca instrukcja opisuje motywy, kolory i własne tapety. Oficjalny komunikat społeczności z 27 sierpnia 2026 wskazuje wymóg włączenia RCS dla opisywanej funkcji; zmiany wyglądu są lokalne dla użytkownika. [S08, S09]

### 5.2 Co warto przejąć

- **Jasne rozróżnienie kanału:** użytkownik powinien wiedzieć, czy pisze SMS, MMS czy RCS. Dla własnej aplikacji projektować to od początku, nawet jeżeli pierwsza wersja obsłuży tylko dwa pierwsze kanały.
- **Wyszukiwanie jako osobny scenariusz:** szukanie kontaktu, treści i załącznika wymaga różnych podpowiedzi i filtrów.
- **Czytelny stan wysyłki:** błąd powinien prowadzić do konkretnej akcji, nie pozostawiać użytkownika z samym symbolem.
- **Ochrona przed przypadkową utratą:** obecny produkt ma kosz z możliwością odzyskania rozmów przez 30 dni; zapowiedź wyróżnia 7 dni na Android Go. Kosz nie jest już unikalnym pomysłem dla konkurenta. [S16, S17]

Planowanie wiadomości i wyszukiwanie są opisane w oficjalnej pomocy. Obsługa komputera działa przez powiązanie z aplikacją telefonu i wymaga połączenia; nie należy utożsamiać jej z niezależnym transportem SMS z przeglądarki. [S13, S14]

### 5.3 Gdzie może pozostawać miejsce na poprawę

Moim zdaniem nowa aplikacja może być bardziej skupiona na spokojnej skrzynce i powtarzalnych zadaniach. Nie trzeba umieszczać na tym samym poziomie rozmów, dodatków AI, efektów i wszystkich możliwości personalizacji. Najpierw trzeba jednak sprawdzić, czy realnym użytkownikom obecny układ przeszkadza.

Widoczna recenzja Google Play z 19 września 2026 pozytywnie ocenia zwykłe działanie aplikacji, jednocześnie wskazując niedoskonałość wyszukiwania. Inna, kwietniowa, skarży się na brak własnego tła. Tej drugiej nie traktuję jako aktualnego opisu produktu po sierpniowym rozszerzeniu motywów. [S06, S09]

### 5.4 Ważna subtelność dotycząca edycji

Aktualna pomoc Google opisuje edycję do 15 minut także dla SMS i MMS, ale zaznacza, że przy SMS odbiorca dostaje kolejną wiadomość zaczynającą się od gwiazdki. Nie oznacza to zmiany już dostarczonego SMS w telefonie odbiorcy. [S15]

**Rekomendacja autora:** jeżeli wdrażamy podobną funkcję, nazwać ją w kanale SMS Wyślij poprawioną wersję i uprzedzić o kolejnym wysłaniu. To prostsze i uczciwsze niż sugerowanie możliwości z iMessage.

## 6 Samsung Messages

### 6.1 Aktualny status i znaczenie regionu

Oficjalna strona Samsunga potwierdza zakończenie działania Samsung Messages i wyraźnie ogranicza komunikat do rynku USA. Wskazuje wyjątek dla Androida 11 i starszych. Wniosku o identycznym terminie dla Polski nie da się z tego komunikatu wyprowadzić. [S18]

Dla naszego projektu Samsung pozostaje ważną referencją organizacji i ergonomii. Nie należy jednak budować planu pozyskania klientów na założeniu, że wszyscy polscy użytkownicy zostali właśnie zmuszeni do opuszczenia tej aplikacji.

### 6.2 Co jest wartościowe

Instrukcje opisują kategorie rozmów, planowanie, indywidualne dźwięki, tła rozmów, zmianę rozmiaru tekstu gestem oraz kosz. To praktyczny zestaw funkcji codziennych. Dokumentacja amerykańska dotyczy określonych wersji i urządzeń, więc nie stanowi katalogu gwarantowanego na każdym Galaxy. [S19]

Osobna instrukcja grup opisuje kategoryzowanie rozmów oraz rozróżnia SMS wysyłany do wielu osób od rozmowy grupowej MMS. To ważna różnica dla projektowania nowego klienta: wysyłka do kilku odbiorców nie zawsze oznacza wspólny czat. [S20]

Z wytycznych One UI warto przejąć uwzględnianie sytuacji, w których dostępna jest tylko jedna ręka. [S21] Dla naszej aplikacji oznacza to łatwo dostępne rozpoczęcie rozmowy, dolne panele wyboru i brak wymuszania ciągłego sięgania do narożników.

### 6.3 Co przejąć ostrożnie

Kategorie mają wartość, jeśli użytkownik rozumie, gdzie trafiają rozmowy. Nie rekomenduję domyślnego zestawu sześciu zakładek ani ukrywania nieznanych nadawców bez jasnego oznaczenia. Kurier, lekarz lub pierwszy SMS od klienta może pochodzić z numeru spoza kontaktów.

Moja propozycja: domyślnie Wszystkie i Nieprzeczytane. Kategorie własne można dodać później, a filtry kodów lub przesyłek powinny pokazywać trafienia bez usuwania wiadomości z głównego obrazu historii.

## 7 Wiadomości Apple

### 7.1 Co daje wrażenie dopracowania

Moja ocena języka interfejsu Apple: spójna typografia, oszczędna liczba akcji w głównym obszarze i przewidywalna hierarchia kierują uwagę na rozmowę. Warto przejąć zasady kompozycji i jakość stanów, tworząc własną tożsamość wizualną Androida. Podstawowy przewodnik opisuje rozwijanie dodatkowych możliwości przez przycisk obok pola tekstowego. [S22]

Estetyka przezroczystości sama w sobie nie gwarantuje czytelności. Własny produkt powinien najpierw działać przy dużym tekście, mocnym świetle i ograniczeniu animacji; dekoracyjne efekty mogą być dodatkiem.

### 7.2 Funkcje i ich granice

| Funkcja Apple | Potwierdzone ograniczenie | Wniosek dla naszej aplikacji |
| --- | --- | --- |
| Tła rozmów | Instrukcja wiąże je z iMessage i zgodnymi wersjami systemów; tło mogą widzieć i zmieniać uczestnicy [S24] | Lokalne tło SMS da się zaprojektować; wspólne ustawienie wymaga obsługi protokołu |
| Wyślij później | iMessage, do 14 dni; wiadomość może zostać dostarczona mimo wyłączenia urządzeń nadawcy [S25] | Lokalne planowanie na Androidzie ma inne warunki działania |
| Cofnięcie i edycja | Instrukcja opisuje cofnięcie do 2 minut oraz do 5 edycji w 15 minut w obsługiwanych rozmowach iMessage [S26] | Nie obiecywać zdalnego cofania zwykłego SMS |
| Filtrowanie nadawców | Osobny obszar nieznanych nadawców i wyjątki dla niektórych pilnych komunikatów; dostępność regionalna [S27] | Rozróżnić nieznanego nadawcę od spamu |
| Nowsze funkcje AI | Aktualny przewodnik iOS 27 ogranicza początkową dostępność Siri AI między innymi regionalnie; UE nie jest objęta startem tej funkcji na iOS [S23] | Nie zakładać, że prezentacja z innego rynku opisuje doświadczenie w Polsce |

### 7.3 Bezpieczeństwo między platformami

Nieaktualne byłoby ogólne stwierdzenie, że rozmowy RCS iPhone z Androidem zawsze są nieszyfrowane end to end. Apple ogłosił 11 maja 2026 wdrażanie takiego szyfrowania w wersji beta od iOS 26.5 u obsługiwanych operatorów i z aktualnymi Wiadomościami Google. Nie dowodzi to dostępności w każdej polskiej sieci. [S28]

Google nadal opisuje warunki szyfrowania i oznaczenia kłódką, a szczegóły pomocy mogą odzwierciedlać różne etapy wdrożenia. [S10, S11] W projekcie stan bezpieczeństwa należy uzależniać od faktycznej sesji i kanału, nigdy od koloru dymka.

### 7.4 Co daje Apple nowym deweloperom

TelephonyMessagingKit opisuje wysyłanie i odbieranie SMS, MMS i RCS przez aplikację iPhone. Dokumentacja wymaga odpowiedniego entitlement, ustawienia jako domyślnej aplikacji operatorowej oraz konta zarejestrowanego w UE i urządzenia znajdującego się w UE. [S29, S30]

To uzasadnia osobny test techniczny wersji iPhone dla Polski. Nie jest równoznaczne z dostępem do iMessage, całej wcześniejszej historii, szyfrowania każdej sesji ani pełnej funkcjonalności aplikacji Apple. Te elementy trzeba potwierdzić na urządzeniu i w aktualnej dokumentacji przed wpisaniem ich do obietnicy produktu.

## 8 Pozostali konkurenci

| Produkt | Potwierdzony wyróżnik w publicznym opisie | Znaczenie dla projektu |
| --- | --- | --- |
| Chomp SMS | Blokada kodem, szablony tekstu, dwie karty SIM, planowanie, szeroka personalizacja [S38] | Sam zestaw funkcji power user nie będzie wyjątkowy |
| Pulse SMS | Synchronizacja SMS i MMS na inne urządzenia, prywatne rozmowy i automatyczne odpowiedzi [S39] | Desktop jest osobnym obszarem konkurencji i kosztów |
| Fossify Messages | SMS i MMS, import i eksport, blokowanie numerów i słów, kontrola podglądu [S40] | Prywatność i prostota też mają konkurencję |
| QUIK | Otwarty następca QKSMS; planowanie, backup, archiwum, reakcje, opóźnienie wysyłki [S41] | Istnieją dojrzałe wzorce do analizy zachowania |

QUIK deklaruje licencję GPLv3. To nie jest automatycznie gotowy szkielet zamkniętej aplikacji komercyjnej. Przed wykorzystaniem kodu należy ustalić model licencyjny projektu; na etapie audytu wystarczy traktować repozytorium jako referencję. [S41]

**Wniosek autora:** rynek jest zajęty na poziomie listy funkcji. Szansa dotyczy jakości ich połączenia, domyślnego wyglądu i konkretnego scenariusza odbiorcy. Nie ma dowodu, że samo usunięcie reklam lub dodanie motywów wystarczy.

## 9 Macierz funkcji głównych aplikacji

Legenda: **Tak** oznacza opis w wskazanym źródle, **Warunkowo** oznacza zależność od kanału, wersji lub regionu, **Nieustalone** oznacza brak rozstrzygającego materiału w tym audycie. Nieustalone nie oznacza braku funkcji. Samsung jest referencją wersji opisanych w dokumentacji, z zastrzeżeniem zakończenia usługi w USA.

| Obszar | Textra | Google | Samsung | Apple |
| --- | --- | --- | --- | --- |
| Zwykłe SMS i MMS | Tak [S02] | Tak [S06] | Tak, zależnie od aktualnej dostępności [S18, S19] | Tak [S22] |
| RCS | Nie [S04] | Tak, warunkowo [S11] | Zależnie od urządzenia i rynku; nie zakładać dla USA [S18] | Tak, warunkowo [S28] |
| iMessage | Nie | Nie | Nie | Tak [S22] |
| Szyfrowanie zwykłego SMS end to end | Nie | Nie | Nie | Nie; iMessage to inny kanał |
| Kolory i tła rozmowy | Personalizacja [S01] | Tak, warunkowo [S08, S09] | Tak w opisanych wersjach [S19] | Tła iMessage [S24] |
| Planowanie wysyłki | Tak [S03] | Tak [S13] | Tak [S19] | iMessage do 14 dni [S25] |
| Anulowanie przed przekazaniem do sieci | Okno opóźnienia [S01] | Nieustalone jako identyczna funkcja | Nieustalone | Osobne cofanie iMessage [S26] |
| Zmiana już wysłanego SMS u odbiorcy | Nie | Korekta SMS jest nową wiadomością [S15] | Nie | Nie |
| Reakcje | Tak; nie oznaczają RCS [S02] | Tak, zależnie od rozmowy [S07] | Instrukcja USA wskazuje brak [S19] | Tak w obsługiwanych rozmowach [S22] |
| Własne kategorie rozmów | Nieustalone | Brak odpowiednika w przywołanej instrukcji Samsunga [S20] | Tak [S20] | Nie utożsamiać filtrów z kategoriami użytkownika [S27] |
| Kosz | Nieustalone | Tak [S16] | Tak w opisanych wersjach [S19] | Odzyskiwanie opisane w przewodniku [S22] |
| Komputer i inne urządzenia | Integracje zewnętrzne [S02] | Własny klient web [S14] | Zależnie od usługi i rynku [S18] | Ekosystem Apple [S22] |
| Rozbudowany antyspam | Blokowanie; nie potwierdzono równoważności z Google [S02] | Standardowy i rozszerzony model ochrony [S12] | Nieustalona równoważność | Filtrowanie i zgłaszanie [S27] |

Dla nowej aplikacji najważniejsze jest rozróżnienie funkcji lokalnej i funkcji protokołu. Wygląd, foldery i przypomnienia można projektować samodzielnie. Odczyt u odbiorcy, wspólne ankiety i edycja po dostarczeniu wymagają odpowiedniej komunikacji między końcami rozmowy.

## 10 Głos użytkowników

Próba jakościowa obejmuje sześć celowo dobranych wątków społeczności Textry oraz widoczne recenzje kart sklepu. Dobór nastawiono na powody zmiany i pozostania. Nie kodowano wszystkich postów ani nie wyliczano udziału zadowolonych i niezadowolonych osób. Wątki w większości opisują realia operatorów amerykańskich, więc nie przenoszę ich częstości na Polskę.

| Materiał i data | Co opisują uczestnicy | Jak wykorzystać w badaniu |
| --- | --- | --- |
| Wątek o zmianie aplikacji, 20 października 2026 [S44] | Odejście mimo wieloletniego używania; kłopoty z MMS grupowym | Sprawdzić koszt rezygnacji z RCS i jakość migracji |
| Texts failing, 4 października 2026 [S45] | Brakujące lub niedochodzące wiadomości; niepewna przyczyna | Projektować diagnostykę, testować faktyczne dostarczanie |
| Other messaging apps, 19 września 2026 [S46] | Poszukiwanie alternatywy po reklamach z dźwiękiem | Sprawdzić tolerancję modelu reklamowego, uwzględnić późniejszą poprawkę |
| Why does Textra work for some but not all, 30 kwietnia 2026 [S47] | Różne doświadczenia po zmianie urządzenia | Porównywać nowe instalacje i migracje na różnych modelach |
| Switched to Google Messages for RCS, 16 marca 2025 [S48] | Wybór RCS mimo sympatii do Textry | Pytać o rzeczywiste zachowanie, nie tylko deklarację estetyczną |
| Group Messaging via Textra possible, 21 października 2026 [S49] | Jedna osoba opisuje poprawę po wyłączeniu RCS | Nie traktować obejścia jako gwarancji dla wszystkich |

W tych materiałach powracają personalizacja, przyzwyczajenie, kompatybilność grup i zaufanie. Nie należy na tej podstawie wnioskować, że większość ludzi woli konkretny interfejs. Własne badanie powinno oddzielić użytkowników SMS od osób, które nie mogą zrezygnować z RCS.

**Istotne sprostowanie:** brak RCS w kliencie SMS nie oznacza automatycznie, że każdy SMS od telefonu z włączonym RCS zaginie. Kanał wysłania, rejestracja numeru, fallback i stan grupy mają znaczenie. Oficjalna pomoc Google przewiduje procedurę wyłączenia RCS przy zmianie aplikacji i problemach z odbiorem. [S34]

## 11 Audyt doświadczenia od pierwszego uruchomienia

### 11.1 Przed zmianą aplikacji domyślnej

Najpierw pokazać krótki, prawdziwy opis korzyści oraz ograniczenia: aplikacja obsługuje SMS i MMS; zaawansowane czaty RCS nie będą dostępne w tej wersji. Dopiero później uruchomić systemowy wybór domyślnej aplikacji i wymagane uprawnienia. Android opisuje wymóg proszenia o rolę przed związanymi z nią uprawnieniami. [S32]

Nie obiecywać przeniesienia wszystkich rozmów RCS do lokalnej historii. Nie prosić na starcie o mikrofon, aparat, pełną bibliotekę zdjęć i konto, jeśli użytkownik jeszcze nie korzysta z tych funkcji. Kontakt z obsługą i podgląd zasad prywatności powinny być dostępne bez przechodzenia przez reklamowy ekran.

### 11.2 Lista rozmów

Proponuję wyraźną nazwę kontaktu, maksymalnie dwie linie podglądu i widoczne oznaczenie szkicu lub nieudanej wysyłki. Nieprzeczytanie powinno mieć sygnał inny niż sam kolor. Spójne odstępy mają pomagać skanować listę; nadmiernie duże karty ograniczyłyby widoczność rozmów.

Własne foldery i kategorie mają uzupełniać listę, a nie tworzyć skomplikowaną strukturę na pierwszym ekranie. Akcja archiwizacji powinna być łatwa do cofnięcia. Usuwanie wymaga czytelnego rozróżnienia między koszem, usunięciem lokalnego widoku i trwałym skasowaniem danych.

### 11.3 Rozmowa i pole pisania

Najwyższy priorytet mają tekst, odbiorca i karta SIM. Użytkownik powinien widzieć, z którego numeru wyśle wiadomość, zanim naciśnie Wyślij. Pole pisania powinno rosnąć do rozsądnej wysokości i zachowywać szkic przy zmianie aplikacji.

Panel dodatków powinien pokazywać krótki zestaw nazwanych opcji: Zdjęcie, Aparat, Zaplanuj i ewentualnie Szablony. Nagranie głosu wymaga osobnego uzasadnienia wartości w świecie MMS. Nie każda funkcja potrzebuje stałej ikony obok klawiatury.

### 11.4 Stany problemowe

Przykłady komunikatów do prototypu, wymagające dopasowania do faktycznie wykrytego stanu:

- Nie udało się wysłać. Wybierz kartę SIM i spróbuj ponownie.
- Ten załącznik wymaga MMS. Przed wysłaniem sprawdź rozmiar i ustawienia operatora.
- Wiadomość czeka na zaplanowaną wysyłkę. Telefon musi być włączony i mieć dostęp do sieci operatora.
- Nie potwierdzono doręczenia. To nie oznacza, że odbiorca przeczytał wiadomość.

Nie każdy błąd da się rozpoznać dokładnie. Gdy przyczyna jest nieznana, komunikat powinien to uczciwie powiedzieć. Interfejs nie może zgadywać, że odbiorca zablokował nadawcę.

## 12 Kierunek wizualny nowej aplikacji

### 12.1 Rekomendowany styl

**Spokojny, współczesny Material z własną typografią i konsekwentnymi detalami.** Neutralne powierzchnie, jeden akcent, miękkie dymki, proste ikony i wyraźne stany. Efekt premium powinien wynikać z proporcji, ruchu i dopracowania treści.

| Element | Propozycja do prototypu | Uzasadnienie |
| --- | --- | --- |
| Motyw jasny | Ciepła biel i ciemny tekst | Łagodne powierzchnie, czytelna treść |
| Motyw ciemny | Głęboki grafit; osobno opcja czerni | Pozwala porównać dwa preferowane sposoby odbioru |
| Akcent | Granatowo fioletowy albo chłodny niebieski | Wyrazisty bez dominowania całej rozmowy |
| Dymki | Jeden spójny system narożników i marginesów | Przewidywalność przy krótkim i długim tekście |
| Typografia | Systemowa rodzina; wyraźne poziomy wagi i rozmiaru | Skalowanie, polskie znaki i zgodność z urządzeniem |
| Gęstość | Standardowa oraz kompaktowa | Różne potrzeby bez dziesiątek suwaków |
| Animacje | Krótkie przejścia wspierające orientację | Odpowiedź na akcję bez opóźniania pracy |
| Personalizacja | Kilka kompletnych motywów i podgląd | Użytkownik widzi efekt, zanim go zapisze |

Przykładowa paleta startowa: tło jasne #F7F8FC, tekst #162035, akcent #4855D8, powierzchnia dymka #E9EDFF, tło ciemne #111621. To propozycja, nie zatwierdzony system. Wszystkie kombinacje trzeba sprawdzić pod kątem kontrastu, również po wyborze własnej tapety.

### 12.2 Trzy warianty do porównania

| Wariant | Charakter | Ryzyko | Ocena autora |
| --- | --- | --- | --- |
| Spokojny Material | Stonowane powierzchnie i lekka personalizacja | Może wydać się zbyt podobny do aplikacji systemowej | Najlepszy kandydat na domyślny |
| Minimalny edytorski | Mocniejsza typografia i mało dekoracji | Może zmniejszać ilość treści na ekranie | Dobry drugi wariant testowy |
| Subtelna przezroczystość | Delikatne warstwy przy nagłówku lub panelach | Kontrast, wydajność i rozpraszające tło | Opcjonalny motyw po pomiarach |

Nie rekomenduję budowania całej tożsamości na kopiowaniu szkła z iPhone'a. Warto przejąć staranność kompozycji Apple, zachowując działanie i gesty spodziewane na Androidzie.

### 12.3 Trzy ekrany do pierwszego prototypu

**Skrzynka:** tytuł, wyszukiwanie, dwa podstawowe filtry, lista rozmów i łatwo dostępna akcja napisania wiadomości. Przypięte rozmowy nadal mają wyglądać jak rozmowy, a nie osobny społecznościowy karuzelowy moduł.

**Rozmowa:** odbiorca, treść, wyraźny stan wiadomości oraz dolne pole pisania z widoczną kartą SIM. Dodatki w dolnym panelu. Główny ekran nie pokazuje AI ani katalogu motywów.

**Wygląd:** duży podgląd, kilka spójnych kompozycji, zmiana gęstości i tekstu, kontrast oraz przywrócenie ustawień. Zaawansowane opcje są dostępne, ale nie dominują.

Wersja PDF zawiera autorski schemat tych ekranów. To ilustracja układu i hierarchii, nie gotowa aplikacja ani reprodukcja ekranu konkurenta.

### 12.4 Dostępność jako warunek jakości

Android zaleca pola dotykowe co najmniej 48 na 48 dp. Jako kryterium dla tekstu przyjmuję WCAG 2.2: kontrast minimum 4,5 do 1 dla zwykłego tekstu i 3 do 1 dla odpowiednio dużego. To cel projektowy, nie deklaracja formalnej certyfikacji. [S36, S37]

Do prototypu należy dodać duży tekst, TalkBack, obsługę bez polegania wyłącznie na kolorze, jasny fokus i ograniczenie animacji. Szczególnie ważne są komunikaty o błędzie, timer opóźnienia, wybór karty oraz oznaczenie nieprzeczytania. Przycisk widoczny jako mała ikona nadal powinien mieć duży obszar dotykowy.

## 13 Funkcje do przejęcia i priorytety

Poniższa lista jest propozycją zakresu, nie opisem istniejącego produktu. P0 oznacza warunek pierwszej wiarygodnej bety, P1 kolejne rozwinięcie, P2 osobny etap po walidacji.

| Funkcja | Inspiracja | Priorytet | Co musi być sprawdzone |
| --- | --- | --- | --- |
| Nowoczesny motyw jasny i ciemny | Apple i współczesny Android | P0 | Czytelność i preferencja po tygodniu, nie tylko na obrazku |
| Stabilne SMS i MMS | Podstawa kategorii | P0 | Operatorzy, załączniki, odbiór i ponowienia |
| Czytelna karta SIM | Potrzeba użytkowa | P0 | Brak wysłania z niewłaściwego numeru |
| Wyszukiwanie kontaktów i treści | Google i potrzeby z opinii | P0 | Polskie znaki, fragmenty numerów, duża historia |
| Szkice i odzyskanie po restarcie procesu | Codzienny przepływ | P0 | Brak utraty tekstu i błędnego odbiorcy |
| Przypinanie i nieprzeczytane | Textra i klienci systemowi | P0 | Widoczne, przewidywalne reguły |
| Odpowiedź z powiadomienia | Standard Androida | P0 | Blokada ekranu i zmiany uprawnień |
| Blokowanie i przywracanie nadawcy | Wiele produktów | P0 | Brak ukrytej utraty ważnych wiadomości |
| Planowanie i lista zaplanowanych | Textra i Samsung | P1 | Restart, zmiana czasu, brak sieci, utrata roli SMS |
| Krótkie opóźnienie wysłania | Textra i QUIK | P1 | Czytelny moment przekazania do sieci |
| Kopiowanie fragmentu tekstu | Textra | P1 | Linki, numery, długie wiadomości |
| Lokalne kategorie użytkownika | Samsung | P1 | Prosty model przynależności rozmowy |
| Kosz i jawne trwałe usuwanie | Google i Samsung | P1 | Zgodność własnej bazy z historią systemową |
| Filtry kodów i przesyłek | Potrzeba do walidacji | P1 | Trafność na polskich wiadomościach |
| Eksport i przywracanie | Fossify i QUIK | P1 | Format, duplikaty i załączniki |
| Personalizacja konkretnej rozmowy | Textra | P1 | Reset, podgląd i kontrast |
| Desktop i synchronizacja | Pulse i Google | P2 | Model kluczy, koszty, odzyskiwanie i unieważnianie sesji |
| Sugestie pisania lub streszczenia | Rozwój kategorii | P2 | Zgoda, język polski, korzyść i prywatność |
| Pełny RCS Android | Platforma i partnerzy | Osobna bramka | Potwierdzony dostęp i interoperacyjność |
| Wersja iPhone dla UE | TelephonyMessagingKit | Osobna bramka | Entitlement, urządzenia, operatorzy i zakres API |

Największą pokusą byłoby zrobienie od razu wszystkiego. To zwiększa powierzchnię błędów i utrudnia ustalenie, za co odbiorca chce płacić. Pierwsza wersja powinna być mała, ale rzeczywiście używalna jako domyślna aplikacja SMS.

## 14 Wykonalność techniczna i ograniczenia

### 14.1 Funkcje lokalne a kanał transportowy

| Możliwość | Ocena dla niezależnej aplikacji Android | Konsekwencja |
| --- | --- | --- |
| SMS i MMS | Realistyczne przy właściwej roli, uprawnieniach i implementacji [S32, S33, S35] | Konieczne testy na fizycznych urządzeniach |
| Własny wygląd, foldery, przypomnienia | Realistyczne funkcje lokalne | Nie wymagają własnej sieci komunikacyjnej |
| Pełny RCS przez zwykłą instalację z Play | Brak podstaw do obietnicy w obecnej dokumentacji [S31] | Nie wpisywać jako pewnej funkcji roadmapy |
| iMessage na Androidzie | Brak oficjalnego dostępu wykazanego w audycie | Nie budować planu na mostkach i obejściach |
| Zdalne cofnięcie zwykłego SMS | Brak mechanizmu dla takiej obietnicy | Można anulować wyłącznie przed wysłaniem |
| Wiarygodny odczyt SMS | Nie utożsamiać raportu doręczenia z przeczytaniem | Stany muszą używać poprawnych nazw |
| AI analizujące treść | Technicznie możliwe, lecz osobny produktowo i prywatnościowo zakres | Najpierw wykazać korzyść |

### 14.2 RCS nie jest pojedynczą biblioteką do dodania

Wniosek z dokumentacji Androida jest węższy niż zdanie, że RCS na całym świecie należy wyłącznie do Google. RCS jest standardem, ale dostęp opisanej ścieżki Android IMS jest ograniczony przez uprawnienia i certyfikację. Współpraca z OEM lub operatorem wymaga osobnego potwierdzenia. Zakup serwera nie znosi tych warunków. [S31]

Nie należy też mylić RCS Business Messaging z API do prywatnej aplikacji SMS. Oficjalna dokumentacja RBM opisuje agentów reprezentujących marki, ich weryfikację i uruchamianie. Wniosek: to inny przypadek użycia niż przejęcie indywidualnej skrzynki użytkownika. [S50]

### 14.3 Migracja to krytyczny fragment produktu

Przed włączeniem nowego klienta trzeba wyjaśnić różnicę między historią SMS i MMS a rozmowami RCS w poprzedniej aplikacji. Należy przygotować instrukcję diagnostyczną i kontrolny SMS do zaufanego odbiorcy. Test nie może wysyłać nic automatycznie bez działania użytkownika.

Procedura wyrejestrowania RCS może pomóc w określonych problemach po zmianie klienta. Oficjalna pomoc Google przestrzega jednak przed częstym przełączaniem i wskazuje skutki dla grup. Nie rekomenduję obietnicy, że wyłączenie RCS zawsze natychmiast przywróci wszystkie rozmowy. [S34]

### 14.4 Dwie karty i wieloczęściowe SMS

Android udostępnia SmsManager i operacje powiązane z subskrypcją. Dokumentacja opisuje też sytuację z wieloma kartami i brakiem domyślnego wyboru. Implementacja musi wybierać subskrypcję jawnie, zamiast zakładać, że system zawsze zapyta użytkownika. [S35]

Typowe limity pojedynczego SMS to 160 znaków GSM 7 albo 70 w kodowaniu Unicode; przy wiadomościach łączonych pojemność części jest mniejsza, zwykle 153 lub 67. To nie jest prosty licznik widocznych znaków: rozszerzenia alfabetu i emoji mogą zmieniać wynik. Liczbę części należy wyliczać zgodnie z zachowaniem platformy, bez szacowania ceny taryfy. [S43]

Praktyczny polski wyróżnik do sprawdzenia: pokaż liczbę części i zaproponuj wersję bez znaków diakrytycznych wyłącznie na życzenie. Nie zmieniać tekstu automatycznie; poprawna polszczyzna jest częścią jakości produktu.

### 14.5 Planowanie i praca w tle

Dokumentacja Androida opisuje ograniczenia alarmów i dodatkowy dostęp do precyzyjnego uruchamiania. WorkManager nie powinien być traktowany jako gwarancja wysłania dokładnie o wskazanej minucie. Trzeba uwzględnić brak sieci, wyłączony telefon, cofnięcie uprawnień, restart i zmianę strefy czasu. [S51]

Wymaganie produktu: każda zaplanowana pozycja pokazuje swój stan i warunki. Po minięciu terminu użytkownik musi móc zrozumieć, czy wiadomość została wysłana, czeka, czy wymaga decyzji. Nie wysyłać po wielu godzinach spóźnienia bez wcześniej ustalonej reguły.

## 15 Prywatność i zaufanie

### 15.1 Co deklarują konkurenci

Polityka aplikacji Textra deklaruje brak przechowywania SMS i MMS oraz książki kontaktów na serwerach firmy. Jednocześnie opisuje zbieranie danych technicznych przez dostawców reklam, między innymi identyfikatora reklamowego, adresu IP i informacji o urządzeniu. To odrębne kwestie. Polityka witryny internetowej nie zastępuje polityki aplikacji. [S05]

Google opisuje ochronę przed spamem w wariancie standardowym i rozszerzonym, z przetwarzaniem lokalnym oraz opcjonalnym Private AI Compute. Wskazuje też sprawdzanie adresów URL. Nie należy więc twierdzić ani że wszystko działa wyłącznie lokalnie, ani że każda wiadomość jest bezwarunkowo wysyłana do ogólnej chmury AI. [S12]

Deklaracje sklepu i polityki nie są wynikiem niezależnego audytu ruchu sieciowego. Nie ustalono tu, czy konkretne wersje aplikacji realizują wszystkie zapisy polityk w każdym wariancie konfiguracji.

### 15.2 Rekomendowana obietnica własnego produktu

Domyślnie bez konta i bez reklam w rozmowie. Wyszukiwanie, indeks, foldery i proste reguły klasyfikacji działają na urządzeniu. Zewnętrzne usługi wymagają jasnej informacji, jakie dane opuszczają telefon. Wsparcie techniczne nie powinno domyślnie dostawać treści wiadomości ani numerów.

Jeżeli pojawi się blokada biometryczna, trzeba uczciwie opisać jej zakres. Może blokować dostęp do interfejsu naszej aplikacji, ale nie należy obiecywać, że ukrywa SMS przed wszystkimi innymi uprawnionymi aplikacjami lub systemem.

Przed synchronizacją lub AI potrzebny jest projekt przepływu danych: kto ma klucze, co trafia na serwer, co zawiera backup, jak odłączyć urządzenie i jak usuwać dane. Sama etykieta Prywatne albo kłódka nie zastępuje takiego projektu.

## 16 Monetyzacja do zweryfikowania

Rekomenduję test modelu bez reklam w skrzynce. Dla klienta SMS przewidywalny zakup Pro może pasować do oczekiwań części odbiorców. Nie ma jeszcze badania potwierdzającego ich skłonność do zapłaty za nasz projekt.

| Model | Co można sprzedawać | Ryzyko | Rekomendacja |
| --- | --- | --- | --- |
| Jednorazowe Pro | Motywy, zaawansowane reguły i wygodne dodatki lokalne | Finansowanie wieloletniego utrzymania | Najlepszy kandydat do testu |
| Subskrypcja | Rzeczywiście kosztowna usługa chmurowa lub desktop | Trudna do uzasadnienia za sam wygląd | Dopiero po wykazaniu stałej wartości |
| Płatne pakiety wyglądu | Starannie zaprojektowane warianty | Nadmiar sklepu z motywami niszczy spokój | Ewentualny dodatek |
| Reklamy | Darmowe korzystanie finansowane emisją | Zaufanie, prywatność i przypadkowe przerwania | Nie jako domyślny fundament |

Propozycje cen do testu, **nie aktualne ceny rynkowe ani prognoza przychodu**: 19,99 zł, 39,99 zł i 59,99 zł za jednorazowe Pro. Najpierw badać, czy odbiorca wybiera produkt przy jawnie podanym braku RCS. Pokazywanie ceny dopiero po zachwycie wyglądem zafałszowałoby ocenę.

Nie zamykać za opłatą podstawowego odbierania, naprawiania błędów, cofnięcia przypadkowej akcji lub pobrania własnych danych. Wybór darmowych i płatnych funkcji musi zostać doprecyzowany po badaniu, a nie być przypadkowym podziałem listy ustawień.

## 17 Plan walidacji i kryteria decyzji

### 17.1 Badanie potrzeb

Proponuję 12 rozmów: cztery osoby korzystające z Textry, cztery używające Google lub Samsunga i cztery z dużą liczbą SMS usługowych. Grupy mogą się częściowo pokrywać. Rekrutować także osoby z dwiema kartami i różnymi wielkościami historii.

Najważniejsze pytania dotyczą ostatnich faktycznych zdarzeń: kiedy nie udało się znaleźć wiadomości, co było przyczyną zmiany klienta, z jakich funkcji korzystano w poprzednim tygodniu, czy rozmowy grupowe wymagają RCS. Nie pytać wyłącznie, czy ktoś chce ładniejszą aplikację.

### 17.2 Test prototypu

Porównać trzy warianty wizualne na identycznych, fikcyjnych danych. Testować: znalezienie adresu w starej rozmowie, wysłanie z drugiej karty, zaplanowanie SMS, anulowanie przed wysłaniem i odszukanie nieznanego nadawcy. Kolejność wariantów zmieniać między uczestnikami.

Proponowane progi decyzji, ustalone przez autora do przyszłego testu:

- Co najmniej 8 z 12 osób wykonuje podstawowe zadania bez podpowiedzi.
- Co najmniej 8 z 12 poprawnie rozumie ograniczenie RCS po krótkim wprowadzeniu.
- Najwyżej jedna osoba myli anulowanie przed wysłaniem z cofnięciem dostarczonego SMS.
- Wybór karty SIM nie prowadzi do pomyłki w żadnym krytycznym zadaniu testowym.

Te progi nie stanowią statystycznego dowodu dopasowania do rynku. Służą wykrywaniu dużych problemów i podejmowaniu decyzji o kolejnej iteracji.

### 17.3 Beta na fizycznych urządzeniach

Następnie zamknięta beta z około 30 osobami przez cztery tygodnie. Priorytet to rzeczywiste odbieranie i wysyłanie na Play, Orange, T-Mobile i Plus, nowe instalacje oraz migracje. Emulator nie odtwarza w pełni zachowania sieci operatora.

Mierzyć aktywację po zmianie aplikacji domyślnej, pozostanie przy niej po 7 i 28 dniach, powody powrotu do poprzedniego klienta, błędy wysyłki i zadania wyszukiwania. Bez zbierania treści wiadomości. Odróżniać rezygnację z powodu RCS od rezygnacji z powodu wyglądu lub błędu aplikacji.

**Proponowany warunek kontynuacji:** wśród uczestników, którzy zaakceptowali zakres SMS i MMS, przynajmniej połowa nadal wybiera aplikację jako domyślną po 28 dniach, a krytyczne błędy utraty lub zdublowania danych zostały rozwiązane. To wewnętrzna bramka projektu, nie benchmark branżowy.

### 17.4 Kiedy zmienić kierunek

Jeżeli użytkownicy wolą nasz wygląd, ale nie zostają z powodu RCS, dokładanie motywów nie rozwiąże problemu. Wtedy rozważyć węższy produkt organizujący SMS, osobną ścieżkę iPhone UE albo rozmowy z partnerami infrastrukturalnymi. Jeżeli natomiast zaakceptowany klient SMS przegrywa przez wyszukiwanie lub błędy, naprawić podstawy przed rozszerzaniem zakresu.

## 18 Proponowany podział prac

To kolejność prac, nie gwarantowany harmonogram. Długość zależy od zespołu, punktu startowego, doświadczenia z telephony, zaplecza testowego i decyzji o funkcjach.

| Etap | Wynik do oceny | Warunek zakończenia |
| --- | --- | --- |
| A Zakres i protokoły | Krótka specyfikacja SMS i MMS, mapa ryzyk migracji | Brak ukrytego założenia o pełnym RCS |
| B Wygląd i przepływy | Trzy ekrany oraz ich stany błędu i pustej listy | Test zadaniowy i wybór kierunku |
| C Rdzeń Android | Domyślna aplikacja, SMS, MMS, historia, SIM i powiadomienia | Kontrolowane testy operatorów |
| D Wersja do codziennego używania | Wyszukiwanie, szkice, blokowanie, niezawodne stany | Beta bez otwartych krytycznych usterek |
| E Przewaga użytkowa | Planowanie, kategorie, personalizacja, eksport | Potwierdzona korzyść w codziennych zadaniach |
| F Decyzja rynkowa | Retencja, powody rezygnacji i test ceny | Uzasadnienie dalszej inwestycji |

Proponowany zestaw materiałów po audycie: PRD, opis systemu wizualnego, makiety głównych przepływów oraz backlog z kryteriami akceptacji. Ten raport przygotowuje do ich stworzenia; nie oznacza rozpoczęcia implementacji aplikacji.

## 19 Rejestr ryzyk i niewiadomych

| Ryzyko | Waga dla decyzji | Dowód lub stan | Sposób ograniczenia |
| --- | --- | --- | --- |
| Brak pełnego RCS Android | Krytyczna | Ograniczenia platformy [S31] | Zakres SMS i MMS lub potwierdzona ścieżka partnerska |
| Niedochodzące wiadomości po migracji | Krytyczna | Relacje użytkowników i pomoc migracyjna [S34, S45] | Testy, instrukcja, jawna granica historii |
| Błędna karta SIM | Krytyczna | Złożoność API i konfiguracji [S35] | Jawny wybór i scenariusze zmian kart |
| Rozjazd bazy lokalnej i systemowej | Wysoka | Ryzyko projektu, nie stwierdzony błąd konkurenta | Jednoznaczne źródło danych i scenariusze przełączania aplikacji |
| Estetyka bez retencji | Wysoka | Hipoteza niepotwierdzona | Beta i pomiar powrotu do starego klienta |
| Nadmierny zakres | Wysoka | Decyzja projektowa | Mały P0 i odrębne bramki dla chmury i RCS |
| Niedokładne planowanie | Wysoka | Ograniczenia pracy w tle [S51] | Testy restartu, czasu i uprawnień |
| Błędna klasyfikacja ważnych SMS | Wysoka | Ryzyko własnego rozwiązania | Widoczne filtry, ręczne poprawki, brak cichego kasowania |
| Niedoszacowane utrzymanie MMS | Wysoka | Zależność od urządzenia i operatora | Zestaw fizycznych telefonów i kart testowych |
| Użytkownicy nie zapłacą | Wysoka | Brak danych | Test ceny po ujawnieniu ograniczeń |
| Przeniesienie realiów USA do Polski | Średnia | Wiele relacji dotyczy USA | Polska rekrutacja i źródła regionalne |
| Przezroczystości pogorszą czytelność | Średnia | Ryzyko kierunku wizualnego | Test kontrastu i alternatywny motyw |

Niewiadome wymagające dalszej pracy: bieżąca dostępność Samsung Messages na polskich modelach i wersjach, lokalna cena Textra Pro, rzeczywista skala użytkowników niezadowolonych z wyglądu, warunki dostępu i ograniczenia TelephonyMessagingKit w testowym projekcie oraz gotowość do zapłaty mimo braku RCS na Androidzie.

## 20 Rekomendacja końcowa dla projektu

**Zrobiłbym najpierw prototyp i techniczną podstawę klienta SMS i MMS.** Kierunek wizualny powinien łączyć spokojną czytelność, dopracowane detale i wygodę używania na dużym telefonie. Najbardziej obiecujący zestaw to dobra skrzynka, wyszukiwanie, pewny wybór SIM, planowanie i niewymuszona personalizacja.

Nie ma jeszcze dowodu, że rynek potrzebuje kolejnego ogólnego komunikatora SMS, ale istnieją konkretne sygnały do przetestowania: przywiązanie do kontroli w Textrze, potrzeba organizacji i niezadowolenie części osób z migracji. O powodzeniu zdecyduje możliwość używania aplikacji na co dzień z akceptowalnymi ograniczeniami.

**Decyzja dziś:** przejść do prototypu i badania małym kosztem; uzależnić pełną inwestycję od wyników. Jeśli celem ma być bezwarunkowo lepszy zamiennik Google ze wszystkimi czatami RCS, najpierw potrzebne jest rozwiązanie bariery infrastrukturalnej.

## 21 Źródła

Wszystkie źródła sprawdzono 26 października 2026 roku. Identyfikatory w tekście wskazują poniższe pozycje. Daty postów i komunikatów podano tam, gdzie są istotne. Źródła producenta dokumentują funkcje lub deklaracje, a społecznościowe dokumentują doświadczenia autorów postów.

- [S01] Textra, strona produktu, funkcje i cena Pro. [Otwórz źródło](https://www.textra.me/)
- [S02] Textra SMS, Google Play, odczyt en US, funkcje, ocena i widoczne opinie. [Otwórz źródło](https://play.google.com/store/apps/details?id=com.textra&hl=en_US)
- [S03] Textra, oficjalny changelog, w tym wersja 4.87. [Otwórz źródło](https://inapp.textra.me/changelog)
- [S04] Textra, oficjalna baza pomocy o RCS. [Otwórz źródło](https://textra.uservoice.com/knowledgebase/articles/1846105-rcs-support-for-textra-sms)
- [S05] Textra, polityka prywatności aplikacji. [Otwórz źródło](https://textra.me/privacyapp)
- [S06] Wiadomości Google, Google Play, odczyt en US. [Otwórz źródło](https://play.google.com/store/apps/details?id=com.google.android.apps.messaging&hl=en_US)
- [S07] Google, strona Wiadomości Google. [Otwórz źródło](https://www.android.com/google-messages/)
- [S08] Google, personalizacja rozmów. [Otwórz źródło](https://support.google.com/messages/answer/14790371?hl=en-ca)
- [S09] Google Messages Community, komunikat menedżera społeczności o motywach, 27 sierpnia 2026. [Otwórz źródło](https://support.google.com/messages/thread/457246478/personalize-your-chats-to-match-your-style-with-custom-backgrounds-and-themes-%F0%9F%8E%89)
- [S10] Google, korzystanie z szyfrowania end to end. [Otwórz źródło](https://support.google.com/messages/answer/10252671?hl=en)
- [S11] Google, działanie i bezpieczeństwo RCS. [Otwórz źródło](https://support.google.com/messages/answer/9592174?hl=en)
- [S12] Google, prywatność ochrony przed spamem i oszustwami. [Otwórz źródło](https://support.google.com/messages/answer/9327903?hl=en)
- [S13] Google, wyszukiwanie i planowanie wiadomości. [Otwórz źródło](https://support.google.com/messages/answer/10456318?hl=en)
- [S14] Google, wiadomości na komputerze i tablecie. [Otwórz źródło](https://support.google.com/messages/answer/7611075?hl=en)
- [S15] Google, edycja wysłanej wiadomości. [Otwórz źródło](https://support.google.com/messages/answer/14929292?hl=en)
- [S16] Google, folder kosza. [Otwórz źródło](https://support.google.com/messages/answer/16962293?hl=en)
- [S17] Google Messages Community, zapowiedź kosza, 20 marca 2026. [Otwórz źródło](https://support.google.com/messages/thread/418575777?hl=en)
- [S18] Samsung USA, oficjalny komunikat zakończenia działania, wyłącznie USA. [Otwórz źródło](https://www.samsung.com/us/apps/samsung-messages/)
- [S19] Samsung USA, instrukcja Samsung Messages i ograniczenia wersji. [Otwórz źródło](https://www.samsung.com/us/support/answer/ANS10001926/)
- [S20] Samsung USA, rozmowy grupowe i kategorie. [Otwórz źródło](https://www.samsung.com/us/support/answer/ANS10003653/)
- [S21] Samsung Developer, dostępność One UI. [Otwórz źródło](https://developer.samsung.com/one-ui/accessibility/intro.html)
- [S22] Apple, rozpoczęcie pracy z Wiadomościami, bieżący przewodnik iPhone. [Otwórz źródło](https://support.apple.com/en-ie/guide/iphone/iph6c75e9c87/ios)
- [S23] Apple, nowości i ograniczenia iOS 27. [Otwórz źródło](https://support.apple.com/guide/iphone/whats-new-in-ios-27-iphfed2c4091/ios)
- [S24] Apple, tła w Wiadomościach. [Otwórz źródło](https://support.apple.com/en-gb/guide/iphone/iph605fa06e4/ios)
- [S25] Apple, planowanie iMessage. [Otwórz źródło](https://support.apple.com/guide/iphone/schedule-a-text-message-to-send-later-iph5ae9a7be6/26/ios/26)
- [S26] Apple, cofanie i edytowanie wiadomości, bieżący przewodnik. [Otwórz źródło](https://support.apple.com/en-ie/guide/iphone/iphe67195653/ios)
- [S27] Apple, filtrowanie i nieznani nadawcy. [Otwórz źródło](https://support.apple.com/en-euro/guide/iphone/iph203ab0be4/ios)
- [S28] Apple Newsroom, wdrażanie szyfrowanego RCS, 11 maja 2026. [Otwórz źródło](https://www.apple.com/newsroom/2026/05/end-to-end-encrypted-rcs-messaging-begins-rolling-out-today-in-beta/)
- [S29] Apple Developer, TelephonyMessagingKit. [Otwórz źródło](https://developer.apple.com/documentation/TelephonyMessagingKit)
- [S30] Apple Developer, Default Carrier Messaging App entitlement. [Otwórz źródło](https://developer.apple.com/documentation/BundleResources/Entitlements/com.apple.developer.carrier-messaging-app)
- [S31] AOSP, IMS single registration, sekcja Security and permissions. [Otwórz źródło](https://source.android.com/docs/core/connect/ims-single-registration)
- [S32] Android Developers, uprawnienia aplikacji domyślnych. [Otwórz źródło](https://developer.android.com/guide/topics/permissions/default-handlers)
- [S33] Google Play, zasady używania uprawnień SMS i rejestru połączeń. [Otwórz źródło](https://support.google.com/googleplay/android-developer/answer/10208820?hl=en)
- [S34] Google, włączanie i wyłączanie RCS przy zmianie aplikacji. [Otwórz źródło](https://support.google.com/messages/answer/7189714?hl=en)
- [S35] Android Developers, SmsManager i subskrypcje. [Otwórz źródło](https://developer.android.com/reference/android/telephony/SmsManager)
- [S36] W3C, WCAG 2.2, Contrast Minimum. [Otwórz źródło](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html)
- [S37] Android Developers, wielkość pól dotykowych. [Otwórz źródło](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views?hl=en)
- [S38] Chomp SMS, Google Play. [Otwórz źródło](https://play.google.com/store/apps/details?id=com.p1.chompsms&hl=en_US)
- [S39] Pulse SMS, Google Play. [Otwórz źródło](https://play.google.com/store/apps/details?id=xyz.klinker.messenger&hl=en_US)
- [S40] Fossify Messages, Google Play. [Otwórz źródło](https://play.google.com/store/apps/details?id=org.fossify.messages&hl=en_US)
- [S41] QUIK, oficjalne repozytorium, README i deklaracja GPLv3. [Otwórz źródło](https://github.com/quik-sms/quik)
- [S42] UKE, komunikat o raporcie rynku za 2025 rok, 30 czerwca 2026. [Otwórz źródło](https://uke.gov.pl/akt/raport-o-stanie-rynku-komunikacji-elektronicznej-w-2025-roku,670.html)
- [S43] Twilio, kodowanie i segmenty SMS; parametry zależą od implementacji i rynku. [Otwórz źródło](https://www.twilio.com/docs/glossary/what-sms-character-limit)
- [S44] Reddit Textra, zmiana na Google po problemach z grupami, 20 października 2026. [Otwórz źródło](https://www.reddit.com/r/textra/comments/1xb0b70/finally_had_to_join_all_of_the_other_folks_and/)
- [S45] Reddit Textra, Texts failing, 4 października 2026. [Otwórz źródło](https://www.reddit.com/r/textra/comments/1wxqrdp/texts_failing/)
- [S46] Reddit Textra, Other messaging apps, 19 września 2026. [Otwórz źródło](https://www.reddit.com/r/textra/comments/1wkikig/other_messaging_apps/)
- [S47] Reddit Textra, Why does Textra work for some but not all, 30 kwietnia 2026. [Otwórz źródło](https://www.reddit.com/r/textra/comments/1t03zc3/why_does_textra_work_for_some_but_not_all/)
- [S48] Reddit Textra, Switched to Google Messages for RCS, 16 marca 2025. [Otwórz źródło](https://www.reddit.com/r/textra/comments/1jco48w/switched_to_google_messages_for_rcs/)
- [S49] Reddit Textra, Group Messaging via Textra possible, 21 października 2026. [Otwórz źródło](https://www.reddit.com/r/textra/comments/1xbdoyw/group_messaging_via_textra_possible_ymmv/)
- [S50] Google Developers, RCS for Business, zarządzanie agentami marek. [Otwórz źródło](https://developers.google.com/business-communications/rcs-business-messaging/guides/management-api/agents)
- [S51] Android Developers, alarmy i planowanie pracy. [Otwórz źródło](https://developer.android.com/develop/background-work/services/alarms)

## 22 Ustalenia z rozmowy i zakres projektu Chatra

Uzupełnienie z 29 października 2026 roku zbiera dalszą rozmowę o SMS, iMessage, grupach, jakości MMS, własnym przesyłaniu zdjęć, językach programowania i stabilności systemów. Rozdziały 1-21 zachowują badanie bazowe z 26 października. Nowe rozdziały rozszerzają jego zakres; nie oznaczają ponownego sprawdzenia wszystkich wcześniejszych ocen sklepowych, cen i wdrożeń.

### 22.1 Co zostało wskazane przez użytkownika

- Celem jest aplikacja do wiadomości wygodniejsza i ładniejsza od Textry.
- Podstawą pozostają SMS i MMS. Rozmowy głosowe oraz połączenia wideo zostały wyłączone z zakresu.
- Po omówieniu strat jakości MMS użytkownik wskazał kierunek własnego sposobu przesyłania załączników.
- Należy zapisać ustalenia w audycie i umieścić dokumentację w repozytorium Chatra.

Wybór Kotlin, Rust, C++ lub innego języka nie został zatwierdzony przez użytkownika. Poniższe propozycje techniczne pozostają rekomendacjami do sprawdzenia w prototypie. Nie zatwierdzono również ceny usługi, docelowej infrastruktury ani gwarancji obsługi RCS.

### 22.2 Najważniejsze wnioski produktowe

- Zachować praktyczną kontrolę znaną z Textry: ustawienia rozmów, planowanie, krótkie opóźnienie wysłania i wygodne operacje na tekście.
- Od Apple przejąć staranność kompozycji: hierarchię, typografię, odstępy i spokojny ekran rozmowy. Stworzyć własną tożsamość na Androidzie.
- Od Samsunga przejąć organizację i wygodę obsługi jedną ręką.
- Zaprojektować kilka spójnych motywów z podglądem zamiast domyślnego eksponowania dziesiątek parametrów.
- Priorytetami funkcjonalnymi są wyszukiwanie, szkice, nieprzeczytane, przypinanie, wybór SIM, planowanie i odzyskiwanie przypadkowo usuniętych danych.
- Bezpieczeństwo historii i przewidywalna wysyłka są warunkami wartości estetycznego interfejsu.
- Brak reklam w rozmowach i jednorazowe Pro są rekomendacjami modelu biznesowego do walidacji.
- Popularność istniejących klientów potwierdza istnienie kategorii, lecz nie dowodzi popytu na Chatra. Trzeba mierzyć pozostanie przy aplikacji jako domyślnej.

To synteza rekomendacji z części bazowej, a nie nowe wyniki badań użytkowników. Szczegóły i źródła pozostają w rozdziałach 3-20.

### 22.3 iPhone i iMessage

Komunikacja z numerem telefonu użytkownika iPhone'a nie wymaga bezpośredniej integracji z iMessage. Aplikacja Wiadomości na iPhonie obsługuje także SMS, MMS i RCS; faktyczny kanał zależy od rozmowy i dostępności. Dla Chatra pierwszym wspólnym kanałem z iPhone'em są SMS i MMS. Nie oznacza to przejęcia istniejącej historii ani funkcji iMessage. [S53]

Nie zakładamy oficjalnego dostępu do iMessage na Androidzie. Mostki wymagające dodatkowego urządzenia lub nieoficjalnych obejść nie stanowią podstawy planu produktu. TelephonyMessagingKit na iPhonie pozostaje osobną możliwością do sprawdzenia dla UE, opisaną w rozdziale 7.4; nie daje automatycznie dostępu do iMessage. [S29, S30]

### 22.4 Dwa różne rodzaje wysyłki grupowej

| Tryb | Zachowanie | Nazwa proponowana w interfejsie |
| --- | --- | --- |
| Oddzielne SMS do wielu osób | Każdy otrzymuje własną wiadomość; odpowiedzi wracają indywidualnie | Wyślij osobno do każdego |
| Grupowy MMS | Uczestnicy mogą widzieć odpowiedzi pozostałych w grupie; zależy od obsługi sieci i urządzeń | Rozmowa grupowa MMS |

To rozróżnienie wynika również z instrukcji Apple. Przy grupowym MMS trzeba jasno pokazać listę uczestników oraz to, że ich numery będą widoczne dla pozostałych. Koszty i limity należy wiązać z rzeczywistym kanałem i taryfą. [S53]

Istniejącej grupy iMessage lub RCS nie da się potraktować jako automatycznie przeniesionej grupy MMS. Rozpoczęcie nowej rozmowy MMS z tymi samymi osobami wymaga sprawdzenia obsługi operatorów i konfiguracji telefonów. Nie obiecujemy ciągłości tożsamości grupy ani wszystkich funkcji poprzedniego kanału.

### 22.5 RCS i migracja

Zmiana domyślnego klienta SMS nie zapewnia dostępu do pełnego RCS. Ograniczenia Android IMS pozostają opisane w rozdziale 14.2. Własne przesyłanie załączników również nie otwiera dostępu do infrastruktury RCS. [S31]

W określonych problemach po zmianie aplikacji potrzebna może być procedura wyłączenia RCS w poprzednim kliencie. Pomoc Google opisuje tę sytuację i wskazuje skutki przełączania dla grup. Chatra powinna udostępniać instrukcję i test kontrolny, bez obietnicy natychmiastowego odzyskania każdej rozmowy. Nie wymagamy od wszystkich kontaktów wyłączenia RCS jako standardowego sposobu korzystania z aplikacji. [S34]

## 23 Jakość MMS i własne przesyłanie załączników

### 23.1 Skąd biorą się straty jakości

MMS podlega limitom rozmiaru załączników ustalanym przez operatora. Apple potwierdza, że iPhone może kompresować zdjęcia i wideo, aby umożliwić wysłanie. Dokładnej wartości limitu nie można ustalić uniwersalnie dla każdej sieci i konfiguracji. [S52]

Zmniejszanie rozmiaru może oznaczać utratę rozdzielczości i szczegółów. Film wymaga zapisania wielu klatek oraz dźwięku, więc mały budżet danych szczególnie ogranicza jego jakość. To wyjaśnienie techniczne; w tym audycie nie zmierzono strat dla konkretnych polskich operatorów.

Problem może dotyczyć obu kierunków: iPhone do Androida i Android do iPhone'a. Dobra jakość między urządzeniami Apple może wynikać z użycia iMessage zamiast MMS. RCS również stanowi inny kanał; nie jest lepszym ustawieniem kompresji tego samego MMS. [S28, S53]

### 23.2 Co można poprawić wewnątrz MMS

- Dobierać rozmiar i kompresję do potwierdzonego limitu zamiast niepotrzebnie zmniejszać plik bardziej.
- Pokazywać podgląd efektu oraz informację, że wysłana zostanie pomniejszona kopia.
- Zachować oryginał na urządzeniu i umożliwić świadomy wybór sposobu wysłania.
- Nie obiecywać odzyskania szczegółów, których nie ma w otrzymanym, skompresowanym pliku.

Zmiana języka implementacji lub szybszego procesora nie podnosi limitu MMS. Lepszy algorytm może efektywniej wykorzystać dostępny rozmiar; nie gwarantuje zachowania oryginału w każdych warunkach.

### 23.3 Proponowany własny kanał HD

Rekomendowany prototyp zachowuje tekstowe SMS i MMS oraz dodaje osobny transfer internetowy dla załączników. Własny sposób przesyłania oznacza kontrolę nad przepływem pliku, stanami, uprawnieniami i przechowywaniem. Nie wymaga tworzenia od podstaw TCP, TLS ani własnej kryptografii.

1. Nadawca wybiera oryginał lub jawnie opisaną kopię i tryb wysyłki.
2. Chatra wysyła plik przez szyfrowane połączenie do usługi przechowującej załączniki.
3. Usługa potwierdza zapis; aplikacja utrwala identyfikator transferu i jego stan.
4. Jeśli odbiorca korzysta z obsługującego tę funkcję klienta Chatra, może zobaczyć załącznik bezpośrednio w rozmowie.
5. Jeśli odbiorca nie ma takiego klienta, nadawca może wysłać SMS z odnośnikiem otwieranym w przeglądarce, również na iPhonie.
6. Nieudany transfer oferuje ponowienie lub świadomą zmianę kanału. Aplikacja nie wysyła automatycznie kolejnego płatnego SMS albo MMS bez określonej reguły i zgody użytkownika.

To projekt proponowanego działania, nie funkcja już zaimplementowana. W wariancie z odnośnikiem standardowa aplikacja odbiorcy dostaje tekst i link. Nie obiecujemy, że pokaże nasz własny załącznik jak natywny MMS. Podgląd odnośnika nie jest gwarantowany.

### 23.4 Jak zachować oryginalną jakość

Tryb Oryginał powinien przesyłać plik bez ponownego stratnego kodowania. Podglądy i miniatury mogą być osobnymi plikami; nie powinny zastępować oryginału podczas pobierania. Testem poprawności jest zgodność skrótu SHA-256 pliku przed wysłaniem i po pobraniu.

Jeżeli użytkownik wybiera usunięcie metadanych lokalizacji albo zmianę formatu, otrzymuje zmodyfikowaną kopię. Interfejs powinien odróżniać ją od bitowo identycznego oryginału. Dostępność danego formatu w przeglądarce lub galerii wymaga osobnego sprawdzenia; zachowanie bajtów nie gwarantuje podglądu na każdym urządzeniu.

### 23.5 Dostęp do pliku i koszty

Przed produkcyjną wersją trzeba ustalić zasady dostępu: czy wystarcza posiadanie linku, czy wymagane jest dodatkowe potwierdzenie odbiorcy, jak długo plik jest dostępny i jak nadawca cofa dostęp. Samo HTTPS nie oznacza szyfrowania end to end. Taką obietnicę można złożyć dopiero po zaprojektowaniu i sprawdzeniu zarządzania kluczami.

Usługa generuje koszty przechowywania i transferu, a przy weryfikacji numerów także możliwe koszty wiadomości weryfikacyjnych. Ceny, limity wielkości, czas retencji i finansowanie nie zostały wybrane. Zwykłe SMS i MMS powinny działać bez obowiązkowego konta w usłudze HD; uprawnienie do transferu internetowego projektujemy osobno.

Pierwszy zakres nie obejmuje budowy rozbudowanego komunikatora głosowego ani wideo. Własne grupy internetowe również nie powstają automatycznie wraz z hostingiem zdjęć: wymagałyby osobnego projektu członkostwa, dostępu i historii. Na start grupy pozostają osobnym testowanym scenariuszem MMS.

## 24 Języki programowania i wydajność

### 24.1 Trudniejszy język nie gwarantuje przewagi

Nie ma ogólnego rankingu, w którym język trudniejszy od Rusta automatycznie wysyła pliki szybciej lub zapewnia lepszy obraz. Rust jest przeznaczony także do wydajnych usług sieciowych i nie wymaga garbage collectora. C++ opiera swój projekt na zasadzie unikania zbędnego narzutu abstrakcji. O wyniku konkretnej implementacji decydują algorytm, biblioteki, kompilacja i profil obciążenia. [S54, S55]

| Technologia | Trudność według autora | Możliwa rola w Chatra | Czego nie gwarantuje |
| --- | --- | --- | --- |
| Rust | Wysoka, zwłaszcza model własności i współbieżność | Moduł transferu lub usługa serwerowa | Braku błędów logiki, poprawnej migracji ani niższych kosztów sieci |
| C++ | Bardzo wysoka w złożonych projektach | Wykorzystanie istniejących bibliotek multimedialnych lub zmierzonego fragmentu obliczeniowego | Automatycznej przewagi wydajności nad Rustem |
| C | Niewielka składnia, trudne utrzymanie poprawności dużego programu | Integracje z bibliotekami i kodem niskopoziomowym | Stabilności wynikającej z samej prostoty składni |
| Zig | Wysoka przy ręcznym zarządzaniu pamięcią | Alternatywa do rozważenia dla małych modułów systemowych | Ogólnej przewagi nad Rustem |
| Asembler ARM64 | Skrajnie wysoka dla dużego programu | Ewentualnie bardzo mały, zmierzony fragment obliczeń | Szybszej całej aplikacji lub szybszego łącza |

Ocena trudności jest jakościową opinią autora. Dokumentacja Zig opisuje ręczne zarządzanie pamięcią, a dokumentacja Microsoft przedstawia asembler i intrinsics jako narzędzia zależnej od procesora kontroli niskopoziomowej. Nie wykonano benchmarku tych języków dla Chatra. [S56, S57]

### 24.2 Rekomendacja robocza

- Interfejs i integracja z Androidem: Kotlin oraz Jetpack Compose jako proponowany punkt wyjścia.
- Pierwszy prototyp wysyłania powinien wykazać, czy potrzebujemy osobnego modułu natywnego. Rust jest sensownym kandydatem, ale nie jest obowiązkowy do zwykłego transferu plików.
- C++ lub C stosować przy uzasadnionej integracji biblioteki albo wykazanej potrzebie wydajnościowej.
- Asembler rozważać dopiero po pomiarze wąskiego gardła; nie planować w nim całej aplikacji.

Własna kompresja lub dekodery nie są wymagane do przesłania oryginalnego pliku. Prototyp może korzystać ze standardowego transferu internetowego i gotowych bibliotek. Wybór docelowego stosu wymaga osobnego prototypu technicznego.

### 24.3 Co mierzyć zamiast oceniać nazwę języka

Mierzyć czas wysyłania i pobierania pliku, liczbę ponowień, ruch sieciowy, zużycie CPU i pamięci oraz wpływ na baterię. Używać tych samych plików, telefonu i warunków sieciowych. Oddzielać czas oczekiwania na sieć od rzeczywistych obliczeń. Bez takich danych nie ma podstaw do obietnic wielokrotnego przyspieszenia.

## 25 Stabilność systemów i wnioski dla aplikacji

### 25.1 Czego nie wynika z porównania macOS Windows i Androida

W rozmowie pojawiła się obserwacja większej stabilności macOS. Nie stanowi ona porównawczego pomiaru awaryjności tych systemów. Trzeba rozróżnić płynność animacji, awarię jednej aplikacji, nieudane działanie w tle oraz restart całego systemu.

Integracja sprzętu i oprogramowania, sterowniki, zakres konfiguracji oraz testy wpływają na przewidywalność. Wyjaśnienie, że węższy zestaw kontrolowanych konfiguracji ułatwia testowanie, jest wnioskiem inżynierskim; nie dowodzi przewagi każdej wersji macOS nad każdym urządzeniem z Windows lub Androidem.

W porównaniu omawianym w rozmowie Apple kontroluje własne modele komputerów i ich system. Windows musi współpracować z bardzo różnymi konfiguracjami oraz sterownikami dostawców. Android także występuje w wielu wariantach producentów. Większa liczba kombinacji utrudnia wyczerpujące testy, ale nie przesądza, że konkretna instalacja będzie niestabilna.

### 25.2 Izolowanie skutków błędu

Apple wskazuje, że sterowniki DriverKit i rozszerzenia działające w przestrzeni użytkownika poprawiają stabilność w porównaniu z umieszczaniem takich usług w procesie jądra. XNU zawiera także komponenty C++; stabilność Apple nie wynika z jednego języka niedostępnego innym twórcom. Apple opisuje też rzeczywiste awarie jądra i nieoczekiwane restarty. [S58, S59, S61]

Android składa się z wielu warstw, w tym frameworka, usług, HAL i komponentów zależnych od dostawców. W końcowym produkcie uczestniczą różne podmioty. Wniosek dla Chatra: trzeba sprawdzać zachowanie na konkretnych telefonach i wersjach oprogramowania zamiast zakładać identyczne działanie każdej konfiguracji. [S60]

### 25.3 Jak tę lekcję zastosować do Chatra

- Utrwalać stan wiadomości i transferu, żeby zamknięcie procesu nie oznaczało utraty pracy.
- Rozdzielić interfejs, SMS i MMS, transfer internetowy oraz przechowywanie danych tak, aby błąd jednego zadania nie niszczył historii.
- Projektować kontrolowane ponowienia i identyfikatory operacji, aby wznowienie nie tworzyło duplikatów.
- Poprawnie rozpoznawać brak sieci, nieaktualny link, brak miejsca, utratę roli SMS i zmianę karty SIM.
- Nie przedstawiać wysłania do serwera jako potwierdzenia odebrania lub przeczytania przez człowieka.
- Sprawdzać zgodność danych, odzyskiwanie po restarcie i zachowanie przy ograniczeniach pracy w tle.

Rust może ograniczyć pewne klasy błędów pamięci, ale nie zastąpi poprawnego projektu stanów i testów. Kotlin, C++ ani asembler również nie zapewniają samodzielnie stabilności całego produktu.

## 26 Decyzje i kryteria sprawdzenia uzupełnienia

| Pozycja | Status | Następny konkretny krok |
| --- | --- | --- |
| Lepsza aplikacja SMS i MMS | Kierunek wskazany przez użytkownika | Prototyp głównych ekranów i rdzenia wiadomości |
| Połączenia głosowe i wideo | Poza zakresem | Nie dodawać do pierwszej wersji |
| Własny sposób przesyłania załączników | Kierunek wskazany przez użytkownika | Sprawdzić wariant plik internetowy oraz SMS z linkiem |
| Zachowanie oryginalnej jakości | Cel proponowanego trybu Oryginał | Porównać skrót pliku przed i po transferze |
| Kotlin i Rust | Rekomendacja autora | Prototyp i pomiary przed ostatecznym wyborem |
| C++ C Zig i asembler | Omówione alternatywy | Używać wyłącznie przy konkretnym uzasadnieniu |
| Pełny RCS Android | Niepotwierdzona możliwość dla zwykłej instalacji | Oddzielnie potwierdzić dostęp i warunki |
| Integracja iMessage na Androidzie | Nieprzyjęta podstawa projektu | Zapewnić komunikację z numerami iPhone przez dostępne kanały |
| Cena i limity HD | Nierozstrzygnięte | Ustalić model kosztów i zasady dostępu |

Do weryfikacji prototypu dochodzą następujące scenariusze:

1. iPhone odbiera zwykły SMS oraz MMS wysłany z Androida; sprawdzić także kierunek odwrotny.
2. Oddzielne SMS do wielu odbiorców i wspólny MMS grupowy zachowują się zgodnie z wybranym trybem.
3. Odbiorca bez Chatra otwiera odnośnik na iPhonie i pobiera dostępny plik.
4. Pobranie w trybie Oryginał daje identyczne bajty; miniatura nie zastępuje oryginału.
5. Przerwanie sieci, restart aplikacji i ponowienie nie gubią pliku ani nie wysyłają zbędnych wiadomości.
6. Cofnięty lub wygasły dostęp daje zrozumiały komunikat; interfejs nie twierdzi, że plik nadal jest dostępny.
7. Zmiana SIM i utrata roli domyślnego klienta nie prowadzą do wysyłki z niewłaściwego numeru.
8. Przy wariancie kopiowania bez metadanych użytkownik rozumie, że plik różni się od oryginału.

To kryteria przyszłych testów. W tym uzupełnieniu nie wykonano implementacji ani testów na telefonach. Dokumentacja zapisuje zakres, ograniczenia i propozycje, aby kolejne prace nie opierały się na niepotwierdzonych obietnicach.

## 27 Źródła uzupełnienia

Poniższe materiały były podstawą wyjaśnień w dalszej rozmowie 27 października 2026 roku i zostały zebrane w tym uzupełnieniu 29 października. Identyfikatory S01-S51 zachowują znaczenie z części bazowej. Nie jest to deklaracja ponownego pełnego researchu na dzień uzupełnienia.

- [S52] Apple, ograniczenia załączników i kompresja MMS na iPhonie. [Otwórz źródło](https://support.apple.com/pl-pl/118433)
- [S53] Apple, rodzaje wiadomości grupowych i różnica między grupowym SMS MMS RCS i iMessage. [Otwórz źródło](https://support.apple.com/en-ie/118236)
- [S54] Rust, przeznaczenie języka i brak garbage collectora. [Otwórz źródło](https://rust-lang.org/)
- [S55] Standard C++, zasada zero overhead. [Otwórz źródło](https://isocpp.org/wiki/faq/big-picture)
- [S56] Zig, dokumentacja i zarządzanie pamięcią. [Otwórz źródło](https://ziglang.org/documentation/)
- [S57] Microsoft Learn, intrinsics i asembler. [Otwórz źródło](https://learn.microsoft.com/en-us/cpp/intrinsics)
- [S58] Apple Developer, sterowniki i rozszerzenia działające poza jądrem. [Otwórz źródło](https://developer.apple.com/documentation/systemextensions/implementing-drivers-system-extensions-and-kexts)
- [S59] Apple, repozytorium XNU i opis komponentów. [Otwórz źródło](https://github.com/apple-oss-distributions/xnu/blob/main/README.md)
- [S60] AOSP, warstwy architektury Androida. [Otwórz źródło](https://source.android.com/docs/core/architecture)
- [S61] Apple, awarie jądra i nieoczekiwane restarty Maca. [Otwórz źródło](https://support.apple.com/guide/mac-help/if-your-mac-restarts-and-a-message-appears-mchlp2890/27/mac/27)
