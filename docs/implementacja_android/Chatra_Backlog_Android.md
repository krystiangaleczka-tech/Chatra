# Chatra Szczegółowy backlog implementacji Android

Wersja 1.0 · 29 października 2026 · powiązanie z dokumentem Chatra Dokumentacja Android

Backlog obejmuje pełną aplikację, nie tylko statyczne ekrany. Każde zadanie ma zakres, wykonawcę, zależności, nakład oraz kryteria i weryfikację. Nakład oznacza osobodni pracy przy znanych kontraktach i dostępnym środowisku, nie termin kalendarzowy ani ofertę cenową. Zakresy są orientacyjne; po spike szkła i MMS należy je ponownie oszacować.

P0 oznacza fundament lub ryzyko blokujące dalsze prace; P1 oznacza pozostały wymagany zakres pełnego wydania. P1 nie oznacza funkcji, którą można pominąć bez zmiany zakresu. Skrót BE oznacza backend, QA oznacza testy, a UX oznacza projektowanie interfejsu. Zadania z kilkoma rolami mają wspólny nakład, nie podwójne naliczenie.

## 1 Kamienie milowe i zasady zamykania

| Etap | Wynik | Warunek przejścia |
| --- | --- | --- |
| M0 Ustalenie podstaw | CH001–CH006 | Zamknięte ryzyka fontu, szkła, minimalnego Androida i granic produktu |
| M1 Wierny interfejs | CH007–CH020 | 10 ekranów z danymi demonstracyjnymi, raport obrazów i komplet projektów brakujących |
| M2 Lokalny klient | CH021–CH034 | Rzeczywiste SMS/MMS, SIM, szkice, powiadomienia i media |
| M3 Funkcje lokalne | CH035–CH045 | Harmonogram, wyszukiwanie, wygląd, porządek i Pro |
| M4 Pełne HD | CH046–CH053 | Dwa urządzenia i odbiorca linku obsługują cały przepływ |
| M5 Odbiór i wydanie | CH054–CH064 | Raporty regresji, urządzeń, prywatności, dostępności i gotowość dystrybucji |

Po CH005 można prowadzić niezależne prace UI i telefonii, a po CH046/CH047 backend i klient HD. Zadania QA przygotowywać wcześniej na gotowych kontraktach; podane zależności są warunkami finalnego zamknięcia. Publikacja wymaga zakończenia pełnego zakresu. Samo „ekran się otwiera” nie jest kryterium wykonania.

Definition of Done każdego zadania: implementacja w repozytorium, przegląd, odpowiednie testy lub mierzalny dowód działania, obsłużone błędy, aktualna dokumentacja oraz brak danych wrażliwych w logach. Dla UI dodatkowo odnośnik do obrazu wzorca i raport porównania. Dla operacji systemowych test na wymaganym poziomie platformy; fake transport nie zastępuje urządzenia.

## 2 Ustalenia i fundament

### CH001 Ustalić kontrakt produktu i decyzje otwarte

**P0 · produkt i lider Android · 1–2 osobodni · zależności brak**

Wykonanie: utworzyć rejestr G01–G10 z dokumentacji; wskazać właścicieli. Potwierdzić minSdk, dystrybucję, listę operatorów, obsługę wideo/grup MMS, kosz i zakres Pro. Zapisać, że HD wymaga backendu, a RCS/iMessage/połączenia nie wchodzą w zakres. Przygotować listę brakujących ekranów i polityk bez wymyślania ich jako części HTML.

Odbiór: każda decyzja ma wariant, właściciela i zadanie zamykające; istnieje definicja pełnego wydania i etapu demo. Weryfikacja: przegląd matrycy 10 plansz × funkcje, brak nieoznaczonych założeń.

### CH002 Zamrozić wzorce obrazu i typografii

**P0 · UX i Android i QA · 2–3 osobodni · zależności CH001**

Wykonanie: zweryfikować SHA256 HTML i zachować niezmienny oryginał. Otworzyć go na referencyjnym Androidzie, ustalić rzeczywisty font z zadeklarowanego stosu i render 390×844. Oddzielić ramkę iframe od zawartości. Zapisać API, density, fontScale, renderer, locale, dane i zegar. Zestawić deklarowane CSS z computed geometry, uwzględniając border i box sizing.

Odbiór: 10 wzorców, manifest warunków i uzgodniony wariant fontu. Każdy niejednoznaczny wymiar ma pomiar; brak cichego zamienienia SF na Inter. Weryfikacja: overlay dwóch kolejnych renderów jest stabilny.

### CH003 Sprawdzić wykonalność natywnego szkła

**P0 · Android grafika · 3–5 osobodni · zależności CH002**

Wykonanie: przygotować prototyp S02 i S04 w Compose, z prawdziwym próbkowaniem sceny, saturacją, maską, gradientem i refleksami. Porównać do CSS oraz kontrolnego wariantu WebView. Zmierzyć GPU, klatki, pamięć i scroll. Sprawdzić szkło nad treścią, nie tylko statyczną tapetą. Opisać wymagane API i koszt starszych urządzeń.

Odbiór: decyzja G03 na podstawie obrazów i pomiaru; tekst nad panelem pozostaje ostry, brak rekurencji warstw. Weryfikacja: urządzenie z docelowego minimum i ekran nakładających się paneli. Nie uznawać matowej karty za pełną zgodność.

### CH004 Przygotować projekt i powtarzalne środowisko

**P0 · Android · 1–2 osobodni · zależności CH001 CH003**

Wykonanie: ustalić stabilne kompatybilne wersje JDK/Kotlin/AGP/Compose BOM i aktualne target/compile SDK. Utworzyć katalog wersji, wrapper, moduły i warianty demo/debug/release. Dodać konfigurację podpisu poza repozytorium, przykładowe zmienne bez sekretów i instrukcję uruchomienia. Build demo nie ma prawdziwej wysyłki ani zakupów.

Odbiór: czyste środowisko buduje demo i release testowy bez ręcznych zmian plików. Weryfikacja: odtworzenie według README i kontrola, że fixture nie trafiają do produkcyjnego źródła danych.

### CH005 Zdefiniować modele i granice architektury

**P0 · lider Android · 2–3 osobodni · zależności CH001**

Wykonanie: zdefiniować typowane identyfikatory, Conversation, MessageRef, Draft, SendOperation, Attempt, Schedule i Attachment. Rozdzielić transport SMS/MMS/HD, repozytoria, zegar i dostawcę SIM. Ustalić UiState/UiAction, błędy i kontrakt zapisu. Zapisać ADR o providerze jako historii SMS/MMS i Room jako metadanych/projekcji.

Odbiór: UI może działać z repozytorium testowym, a logika wysyłania bez UI. Weryfikacja: przegląd zależności modułów oraz sekwencji send/cancel/retry; brak cyklicznej zależności feature→feature.

