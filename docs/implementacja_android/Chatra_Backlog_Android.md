# Chatra Szczegółowy backlog implementacji Android

Wersja 1.1 · 29 października 2026 · powiązanie z dokumentem Chatra Dokumentacja Android

Backlog obejmuje pełną aplikację, nie tylko statyczne ekrany. Każde zadanie ma zakres, wykonawcę, zależności, nakład oraz kryteria i weryfikację. Nakład oznacza osobodni pracy przy znanych kontraktach i dostępnym środowisku, nie termin kalendarzowy ani ofertę cenową. Zakresy są orientacyjne; po spike szkła i MMS należy je ponownie oszacować.

P0 oznacza fundament lub ryzyko blokujące dalsze prace; P1 oznacza pozostały wymagany zakres pełnego wydania. P1 nie oznacza funkcji, którą można pominąć bez zmiany zakresu. P2 oznacza opcję lub badanie wymagające odrębnej decyzji, bez blokowania pierwszego wydania. Skrót BE oznacza backend, QA oznacza testy, a UX oznacza projektowanie interfejsu. Zadania z kilkoma rolami mają wspólny nakład, nie podwójne naliczenie.

## 1 Kamienie milowe i zasady zamykania

| Etap | Wynik | Warunek przejścia |
| --- | --- | --- |
| M0 Ustalenie podstaw | CH001–CH006 | Zamknięte ryzyka fontu, szkła, minimalnego Androida i granic produktu |
| M1 Wierny interfejs | CH007–CH020 | 10 ekranów z danymi demonstracyjnymi, raport obrazów i komplet projektów brakujących |
| M2 Lokalny klient | CH021–CH034 | Rzeczywiste SMS/MMS, SIM, szkice, powiadomienia i media |
| M3 Funkcje lokalne | CH035–CH045 | Harmonogram, wyszukiwanie, wygląd, porządek i Pro |
| M3X Rozszerzenia UX | CH065–CH070 | Karta kontaktu, galeria, kolaże, personalizacja, gesty i nieprzeczytane zintegrowane przed odbiorem |
| M4 Pełne HD | CH046–CH053 | Dwa urządzenia i odbiorca linku obsługują cały przepływ |
| M5 Odbiór i wydanie | CH054–CH064 oraz bramki CH065–CH070 | QA obejmuje również nowe funkcje UX |
| M6 Po wydaniu / badania | CH071–CH072 | Opcjonalny eksport/skrót i spike iOS/RCS nie blokują M5 |

Po CH005 można prowadzić niezależne prace UI i telefonii, a po CH046/CH047 backend i klient HD. Zadania QA przygotowywać wcześniej na gotowych kontraktach; podane zależności są warunkami finalnego zamknięcia. Publikacja wymaga zakończenia pełnego zakresu. Samo „ekran się otwiera” nie jest kryterium wykonania.

Definition of Done każdego zadania: implementacja w repozytorium, przegląd, odpowiednie testy lub mierzalny dowód działania, obsłużone błędy, aktualna dokumentacja oraz brak danych wrażliwych w logach. Dla UI dodatkowo odnośnik do obrazu wzorca i raport porównania. Dla operacji systemowych test na wymaganym poziomie platformy; fake transport nie zastępuje urządzenia.

## 2 Ustalenia i fundament

### CH001 Ustalić kontrakt produktu i decyzje otwarte

**P0 · produkt i lider Android · 1–2 osobodni · zależności brak**

Wykonanie: utworzyć rejestr G01–G10 z dokumentacji; wskazać właścicieli. Potwierdzić minSdk, dystrybucję, listę operatorów, obsługę wideo/grup MMS, kosz i zakres Pro. Zapisać, że HD wymaga backendu, a RCS/iMessage/połączenia nie wchodzą w zakres. Przygotować listę brakujących ekranów i polityk bez wymyślania ich jako części HTML.

Odbiór: każda decyzja ma wariant, właściciela i zadanie zamykające; istnieje definicja pełnego wydania i etapu demo. Weryfikacja: przegląd matrycy 10 plansz × funkcje, brak nieoznaczonych założeń.

**Uzupełnienie UX 29.10.2026:** Do rejestru zakresu dodać UX01–UX08 oraz ścieżki CH065–CH072: sześć rozszerzeń wymaganych w pełnym wydaniu (CH065–CH070) i dwie pozycje opcjonalne lub badawcze (CH071–CH072). Zapisać, że są dodatkiem do wzorca Liquid Glass i nie legalizują iMessage ani RCS na Androidzie.

**Odbiór rozszerzony:** Matryca zakresu rozróżnia funkcje już zaplanowane od nowych; zakres pełnego wydania obejmuje CH065–CH070, a CH071–CH072 nie blokują wydania bez odrębnej decyzji.


**Wynik CH001 (29.10.2026):** rejestr zakresu, G01–G10, definicje demo/wydania i matryca 10 × 10 zapisane w [CH001_Kontrakt_produktu_i_rejestr_decyzji.md](CH001_Kontrakt_produktu_i_rejestr_decyzji.md), wraz z [rejestrem JSON](CH001_Rejestr_decyzji.json). **Status zadania dokumentacyjnego: wykonany; status otwartych decyzji: do domknięcia w taskach wskazanych przy G01–G10.** Nie oznacza to uruchomionych SMS/MMS, potwierdzonych operatorów ani produkcyjnego HD.

### CH002 Zamrozić wzorce obrazu i typografii

**P0 · UX i Android i QA · 2–3 osobodni · zależności CH001**

Wykonanie: zweryfikować SHA256 HTML i zachować niezmienny oryginał. Otworzyć go na referencyjnym Androidzie, ustalić rzeczywisty font z zadeklarowanego stosu i render 390×844. Oddzielić ramkę iframe od zawartości. Zapisać API, density, fontScale, renderer, locale, dane i zegar. Zestawić deklarowane CSS z computed geometry, uwzględniając border i box sizing.

Odbiór: 10 wzorców, manifest warunków i uzgodniony wariant fontu. Każdy niejednoznaczny wymiar ma pomiar; brak cichego zamienienia SF na Inter. Weryfikacja: overlay dwóch kolejnych renderów jest stabilny.

**Wynik częściowy CH002 (2026-10-29):** wykonano niezależną weryfikację sumy SHA256 HTML i audyt 10 referencji Chrome/macOS, geometrii CSS i wrappera. [Raport CH002](CH002_Weryfikacja_wzorcow.md), [pomiary](CH002_Pomiary_i_manifest.json) i [walidator](../../scripts/ch002_verify_reference.mjs). **Blokada:** nie wykonano renderu Android 390×844, pomiaru faktycznego fontu ani dwóch prób stabilności; G01 pozostaje otwarte. Status `in_progress_blocked_android_reference`; nie oznaczać CH002 jako ukończonego.

**Render Android CH002 (2026-10-29, wieczór):** oryginał wyrenderowano na Samsung Galaxy S26 Ultra (Android 17/API 37, Chrome 156, DPR 3.5): 10 ekranów × 2 przebiegi, overlay stabilny w tolerancji 3/255 (6/10 bajtowo identycznych). Rendered Fonts: **Inter (web font z bundla) dla 100 % glifów**; SF nie występuje. Zmierzono 161/161 elementów; metryki Inter przesuwają łamanie wierszy do +22 px (S04/S06), więc goldeny Androida zastępują geometrię macOS. [Raport CH002](CH002_Weryfikacja_wzorcow.md), [golden Android](CH002_Android/run1), [porównanie](CH002_Android/porownanie.json), [skrypt przechwytywania](../../scripts/ch002_android_capture.mjs). Status `android_reference_verified_awaiting_g01_approval`: do zamknięcia brakuje tylko zatwierdzenia G01 (Inter + goldeny Android) przez UX/QA.

**CH002 ukończony (05.11.2026):** G01 zatwierdzona: Inter 400–700 (zmienny) jako font Androida, goldeny Androida z [CH002_Android/run1](CH002_Android/run1) jako wzorzec 1:1, plakietka „HD” w wadze 700. Status `completed`. CH003 i CH006 porównują wyniki z goldenami Androida, a CH007 dostarcza zasoby fontu i metryki.

### CH003 Sprawdzić wykonalność natywnego szkła

**P0 · Android grafika · 3–5 osobodni · zależności CH002**

Wykonanie: przygotować prototyp S02 i S04 w Compose, z prawdziwym próbkowaniem sceny, saturacją, maską, gradientem i refleksami. Porównać do CSS oraz kontrolnego wariantu WebView. Zmierzyć GPU, klatki, pamięć i scroll. Sprawdzić szkło nad treścią, nie tylko statyczną tapetą. Opisać wymagane API i koszt starszych urządzeń.

Odbiór: decyzja G03 na podstawie obrazów i pomiaru; tekst nad panelem pozostaje ostry, brak rekurencji warstw. Weryfikacja: urządzenie z docelowego minimum i ekran nakładających się paneli. Nie uznawać matowej karty za pełną zgodność.

**Wynik CH003 (07.11.2026):** POC na S26 Ultra (API 37, 120 Hz, build release): szkło Compose na `RenderEffect` zgodne z goldenami (S01 3,4 %, S02 6,2 %, S03 6,7 %, arkusz S04 3,6 %), tekst nad panelami ostry, nakładające się panele (arkusz S04 nad szkłem S03) bez rekurencji. Koszt względem matu: jank S03 0,9 % vs 0,5 %, S04 3,3–3,9 % vs 2,0 %; p99 klatki S04 46–48 ms vs 22–30 ms; **pamięć graficzna +76 MB (S03) / +143 MB (S04)**. Rekomendacja G03: Compose bez WebView. **Optymalizacja (07.11.2026):** rozmycie w ¼ rozdzielczości — obraz bez zmian (≤ 1/255), pamięć szkła +21/+27 MB względem matu (było +76/+143), jank S03 0,35 %, S04 2,3–2,5 %, p99 S04 30 ms. **Galaxy A15 5G (90 Hz, 3,5 GB):** pamięć szkła +8/+14 MB względem matu (bez optymalizacji +41/+82), jank S03 0,3–0,5 % (mat 0,15 %), S04 przy rozciąganiu krawędzi listy ≈ 10 % (bez optymalizacji 30–34 %, mat 0,2 %); stuknięcia w S04 24,6 % w obu wariantach — cecha każdej pojedynczej zmiany stanu w aplikacji (S02 filtr: 50 % na S26), GPU 12–15 ms na klatkę po bezczynności; szkło, cienie i układ tekstu wykluczone, przyczyna do potwierdzenia profilerem GPU. Status `poc_completed_awaiting_api31_and_approval`. [Raport](CH003_Wykonalnosc_szkla.md), [pomiary](CH003_Pomiary.json).  Demo: `--ez matte true` wymusza wariant matowy do porównań.

### CH004 Przygotować projekt i powtarzalne środowisko

**P0 · Android · 1–2 osobodni · zależności CH001 CH003**

Wykonanie: ustalić stabilne kompatybilne wersje JDK/Kotlin/AGP/Compose BOM i aktualne target/compile SDK. Utworzyć katalog wersji, wrapper, moduły i warianty demo/debug/release. Dodać konfigurację podpisu poza repozytorium, przykładowe zmienne bez sekretów i instrukcję uruchomienia. Build demo nie ma prawdziwej wysyłki ani zakupów.

Odbiór: czyste środowisko buduje demo i release testowy bez ręcznych zmian plików. Weryfikacja: odtworzenie według README i kontrola, że fixture nie trafiają do produkcyjnego źródła danych.

### CH005 Zdefiniować modele i granice architektury

**P0 · lider Android · 2–3 osobodni · zależności CH001**