### CH006 Przygotować dane demonstracyjne i raportowanie

**P0 · Android i QA · 1–2 osobodni · zależności CH002 CH005**

Wykonanie: przepisać fixture wszystkich 10 plansz, polskie teksty, liczby, daty, SIM i gradientowe miniatury. Dodać kontrolowany zegar 29 października 2026 i przypadki błędu/draft/HD. Przygotować komendę zapisu ekranów oraz raport overlay/mapa różnic. Zachować literalny błędny licznik 21/160 tylko w fixture, oznaczając wyjątek względem produkcyjnej logiki.

Odbiór: stabilne ID i render bez sieci oraz kontaktów urządzenia. Weryfikacja: dwa uruchomienia generują te same dane i nie wysyłają SMS.

## 3 System wizualny i dziesięć ekranów

### CH007 Wdrożyć fonty tokeny i ikony

**P0 · Android UI · 2–3 osobodni · zależności CH002 CH004**

Wykonanie: przygotować zasoby fontu z polskimi znakami i wymaganymi wagami oraz sprawdzić metryki i licencje. Przenieść kolory, typografię, promienie, odstępy i cienie do tokenów. Przekonwertować źródłowe ścieżki SVG na zasoby Androida, zachowując viewBox, stroke, cap i join. Rozdzielić wartość CSS od wymiaru faktycznego boxu.

Odbiór: galeria komponentów pokazuje komplet tokenów i ikon, poprawnie wyświetlane „ąęłńóśźż” i cyfry. Weryfikacja: porównanie baselines, wag i kształtów; brak automatycznych podmian na ikony Material.

### CH008 Zbudować tła i komponenty szklane

**P0 · Android grafika · 4–6 osobodni · zależności CH003 CH007**

Wykonanie: implementować eliptyczne gradienty per ekran, GlassControl/Card/Sheet/Dock oraz avatar, badge i bubble. Zachować kolejność Z, maski i różne blur 28/30/40. Buforować niezmienne warstwy; panel nie trafia do własnego backdropu. Dodać przełącznik matowych powierzchni do ustawień dostępności.

Odbiór: zgodność geometrii i powierzchni z tokenami oraz brak rozmycia tekstu. Weryfikacja: nakładki na wzorcach, scroll za dockiem, otwarty arkusz i pomiar kosztu GPU na urządzeniu.

### CH009 Wdrożyć szkielet nawigacji i insets

**P0 · Android UI · 2–3 osobodni · zależności CH004 CH005 CH008**

Wykonanie: trzy zakładki z zachowaniem stosu, FAB, rozmowa, wyszukiwarka i arkusze. Rozdzielić Back IME/arkusz/ekran. Zdefiniować bezpieczny routing po ID i obsługę wejść zewnętrznych. Ustawić edge to edge i jeden właściciel insets. Zapewnić dolny padding przewijanych list.

Odbiór: brak duplikowania ekranów po wielokrotnym kliknięciu i brak zasłoniętych akcji. Weryfikacja: gesty, 3 przyciski, wycięcie, IME, obrót i odtworzenie procesu na otwartej zakładce.

### CH010 Odtworzyć S01 Start i ograniczenia

**P1 · Android UI · 1–2 osobodni · zależności CH006 CH008 CH009**

Wykonanie: logo, dekoracyjne koła, treści obu sekcji, informacja o historii, CTA i przyciski pomocnicze. Podpiąć tymczasowy kontrakt konfiguracji, później CH021. Dodać loading, odmowę, brak roli i brak telefonii. Treść przewijalna na małym ekranie i dużym foncie.

Odbiór: obraz S01 zgodny ze wzorcem; naciśnięcia mają określone rezultaty i nie żądają galerii/aparatu. Weryfikacja: fixture, kontrola tekstów oraz odmowa/anulowanie/sukces fałszywego role adaptera.

### CH011 Odtworzyć S02 Skrzynka

**P1 · Android UI · 2–3 osobodni · zależności CH006 CH008 CH009**

Wykonanie: nagłówek, pole wyszukiwania, filtr, przypięte/pozostałe, siedem wariantów wierszy i dock. Wyświetlać prawidłowe line clamp, badge, draft i błąd. Dodać stany pustej listy, braku wyników filtra, odczytu i utraty roli. Menu i long press według CH020.

Odbiór: widoczne detale S02 zgodne, filtr liczy wątki, ostatni wiersz osiągalny. Weryfikacja: fixture plus lista 1000 wątków, długie nazwy i natychmiastowa zmiana odczytu bez resetu scrolla.

### CH012 Odtworzyć S03 Rozmowa

**P1 · Android UI · 3–4 osobodni · zależności CH006 CH008 CH009**

Wykonanie: pływający header, sekcje dni, oba dymki, karta HD, wszystkie statusy, Ponów, odliczanie i kompozytor. Oddzielić listę od warstw pływających. Obsłużyć przewijanie do wiadomości, paginację i wskaźnik nowych wiadomości. Pole wieloliniowe rośnie do ustalonej wysokości, potem przewija tekst.

Odbiór: zgodny S03 oraz statusy odrębne od treści; brak utraty szkicu przy IME i powrocie. Weryfikacja: długa rozmowa, kilkulinijkowa treść, przychodząca wiadomość podczas czytania historii i błędy transportu.

### CH013 Odtworzyć S04 Dodatki i Chatra HD

**P1 · Android UI · 2–3 osobodni · zależności CH006 CH008 CH009**

Wykonanie: sheet 374×640, dim, uchwyt, cztery akcje, miniatury, Zmień i trzy opcje. Wspólny stan wybranych plików z kompozytorem. Dynamiczne CTA i opis kosztu/rozmiaru; stan bez plików i błędu. Przy mniejszym ekranie przewijanie, stała dostępność CTA.

Odbiór: zgodny S04; opcje wyboru wzajemnie się wykluczają i aktualizują wariant zlecenia. Weryfikacja: 4 pliki, brak plików i wiele plików, zamknięcie bez utraty danych i TalkBack po otwarciu arkusza.

### CH014 Odtworzyć S05 Formularz harmonogramu

**P1 · Android UI · 2–3 osobodni · zależności CH006 CH008 CH009**

Wykonanie: arkusz, podgląd szkicu, skróty, data/godzina, wybór SIM i polityki spóźnienia. Zaprojektowane pickery z CH020. Wstrzyknąć zegar; daty i etykieta CTA wynikają ze stanu. Walidacja daty, brak SIM i brak dostępu do alarmów widoczne przed potwierdzeniem.

Odbiór: zgodny stan 31 października 10:00 i poprawna zmiana pól. Weryfikacja: termin przeszły, północ, DST i zmiana odbiorcy; zapis na razie przez kontrakt testowy schedule repository.