**Status 06.11.2026: wykonano CH005.** [Kontrakty Kotlin/JVM](../../core/model/README.md) obejmują typowane ID, modele, porty transportów, repozytoriów, zegara i SIM, UiState/UiAction, błędy, statusy wysyłki i testowy CAS. [ADR](CH005_ADR_Modele_i_granice.md) opisuje źródła prawdy oraz migracje. Lokalny test JVM: 31 asercji PASS. Integracje Android/Room i próby fizyczne należą do dalszych zadań.


Wykonanie: zdefiniować typowane identyfikatory, Conversation, MessageRef, Draft, SendOperation, Attempt, Schedule i Attachment. Rozdzielić transport SMS/MMS/HD, repozytoria, zegar i dostawcę SIM. Ustalić UiState/UiAction, błędy i kontrakt zapisu. Zapisać ADR o providerze jako historii SMS/MMS i Room jako metadanych/projekcji.

Odbiór: UI może działać z repozytorium testowym, a logika wysyłania bez UI. Weryfikacja: przegląd zależności modułów oraz sekwencji send/cancel/retry; brak cyklicznej zależności feature→feature.

**Uzupełnienie UX 29.10.2026:** Rozszerzyć kontrakty modelu o trwałe ustawienia per conversationId (dziedziczenie z globalnego motywu), read/unread override, stan wyciszenia, lokalne metadane przypięć, identyfikator źródłowej wiadomości dla mediów oraz bezpieczne identyfikatory skrótów. Określić migracje i zachowanie po usunięciu wątku.

**Odbiór rozszerzony:** Modele obsługują odtwarzalną galerię, skok do wiadomości i ustawienia per rozmowa bez duplikowania treści wiadomości w Room.


### CH006 Przygotować dane demonstracyjne i raportowanie

**P0 · Android i QA · 1–2 osobodni · zależności CH002 CH005**

**Status 06.11.2026: częściowo wykonano.** [Generator 10 fixture i walidacja](../../core/testing/README.md) zachowują 500 elementów, polskie teksty, SIM, zegar, źródłowe stany błędu/szkicu/HD i wyjątek 21/160. [Raport techniczny](CH006_Fixture_i_porownanie.md) zawiera testy oraz skrypt overlay/diff z goldenami CH002. **Do zamknięcia:** renderer i dwa zrzuty 10 ekranów na Androidzie bez kontaktów/sieci, porównanie i odbiór.


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

**Wynik CH010 (07.11.2026):** S01 w Compose ([StartScreen.kt](../../android/app/src/main/java/app/chatra/ui/start/StartScreen.kt)): logo szklane, koła dekoracyjne jako część sceny (rozmywane przez karty), obie karty, informacja o historii, CTA i dwa przyciski pomocnicze (Prywatność, Pomoc → trasy CH044). Tymczasowy kontrakt `SmsRoleGate` + maszyna stanów `SmsSetup` w `core:model` (sprawdzanie, gotowość, prośba, sukces, anulowanie, odmowa trwała → ustawienia systemu, brak telefonii, brak roli; ponowne sprawdzenie po powrocie na ekran); adapter systemowy dostarcza CH021. Demo: `FakeSmsRoleGate`, scenariusz z intencji `--es screen S01 --es role cancelled|denied|unavailable|no-telephony|held`. Po sukcesie stos sekcji zastępowany Skrzynką (Start nie zostaje pod spodem). Treść przewija się przy małym ekranie i dużym foncie; CTA rośnie z tekstem. Ekran nie prosi o aparat ani zdjęcia. Testy: `SmsSetupTest` (7 przypadków, w tym podwójne kliknięcie). Porównanie z goldenem `android-S01.png` (S26 Ultra, DPR 3,5, tryb referencyjny 390×844): 3,37 % pikseli > 16/255 (start 16,6 %), wszystkie bloki w ±1 dp, dwa przebiegi identyczne. Odtworzone zachowania Chrome: kurczenie logo i CTA przy przepełnieniu (`flex-shrink`, logo ≈47, CTA ≈41), obramowania `content-box` (logo 62, treść kart 312), `line-height` 40 < wysokości glifu 36 px. Zmiana globalna `TextMotion.Animated` (ułamkowe szerokości glifów jak w Chrome) poprawia też S02 (6,43 → 6,21 %) i S03 (7,24 → 6,65 %), S04 bez zmian (≈18,9 %, rozbieżność sprzed CH010).

### CH011 Odtworzyć S02 Skrzynka

**P1 · Android UI · 2–3 osobodni · zależności CH006 CH008 CH009**

Wykonanie: nagłówek, pole wyszukiwania, filtr, przypięte/pozostałe, siedem wariantów wierszy i dock. Wyświetlać prawidłowe line clamp, badge, draft i błąd. Dodać stany pustej listy, braku wyników filtra, odczytu i utraty roli. Menu i long press według CH020.

Odbiór: widoczne detale S02 zgodne, filtr liczy wątki, ostatni wiersz osiągalny. Weryfikacja: fixture plus lista 1000 wątków, długie nazwy i natychmiastowa zmiana odczytu bez resetu scrolla.

**Uzupełnienie UX 29.10.2026:** Przewidzieć wejście do działań z długiego przytrzymania wiersza i dostępności, a także dostęp do oznaczania wszystkich jako przeczytane. Projekt przesunięć/gestów i konfiguracji realizuje CH069; na S02 nie zmieniać geometrii zatwierdzonego wzorca.

**Odbiór rozszerzony:** Przypięcia, nieprzeczytane i dostępne akcje pozostają spójne po zmianie wątku lub powrocie z ekranu szczegółów; funkcje gestowe mają alternatywę bez gestu.


### CH012 Odtworzyć S03 Rozmowa

**P1 · Android UI · 3–4 osobodni · zależności CH006 CH008 CH009**

Wykonanie: pływający header, sekcje dni, oba dymki, karta HD, wszystkie statusy, Ponów, odliczanie i kompozytor. Oddzielić listę od warstw pływających. Obsłużyć przewijanie do wiadomości, paginację i wskaźnik nowych wiadomości. Pole wieloliniowe rośnie do ustalonej wysokości, potem przewija tekst.

Odbiór: zgodny S03 oraz statusy odrębne od treści; brak utraty szkicu przy IME i powrocie. Weryfikacja: długa rozmowa, kilkulinijkowa treść, przychodząca wiadomość podczas czytania historii i błędy transportu.

**Uzupełnienie UX 29.10.2026:** Nagłówek rozmowy prowadzi do karty szczegółów CH065. Zapewnić zakotwiczenie konkretnej wiadomości dla wyników wyszukiwania i galerii, render grup załączników z CH067 oraz punkt wejścia do pierwszej nieprzeczytanej z CH070. Zachować IME, SIM, stany HD i wzorzec S03.

**Odbiór rozszerzony:** Powrót ze szczegółów/galerii zachowuje pozycję i szkic; skok do wiadomości działa przy stronicowaniu, nie przenosi użytkownika arbitralnie na koniec.


### CH013 Odtworzyć S04 Dodatki i Chatra HD

**P1 · Android UI · 2–3 osobodni · zależności CH006 CH008 CH009**

Wykonanie: sheet 374×640, dim, uchwyt, cztery akcje, miniatury, Zmień i trzy opcje. Wspólny stan wybranych plików z kompozytorem. Dynamiczne CTA i opis kosztu/rozmiaru; stan bez plików i błędu. Przy mniejszym ekranie przewijanie, stała dostępność CTA.

Odbiór: zgodny S04; opcje wyboru wzajemnie się wykluczają i aktualizują wariant zlecenia. Weryfikacja: 4 pliki, brak plików i wiele plików, zamknięcie bez utraty danych i TalkBack po otwarciu arkusza.

**Korekta zgodności S04 (07.11.2026):** porównanie z goldenem `android-S04.png` (S26 Ultra, tryb referencyjny) 18,9 % → 15,9 % pikseli > 16/255; sam arkusz (y 196–760) 3,6 %, położenie elementów ±0,6 dp. Poprawki: warianty „Kopia bez lokalizacji” i „MMS” były niewidoczne (lista i odstęp dzieliły miejsce wagą po połowie), obramowania `content-box` opcji radiowych, padding arkusza `border-box` (treść y 205, szerokość 336), uchwyt skurczony do 0 jak w Chrome, wiersz kafelków 75,71, radio 16 × 20 jak w Chrome Android. **Świadome odstępstwa** (≈80 % pozostałej różnicy): pod arkuszem rysowana jest prawdziwa rozmowa S03, a nie abstrakcyjne placeholdery szablonu; CTA ma 54 dp i jest przypięte na dole (lista przewija się nad nim), podczas gdy golden ściska je do 20,6 dp i wypycha poza arkusz — nie spełniałoby celu dotyku.

### CH014 Odtworzyć S05 Formularz harmonogramu

**P1 · Android UI · 2–3 osobodni · zależności CH006 CH008 CH009**

Wykonanie: arkusz, podgląd szkicu, skróty, data/godzina, wybór SIM i polityki spóźnienia. Zaprojektowane pickery z CH020. Wstrzyknąć zegar; daty i etykieta CTA wynikają ze stanu. Walidacja daty, brak SIM i brak dostępu do alarmów widoczne przed potwierdzeniem.

Odbiór: zgodny stan 31 października 10:00 i poprawna zmiana pól. Weryfikacja: termin przeszły, północ, DST i zmiana odbiorcy; zapis na razie przez kontrakt testowy schedule repository.

**Wynik CH014 (08.11.2026):** arkusz S05 w Compose ([ScheduleSheet.kt](../../android/app/src/main/java/app/chatra/ui/schedule/ScheduleSheet.kt)) nad rozmową, ze wspólnym stanem rozmowy (szkic, odbiorcy, SIM): podgląd treści, skróty liczone od bieżącej daty w strefie urządzenia (`scheduleShortcuts`: jutro 8:00, najbliższa sobota 10:00, najbliższy poniedziałek 9:00), data i godzina, segment SIM, polityka spóźnienia i CTA z datą wynikającą ze stanu. Walidacja przed potwierdzeniem (`validateSchedule`): termin przeszły lub bliższy niż 1 min, nieistniejąca godzina przy zmianie czasu (wysyłka o przesuniętej godzinie i komunikat), podwójna godzina (pierwsze wystąpienie i komunikat), brak treści, brak SIM lub SIM do potwierdzenia, brak zgody na dokładne alarmy (`ExactAlarmGate`: komunikat i CTA do ustawień, ponowne sprawdzenie po powrocie). Zapis przez kontrakt testowy `ScheduleRepository.schedule` (operacja `SCHEDULED` + `ScheduledMessage` z Instant, LocalDateTime i strefą; szkic przechodzi do harmonogramu), po zapisie przejście do zakładki Zaplanowane. „Wróć” prowadzi do Dodatków (S04). Pickery daty i godziny to tymczasowo dialogi systemowe — projekty w CH020. Tryb referencyjny: preset treści i terminu z fixture (wzorzec S05 ma inną treść niż kompozytor S03). Demo: `--ez exactAlarms false`. Testy: `ScheduleFormTest` (skróty z czwartku 29.10, sobota → kolejna sobota, po północy, przeszłość, przeskok i powtórzenie godziny przy zmianie czasu), `FixtureTest.scheduleFromS05…`. Porównanie z goldenem `android-S05.png` na S26 (tryb referencyjny): 6,58% → 3,27% pikseli z różnicą >16/255, przesunięcie pasów ≤ 1 px; reszta to celowe tło (prawdziwa rozmowa pod arkuszem, jak w S04). Poprawki przy porównaniu: obramowania CSS 1 px przyciągane do pikseli urządzenia jak w Chrome (`cssBorder()`: 3 px przy DPR 3.5 zamiast 3.5) w arkuszu, karcie odbiorcy i separatorach pól oraz zachłanne łamanie wierszy (`LineBreak.Simple` w `chatraText`), dzięki któremu „…przekracza 1 / h” łamie się jak we wzorcu. Kontrola regresji: S01–S03 bez zmian, S04 15,86% → 14,96%.

### CH015 Odtworzyć S06 Zaplanowane

**P1 · Android UI · 1–2 osobodni · zależności CH006 CH008 CH009**

Wykonanie: karta decyzji, trzy akcje, lista oczekujących, podsumowanie ostatnio wysłanego i dock. Lista sortowana po terminie, błędy wyróżnione tekstem i ikoną. Dodać pusty harmonogram, wiele zaległych pozycji i brak dokładnych alarmów.

Odbiór: zgodny S06, licznik listy dynamiczny. Weryfikacja: usunięcie jednego harmonogramu nie usuwa rozmowy, Wyślij teraz uruchamia jedną akcję, powrót zachowuje pozycję.

**Wynik CH015 (08.11.2026):** ekran S06 w Compose ([ScheduledScreen.kt](../../android/app/src/main/java/app/chatra/ui/scheduled/ScheduledScreen.kt), [ScheduledViewModel.kt](../../android/app/src/main/java/app/chatra/ui/scheduled/ScheduledViewModel.kt)) w zakładce Zaplanowane: nagłówek, karta decyzji (szkło `GlassStyles.Decision`), lista „Czeka · N” z licznikiem ze stanu, stopka ostatniej wysyłki i dock. Logika w modelu ([ScheduledOverview.kt](../../android/core/model/src/main/java/app/chatra/core/model/ScheduledOverview.kt)): decyzje nad oczekującymi, obie rosnąco po terminie; osobne powody decyzji — termin minął (Wyślij teraz / Zmień termin / usuń), błąd wysyłki (tekst i ikona na czerwono, Ponów / usuń), niepewna próba (ostrzeżenie o duplikacie). Kontrakt `ScheduleRepository`: `sendNow` (jedno przejście do DISPATCHING na wersję zlecenia; drugie stuknięcie nic nie robi, dodatkowo blokada w ViewModelu), `reschedule` (nowa wersja zlecenia, token alarmu wyczyszczony), `delete` (anuluje operację, rozmowa i historia zostają), `lastSent`. Utracona SIM: karta pokazuje wybór karty i nie wysyła bez świadomego wyboru; w liście pozycja z niedostępną kartą ma ikonę i tekst. Pusty harmonogram i brak zgody na dokładne alarmy mają osobne komunikaty (demo: `--ez exactAlarms false`). Zmiana terminu tymczasowo przez systemowe dialogi daty i godziny (CH020), z walidacją `validateSchedule`. Stuknięcie pozycji otwiera rozmowę; lista ma własny stan przewijania w zakładce, więc powrót zachowuje pozycję. Testy: `ScheduledActionsTest` (podział i kolejność z fixture, jedno uruchomienie „Wyślij teraz” i jedna wiadomość w rozmowie, zakończenie wysyłki i stopka, usunięcie bez utraty rozmowy, zmiana terminu z nową wersją i sortowaniem). Porównanie z goldenem `android-S06.png` na S26: 4,86% pikseli z różnicą >16/255, pasy przesunięte o ≤ 1,7 dp (zaokrąglanie wysokości wierszy tekstu do pikseli kumuluje się w liście). Na urządzeniu sprawdzone: podwójne „Wyślij teraz” → jedna wysyłka, karta znika, stopka pokazuje Tomka; komunikat o alarmach.

### CH016 Odtworzyć S07 Wyszukiwanie

**P1 · Android UI · 2–3 osobodni · zależności CH006 CH008 CH009**

Wykonanie: pole, wyczyść/anuluj, filtry, objaśnienie, dwa szybkie trafienia, lista i mark. Zapytanie, filtry i scroll w stanie możliwym do odtworzenia. Dodać brak wyników, indeksowanie i błąd. Kopiowanie przez systemowy schowek bez logowania wartości.

Odbiór: zgodny S07 i poprawny powrót z wyniku. Weryfikacja: długie zapytanie, wiele wyróżnień, duży font, dwa różne przyciski Kopiuj i czyszczenie pola.

**Implementacja CH016 (09.11.2026):** S07 zaimplementowany: lokalny kontrakt wyszukiwania, filtry, wyróżnienia, dwa Kopiuj, kotwica MessageRef, stan zapisywalny. Testy JVM zaliczone. Końcowy odbiór obrazu i interakcji oczekuje na ponowne połączenie ADB. [Zakres, testy i ograniczenia odbioru](CH016_CH018_Wdrozenie_i_weryfikacja.md). Weryfikacja działającego demo w emulatorze i poprawki z 10.11: [raport QA](CH016_CH018_Emulator_QA_2026-11-10.md).

### CH017 Odtworzyć S08 Nowa wiadomość

**P1 · Android UI · 2–3 osobodni · zależności CH006 CH008 CH009**

Wykonanie: chipy, wpisywanie numeru, tryby wysyłki, ostrzeżenie grupowe, sugestie i kompozytor. Puste i niepoprawne dane blokują wysłanie. Chipy muszą zawijać się zgodnie z projektem. Widoczny transport i liczba operacji zmieniają się wraz z odbiorcami.

Odbiór: zgodny S08 dla 3 osób; usuwanie konkretnego chipa zachowuje resztę. Weryfikacja: 1 osoba, 3 osoby i duża liczba osób, IME, długi kontakt i powrót do szkicu po anulowaniu.

**Implementacja CH017 (09.11.2026):** S08 zaimplementowany: odbiorcy, walidacja, osobne SMS/MMS, wybór SIM i szkic. Testy obejmują podwójne Wyślij i częściowy błąd bez ponawiania przyjętych zleceń. Końcowy odbiór na telefonie pozostaje otwarty. [Zakres, testy i ograniczenia odbioru](CH016_CH018_Wdrozenie_i_weryfikacja.md). Weryfikacja działającego demo w emulatorze i poprawki z 10.11: [raport QA](CH016_CH018_Emulator_QA_2026-11-10.md).

### CH018 Odtworzyć S09 Wygląd

**P1 · Android UI · 1–2 osobodni · zależności CH006 CH008 CH009**

Wykonanie: nagłówek, podgląd o wysokości 196 jednostek projektu, cztery próbki, segmenty, suwak i switche 52×32 oraz reset. Przenosić stan do podglądu bez zapisu demonstracyjnych danych. Tryby niezaprojektowane w pełni kierować do kontraktu tokenów CH020/CH040, bez fikcyjnego działania.

Odbiór: zgodny domyślny S09; role radio/switch/slider i bieżący stan dostępne dla czytnika. Weryfikacja: suwak 1/4/7, reset i przejście wstecz bez utraty wyboru.

**Implementacja CH018 (09.11.2026):** S09 zaimplementowany: podgląd 196 dp, wybory, slider, dostępne przełączniki i reset. Niezatwierdzone warianty opisane jako podgląd roboczy do CH020/CH040. Końcowy odbiór obrazu i interakcji pozostaje otwarty. [Zakres, testy i ograniczenia odbioru](CH016_CH018_Wdrozenie_i_weryfikacja.md). Weryfikacja działającego demo w emulatorze i poprawki z 10.11: [raport QA](CH016_CH018_Emulator_QA_2026-11-10.md).

### CH019 Odtworzyć S10 Ustawienia

**P1 · Android UI · 1–2 osobodni · zależności CH006 CH008 CH009**

Wykonanie: karta Pro, sekcje Wysyłanie/Wygląd i porządek/Prywatność, ikony i dock. Podsumowania z modelu zamiast stałych 3/4. Placeholder ceny wyłącznie w fixture; produkcyjny brak oferty ma własny tekst. Każdy wiersz emituje oddzielną akcję.

Odbiór: zgodny S10 z fixture oraz poprawny routing wszystkich pozycji. Weryfikacja: krótki ekran, duży font, brak produktu sklepu i różne liczniki kosza/blokad.

**Wynik CH019 (10.11.2026):** S10 i routing zaimplementowane, podsumowania z modelu, cena wyłącznie w fixture. Sprawdzone w emulatorze: zmiana opóźnienia, przełącznik i liczniki blokad/kosza. [Zakres i granice odbioru](CH019_CH021_Wdrozenie_i_weryfikacja.md).

### CH020 Uzupełnić brakujące projekty i stany

**P0 · UX i produkt · 4–6 osobodni · zależności CH001 CH002 CH008**

Wykonanie: zaprojektować menu skrzynki, szczegóły rozmowy, SIM, pickery, szablony, ustawienia HD, blokady, kosz, pomoc, prywatność, paywall i stronę linku. Opisać empty/loading/error/disabled/permission dla wszystkich ekranów. Dokończyć Mgłę/Grafit/Kontrast, dark i compact z tokenami. Każdy widok ma komplet akcji, fokusu i tekstów.

Odbiór: katalog projektów i mapa do zadań; nowe widoki rozpoznawalne jako uzupełnienie, domyślne 10 plansz nie przeprojektowane. Weryfikacja: przegląd scenariuszy bez martwych końców.

**Uzupełnienie UX 29.10.2026:** Uzupełnić katalog brakujących ekranów o szczegóły kontaktu/grupy (CH065), galerię rozmowy (CH066), gesty i menu (CH069), ustawienia lokalnego wyglądu (CH068), nieprzeczytane (CH070), opcjonalny eksport i skrót (CH071). Wszystkie podwidoki zgodne z językiem Liquid Glass; wzorzec S01–S10 pozostaje bez zmian.

**Odbiór rozszerzony:** Zatwierdzone interakcje, empty/loading/error, ograniczenia numerów i protokołów oraz dostępność dla CH065–CH071 mają mapę do zadania i dowodu odbioru.


**Wynik CH020 (10.11.2026):** [Katalog 18 projektów i siedmiu stanów](CH020_Katalog_projektow_i_stanow.md), plansza SVG, podgląd w aplikacji i osiem palet tokenów. Walidacja automatyczna przeszła. Akceptacja UX/produktu G09 pozostaje otwarta.

## 4 Telefonia dane i media

### CH021 Wdrożyć rolę SMS i uprawnienia

**P0 · Android platforma · 2–3 osobodni · zależności CH004 CH005 CH010**

Wykonanie: manifest pełnego klienta SMS, RoleManager, kontrola dostępności i wyników. Oddzielić nadanie roli od uprawnień przyznawanych podczas działania aplikacji. Wdrożyć utratę roli podczas działania, blokadę automatycznej wysyłki i bezpieczne powroty z ustawień. Pytać o dostęp do kontaktów, SIM, powiadomień i mediów w kontekście użycia danej funkcji.

Odbiór: aplikacja pojawia się jako kandydat roli i nie odczytuje chronionej historii przed dostępem. Weryfikacja: świeża instalacja, odmowa, ponowienie, cofnięcie roli oraz bezpieczny stan każdego receivera po zmianie.

**Wynik CH021 (10.11.2026):** Pełny manifest, RoleManager, osobne zgody kontekstowe, rewalidacja po powrocie i utracie roli, chronione odbiorniki oraz prywatne wejście SENDTO/RESPOND. `full` kompiluje się; historia i transporty są kolejnymi zadaniami. [Raport i otwarte próby systemowe](CH019_CH021_Wdrozenie_i_weryfikacja.md).

### CH022 Zintegrować historię providera i projekcję Room

**P0 · Android dane · 3–5 osobodni · zależności CH005 CH021**