### CH015 Odtworzyć S06 Zaplanowane

**P1 · Android UI · 1–2 osobodni · zależności CH006 CH008 CH009**

Wykonanie: karta decyzji, trzy akcje, lista oczekujących, podsumowanie ostatnio wysłanego i dock. Lista sortowana po terminie, błędy wyróżnione tekstem i ikoną. Dodać pusty harmonogram, wiele zaległych pozycji i brak dokładnych alarmów.

Odbiór: zgodny S06, licznik listy dynamiczny. Weryfikacja: usunięcie jednego harmonogramu nie usuwa rozmowy, Wyślij teraz uruchamia jedną akcję, powrót zachowuje pozycję.

### CH016 Odtworzyć S07 Wyszukiwanie

**P1 · Android UI · 2–3 osobodni · zależności CH006 CH008 CH009**

Wykonanie: pole, wyczyść/anuluj, filtry, objaśnienie, dwa szybkie trafienia, lista i mark. Zapytanie, filtry i scroll w stanie możliwym do odtworzenia. Dodać brak wyników, indeksowanie i błąd. Kopiowanie przez systemowy schowek bez logowania wartości.

Odbiór: zgodny S07 i poprawny powrót z wyniku. Weryfikacja: długie zapytanie, wiele wyróżnień, duży font, dwa różne przyciski Kopiuj i czyszczenie pola.

### CH017 Odtworzyć S08 Nowa wiadomość

**P1 · Android UI · 2–3 osobodni · zależności CH006 CH008 CH009**

Wykonanie: chipy, wpisywanie numeru, tryby wysyłki, ostrzeżenie grupowe, sugestie i kompozytor. Puste i niepoprawne dane blokują wysłanie. Chipy muszą zawijać się zgodnie z projektem. Widoczny transport i liczba operacji zmieniają się wraz z odbiorcami.

Odbiór: zgodny S08 dla 3 osób; usuwanie konkretnego chipa zachowuje resztę. Weryfikacja: 1 osoba, 3 osoby i duża liczba osób, IME, długi kontakt i powrót do szkicu po anulowaniu.

### CH018 Odtworzyć S09 Wygląd

**P1 · Android UI · 1–2 osobodni · zależności CH006 CH008 CH009**

Wykonanie: nagłówek, podgląd o wysokości 196 jednostek projektu, cztery próbki, segmenty, suwak i switche 52×32 oraz reset. Przenosić stan do podglądu bez zapisu demonstracyjnych danych. Tryby niezaprojektowane w pełni kierować do kontraktu tokenów CH020/CH040, bez fikcyjnego działania.

Odbiór: zgodny domyślny S09; role radio/switch/slider i bieżący stan dostępne dla czytnika. Weryfikacja: suwak 1/4/7, reset i przejście wstecz bez utraty wyboru.

### CH019 Odtworzyć S10 Ustawienia

**P1 · Android UI · 1–2 osobodni · zależności CH006 CH008 CH009**

Wykonanie: karta Pro, sekcje Wysyłanie/Wygląd i porządek/Prywatność, ikony i dock. Podsumowania z modelu zamiast stałych 3/4. Placeholder ceny wyłącznie w fixture; produkcyjny brak oferty ma własny tekst. Każdy wiersz emituje oddzielną akcję.

Odbiór: zgodny S10 z fixture oraz poprawny routing wszystkich pozycji. Weryfikacja: krótki ekran, duży font, brak produktu sklepu i różne liczniki kosza/blokad.

### CH020 Uzupełnić brakujące projekty i stany

**P0 · UX i produkt · 4–6 osobodni · zależności CH001 CH002 CH008**

Wykonanie: zaprojektować menu skrzynki, szczegóły rozmowy, SIM, pickery, szablony, ustawienia HD, blokady, kosz, pomoc, prywatność, paywall i stronę linku. Opisać empty/loading/error/disabled/permission dla wszystkich ekranów. Dokończyć Mgłę/Grafit/Kontrast, dark i compact z tokenami. Każdy widok ma komplet akcji, fokusu i tekstów.

Odbiór: katalog projektów i mapa do zadań; nowe widoki rozpoznawalne jako uzupełnienie, domyślne 10 plansz nie przeprojektowane. Weryfikacja: przegląd scenariuszy bez martwych końców.

## 4 Telefonia dane i media

### CH021 Wdrożyć rolę SMS i uprawnienia

**P0 · Android platforma · 2–3 osobodni · zależności CH004 CH005 CH010**

Wykonanie: manifest pełnego klienta SMS, RoleManager, kontrola dostępności i wyników. Oddzielić nadanie roli od uprawnień przyznawanych podczas działania aplikacji. Wdrożyć utratę roli podczas działania, blokadę automatycznej wysyłki i bezpieczne powroty z ustawień. Pytać o dostęp do kontaktów, SIM, powiadomień i mediów w kontekście użycia danej funkcji.

Odbiór: aplikacja pojawia się jako kandydat roli i nie odczytuje chronionej historii przed dostępem. Weryfikacja: świeża instalacja, odmowa, ponowienie, cofnięcie roli oraz bezpieczny stan każdego receivera po zmianie.

### CH022 Zintegrować historię providera i projekcję Room

**P0 · Android dane · 3–5 osobodni · zależności CH005 CH021**

Wykonanie: DAO, migracje i klucze z typem SMS/MMS. Stronicowany odczyt historii, rozpoznanie uczestników i ThreadID, ContentObserver oraz odtwarzanie projekcji. Nie duplikować kanonicznej historii. Wspierać odczyt MMS z częściami i leniwe miniatury. Zasilać UI przez Flow.

Odbiór: istniejące wiadomości pozostają nienaruszone; ponowny odczyt nie tworzy duplikatów. Weryfikacja: duża historia, identyczne ID w różnych tabelach, zewnętrzna zmiana, błąd providera i brak roli.

### CH023 Obsłużyć odbiór SMS i status przeczytania

**P0 · Android telefonia · 2–3 osobodni · zależności CH021 CH022**

Wykonanie: odbiornik SMS_DELIVER, składanie części, trwały zapis zgodnie z obowiązkami klienta i aktualizacja projekcji. Odróżnić nową wiadomość od ponownego zdarzenia; zachować adres, czas i SIM. Oznaczanie przeczytania przez właściwy use case, synchronizacja badge oraz powiadomienia.

Odbiór: przychodzący SMS jest w historii systemowej i właściwym wątku, bez podwojenia. Weryfikacja: pojedynczy/multipart, ekran zamknięty, proces nieaktywny, aktywna rozmowa oraz wielokrotne odświeżenie obserwatora.

### CH024 Wdrożyć subskrypcje i wybór SIM

**P0 · Android telefonia · 2–3 osobodni · zależności CH020 CH021 CH005**

Wykonanie: lista aktywnych subskrypcji, obserwacja zmian, nazwy i domyślna karta. Ustalić pierwszeństwo wybór szkicu→preferencja rozmowy→domyślna, z walidacją dostępności. Zapisywać ID i migawkę opisu, nie sam slot. Wybór jawny w rozmowie, edytorze i harmonogramie.

Odbiór: brak cichego przełączenia po wyjęciu karty; jedna karta nie uruchamia zbędnego pytania. Weryfikacja: dwie karty SIM i eSIM, brak dostępu do listy, SIM usunięta po zapisaniu szkicu i zmiana systemowej domyślnej.

### CH025 Zaimplementować licznik i kodowanie SMS

**P0 · Android domena · 1–2 osobodni · zależności CH005 CH024**

Wykonanie: adapter platformowego dzielenia tekstu, model encoding/units/parts i prezentacja licznika. Ustalić opis dla Unicode i multipart. Dodać opt in transliteracji z podglądem i możliwością przywrócenia. Zaktualizować licznik podczas edycji i po zmianie transportu.

Odbiór: treść nigdy nie jest zmieniana samoczynnie, wynik nie korzysta z prostego length/160. Weryfikacja: granice części, GSM extension, polskie znaki, emoji i źródłowy tekst 20 znaków z niepoprawnym fixture 21/160.

### CH026 Wdrożyć wysyłanie SMS

**P0 · Android telefonia · 3–4 osobodni · zależności CH022 CH024 CH025**

Wykonanie: utworzyć trwałe operacje, próby, per recipient i per part. Przekazać do SmsManager właściwej SIM, z unikalnymi callbackami. Zapisać i uzgadniać rekordy providera zgodnie z obowiązkami domyślnego klienta. Wysyłka niezależna od aktualnego ekranu.

Odbiór: drugi klik nie tworzy tej samej operacji, sent nie oznacza delivered. Weryfikacja: rzeczywisty SMS i multipart z obu kart SIM, brak zasięgu, tryb samolotowy i brak uprawnienia w chwili wykonania.

### CH027 Uzgodnić statusy ponawianie i niepewne próby

**P0 · Android domena · 2–3 osobodni · zależności CH026**

Wykonanie: maszyna stanów, agregacja części, callbacki poza kolejnością i deduplikacja. Retry tylko jawnie dla pewnej porażki, nigdy automatycznie po niepewnym crashu. Dodać DISPATCH_UNCERTAIN i decyzję użytkownika. Rekoncyliować z providerem, jeśli istnieją porównywalne dane, bez zgadywania sukcesu.

Odbiór: DELIVERED nie cofa się do SENT, brak duplikatów przy ponownym callbacku. Weryfikacja: awaria tuż przed/po wywołaniu API, jedna część nieudana, brak raportu doręczenia i retry po zmianie SIM.

### CH028 Utrwalić szkice i okno anulowania

**P0 · Android dane i domena · 3–4 osobodni · zależności CH012 CH017 CH022 CH026 CH027**

Wykonanie: zapis szkicu tekst/odbiorcy/pliki/SIM, trwały snapshot operacji PENDING_UNDO. Odliczanie od deadline, atomowy cancel przeciw dispatch, przywrócenie szkicu po cancel. Zdefiniować bezpieczne zachowanie po zabiciu procesu i restarcie. Nigdy nie wywoływać transportu przed końcem okna.

Odbiór: powrót i obrót nie gubią szkicu; skuteczny cancel nie wysyła. Weryfikacja: 2,9 s, 3,0 s i 3,1 s, wielokrotny klik, brak aktywnego UI i wyścig cancel/send.

### CH029 Podłączyć kontakty i wysyłkę do wielu osób

**P1 · Android domena · 2–3 osobodni · zależności CH017 CH022 CH025 CH026 CH028**

Wykonanie: wyszukiwanie kontaktów, wybór kilku numerów, normalizacja, deduplikacja, sugestie i ręczny numer. Osobne SMS jako domyślny tryb, nadrzędna operacja i wyniki odbiorców. Grupowy MMS wymaga jawnego trybu. Zablokować nieprawidłowe adresy i alfanumerycznych nadawców wyjściowych.

Odbiór: tryb osobnych wiadomości nie ujawnia numerów pozostałych odbiorców; retry omija już wysłanych. Weryfikacja: 3 osoby i częściowy sukces, ten sam numer zapisany z prefiksem +48 i w formacie lokalnym, odmowa kontaktów i dodanie załącznika wymagające wyboru transportu.

### CH030 Wdrożyć powiadomienia i wejścia systemowe

**P1 · Android platforma · 2–3 osobodni · zależności CH009 CH020 CH023 CH026 CH028**

Wykonanie: kanały wiadomości/błędy/harmonogram/HD, grupowanie, prywatność lockscreen i deep link. SENDTO/RESPOND_VIA_MESSAGE oraz bezpieczne udostępnianie. Szybka odpowiedź korzysta ze wspólnej kolejki i jawnie wybranej SIM. Walidować zewnętrzne argumenty i URI; nie wysyłać automatycznie po otwarciu intentu.

Odbiór: właściwy wątek, aktualne badge, brak tekstu na blokadzie przy ukrytych podglądach. Weryfikacja: brak zgody na powiadomienia, urządzenie zablokowane, cold start i wielokrotne powiadomienie.

### CH031 Przygotować cykl życia załączników

**P0 · Android media · 2–3 osobodni · zależności CH013 CH022 CH028**

Wykonanie: Photo Picker i aparat, walidacja MIME/rozmiaru, trwały URI grant lub kopia prywatna, miniatury, porządek i usuwanie. Liczyć sumę rozmiarów i SHA256 poza głównym wątkiem. Zapewnić FileProvider i czyszczenie cache bez naruszenia aktywnej operacji.

Odbiór: wybrane pliki nadal dostępne po restarcie; brak szerokiej zgody na galerię bez potrzeby. Weryfikacja: utracony dostęp do URI, brak miejsca, przerwane kopiowanie, duże wideo i anulowanie po wyborze.

### CH032 Zbudować transport MMS i grupy

**P0 · Android telefonia · 6–10 osobodni · zależności CH022 CH024 CH027 CH029 CH031**

Wykonanie: ocenić utrzymywany i zgodny licencyjnie adapter PDU, obsłużyć konstrukcję nagłówków/części, send/download, WAP_PUSH i zapis providera. Limity i kompresja dla operatora/SIM; tekst, zdjęcie, wspierane wideo i grupy. Rozróżnić APN, dane, timeout, przekroczony limit i częściowy błąd.

Odbiór: rzeczywisty MMS w obu kierunkach oraz grupa u uzgodnionych operatorów. Weryfikacja: fizyczne urządzenia z dwiema kartami SIM, roaming, brak danych i restart. Dokumentowany brak wsparcia nie może wyglądać jak sukces.