Wykonanie: DAO, migracje i klucze z typem SMS/MMS. Stronicowany odczyt historii, rozpoznanie uczestników i ThreadID, ContentObserver oraz odtwarzanie projekcji. Nie duplikować kanonicznej historii. Wspierać odczyt MMS z częściami i leniwe miniatury. Zasilać UI przez Flow.

Odbiór: istniejące wiadomości pozostają nienaruszone; ponowny odczyt nie tworzy duplikatów. Weryfikacja: duża historia, identyczne ID w różnych tabelach, zewnętrzna zmiana, błąd providera i brak roli.

**Uzupełnienie UX 29.10.2026:** Zapewnić stabilne relacje załącznik→wiadomość→wątek, klucze do galerii mediów i bezpieczne zapytania po conversationId. Przechowywać jedynie ustawienia i indeksy lokalne, nie tworzyć drugiej kanonicznej kopii SMS/MMS. Zastosować migracje dla zmian wyciszenia i oznaczenia nieprzeczytane.

**Odbiór rozszerzony:** Indeks galerii i ustawienia nadal działają po rekoncyliacji, zmianie kontaktu, ponownej instalacji w granicach istniejącej polityki oraz aktualizacji providera.


**Wynik CH022 (10.11.2026):** Provider SMS/MMS, typowane referencje i relacje części w Room, migracja, odczyt stronami i odtwarzanie projekcji po zmianach. Emulator: historia 450 rekordów, aktualizacja istniejącego MMS, błąd providera, zachowanie metadanych. [Raport CH022–CH026](CH022_CH026_Wdrozenie_i_weryfikacja.md).

### CH023 Obsłużyć odbiór SMS i status przeczytania

**P0 · Android telefonia · 2–3 osobodni · zależności CH021 CH022**

Wykonanie: odbiornik SMS_DELIVER, składanie części, trwały zapis zgodnie z obowiązkami klienta i aktualizacja projekcji. Odróżnić nową wiadomość od ponownego zdarzenia; zachować adres, czas i SIM. Oznaczanie przeczytania przez właściwy use case, synchronizacja badge oraz powiadomienia.

Odbiór: przychodzący SMS jest w historii systemowej i właściwym wątku, bez podwojenia. Weryfikacja: pojedynczy/multipart, ekran zamknięty, proces nieaktywny, aktywna rozmowa oraz wielokrotne odświeżenie obserwatora.

**Uzupełnienie UX 29.10.2026:** Rozszerzyć use case o ręczne 'oznacz jako nieprzeczytane', 'oznacz wszystkie jako przeczytane' i semantykę pierwszej nieprzeczytanej, bez oznaczania wiadomości przed realnym pokazaniem treści. Zdefiniować synchronizację badge, provider i powiadomień także po restarcie (CH070).

**Odbiór rozszerzony:** Powiadomienia, liczniki i ręczny stan nieprzeczytania nie rozjeżdżają się po odczycie, ponownym wejściu, przyjściu nowego SMS ani zmianie roli.


**Wynik CH023 (10.11.2026):** Trwałe PDU, składanie multipart, zapis do providera, deduplikacja i rekoncyliacja przerwy zapisu; pokazane wiadomości, ręczny stan odczytu, badge i powiadomienie. Sprawdzono receiver przez konsolę AVD oraz testy integracyjne. Odbiór fizyczny pozostaje otwarty. [Raport](CH022_CH026_Wdrozenie_i_weryfikacja.md).

### CH024 Wdrożyć subskrypcje i wybór SIM

**P0 · Android telefonia · 2–3 osobodni · zależności CH020 CH021 CH005**

Wykonanie: lista aktywnych subskrypcji, obserwacja zmian, nazwy i domyślna karta. Ustalić pierwszeństwo wybór szkicu→preferencja rozmowy→domyślna, z walidacją dostępności. Zapisywać ID i migawkę opisu, nie sam slot. Wybór jawny w rozmowie, edytorze i harmonogramie.

Odbiór: brak cichego przełączenia po wyjęciu karty; jedna karta nie uruchamia zbędnego pytania. Weryfikacja: dwie karty SIM i eSIM, brak dostępu do listy, SIM usunięta po zapisaniu szkicu i zmiana systemowej domyślnej.

**Wynik CH024 (10.11.2026):** Lista i obserwacja subskrypcji, systemowa domyślna, pierwszeństwo szkic/rozmowa/aplikacja/system; ID i migawki opisów. Wysyłka sprawdza aktualną kartę. Dual SIM i eSIM wymagają fizycznego odbioru. [Raport](CH022_CH026_Wdrozenie_i_weryfikacja.md).

### CH025 Zaimplementować licznik i kodowanie SMS

**P0 · Android domena · 1–2 osobodni · zależności CH005 CH024**

Wykonanie: adapter platformowego dzielenia tekstu, model encoding/units/parts i prezentacja licznika. Ustalić opis dla Unicode i multipart. Dodać opt in transliteracji z podglądem i możliwością przywrócenia. Zaktualizować licznik podczas edycji i po zmianie transportu.

Odbiór: treść nigdy nie jest zmieniana samoczynnie, wynik nie korzysta z prostego length/160. Weryfikacja: granice części, GSM extension, polskie znaki, emoji i źródłowy tekst 20 znaków z niepoprawnym fixture 21/160.

**Wynik CH025 (10.11.2026):** Adapter calculateLength/divideMessage, jednostki ostatniej części, jawna transliteracja i przywrócenie oryginału. Platformowe testy ASCII, rozszerzeń GSM, polskich znaków i emoji przeszły; brak samoczynnej zmiany tekstu. [Raport](CH022_CH026_Wdrozenie_i_weryfikacja.md).

### CH026 Wdrożyć wysyłanie SMS

**P0 · Android telefonia · 3–4 osobodni · zależności CH022 CH024 CH025**

Wykonanie: utworzyć trwałe operacje, próby, per recipient i per part. Przekazać do SmsManager właściwej SIM, z unikalnymi callbackami. Zapisać i uzgadniać rekordy providera zgodnie z obowiązkami domyślnego klienta. Wysyłka niezależna od aktualnego ekranu.

Odbiór: drugi klik nie tworzy tej samej operacji, sent nie oznacza delivered. Weryfikacja: rzeczywisty SMS i multipart z obu kart SIM, brak zasięgu, tryb samolotowy i brak uprawnienia w chwili wykonania.

**Wynik CH026 (10.11.2026):** Trwałe operacje/odbiorcy/próby/części, unikalne zadania, CAS i callbacki, natywny transport wybranej SIM oraz własne rekordy providera. W emulatorze potwierdzono pojedynczy SMS i dwie części przez prawdziwy SmsManager, z odrębnym sent/delivered. Próby niepewne nie są automatycznie powtarzane. Fizyczna telefonia pozostaje do odbioru. [Raport](CH022_CH026_Wdrozenie_i_weryfikacja.md).

### CH027 Uzgodnić statusy ponawianie i niepewne próby

**P0 · Android domena · 2–3 osobodni · zależności CH026**

Wykonanie: maszyna stanów, agregacja części, callbacki poza kolejnością i deduplikacja. Retry tylko jawnie dla pewnej porażki, nigdy automatycznie po niepewnym crashu. Dodać DISPATCH_UNCERTAIN i decyzję użytkownika. Rekoncyliować z providerem, jeśli istnieją porównywalne dane, bez zgadywania sukcesu.

Odbiór: DELIVERED nie cofa się do SENT, brak duplikatów przy ponownym callbacku. Weryfikacja: awaria tuż przed/po wywołaniu API, jedna część nieudana, brak raportu doręczenia i retry po zmianie SIM.

**Implementacja 10.11.2026:** [zakres i weryfikacja](CH027_CH028_Wdrozenie_i_weryfikacja.md); odbiór fizyczny pozostaje otwarty.

### CH028 Utrwalić szkice i okno anulowania

**P0 · Android dane i domena · 3–4 osobodni · zależności CH012 CH017 CH022 CH026 CH027**

Wykonanie: zapis szkicu tekst/odbiorcy/pliki/SIM, trwały snapshot operacji PENDING_UNDO. Odliczanie od deadline, atomowy cancel przeciw dispatch, przywrócenie szkicu po cancel. Zdefiniować bezpieczne zachowanie po zabiciu procesu i restarcie. Nigdy nie wywoływać transportu przed końcem okna.

Odbiór: powrót i obrót nie gubią szkicu; skuteczny cancel nie wysyła. Weryfikacja: 2,9 s, 3,0 s i 3,1 s, wielokrotny klik, brak aktywnego UI i wyścig cancel/send.

**Implementacja 10.11.2026:** [zakres i weryfikacja](CH027_CH028_Wdrozenie_i_weryfikacja.md); odbiór fizyczny pozostaje otwarty.

### CH029 Podłączyć kontakty i wysyłkę do wielu osób

**Wynik 11.11.2026:** [kontakty, deduplikacja i wielu odbiorców](CH029_Kontakty_i_wielu_odbiorcow.md). Wyodrębniony zakres CH029; dowody w raporcie.

**P1 · Android domena · 2–3 osobodni · zależności CH017 CH022 CH025 CH026 CH028**

Wykonanie: wyszukiwanie kontaktów, wybór kilku numerów, normalizacja, deduplikacja, sugestie i ręczny numer. Osobne SMS jako domyślny tryb, nadrzędna operacja i wyniki odbiorców. Grupowy MMS wymaga jawnego trybu. Zablokować nieprawidłowe adresy i alfanumerycznych nadawców wyjściowych.

Odbiór: tryb osobnych wiadomości nie ujawnia numerów pozostałych odbiorców; retry omija już wysłanych. Weryfikacja: 3 osoby i częściowy sukces, ten sam numer zapisany z prefiksem +48 i w formacie lokalnym, odmowa kontaktów i dodanie załącznika wymagające wyboru transportu.

**Uzupełnienie UX 29.10.2026:** Karta rozmowy CH065 musi rozpoznawać kontakt z kilkoma numerami, niezapisany numer i alfanumerycznego nadawcę. Akcja 'Zadzwoń' uruchamia wyłącznie systemowy dialer dla prawidłowego numeru, nie jest połączeniem VoIP. Zachować jawny wybór numeru/SIM.

**Odbiór rozszerzony:** Nie można po cichu zadzwonić na zły numer ani pokazać działającego telefonu przy alfanumerycznym nadawcy.


### CH030 Wdrożyć powiadomienia i wejścia systemowe

**P1 · Android platforma · 2–3 osobodni · zależności CH009 CH020 CH023 CH026 CH028**

Wykonanie: kanały wiadomości/błędy/harmonogram/HD, grupowanie, prywatność lockscreen i deep link. SENDTO/RESPOND_VIA_MESSAGE oraz bezpieczne udostępnianie. Szybka odpowiedź korzysta ze wspólnej kolejki i jawnie wybranej SIM. Walidować zewnętrzne argumenty i URI; nie wysyłać automatycznie po otwarciu intentu.

Odbiór: właściwy wątek, aktualne badge, brak tekstu na blokadzie przy ukrytych podglądach. Weryfikacja: brak zgody na powiadomienia, urządzenie zablokowane, cold start i wielokrotne powiadomienie.

**Uzupełnienie UX 29.10.2026:** Zaimplementować wyciszenie per rozmowa i kontrolę jego trwania, czytelne stany UI oraz prawidłowe mapowanie na Android NotificationChannels i skróty konwersacji. Wyciszenie nie jest blokowaniem ani kasowaniem wiadomości (CH065).

**Odbiór rozszerzony:** Nowa wiadomość w wyciszonej rozmowie aktualizuje historię i liczniki bez niepożądanego alertu; zmiana preferencji pozostaje skuteczna po restarcie.