### CH033 Zbudować oryginał i kopię bez lokalizacji

**P0 · Android media · 3–5 osobodni · zależności CH031**

Wykonanie: oryginał jako niezmienne bajty; osobne pochodne dla MMS i usunięcia lokalizacji. Zdefiniować formaty zdjęć/wideo i pełny zestaw wspieranych metadanych lokalizacyjnych. Dla formatów nieobsługiwanych jawny błąd albo bezpieczna konwersja z oznaczeniem. Nie nadpisywać pliku źródłowego.

Odbiór: oryginał ma identyczny hash; kopia nie zawiera lokalizacji w obsługiwanych polach i nie jest opisana jako oryginał. Weryfikacja: niezależny odczyt metadanych, orientacja i jakość po konwersji, nieobsługiwany format.

### CH034 Wdrożyć szablony wiadomości

**P1 · Android · 1–2 osobodni · zależności CH020 CH028**

Wykonanie: lokalny katalog szablonów, dodanie/edycja/usunięcie, wybór w dodatkach. Wstawienie w kursor lub jasna reguła dopisania; zastąpienie niepustej treści wyłącznie po świadomej akcji. Zachować odbiorców, SIM i pliki. Brak synchronizacji z chmurą w tym zakresie.

Odbiór: akcja Szablony jest kompletna, stan pusty prowadzi do utworzenia. Weryfikacja: polskie znaki, długa treść, reset procesu i brak podwojenia po dwukrotnym kliknięciu.

## 5 Harmonogram wyszukiwanie i ustawienia

### CH035 Zaimplementować trwały harmonogram

**P0 · Android domena · 3–5 osobodni · zależności CH014 CH022 CH024 CH026 CH027 CH028**

Wykonanie: rekord instant/localDateTime/zone/polityka/SIM/version, scheduler i unikalny alarm. Sprawdzić specjalny dostęp do dokładnych alarmów. Sprawdzić, czy data jest przyszła i czy warunki przed zapisem są poprawne; rekord zapisany niezależnie od awarii rejestracji alarmu, z rekoncyliacją i widocznym błędem. WorkManager nie pełni roli dokładnego zegara.

Odbiór: po zapisaniu i restarcie procesu termin pozostaje, alarm wywołuje najwyżej jednego wykonawcę. Weryfikacja: brak dostępu, dwie pozycje o tej samej godzinie, zmiana terminu i nieistniejąca godzina podczas zmiany czasu DST.

### CH036 Obsłużyć zaległości restart i zmianę warunków

**P0 · Android platforma · 3–4 osobodni · zależności CH035 CH030**

Wykonanie: rekoncyliacja po boot/aktualizacji/czasie/SIM/roli/uprawnieniach. Polityki pytaj i do 1 h, stan missed, powiadomienie i blokada automatycznego retry niepewnej próby. Uwzględnić brak odblokowania po restarcie, force stop i ograniczenia producentów. Odtwarzać tylko aktualną wersję alarmu.

Odbiór: wyłączony telefon nie jest opisywany jako źródło wysłanego SMS; po upływie okna wiadomość czeka na decyzję. Weryfikacja: spóźnienie 59, 60 i 61 min, Doze, utrata SIM, cofnięcie exact alarm i odzyskanie roli.

### CH037 Podłączyć edycję i decyzje harmonogramu

**P1 · Android · 2–3 osobodni · zależności CH015 CH035 CH036 CH027**

Wykonanie: listy live, edit, cancel, delete, Wyślij teraz i Zmień termin. Wersjonować rekord; stary alarm i spóźniony callback nie zmieniają nowej wersji. Edycja nie narusza wysłanego snapshotu. Potwierdzić usunięcie zlecenia niezależne od rozmowy.

Odbiór: licznik „Czeka” jest poprawny, akcje nie powodują dwóch wysyłek. Weryfikacja: edycja tuż przy terminie, kilka otwartych widoków tego samego zadania i restart między zapisem a anulowaniem alarmu.

### CH038 Wdrożyć lokalny indeks i wyszukiwanie

**P0 · Android dane · 3–4 osobodni · zależności CH016 CH022 CH023**

Wykonanie: indeks odtwarzalny dla wspieranych treści, normalizacja polskich liter w tym „ł”, casefold, tokeny prefiksowe i mapa offsetów. Przyrostowe aktualizacje, debounce 200 ms, anulowanie starego zapytania i paginacja. Indeksowanie z postępem poza głównym wątkiem; bez sieci.

Odbiór: paczk znajduje paczkę/PACZKA, łódź znajduje lodz po normalizacji; highlight trafia w oryginał. Weryfikacja: 50 tysięcy rekordów, usunięcie z providera, odtworzenie indeksu i zapytanie z emoji/interpunkcją.

### CH039 Dodać filtry i szybkie trafienia

**P1 · Android domena · 2–3 osobodni · zależności CH020 CH038**

Wykonanie: typ treści, nadawca i daty łączone operatorem AND; lokalne rozpoznawanie kodów i przesyłek. Zachować wiadomość źródłową, nadawcę i pewność wzorca. Kopiowanie precyzyjnej wartości; bez zgadywania kodu z dowolnej liczby. Powrót z rozmowy przywraca komplet filtrów.

Odbiór: cztery wyniki fixture i poprawne wyróżnienia, filtry rzeczywiście zawężają zbiór. Weryfikacja: numer telefonu podobny do kodu, fałszywy nadawca, brak trafień i przedział dat obejmujący zmianę czasu DST.

### CH040 Utrwalić wygląd i dostępne warianty

**P1 · Android UI · 3–4 osobodni · zależności CH018 CH020 CH007 CH008**

Wykonanie: DataStore dla wszystkich opcji, tokeny czterech kompozycji i dark/auto, compact i skali 1–7. Zastosować zmiany globalnie i w podglądzie; reset do źródłowego stanu. Ograniczenie przezroczystości i kontrast mają osobne tokeny. Nie blokować opcji dostępności płatnością.

Odbiór: wybory zachowane po restarcie, Auto reaguje na system, brak nieczytelnego tekstu. Weryfikacja: kombinacje trybów, fontScale 2.0 i porównanie domyślnego Szkła do wzorca po resecie.

### CH041 Podłączyć ustawienia wysyłania

**P1 · Android · 2–3 osobodni · zależności CH019 CH020 CH024 CH025 CH028**

Wykonanie: ekran SIM i preferencje, opóźnienie 0/3/5/10 s według zatwierdzenia, licznik oraz domyślne warianty HD i pytanie o link. Podsumowania ustawień wynikają z DataStore. Zmiana ustawienia nie modyfikuje zlecenia już przekazanego ani istniejącego harmonogramu bez akcji użytkownika.

Odbiór: ustawienia działają w nowych szkicach, nie zmieniają treści samoczynnie. Weryfikacja: restart, brak SIM, wyłączenie licznika i zmiana delay podczas aktywnego odliczania.

### CH042 Wdrożyć nieznanych i blokady

**P1 · Android dane · 2–3 osobodni · zależności CH020 CH022 CH023 CH030**

Wykonanie: oznaczać nieznanych nadawców bez klasyfikowania ich jako spam; lokalne reguły blokowania, lista i przywracanie. Zatrzymać powiadomienia zgodnie z regułą, zachować historię. Jeżeli G05 wybiera blokadę systemową, dodać osobny komunikat o skutkach dla połączeń i zweryfikować API.

Odbiór: reguła działa dla znormalizowanego numeru, cofnięcie blokady przywraca widoczność; brak cichego kasowania SMS. Weryfikacja: nowy SMS od zablokowanego, odmienne formaty numeru, kontakt dodany później i brak roli.

### CH043 Wdrożyć kosz i odzyskiwanie

**P0 · Android dane · 3–5 osobodni · zależności CH020 CH022 CH023 CH038**

Wykonanie: TrashEntry ze zbiorem provider refs/HD refs, local tombstone, TTL wynoszący 30 dni, restore i potwierdzony purge. Nowa wiadomość w widocznym wątku nie należy do starego zakresu kosza. Sprzątanie tylko po upływie TTL i przy dostępie. W UI wyjaśnić lokalne ukrycie względem innych klientów.

Odbiór: restore bez duplikacji; purge nie usuwa nowych SMS z tego samego wątku. Weryfikacja: upływ 29, 30 i 31 dni, utrata roli, awaria w połowie usuwania, MMS parts i odbudowa indeksu.

### CH044 Zbudować prywatność i pomoc

**P1 · Android i produkt · 1–2 osobodni · zależności CH020 CH021 CH046**

Wykonanie: lokalna strona prywatności opisująca provider/indeks/HD/logi/backup i usuwanie danych. Pomoc z informacją o SMS/MMS/RCS, SIM i harmonogramie; docelowy kontakt dostarcza właściciel. Z obu miejsc dojście z S01/S10. Link zewnętrzny bez automatycznego udostępniania danych.

Odbiór: treść zgodna z faktyczną implementacją, brak wymyślonego adresu i nieprawdziwego E2EE. Weryfikacja: offline, duży font, działające linki i porównanie z zachowaniem HD oraz kosza.

### CH045 Wdrożyć jednorazowy zakup Pro

**P1 · Android i BE · 3–5 osobodni · zależności CH001 CH019 CH020 CH040 CH046**

Wykonanie: zatwierdzić katalog Pro i productId, pobierać lokalną cenę sklepu, paywall, purchase flow, acknowledgement/weryfikacja oraz restore. Obsłużyć oczekującą płatność, anulowanie, zwrot i brak sklepu. Uprawnienie do podstaw nie zależy od Pro; darmowe ustawienia dostępności pozostają dostępne.

Odbiór: w produkcyjnym UI nie ma placeholdera [CENA], zakup jednorazowy odtwarzany zgodnie z zatwierdzonym modelem. Weryfikacja: środowisko testowe sklepu, ponowna instalacja, błędny token, oczekiwanie i cofnięta transakcja.

## 6 Backend i Chatra HD

### CH046 Zaprojektować usługę i zasady danych HD

**P0 · BE i produkt i bezpieczeństwo · 3–4 osobodni · zależności CH001 CH005 CH020**

Wykonanie: wybrać stos/backend, region, storage, bazę, push, hosting linku i koszty. Zamknąć G06/G07: model tożsamości, discovery, limity, retencję, usuwanie i szyfrowanie. Model zagrożeń: enumeracja numerów, przejęty link, cudzy plik, podszycie się i nadużycia. Zapisać ADR i granice obietnic prywatności.

Odbiór: decyzje przed kodem, architektura obejmuje obsługę awarii i odzyskanie numeru. Weryfikacja: przegląd bezpieczeństwa i przepływu danych; brak założenia, że odczyt danych z SIM potwierdza numer.

### CH047 Zdefiniować API i tożsamość instalacji

**P0 · BE i Android · 4–6 osobodni · zależności CH046**

Wykonanie: OpenAPI, modele i kody błędów, podpis/klucz instalacji, sesje i rotacja. Weryfikacja posiadania numeru z limitami prób, opt in odkrywalności i lookup tylko wybranego odbiorcy. Klient Keystore, wylogowanie/odpięcie i zmiana urządzenia. Do kontraktu dodać idempotency i paginację.

Odbiór: dwa urządzenia uzyskują zweryfikowane powiązania i odbiorca nie jest publicznie enumerowalny. Weryfikacja: niewłaściwy lub zużyty kod, replay, skradziona sesja, rotacja i odmowa HD bez utraty SMS.

### CH048 Zbudować magazyn i wznawialny upload

**P0 · BE · 4–6 osobodni · zależności CH046 CH047**

Wykonanie: manifest transferu, plik/chunki, offset, checksum, finalize i stany. Limit rozmiaru/ilości/quota przed uploadem, adresy URL o krótkim okresie ważności i autoryzacja właściciela. Przerwane sesje sprzątać po upływie TTL, obiekty gotowe tylko po weryfikacji. Atomowe powiązanie finalized z kopertą.

Odbiór: wznowienie nie powiela bajtów, błędny hash nie zostaje opublikowany. Weryfikacja: przerwanie w połowie, brak miejsca/quota, równoległe finalize, próba dostępu do innego transferu i opuszczony upload.

### CH049 Zbudować klienta transferów HD

**P1 · Android sieć · 3–5 osobodni · zależności CH031 CH033 CH047 CH048**

Wykonanie: trwała kolejka, kontrola sieci, postęp, resume/backoff i anulowanie. Dobór mechanizmu długiego transferu zgodnego z docelowym Androidem. Jawne zużycie danych i możliwość wstrzymania. Błąd lub restart nie usuwa prywatnej kopii aktywnego pliku.

Odbiór: upload działa po zmianie ekranu i odtwarza stan po ponownym uruchomieniu procesu; nie pokazuje sukcesu przed finalize. Weryfikacja: zmiana sieci Wi-Fi na komórkową, brak sieci, błędy 401/413/429/5xx, anulowanie i 2 równoległe transfery.

### CH050 Zbudować routing kopert i statusy pobrania

**P0 · BE i Android · 4–6 osobodni · zależności CH047 CH048 CH049**

Wykonanie: outbox serwera, koperty wyłącznie dla uprawnionych instalacji, cursor sync i push jako sygnał. Lokalna deduplikacja, historia HD i receipt pobrania po integralnym zapisaniu. Odebranie push nie jest pobraniem; pobranie nie jest odczytem. Uzgodnić zachowanie przy zmianie numeru/klucza.