**Implementacja 12.11.2026:** [CH030–CH034: zakres, testy i granice odbioru](CH030_CH034_Wdrozenie_i_weryfikacja.md). Szybka odpowiedź, wyciszenia i wejścia systemowe mają testy AVD; pełny odbiór urządzeń pozostaje otwarty.

### CH031 Przygotować cykl życia załączników

**P0 · Android media · 2–3 osobodni · zależności CH013 CH022 CH028**

Wykonanie: Photo Picker i aparat, walidacja MIME/rozmiaru, trwały URI grant lub kopia prywatna, miniatury, porządek i usuwanie. Liczyć sumę rozmiarów i SHA256 poza głównym wątkiem. Zapewnić FileProvider i czyszczenie cache bez naruszenia aktywnej operacji.

Odbiór: wybrane pliki nadal dostępne po restarcie; brak szerokiej zgody na galerię bez potrzeby. Weryfikacja: utracony dostęp do URI, brak miejsca, przerwane kopiowanie, duże wideo i anulowanie po wyborze.

**Uzupełnienie UX 29.10.2026:** Dodać interfejs danych dla galerii per rozmowa CH066: paginacja, rodzaj załącznika, licznik, identyfikator wiadomości źródłowej, odczyt stanu MMS/HD, bezpieczne otwieranie i udostępnianie URI. Zabezpieczyć odwołania do utraconych i usuniętych plików.

**Odbiór rozszerzony:** Galeria nie zawyża liczby załączników, nie ładuje jednocześnie wszystkich pełnych plików, nie ujawnia danych wrażliwych przez przypadkowy URI.

**Implementacja 12.11.2026:** [zakres i weryfikacja](CH030_CH034_Wdrozenie_i_weryfikacja.md). Trwałe pliki i port galerii są wdrożone; odbiór zasobów i docelowego urządzenia pozostaje otwarty.

### CH032 Zbudować transport MMS i grupy

**P0 · Android telefonia · 6–10 osobodni · zależności CH022 CH024 CH027 CH029 CH031**

Wykonanie: ocenić utrzymywany i zgodny licencyjnie adapter PDU, obsłużyć konstrukcję nagłówków/części, send/download, WAP_PUSH i zapis providera. Limity i kompresja dla operatora/SIM; tekst, zdjęcie, wspierane wideo i grupy. Rozróżnić APN, dane, timeout, przekroczony limit i częściowy błąd.

Odbiór: rzeczywisty MMS w obu kierunkach oraz grupa u uzgodnionych operatorów. Weryfikacja: fizyczne urządzenia z dwiema kartami SIM, roaming, brak danych i restart. Dokumentowany brak wsparcia nie może wyglądać jak sukces.

**Implementacja 12.11.2026:** [zakres i weryfikacja](CH030_CH034_Wdrozenie_i_weryfikacja.md). Transport, PDU i zapis historii sprawdzono kontrolowanymi testami; rzeczywiste MMS u operatorów i dual SIM/eSIM pozostają do odbioru. Nie oznaczać zadania jako w pełni odebrane.

### CH033 Zbudować oryginał i kopię bez lokalizacji

**P0 · Android media · 3–5 osobodni · zależności CH031**

Wykonanie: oryginał jako niezmienne bajty; osobne pochodne dla MMS i usunięcia lokalizacji. Zdefiniować formaty zdjęć/wideo i pełny zestaw wspieranych metadanych lokalizacyjnych. Dla formatów nieobsługiwanych jawny błąd albo bezpieczna konwersja z oznaczeniem. Nie nadpisywać pliku źródłowego.

Odbiór: oryginał ma identyczny hash; kopia nie zawiera lokalizacji w obsługiwanych polach i nie jest opisana jako oryginał. Weryfikacja: niezależny odczyt metadanych, orientacja i jakość po konwersji, nieobsługiwany format.

**Implementacja 12.11.2026:** [zakres i weryfikacja](CH030_CH034_Wdrozenie_i_weryfikacja.md). Oryginały oraz kopie JPEG bez metadanych są wdrożone dla JPEG/PNG/WebP; sanitacja filmów/GIF/HEIC jest jawnie nieobsługiwana.

### CH034 Wdrożyć szablony wiadomości

**P1 · Android · 1–2 osobodni · zależności CH020 CH028**

Wykonanie: lokalny katalog szablonów, dodanie/edycja/usunięcie, wybór w dodatkach. Wstawienie w kursor lub jasna reguła dopisania; zastąpienie niepustej treści wyłącznie po świadomej akcji. Zachować odbiorców, SIM i pliki. Brak synchronizacji z chmurą w tym zakresie.

Odbiór: akcja Szablony jest kompletna, stan pusty prowadzi do utworzenia. Weryfikacja: polskie znaki, długa treść, reset procesu i brak podwojenia po dwukrotnym kliknięciu.

**Implementacja 12.11.2026:** [zakres i weryfikacja](CH030_CH034_Wdrozenie_i_weryfikacja.md). Lokalne szablony, CRUD, zapis i dopisywanie treści są wdrożone.

## 5 Harmonogram wyszukiwanie i ustawienia

### CH035 Zaimplementować trwały harmonogram

**P0 · Android domena · 3–5 osobodni · zależności CH014 CH022 CH024 CH026 CH027 CH028**

Wykonanie: rekord instant/localDateTime/zone/polityka/SIM/version, scheduler i unikalny alarm. Sprawdzić specjalny dostęp do dokładnych alarmów. Sprawdzić, czy data jest przyszła i czy warunki przed zapisem są poprawne; rekord zapisany niezależnie od awarii rejestracji alarmu, z rekoncyliacją i widocznym błędem. WorkManager nie pełni roli dokładnego zegara.

Odbiór: po zapisaniu i restarcie procesu termin pozostaje, alarm wywołuje najwyżej jednego wykonawcę. Weryfikacja: brak dostępu, dwie pozycje o tej samej godzinie, zmiana terminu i nieistniejąca godzina podczas zmiany czasu DST.

**Implementacja 12.11.2026:** [zakres, weryfikacja i granice odbioru](CH035_CH039_Wdrozenie_i_weryfikacja.md). Kod wdrożony; odbiór urządzeń opisano w raporcie.

### CH036 Obsłużyć zaległości restart i zmianę warunków

**P0 · Android platforma · 3–4 osobodni · zależności CH035 CH030**

Wykonanie: rekoncyliacja po boot/aktualizacji/czasie/SIM/roli/uprawnieniach. Polityki pytaj i do 1 h, stan missed, powiadomienie i blokada automatycznego retry niepewnej próby. Uwzględnić brak odblokowania po restarcie, force stop i ograniczenia producentów. Odtwarzać tylko aktualną wersję alarmu.

Odbiór: wyłączony telefon nie jest opisywany jako źródło wysłanego SMS; po upływie okna wiadomość czeka na decyzję. Weryfikacja: spóźnienie 59, 60 i 61 min, Doze, utrata SIM, cofnięcie exact alarm i odzyskanie roli.

**Implementacja 12.11.2026:** [zakres, weryfikacja i granice odbioru](CH035_CH039_Wdrozenie_i_weryfikacja.md). Kod wdrożony; odbiór urządzeń opisano w raporcie.

### CH037 Podłączyć edycję i decyzje harmonogramu

**P1 · Android · 2–3 osobodni · zależności CH015 CH035 CH036 CH027**

Wykonanie: listy live, edit, cancel, delete, Wyślij teraz i Zmień termin. Wersjonować rekord; stary alarm i spóźniony callback nie zmieniają nowej wersji. Edycja nie narusza wysłanego snapshotu. Potwierdzić usunięcie zlecenia niezależne od rozmowy.

Odbiór: licznik „Czeka” jest poprawny, akcje nie powodują dwóch wysyłek. Weryfikacja: edycja tuż przy terminie, kilka otwartych widoków tego samego zadania i restart między zapisem a anulowaniem alarmu.

**Implementacja 12.11.2026:** [zakres, weryfikacja i granice odbioru](CH035_CH039_Wdrozenie_i_weryfikacja.md). Kod wdrożony; odbiór urządzeń opisano w raporcie.

### CH038 Wdrożyć lokalny indeks i wyszukiwanie

**P0 · Android dane · 3–4 osobodni · zależności CH016 CH022 CH023**

Wykonanie: indeks odtwarzalny dla wspieranych treści, normalizacja polskich liter w tym „ł”, casefold, tokeny prefiksowe i mapa offsetów. Przyrostowe aktualizacje, debounce 200 ms, anulowanie starego zapytania i paginacja. Indeksowanie z postępem poza głównym wątkiem; bez sieci.

Odbiór: paczk znajduje paczkę/PACZKA, łódź znajduje lodz po normalizacji; highlight trafia w oryginał. Weryfikacja: 50 tysięcy rekordów, usunięcie z providera, odtworzenie indeksu i zapytanie z emoji/interpunkcją.

**Uzupełnienie UX 29.10.2026:** Indeks i API zapytań muszą umożliwiać szukanie w kontekście konkretnej rozmowy (conversationId) z zachowaniem zakotwiczenia do messageId. Wyszukiwanie mediów dotyczy metadanych i załączonych treści, nie OCR ani semantyki AI.

**Odbiór rozszerzony:** Wynik z ekranu kontaktu otwiera właściwą wiadomość, także ze starszej strony historii; brak Internetu nie blokuje wyszukiwania lokalnego.


**Implementacja 12.11.2026:** [zakres, weryfikacja i granice odbioru](CH035_CH039_Wdrozenie_i_weryfikacja.md). Kod wdrożony; odbiór urządzeń opisano w raporcie.

### CH039 Dodać filtry i szybkie trafienia

**P1 · Android domena · 2–3 osobodni · zależności CH020 CH038**

Wykonanie: typ treści, nadawca i daty łączone operatorem AND; lokalne rozpoznawanie kodów i przesyłek. Zachować wiadomość źródłową, nadawcę i pewność wzorca. Kopiowanie precyzyjnej wartości; bez zgadywania kodu z dowolnej liczby. Powrót z rozmowy przywraca komplet filtrów.

Odbiór: cztery wyniki fixture i poprawne wyróżnienia, filtry rzeczywiście zawężają zbiór. Weryfikacja: numer telefonu podobny do kodu, fałszywy nadawca, brak trafień i przedział dat obejmujący zmianę czasu DST.

**Uzupełnienie UX 29.10.2026:** Udostępnić identyczny silnik filtrów globalnie i w rozmowie: osoba/wątek, data, tekst, typ pliku, z opcjonalnym SIM jeżeli dane istnieją. Wszystkie filtry są widoczne, usuwalne i łączone AND; CH066 może otworzyć wiadomość źródłową.

**Odbiór rozszerzony:** Filtr rozmowy nie pokazuje wyników z innych wątków; usunięcie filtra i Back przywracają stan bez fałszywych trafień.


**Implementacja 12.11.2026:** [zakres, weryfikacja i granice odbioru](CH035_CH039_Wdrozenie_i_weryfikacja.md). Kod wdrożony; odbiór urządzeń opisano w raporcie.

### CH040 Utrwalić wygląd i dostępne warianty

**P1 · Android UI · 3–4 osobodni · zależności CH018 CH020 CH007 CH008**

Wykonanie: DataStore dla wszystkich opcji, tokeny czterech kompozycji i dark/auto, compact i skali 1–7. Zastosować zmiany globalnie i w podglądzie; reset do źródłowego stanu. Ograniczenie przezroczystości i kontrast mają osobne tokeny. Nie blokować opcji dostępności płatnością.

Odbiór: wybory zachowane po restarcie, Auto reaguje na system, brak nieczytelnego tekstu. Weryfikacja: kombinacje trybów, fontScale 2.0 i porównanie domyślnego Szkła do wzorca po resecie.