Odbiór: odbiorca offline dostaje zaległe koperty raz po synchronizacji; nadawca widzi prawdziwy etap. Weryfikacja: duplikat push, utrata cursora, dwie instalacje, powtórny receipt i próba cudzej skrzynki.

### CH051 Zbudować bezpieczny link i stronę odbioru

**P1 · BE i web i UX · 3–5 osobodni · zależności CH020 CH046 CH048 CH050**

Wykonanie: zgoda na link dla nieposiadającego Chatry, sekretny token, expiry/revoke i strona pobrania na Androidzie/iOS/przeglądarce. Podgląd liczby i rozmiaru plików dopiero przy ważnym dostępie. Brak indeksowania/wycieku przez nagłówek Referer/niepotrzebnych trackerów; przycisk pobierz i stan wygasły.

Odbiór: uprawniony odbiorca pobiera, odwołany link nie działa, sekret nie trafia do logów. Weryfikacja: incognito, iOS, zły/wygasły/odwołany token i wielokrotny klik. SMS z linkiem wymaga osobnego zatwierdzenia.

### CH052 Wdrożyć retencję usuwanie i ochronę HD

**P0 · BE i bezpieczeństwo · 3–5 osobodni · zależności CH046 CH047 CH048 CH050 CH051**

Wykonanie: egzekwować TTL, revoke, usunięcie obiektu lub powiązania, politykę backupu i audyt bez danych. Limity lookup/upload/download i alerty nadużyć. Jeśli G07 wybiera E2EE, uzupełnić uzgodniony protokół i ponownie oszacować zadanie przed implementacją; TLS nie zastępuje E2EE. Sekrety w zarządzanym magazynie, rotacja i separacja środowisk.

Odbiór: polityka serwera zgodna z UI, brak dostępu po cofnięciu i brak wycieków poprzez publiczny magazyn plików. Weryfikacja: negatywne testy obiektowe, granice TTL, odtworzenie backupu i skan logów.

### CH053 Połączyć HD z rozmową i dodatkami

**P1 · Android · 3–4 osobodni · zależności CH012 CH013 CH033 CH041 CH049 CH050 CH051 CH052**

Wykonanie: discovery, oryginał, kopia bez GPS i MMS, dynamiczne CTA, postęp, otrzymanie koperty i karta HD. Rozpoznać nieznany stan dostępności HD bez udawania braku aplikacji. Wyświetlić źródło transportu; odbiór HD nie zamienia się w SMS. Fallback/link tylko po zgodzie.

Odbiór: scenariusz 4 zdjęcia / 38,2 MB ze wzorca działa z rzeczywistymi plikami i potwierdzonym hashem. Weryfikacja: odbiorca Chatra, odbiorca bez Chatry, niedostępny backend, restart i częściowe pobranie.

## 7 Testy odbiór i dystrybucja

### CH054 Zweryfikować domenę i bazę

**P0 · Android i QA · 3–4 osobodni · zależności CH027 CH028 CH032 CH035 CH038 CH043**

Wykonanie: testy istotnych reguł kodowania, deduplikacji, wersji zleceń, multi recipient, indeksu i kosza. Testy DAO i migracji na danych z różnych wersji; kontrolowany zegar i awarie. Nie ograniczać się do testu, który powtarza implementację.

Odbiór: scenariusze duplikacji i utraty danych mają niezależne asercje i przechodzą w CI. Weryfikacja: wstrzykiwanie awarii, callbacki poza kolejnością, częściowe sprzątanie i zachowanie po odtworzeniu.

### CH055 Odebrać zgodność wizualną wszystkich ekranów

**P0 · QA i UX i Android · 3–5 osobodni · zależności CH010 CH011 CH012 CH013 CH014 CH015 CH016 CH017 CH018 CH019 CH040**

Wykonanie: obrazy S01–S10 w warunkach CH002, overlay 50 procent, heatmapa i pomiary obszarów ROI. Sprawdzić typografię, linebreak, stroke ikon, box sizing, gradienty, szkło i dock. Osobny przegląd renderowania GPU na urządzeniu. Zarejestrować i poprawić odchyłki; golden nie aktualizuje się automatycznie.

Odbiór: każdy ekran spełnia kontrakt 1 do 1, wyjątki tylko jawnie uzgodnione. Weryfikacja: podpisany raport z 10 par obrazów i brakiem nieprzejrzanych masek; osobny test 360/430 dp i dużego fontu.

### CH056 Przetestować rzeczywistą telefonię

**P0 · QA i Android · 4–6 osobodni · zależności CH021 CH023 CH024 CH026 CH027 CH029 CH030 CH032**

Wykonanie: macierz fizycznych telefonów/SIM/operatorów z CH001. SMS/MMS w obu kierunkach, długie, Unicode i grupowe, z dwiema kartami SIM, roaming, dane/APN i tryb samolotowy. Potwierdzić kody błędów oraz zachowanie zmiany roli domyślnej. Numery testowe za zgodą ich właścicieli.

Odbiór: raport dostarczenia i odbioru oraz lista wspieranych konfiguracji; brak uznania emulatora za dowód MMS. Weryfikacja: zapis wersji OS/operatora i wyników dla każdej kombinacji, regresja po poprawkach.

### CH057 Przetestować alarmy i awarie wysyłki

**P0 · QA i Android · 3–4 osobodni · zależności CH028 CH035 CH036 CH037**

Wykonanie: zabicie procesu, restart, force stop, Doze, brak odblokowania, cofnięcie roli/alarmów, utrata SIM, zmiana daty/strefy/DST. Kontrolowany wyścig cancel/dispatch i edit/alarm. Granice spóźnienia 59/60/61 min i odzyskanie łączności.

Odbiór: brak niejawnego ponowienia przy niepewności i brak wysyłki po skutecznym anulowaniu; zaległości trafiają do właściwej polityki. Weryfikacja: rejestrowane zdarzenia z correlationId bez treści oraz porównanie liczby rzeczywistych wiadomości.

### CH058 Przetestować HD end to end i bezpieczeństwo

**P0 · QA i bezpieczeństwo · 3–5 osobodni · zależności CH047 CH048 CH050 CH051 CH052 CH053**

Wykonanie: dwa Androidy i odbiorca linku, duże pliki, hash oryginału, sanityzacja GPS, offline/resume/expiry/revoke. Negatywna autoryzacja, enumeracja, replay, tokeny i logi. Ocenić stronę linku i push. Zweryfikować usuwanie zgodnie z retencją oraz spójność komunikatów.

Odbiór: brak wycieków i zgodność całego przepływu, receipt dopiero po rzeczywistym pobraniu. Weryfikacja: niezależny odczyt metadanych i SHA256, niedozwolone dostępy odrzucone, awaria API nie uruchamia MMS.