**Uzupełnienie UX 29.10.2026:** Przewidzieć dziedziczenie globalnych tokenów przez motywy per rozmowa (CH068), oddzielny reset pojedynczego wątku, lokalne tło/kolor/dymki i kontrolę kontrastu. Opcje dostępności mają pierwszeństwo; nie udawać wspólnego tła po stronie odbiorcy SMS.

**Odbiór rozszerzony:** Zmiana motywu globalnego nie niszczy świadomych wyjątków; powrót do globalnego usuwa tylko nadpisania konkretnej rozmowy.


**Wynik CH040 (12.11.2026):** DataStore wszystkich opcji i nadpisań rozmów, palety 4 kompozycji light/dark/auto, skala i compact globalnie, reset per wątek oraz pierwszeństwo dostępności. Pomiar obrazu i font 2.0 w raporcie; G03/G09 i odbiór fizyczny pozostają otwarte. [Implementacja, dowody i granice odbioru](CH040_CH044_Wdrozenie_i_weryfikacja.md).


### CH041 Podłączyć ustawienia wysyłania

**P1 · Android · 2–3 osobodni · zależności CH019 CH020 CH024 CH025 CH028**

Wykonanie: ekran SIM i preferencje, opóźnienie 0/3/5/10 s według zatwierdzenia, licznik oraz domyślne warianty HD i pytanie o link. Podsumowania ustawień wynikają z DataStore. Zmiana ustawienia nie modyfikuje zlecenia już przekazanego ani istniejącego harmonogramu bez akcji użytkownika.

Odbiór: ustawienia działają w nowych szkicach, nie zmieniają treści samoczynnie. Weryfikacja: restart, brak SIM, wyłączenie licznika i zmiana delay podczas aktywnego odliczania.

**Wynik CH041 (12.11.2026):** Preferencje SIM, delay, licznik, wariant plików i pytanie o link podłączone do nowych szkiców. Zmiana preferencji nie przepisuje przyjętej kolejki. HD/linki pozostają niedostępne do etapu serwera. [Implementacja, dowody i granice odbioru](CH040_CH044_Wdrozenie_i_weryfikacja.md).


### CH042 Wdrożyć nieznanych i blokady

**P1 · Android dane · 2–3 osobodni · zależności CH020 CH022 CH023 CH030**

Wykonanie: oznaczać nieznanych nadawców bez klasyfikowania ich jako spam; lokalne reguły blokowania, lista i przywracanie. Zatrzymać powiadomienia zgodnie z regułą, zachować historię. Jeżeli G05 wybiera blokadę systemową, dodać osobny komunikat o skutkach dla połączeń i zweryfikować API.

Odbiór: reguła działa dla znormalizowanego numeru, cofnięcie blokady przywraca widoczność; brak cichego kasowania SMS. Weryfikacja: nowy SMS od zablokowanego, odmienne formaty numeru, kontakt dodany później i brak roli.

**Uzupełnienie UX 29.10.2026:** Akcje wycisz, blokuj, usuń i oznacz nieprzeczytane muszą mieć odrębne, prawdziwe znaczenie w CH065 i CH069. Po zablokowaniu kontaktu UI jednoznacznie pokazuje stan; dostarczone SMS nie znikają po cichu.

**Odbiór rozszerzony:** Blokada z karty kontaktu i ekranu ustawień prowadzi do tych samych danych, a cofnięcie działa bez utraty historii.


**Wynik CH042 (12.11.2026):** Trwałe lokalne blokady z normalizacją numeru, wspólne dane szczegółów i ustawień, brak powiadomień i przywracanie historii. Nieznany nadawca nie jest spamem; kontakt aktualizuje oznaczenie. [Implementacja, dowody i granice odbioru](CH040_CH044_Wdrozenie_i_weryfikacja.md).


### CH043 Wdrożyć kosz i odzyskiwanie

**P0 · Android dane · 3–5 osobodni · zależności CH020 CH022 CH023 CH038**

Wykonanie: TrashEntry ze zbiorem provider refs/HD refs, local tombstone, TTL wynoszący 30 dni, restore i potwierdzony purge. Nowa wiadomość w widocznym wątku nie należy do starego zakresu kosza. Sprzątanie tylko po upływie TTL i przy dostępie. W UI wyjaśnić lokalne ukrycie względem innych klientów.

Odbiór: restore bez duplikacji; purge nie usuwa nowych SMS z tego samego wątku. Weryfikacja: upływ 29, 30 i 31 dni, utrata roli, awaria w połowie usuwania, MMS parts i odbudowa indeksu.

**Wynik CH043 (12.11.2026):** Room v6, zakresy TrashEntry/TrashRef, 30 dni, przywracanie, potwierdzony purge i wznowienie po awarii. Nowy SMS i części MMS objęte próbami providera. WorkManager sprząta dopiero po TTL i z dostępem. [Implementacja, dowody i granice odbioru](CH040_CH044_Wdrozenie_i_weryfikacja.md).


### CH044 Zbudować prywatność i pomoc

**P1 · Android i produkt · 1–2 osobodni · zależności CH020 CH021 CH046**

Wykonanie: lokalna strona prywatności opisująca provider/indeks/HD/logi/backup i usuwanie danych. Pomoc z informacją o SMS/MMS/RCS, SIM i harmonogramie; docelowy kontakt dostarcza właściciel. Z obu miejsc dojście z S01/S10. Link zewnętrzny bez automatycznego udostępniania danych.

Odbiór: treść zgodna z faktyczną implementacją, brak wymyślonego adresu i nieprawdziwego E2EE. Weryfikacja: offline, duży font, działające linki i porównanie z zachowaniem HD oraz kosza.

**Wynik CH044 (12.11.2026):** Prywatność i pomoc offline z S01/S10, faktyczny opis providera, indeksu, plików, backupu, usuwania i nieaktywnego HD. Jawny link Google bez załączania danych; kontakt właściciela pozostaje do dostarczenia. [Implementacja, dowody i granice odbioru](CH040_CH044_Wdrozenie_i_weryfikacja.md).


### CH045 Wdrożyć jednorazowy zakup Pro

**P1 · Android i BE · 3–5 osobodni · zależności CH001 CH019 CH020 CH040 CH046**

Wykonanie: zatwierdzić katalog Pro i productId, pobierać lokalną cenę sklepu, paywall, purchase flow, acknowledgement/weryfikacja oraz restore. Obsłużyć oczekującą płatność, anulowanie, zwrot i brak sklepu. Uprawnienie do podstaw nie zależy od Pro; darmowe ustawienia dostępności pozostają dostępne.

Odbiór: w produkcyjnym UI nie ma placeholdera [CENA], zakup jednorazowy odtwarzany zgodnie z zatwierdzonym modelem. Weryfikacja: środowisko testowe sklepu, ponowna instalacja, błędny token, oczekiwanie i cofnięta transakcja.

**Wynik CH045 (12.11.2026):** Jednorazowy zakup, weryfikacja i potwierdzenie Play, przywracanie oraz roboczy katalog Bursztyn i Las. Wymagane Play Console, licencjonowane próby i akceptacja G08. [Implementacja i weryfikacja](CH045_CH049_Wdrozenie_i_weryfikacja.md).

## 6 Backend i Chatra HD

### CH046 Zaprojektować usługę i zasady danych HD

**P0 · BE i produkt i bezpieczeństwo · 3–4 osobodni · zależności CH001 CH005 CH020**

Wykonanie: wybrać stos/backend, region, storage, bazę, push, hosting linku i koszty. Zamknąć G06/G07: model tożsamości, discovery, limity, retencję, usuwanie i szyfrowanie. Model zagrożeń: enumeracja numerów, przejęty link, cudzy plik, podszycie się i nadużycia. Zapisać ADR i granice obietnic prywatności.

Odbiór: decyzje przed kodem, architektura obejmuje obsługę awarii i odzyskanie numeru. Weryfikacja: przegląd bezpieczeństwa i przepływu danych; brak założenia, że odczyt danych z SIM potwierdza numer.

**Wynik CH046 (12.11.2026):** ADR pilota określa stos, region, dane, odzyskanie numeru, limity, retencję, zagrożenia i koszt. Odbiór G06/G07 pozostaje otwarty. [Implementacja i weryfikacja](CH045_CH049_Wdrozenie_i_weryfikacja.md).

### CH047 Zdefiniować API i tożsamość instalacji

**P0 · BE i Android · 4–6 osobodni · zależności CH046**

Wykonanie: OpenAPI, modele i kody błędów, podpis/klucz instalacji, sesje i rotacja. Weryfikacja posiadania numeru z limitami prób, opt in odkrywalności i lookup tylko wybranego odbiorcy. Klient Keystore, wylogowanie/odpięcie i zmiana urządzenia. Do kontraktu dodać idempotency i paginację.

Odbiór: dwa urządzenia uzyskują zweryfikowane powiązania i odbiorca nie jest publicznie enumerowalny. Weryfikacja: niewłaściwy lub zużyty kod, replay, skradziona sesja, rotacja i odmowa HD bez utraty SMS.

**Wynik CH047 (12.11.2026):** OpenAPI, weryfikacja numeru, podpis Android Keystore, sesje, odpięcie i ograniczone wzajemne discovery. Testy dwóch kluczy na danych syntetycznych. [Implementacja i weryfikacja](CH045_CH049_Wdrozenie_i_weryfikacja.md).

### CH048 Zbudować magazyn i wznawialny upload

**P0 · BE · 4–6 osobodni · zależności CH046 CH047**

Wykonanie: manifest transferu, plik/chunki, offset, checksum, finalize i stany. Limit rozmiaru/ilości/quota przed uploadem, adresy URL o krótkim okresie ważności i autoryzacja właściciela. Przerwane sesje sprzątać po upływie TTL, obiekty gotowe tylko po weryfikacji. Atomowe powiązanie finalized z kopertą.

Odbiór: wznowienie nie powiela bajtów, błędny hash nie zostaje opublikowany. Weryfikacja: przerwanie w połowie, brak miejsca/quota, równoległe finalize, próba dostępu do innego transferu i opuszczony upload.

**Wynik CH048 (12.11.2026):** Porcje, checksum, wznawianie, bilety, limity i sprzątanie. Finalize idempotentnie zapisuje roboczą kopertę. [Implementacja i weryfikacja](CH045_CH049_Wdrozenie_i_weryfikacja.md).

### CH049 Zbudować klienta transferów HD

**P1 · Android sieć · 3–5 osobodni · zależności CH031 CH033 CH047 CH048**

Wykonanie: trwała kolejka, kontrola sieci, postęp, resume/backoff i anulowanie. Dobór mechanizmu długiego transferu zgodnego z docelowym Androidem. Jawne zużycie danych i możliwość wstrzymania. Błąd lub restart nie usuwa prywatnej kopii aktywnego pliku.

Odbiór: upload działa po zmianie ekranu i odtwarza stan po ponownym uruchomieniu procesu; nie pokazuje sukcesu przed finalize. Weryfikacja: zmiana sieci Wi-Fi na komórkową, brak sieci, błędy 401/413/429/5xx, anulowanie i 2 równoległe transfery.

**Wynik CH049 (12.11.2026):** Trwała kolejka WorkManager, ograniczenie sieci, postęp, pauza, resume, anulowanie, backoff oraz zachowanie lokalnych kopii. Odbiór z aktywną usługą pozostaje otwarty. [Implementacja i weryfikacja](CH045_CH049_Wdrozenie_i_weryfikacja.md).

### CH050 Zbudować routing kopert i statusy pobrania

**P0 · BE i Android · 4–6 osobodni · zależności CH047 CH048 CH049**

Wykonanie: outbox serwera, koperty wyłącznie dla uprawnionych instalacji, cursor sync i push jako sygnał. Lokalna deduplikacja, historia HD i receipt pobrania po integralnym zapisaniu. Odebranie push nie jest pobraniem; pobranie nie jest odczytem. Uzgodnić zachowanie przy zmianie numeru/klucza.

Odbiór: odbiorca offline dostaje zaległe koperty raz po synchronizacji; nadawca widzi prawdziwy etap. Weryfikacja: duplikat push, utrata cursora, dwie instalacje, powtórny receipt i próba cudzej skrzynki.

**Stan 13.11.2026:** Implementacja i lokalne testy gotowe; [dowody i pozostałe próby integracyjne](CH050_CH054_Wdrozenie_i_weryfikacja.md).

### CH051 Zbudować bezpieczny link i stronę odbioru

**P1 · BE i web i UX · 3–5 osobodni · zależności CH020 CH046 CH048 CH050**

Wykonanie: zgoda na link dla nieposiadającego Chatry, sekretny token, expiry/revoke i strona pobrania na Androidzie/iOS/przeglądarce. Podgląd liczby i rozmiaru plików dopiero przy ważnym dostępie. Brak indeksowania/wycieku przez nagłówek Referer/niepotrzebnych trackerów; przycisk pobierz i stan wygasły.

Odbiór: uprawniony odbiorca pobiera, odwołany link nie działa, sekret nie trafia do logów. Weryfikacja: incognito, iOS, zły/wygasły/odwołany token i wielokrotny klik. SMS z linkiem wymaga osobnego zatwierdzenia.

**Stan 13.11.2026:** Implementacja i lokalne testy gotowe; [dowody i pozostałe próby integracyjne](CH050_CH054_Wdrozenie_i_weryfikacja.md).

### CH052 Wdrożyć retencję usuwanie i ochronę HD

**P0 · BE i bezpieczeństwo · 3–5 osobodni · zależności CH046 CH047 CH048 CH050 CH051**

Wykonanie: egzekwować TTL, revoke, usunięcie obiektu lub powiązania, politykę backupu i audyt bez danych. Limity lookup/upload/download i alerty nadużyć. Jeśli G07 wybiera E2EE, uzupełnić uzgodniony protokół i ponownie oszacować zadanie przed implementacją; TLS nie zastępuje E2EE. Sekrety w zarządzanym magazynie, rotacja i separacja środowisk.

Odbiór: polityka serwera zgodna z UI, brak dostępu po cofnięciu i brak wycieków poprzez publiczny magazyn plików. Weryfikacja: negatywne testy obiektowe, granice TTL, odtworzenie backupu i skan logów.

**Stan 13.11.2026:** Implementacja i lokalne testy gotowe; [dowody i pozostałe próby integracyjne](CH050_CH054_Wdrozenie_i_weryfikacja.md).

### CH053 Połączyć HD z rozmową i dodatkami

**P1 · Android · 3–4 osobodni · zależności CH012 CH013 CH033 CH041 CH049 CH050 CH051 CH052**

Wykonanie: discovery, oryginał, kopia bez GPS i MMS, dynamiczne CTA, postęp, otrzymanie koperty i karta HD. Rozpoznać nieznany stan dostępności HD bez udawania braku aplikacji. Wyświetlić źródło transportu; odbiór HD nie zamienia się w SMS. Fallback/link tylko po zgodzie.

Odbiór: scenariusz 4 zdjęcia / 38,2 MB ze wzorca działa z rzeczywistymi plikami i potwierdzonym hashem. Weryfikacja: odbiorca Chatra, odbiorca bez Chatry, niedostępny backend, restart i częściowe pobranie.

**Stan 13.11.2026:** Implementacja i lokalne testy gotowe; [dowody i pozostałe próby integracyjne](CH050_CH054_Wdrozenie_i_weryfikacja.md).

## 7 Testy odbiór i dystrybucja

### CH054 Zweryfikować domenę i bazę

**P0 · Android i QA · 3–4 osobodni · zależności CH027 CH028 CH032 CH035 CH038 CH043**

Wykonanie: testy istotnych reguł kodowania, deduplikacji, wersji zleceń, multi recipient, indeksu i kosza. Testy DAO i migracji na danych z różnych wersji; kontrolowany zegar i awarie. Nie ograniczać się do testu, który powtarza implementację.

Odbiór: scenariusze duplikacji i utraty danych mają niezależne asercje i przechodzą w CI. Weryfikacja: wstrzykiwanie awarii, callbacki poza kolejnością, częściowe sprzątanie i zachowanie po odtworzeniu.

**Uzupełnienie UX 29.10.2026:** Do testów domeny dodać ustawienia per conversationId, relacje galerii do źródła oraz reguły mark-read/unread, przypięć i wyciszenia. Nie dopuszczać zduplikowanego indeksu mediów po rekoncyliacji.

**Odbiór rozszerzony:** Testy migracji i awarii potwierdzają zachowanie ustawień i prawidłowe liczniki galerii.

**Stan 13.11.2026:** Implementacja i lokalne testy gotowe; [dowody i pozostałe próby integracyjne](CH050_CH054_Wdrozenie_i_weryfikacja.md).

### CH055 Odebrać zgodność wizualną wszystkich ekranów

**P0 · QA i UX i Android · 3–5 osobodni · zależności CH010 CH011 CH012 CH013 CH014 CH015 CH016 CH017 CH018 CH019 CH040 CH065 CH066 CH067 CH068 CH069 CH070**

Wykonanie: obrazy S01–S10 w warunkach CH002, overlay 50 procent, heatmapa i pomiary obszarów ROI. Sprawdzić typografię, linebreak, stroke ikon, box sizing, gradienty, szkło i dock. Osobny przegląd renderowania GPU na urządzeniu. Zarejestrować i poprawić odchyłki; golden nie aktualizuje się automatycznie.

Odbiór: każdy ekran spełnia kontrakt 1 do 1, wyjątki tylko jawnie uzgodnione. Weryfikacja: podpisany raport z 10 par obrazów i brakiem nieprzejrzanych masek; osobny test 360/430 dp i dużego fontu.

**Uzupełnienie UX 29.10.2026:** Poza obowiązkowym porównaniem 10 wzorców dodać wizualny przegląd nowych podwidoków CH065–CH070 jako osobnych ekranów, bez narzucania im fałszywego kryterium 1:1 wobec nieistniejącego wzorca.

**Odbiór rozszerzony:** Galeria, karta kontaktu, lokalny wygląd i gesty mają zatwierdzone referencje UX; wzorce S01–S10 nie zostały potajemnie zmienione.


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

**P1 · Android i BE i QA · 2–4 osobodni · zależności CH008 CH022 CH032 CH038 CH053 CH066 CH067**

Wykonanie: uruchomienie aplikacji, 50 tys. wiadomości, scroll, otwarcie arkusza, pamięć miniatur i transfer HD. Zmierzyć cele P95 na ustalonym urządzeniu, profilować koszt rejestrowania warstw i rozmycia i indeksowania. Backend monitorować bez prywatnych danych. Poprawić potwierdzone wąskie gardła.

Odbiór: raport liczbowy i spełnione lub jawnie ponownie uzgodnione cele, bez rezygnacji ze szkła 1 do 1. Weryfikacja: identyczny scenariusz przed/po, brak zwiększenia wycieków pamięci i degradacji po dłuższym użyciu.

**Uzupełnienie UX 29.10.2026:** Profilować galerię przy 67 i 1000 załączników, kolaże wielu zdjęć, skok do wiadomości źródłowej, gesture actions i aktualizację tła. Pamiętać o oddzielnych bitmapach miniatur i oryginałów.

**Odbiór rozszerzony:** Nie ma wycieków bitmap ani blokowania UI przy szybkim przełączaniu filtrów i otwieraniu galerii.


### CH060 Odebrać dostępność i układy adaptacyjne

**P1 · QA i UX i Android · 2–3 osobodni · zależności CH020 CH040 CH055 CH065 CH066 CH068 CH069 CH070**

Wykonanie: TalkBack, fokus dialogów, poprawne role i stan, nazwy przycisków i cele 48 dp bez nakładania. Systemowa skala tekstu 1.3/2.0, suwak aplikacji, redukcja przezroczystości i kontrast. Test 360/390/430 dp, IME, cutout i obie metody nawigacji systemu.

Odbiór: każda akcja osiągalna, brak informacji tylko kolorem i brak uciętej treści. Weryfikacja: ręczny przepływ wysłania/zaplanowania/wyszukania wyłącznie z czytnikiem; regresja wizualna domyślnych ustawień.

**Uzupełnienie UX 29.10.2026:** Ująć CH065–CH070: TalkBack dla gestów i przycisków, obsługę galerii klawiaturą i czytnikiem, skok do pierwszej nieprzeczytanej, skalę czcionki i kontrast zdjęcia jako tła.

**Odbiór rozszerzony:** Każda akcja gestowa ma dostępny odpowiednik w menu; przy fontScale 2.0 wszystkie funkcje pozostają osiągalne.


### CH061 Zweryfikować migracje backup i odzyskanie

**P0 · Android i QA · 2–3 osobodni · zależności CH022 CH028 CH035 CH043 CH047 CH050**

Wykonanie: polityka backupu prywatnej bazy/indeksu/kluczy, test aktualizacji i przerwane migracje. Nie odtwarzać nieświadomie starych harmonogramów ani sekretów Keystore z nową instalacją. Rekoncyliacja providera i ponowna weryfikacja HD po utracie powiązania. Opisać ograniczenia reinstalacji.

Odbiór: historia providera nienaruszona, brak drugiej wysyłki po przywróceniu. Weryfikacja: aktualizacja instalacji zawierającej szkice, harmonogram i kosz, czysta instalacja i restore testowy.

### CH062 Przygotować CI konfigurację i instrukcje utrzymania

**P1 · Android i BE i DevOps · 2–3 osobodni · zależności CH004 CH047 CH054**

Wykonanie: lint/build/test, obrazy regresji, migracje bazy BE, staging, secrets i podpisywanie. Instrukcja budowy lokalnej, wdrożenia, wycofania zgodnego z migracjami, rotacji kluczy i alarmów produkcyjnych. Oddzielić środowiska i testowych odbiorców; fixture i sekrety nie trafiają do wydania produkcyjnego.

Odbiór: nowa osoba z zespołu odtwarza build i środowisko testowe z instrukcji. Weryfikacja: pipeline z czystego commitu, kontrola paczek i próbne wycofanie bez utraty danych.

### CH063 Przeprowadzić pełny odbiór produktu

**P0 · produkt i QA · 2–3 osobodni · zależności CH034 CH039 CH041 CH042 CH043 CH044 CH045 CH055 CH056 CH057 CH058 CH059 CH060 CH061 CH062 CH065 CH066 CH067 CH068 CH069 CH070**

Wykonanie: przejść wszystkie widoczne akcje dziesięciu plansz i uzupełnione podwidoki. Sprawdzić SMS i funkcje lokalne bez konta oraz dostępu do internetu, MMS przy wymaganej transmisji operatora, a HD i zakup Pro przy dostępnej sieci. Osobno zweryfikować role, harmonogram, wyszukiwanie i przywracanie zakupu. Zestawić wymaganie→zadanie→dowód, zamknąć błędy krytyczne i otwarte decyzje.

Odbiór: pełna matryca bez martwych przycisków i nieprawdziwych statusów; build gotowy do wydania. Weryfikacja: raport odbioru, jawna lista ograniczeń operatorów i brak odstępstw 1 do 1 bez decyzji.