### CH059 Zmierzyć i poprawić wydajność

**P1 · Android i BE i QA · 2–4 osobodni · zależności CH008 CH022 CH032 CH038 CH053**

Wykonanie: uruchomienie aplikacji, 50 tys. wiadomości, scroll, otwarcie arkusza, pamięć miniatur i transfer HD. Zmierzyć cele P95 na ustalonym urządzeniu, profilować koszt rejestrowania warstw i rozmycia i indeksowania. Backend monitorować bez prywatnych danych. Poprawić potwierdzone wąskie gardła.

Odbiór: raport liczbowy i spełnione lub jawnie ponownie uzgodnione cele, bez rezygnacji ze szkła 1 do 1. Weryfikacja: identyczny scenariusz przed/po, brak zwiększenia wycieków pamięci i degradacji po dłuższym użyciu.

### CH060 Odebrać dostępność i układy adaptacyjne

**P1 · QA i UX i Android · 2–3 osobodni · zależności CH020 CH040 CH055**

Wykonanie: TalkBack, fokus dialogów, poprawne role i stan, nazwy przycisków i cele 48 dp bez nakładania. Systemowa skala tekstu 1.3/2.0, suwak aplikacji, redukcja przezroczystości i kontrast. Test 360/390/430 dp, IME, cutout i obie metody nawigacji systemu.

Odbiór: każda akcja osiągalna, brak informacji tylko kolorem i brak uciętej treści. Weryfikacja: ręczny przepływ wysłania/zaplanowania/wyszukania wyłącznie z czytnikiem; regresja wizualna domyślnych ustawień.

### CH061 Zweryfikować migracje backup i odzyskanie

**P0 · Android i QA · 2–3 osobodni · zależności CH022 CH028 CH035 CH043 CH047 CH050**

Wykonanie: polityka backupu prywatnej bazy/indeksu/kluczy, test aktualizacji i przerwane migracje. Nie odtwarzać nieświadomie starych harmonogramów ani sekretów Keystore z nową instalacją. Rekoncyliacja providera i ponowna weryfikacja HD po utracie powiązania. Opisać ograniczenia reinstalacji.

Odbiór: historia providera nienaruszona, brak drugiej wysyłki po przywróceniu. Weryfikacja: aktualizacja instalacji zawierającej szkice, harmonogram i kosz, czysta instalacja i restore testowy.

### CH062 Przygotować CI konfigurację i instrukcje utrzymania

**P1 · Android i BE i DevOps · 2–3 osobodni · zależności CH004 CH047 CH054**

Wykonanie: lint/build/test, obrazy regresji, migracje bazy BE, staging, secrets i podpisywanie. Instrukcja budowy lokalnej, wdrożenia, wycofania zgodnego z migracjami, rotacji kluczy i alarmów produkcyjnych. Oddzielić środowiska i testowych odbiorców; fixture i sekrety nie trafiają do wydania produkcyjnego.

Odbiór: nowa osoba z zespołu odtwarza build i środowisko testowe z instrukcji. Weryfikacja: pipeline z czystego commitu, kontrola paczek i próbne wycofanie bez utraty danych.

### CH063 Przeprowadzić pełny odbiór produktu

**P0 · produkt i QA · 2–3 osobodni · zależności CH034 CH039 CH041 CH042 CH043 CH044 CH045 CH055 CH056 CH057 CH058 CH059 CH060 CH061 CH062**

Wykonanie: przejść wszystkie widoczne akcje dziesięciu plansz i uzupełnione podwidoki. Sprawdzić SMS i funkcje lokalne bez konta oraz dostępu do internetu, MMS przy wymaganej transmisji operatora, a HD i zakup Pro przy dostępnej sieci. Osobno zweryfikować role, harmonogram, wyszukiwanie i przywracanie zakupu. Zestawić wymaganie→zadanie→dowód, zamknąć błędy krytyczne i otwarte decyzje.

Odbiór: pełna matryca bez martwych przycisków i nieprawdziwych statusów; build gotowy do wydania. Weryfikacja: raport odbioru, jawna lista ograniczeń operatorów i brak odstępstw 1 do 1 bez decyzji.

### CH064 Przygotować dystrybucję i przekazanie

**P0 · właściciel produktu i Android i DevOps · 2–3 osobodni · zależności CH063**

Wykonanie: ponownie sprawdzić wymagania Google Play/target SDK/uprawnień SMS/Billing i dane prywatności. Przygotować podpisany AAB/APK w uzgodnionym kanale, opis sklepu, obrazy, deklaracje i politykę. Przekazać repozytoria, ADR, OpenAPI, konfiguracje bez sekretów i raporty. Przygotowanie do wydania nie oznacza automatycznej publikacji.

Odbiór: paczka instaluje się z kanału testowego, podstawowe przepływy działają w buildzie release; właściciel ma instrukcję wypuszczenia i wsparcia. Weryfikacja: smoke test wydania oraz zgodność treści sklepu z realnym zakresem bez RCS/iMessage.

## 8 Planowanie nakładu i ryzyk

Nakłady nie uwzględniają czasu oczekiwania na decyzje, uzyskanie dostępu do konsoli sklepu, weryfikację dostawców, dostarczenie fizycznych kart SIM i formalny przegląd publikacji. Implementacja E2EE, wsparcie Androida poniżej 31 lub nowe reguły Pro wymagają ponownego oszacowania po zamknięciu decyzji. Nie wolno dopisywać ich do istniejącego nakładu bez oceny.

Najbardziej wrażliwe zadania to CH003 szkło, CH032 MMS, CH036 harmonogram, CH043 kosz oraz CH046–CH052 backend i bezpieczeństwo HD. Te zadania powinny dostarczać wynik techniczny wcześnie. Nie planować całego zespołu wyłącznie według liczby ekranów; domyślny klient SMS zawiera istotną pracę poza UI.

Łączny orientacyjny nakład wynosi **155–240 osobodni** dla całego zespołu. Nie jest to 155–240 dni kalendarzowych.

| Etap | Liczba zadań | Osobodni |
| --- | --- | --- |
| M0 Ustalenie podstaw | 6 | 10–17 |
| M1 Wierny interfejs | 14 | 29–45 |
| M2 Lokalny klient | 14 | 34–53 |
| M3 Funkcje lokalne | 11 | 27–41 |
| M4 Pełne HD | 8 | 27–41 |
| M5 Odbiór i wydanie | 11 | 28–43 |
| Razem | 64 | 155–240 |

Zestawienie `Chatra_Plan_zadan.json` w pakiecie jest eksportem tego backlogu z tymi samymi ID, zależnościami i zakresami osobodni. Zależności są acykliczne. Szacowanie kalendarza powinno uwzględnić dostępność osób odpowiedzialnych za Androida, backend, UX i QA oraz to, że równoległe zadania mają różne wymagane role.