**Uzupełnienie UX 29.10.2026:** Pełny odbiór wydania obejmuje CH065–CH070; przetestować szczegóły, galerię, kolaż, wygląd per rozmowa, gesty i stany nieprzeczytania. CH071–CH072 mają status opcjonalny/badawczy i nie mogą ukrywać braku funkcji wymaganej w wydaniu.

**Odbiór rozszerzony:** Raport zawiera mapę UX01–UX06→CH065–CH070→testy; nie ma fikcyjnego RCS, iMessage ani niewidocznych martwych akcji.


### CH064 Przygotować dystrybucję i przekazanie

**P0 · właściciel produktu i Android i DevOps · 2–3 osobodni · zależności CH063**

Wykonanie: ponownie sprawdzić wymagania Google Play/target SDK/uprawnień SMS/Billing i dane prywatności. Przygotować podpisany AAB/APK w uzgodnionym kanale, opis sklepu, obrazy, deklaracje i politykę. Przekazać repozytoria, ADR, OpenAPI, konfiguracje bez sekretów i raporty. Przygotowanie do wydania nie oznacza automatycznej publikacji.

Odbiór: paczka instaluje się z kanału testowego, podstawowe przepływy działają w buildzie release; właściciel ma instrukcję wypuszczenia i wsparcia. Weryfikacja: smoke test wydania oraz zgodność treści sklepu z realnym zakresem bez RCS/iMessage.

## 7a Rozszerzenia UX wynikające z Textry i iOS (CH065–CH072)

Poniższe taski uzupełniają CH001–CH064. CH065–CH070 są wymagane w pełnym wydaniu i muszą przejść CH063; CH071–CH072 to opcjonalne rozszerzenie i badanie poza bramką wydania. Szczegółowy kontekst: [porównanie Textra/iOS](../UX_TEXTRA_IOS_WNIOSKI_2026-10-29.md). Nie zastępować nimi wzorców wizualnych S01–S10.

### CH065 Wdrożyć kartę kontaktu i szczegóły rozmowy

**P1 · UX i Android · 4–6 osobodni · zależności CH012 CH020 CH029 CH030 CH042**

Wykonanie: na podstawie CH020 stworzyć osobny podwidok otwierany z nagłówka S03. Pokazać prawidłowy kontakt, numer lub członków grupy, akcje Zadzwoń przez systemowy dialer, Szukaj w rozmowie, Wycisz i Więcej; przypięcie, dostęp do galerii CH066, ustawień wyglądu CH068, SIM, blokowania i usuwania. Rozróżnić wyciszenie, blokadę i usunięcie. Nie zakładać wsparcia iMessage/RCS i nie zmieniać planszy S03.

Odbiór: wszystkie akcje działają lub jawnie wyjaśniają niedostępność; po powrocie zachowana pozycja historii i szkic. Weryfikacja: jeden/wiele numerów, alfanumeryczny nadawca, brak dialera/roli, grupa i TalkBack.

### CH066 Zbudować galerię mediów jednej rozmowy

**P1 · Android UI i dane · 4–6 osobodni · zależności CH020 CH022 CH031 CH038 CH065**

Wykonanie: podwidok Wspólne z zakładkami zdjęcia, filmy, linki, dokumenty i pozostałe; paginacja miniaturek, realny licznik elementów, zachowanie po obrocie, otwarcie pliku, zapisz i Sharesheet bez wycieku URI. Każdy element zachowuje MessageRef i akcję Pokaż w rozmowie. Ująć załączniki MMS i Chatra HD z ich realnym stanem.

Odbiór: 67 i 1000 plików wyświetla się kompletnie bez OOM i bez zgubionych stron. Weryfikacja: usunięty plik, brak URI, załącznik HD oczekujący na pobranie, wyszukiwanie źródłowej wiadomości.

### CH067 Renderować kolaże i stosy załączników w wiadomości

**P1 · Android UI · 2–4 osobodni · zależności CH012 CH031 CH020**

Wykonanie: połączyć zestaw mediów należących do jednej wiadomości w zwięzły kolaż/stos, z licznikiem pozostałych zdjęć i pełnoekranowym podglądem; zachować kolejność plików i obsłużyć stany wysyłki, brak miniatury i wielkie obrazy. Nie grupować osobnych wiadomości według podobnej godziny.

Odbiór: 1, 2, 3 i co najmniej 4 zdjęcia są prezentowane czytelnie, każde otwiera prawidłowy oryginał lub stan błędu. Weryfikacja: szybkie przewijanie, długie wiadomości, MMS i HD, dostępność i pamięć.

### CH068 Dodać indywidualny wygląd i ustawienia konwersacji

**P1 · UX i Android UI · 3–5 osobodni · zależności CH020 CH040 CH065**

Wykonanie: per conversationId udostępnić nadpisania globalnego stylu dla tła, koloru akcentu, dymków i powiadomień. Zapewnić podgląd na żywo, wskazanie co pochodzi z motywu globalnego i reset wyłącznie tej rozmowy. Przestrzegać kontrastu, redukcji szkła i ustawień dostępności. W SMS/MMS wygląd jest wyłącznie lokalny; odbiorca nie widzi wspólnego tła.

Odbiór: kilka rozmów ma różne ustawienia, a zmiana globalna nie usuwa wyjątków; reset przywraca dziedziczenie. Weryfikacja: restart, ciemny/jasny, zdjęcie o małym kontraście, TalkBack, brak zmian wzorca S09.

### CH069 Wdrożyć gesty i menu szybkich działań skrzynki

**P1 · UX i Android · 2–4 osobodni · zależności CH011 CH020 CH023 CH030 CH042**

Wykonanie: długie przytrzymanie i opcjonalne gesty przesunięcia na liście rozmów do przypięcia, wyciszania, przeczytane/nieprzeczytane i zarządzania rozmową. Maksymalnie jedna szybka akcja na kierunek; mapowanie do zatwierdzenia po teście; działania destrukcyjne nie mogą wykonać się przypadkiem. Każda akcja ma alternatywę menu i czytnika ekranu; cofnięcie operacji odwracalnych.

Odbiór: gesty nie konfliktują z nawigacją systemową, nie usuwają przypadkowo wątku; opcje menu i gest prowadzą do identycznego stanu. Weryfikacja: przełożenie przypięć, długi swipe, TalkBack, mały ekran i restart.

### CH070 Ujednolicić nieprzeczytane i skok do wiadomości

**P1 · Android dane i UI · 2–3 osobodni · zależności CH012 CH022 CH023 CH038 CH020**

Wykonanie: implementować oznacz jako nieprzeczytane, oznacz wszystkie jako przeczytane i nawigację do pierwszej nieprzeczytanej wiadomości. Zdefiniować spójność ręcznego stanu z providerem, badge i powiadomieniami, w tym realny odczyt tylko po pokazaniu treści. Potwierdzenie masowego oznaczenia przy dużej liczbie rozmów do decyzji UX.

Odbiór: otwarcie starszej części historii nie oznacza z automatu niewidocznych nowych wiadomości; restart, napływ SMS i aktualizacja danych zachowują logiczne stany. Weryfikacja: wiele nieprzeczytanych, duże stronicowanie, głęboki link, powrót do skrzynki.

### CH071 Opcjonalnie dodać eksport i skrót rozmowy na pulpicie

**P2 · Android i UX i bezpieczeństwo · 3–5 osobodni · zależności CH020 CH022 CH031 CH044 CH065**

Wykonanie (po osobnej decyzji produktowej): udostępnianie zaznaczonych wiadomości i załączników przez Android Sharesheet, eksport rozmowy ze świadomym określeniem zakresu i metadanych oraz shortcut do conversationId na ekranie głównym. Nie osadzać treści SMS i numerów w identyfikatorach/etykietach bez zgody. Obsłużyć brak rozmowy i zgodę użytkownika.

Odbiór: eksport nie ujawnia przypadkowo innych wątków, zakres jest czytelny, a skrót nie prowadzi do niewłaściwej osoby. Weryfikacja: przekazanie e-mailem, cofnięte uprawnienia, usunięty wątek, restart i dostępność. Nie blokuje pierwszego pełnego wydania bez zatwierdzenia.

### CH072 Zbadać TelephonyMessagingKit i alternatywny transport Androida

**P2 · architekt mobilny i badania techniczne · 3–5 osobodni · zależności CH001**

Wykonanie (spike, poza MVP): zweryfikować aktualne wymagania Apple TelephonyMessagingKit dla domyślnej aplikacji SMS/MMS/RCS w UE, entitlement, operatorów PL, historię i ograniczenia względem iMessage. Oddzielnie zbadać AlternativeMessageTransportService Android API 37 jako mechanizm alternatywnego transportu bez zakładania automatycznej interoperacyjności RCS. Przygotować macierz ryzyk, koszty, bramki prototypu i decyzję go/no-go.

Odbiór: raport z oficjalnych API i testów na odpowiednich urządzeniach, bez nieudokumentowanej obietnicy dostępu do iMessage lub RCS Google. Weryfikacja: ścieżka uprawnień, operatorzy, wersje OS, zakres historii i transportów. Nie blokuje pierwszego wydania Androida.

## 8 Planowanie nakładu i ryzyk

Nakłady nie uwzględniają czasu oczekiwania na decyzje, uzyskanie dostępu do konsoli sklepu, weryfikację dostawców, dostarczenie fizycznych kart SIM i formalny przegląd publikacji. Implementacja E2EE, wsparcie Androida poniżej 31 lub nowe reguły Pro wymagają ponownego oszacowania po zamknięciu decyzji. Nie wolno dopisywać ich do istniejącego nakładu bez oceny.

Najbardziej wrażliwe zadania to CH003 szkło, CH032 MMS, CH036 harmonogram, CH043 kosz oraz CH046–CH052 backend i bezpieczeństwo HD. Te zadania powinny dostarczać wynik techniczny wcześnie. Nie planować całego zespołu wyłącznie według liczby ekranów; domyślny klient SMS zawiera istotną pracę poza UI.

Historyczny nakład bazowych 64 zadań to **155–240 osobodni**. Nowe CH065–CH070 dodają orientacyjnie **17–28 osobodni** do zakresu pełnego wydania (łącznie **172–268**), a CH071–CH072 kolejne **6–10 osobodni** w backlogu opcjonalnym (łącznie **178–278** dla wszystkich 72 pozycji). Ponieważ rozszerzono także opisy istniejących tasków, **szacunki nie są pełną ponowną wyceną**; należy je zrewidować przy planowaniu. Nakłady nie oznaczają dni kalendarzowych.

| Etap | Liczba zadań | Osobodni |
| --- | --- | --- |
| M0 Ustalenie podstaw | 6 | 10–17 |
| M1 Wierny interfejs | 14 | 29–45 |
| M2 Lokalny klient | 14 | 34–53 |
| M3 Funkcje lokalne | 11 | 27–41 |
| M3X Rozszerzenia UX do wydania | 6 | 17–28 |
| M4 Pełne HD | 8 | 27–41 |
| M5 Odbiór i wydanie | 11 | 28–43 |
| M6 Po wydaniu / badania | 2 | 6–10 |
| **Pełne wydanie** | **70** | **172–268** |
| **Cały backlog (łącznie z opcjami)** | **72** | **178–278** |

Aktualny plik `Chatra_Plan_zadan.json` w repozytorium jest eksportem 72 zadań z tymi samymi ID, zależnościami i zakresami osobodni. Istniejące pliki Word i ZIP pozostają historycznym eksportem wersji 1.0, dopóki nie zostaną osobno wygenerowane na nowo. Zależności są acykliczne. Szacowanie kalendarza powinno uwzględnić dostępność osób odpowiedzialnych za Androida, backend, UX i QA oraz to, że równoległe zadania mają różne wymagane role.
