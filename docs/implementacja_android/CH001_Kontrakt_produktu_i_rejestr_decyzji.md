# CH001 — Kontrakt produktu i rejestr decyzji

**Stan:** wykonano rejestr, rozpisano kontrakt i powiązania; decyzje oznaczone jako otwarte pozostają do zamknięcia w swoich zadaniach.  
**Data:** 29.10.2026  
**Źródła w repozytorium:** [Specyfikacja Android](Chatra_Dokumentacja_Android.md), [Backlog v1.1](Chatra_Backlog_Android.md), [Plan JSON](Chatra_Plan_zadan.json), [Audyt](../AUDYT.md), [Porównanie Textra/iOS](../UX_TEXTRA_IOS_WNIOSKI_2026-10-29.md).  
**Źródło maszynowe:** [CH001_Rejestr_decyzji.json](CH001_Rejestr_decyzji.json).

## 1. Zakres, który naprawdę przyjmujemy

**Chatra to Androidowy klient SMS/MMS z projektem Liquid Glass i planowanym własnym transferem załączników Chatra HD przez internet.** Nie jest klonem iMessage ani działającym dziś zamiennikiem RCS. Opracowanie tego kontraktu nie dowodzi istnienia kodu, APK, działającego operatora lub backendu.

- **Wydanie pełne:** wszystkie wymagane funkcje istniejącego backlogu CH001–CH070, łącznie z CH065–CH070, rzeczywistą telefonią i kompletnym backendem HD. Etap M3X opisuje dodatkowe UX, ale nie zmienia układu dziesięciu wzorców.
- **Opcjonalne:** CH071 (eksport i skróty) oraz CH072 (badania iPhone UE oraz API 37 Android); poza bramką pierwszego wydania.
- **Poza zakresem Android MVP i pełnego wydania:** iMessage, interoperacyjny RCS bez umowy/integracji i testów, rozmowy głosowe/wideo, „AI chatbot” w skrzynce.
- **Nienaruszalne:** 10 ekranów referencyjnych S01–S10. Nowe podwidoki są odrębnym projektem. Brak realnego statusu nie może być maskowany pokazowym komunikatem.
- **Dane i uprawnienia:** lokalna historia SMS/MMS, dostępność roli SMS, wybór SIM; HD jest oddzielnie aktywowany i ma osobne wymagania prywatności.

**Hierarchia decyzji:** jawna instrukcja właściciela produktu > zamknięta i udokumentowana decyzja Gxx > zatwierdzony wzorzec S01–S10 > niniejszy wariant roboczy > eksperyment techniczny. W razie konfliktu zadanie ma zostać oznaczone jako zablokowane decyzją, a nie kończone na zgadywaniu.

## 2. Rejestr G01–G10

**Statusy:** „bazowa decyzja robocza” = zgodne z obecną specyfikacją i nadające się do prototypowania, **nie finalna zgoda**. „Otwarta bramka” = wymaga wyniku badania/akceptacji przed wskazanym zadaniem. Właściciel jest określony **rolą**, nie wymyśloną osobą.

| ID i obszar | Wariant roboczy | Właściciel decyzji | Zamknięcie / weryfikacja w tasku | Stan |
| --- | --- | --- | --- | --- |
| **G01** Typografia i renderer referencyjny | Inter 400–800 na Androidzie jako wariant roboczy; porównanie oryginalnego HTML na referencyjnym Androidzie. | UX + Android UI + QA | `CH002`, `CH007` | otwarta bramka techniczna |
| **G02** Zakres Androida / minSdk i SDK publikacji | minSdk 31; Android jako pierwsza platforma. targetSdk/compileSdk i toolchain wybrać zgodnie z bieżącą polityką sklepu, nie utożsamiać ich z minSdk. | Produkt + lider Android | `CH003`, `CH004` | bazowa decyzja robocza |
| **G03** Szkło, Compose i technologia renderowania | Jetpack Compose z własnymi warstwami szkła i tła; pomiar S02 i S04. | Lider Android UI + QA wydajności | `CH003`, `CH008` | otwarta bramka techniczna |
| **G04** Harmonogram i obsługa opóźnienia | Trwały Instant + LocalDateTime/ZoneId; domyślnie pytaj po spóźnieniu, świadoma opcja wysłania w ciągu 1 h; bez obietnicy wysłania z wyłączonego telefonu. | Produkt + Android platforma | `CH035`, `CH036`, `CH037` | bazowa decyzja robocza |
| **G05** Kosz, retencja i blokowanie | Kosz 30 dni przez lokalne tombstone konkretnych wiadomości; blokowanie lokalne z ukryciem i wyciszeniem powiadomień, bez kasowania historii i bez blokady połączeń. | Produkt + Android dane + prywatność | `CH042`, `CH043`, `CH061` | bazowa decyzja robocza |
| **G06** Tożsamość i aktywacja Chatra HD | Opt-in instalacji z kluczem w Android Keystore i weryfikacją numeru; bez tradycyjnego konta z hasłem. | Backend + bezpieczeństwo + produkt | `CH046`, `CH047`, `CH050` | otwarta bramka bezpieczenstwa |
| **G07** Bezpieczeństwo i limity Chatra HD | TLS, dostęp autoryzowany, tokenowe linki z expiry/revoke; oryginał potwierdzony SHA256; limity, TTL, storage, retencja backupów i koszt bez wymyślania liczb; E2EE nie jest obiecane. | Backend + bezpieczeństwo + produkt | `CH046`, `CH048`, `CH050`, `CH051`, `CH052` | otwarta bramka bezpieczenstwa |
| **G08** Chatra Pro i płatności | Model jednorazowego zakupu w Google Play; SMS/MMS, lokalne wyszukiwanie, harmonogram, dostępność i bezpieczeństwo bez paywalla. Dodatkowe kompozycje i ewentualne reguły organizacji jako kandydaci Pro. | Właściciel produktu + Android Billing | `CH040`, `CH045` | otwarta bramka produktowa |
| **G09** Brakujące ekrany i rozszerzenia UX | Dziesięć plansz S01–S10 bez zmian. Brakujące podwidoki w stylu Liquid Glass; CH065–CH070 wymagane do pełnego wydania; CH071 opcjonalne. | UX + właściciel produktu | `CH020`, `CH065`, `CH066`, `CH067`, `CH068`, `CH069`, `CH070` | bazowa decyzja robocza |
| **G10** Dystrybucja, operatorzy i wsparcie | Android/Polska/Google Play jako pierwszy kanał. Wstępna macierz operatorów: Orange Polska, Play, Plus i T-Mobile Polska; osobno 2 SIM, roaming, grupowy MMS, wideo, połączenia z iPhone. | Właściciel produktu + QA telefonii + Release | `CH056`, `CH062`, `CH064` | otwarta bramka wydawnicza |

### Warianty alternatywne, warunki zamknięcia i dowody

**G01 — Typografia i renderer referencyjny.** Alternatywy: Faktycznie użyty font z referencji; licencjonowany font o zgodnych metrykach. **Dowód zamknięcia:** Raport renderera i fontów, golden screenshot, pomiar metryk i licencji, zatwierdzenie przez UX/QA.

**G02 — Zakres Androida / minSdk i SDK publikacji.** Alternatywy: Niższy minSdk po ocenie kosztu szkła i testów kompatybilności. **Dowód zamknięcia:** ADR kompatybilności, zbudowane warianty, test na minimalnym urządzeniu; zatwierdzony minSdk/targetSdk.

**G03 — Szkło, Compose i technologia renderowania.** Alternatywy: Hybryda albo lokalny zaufany WebView, jeśli wynik POC spełni kontrakt bezpieczeństwa i wierności; nie przyjmować gorszej zgodności jako domyślnej. **Dowód zamknięcia:** Porównanie wzorców S02/S04, kontrola kosztów GPU, scroll, insets i brak dostępu treści z sieci do mostu natywnego.

**G04 — Harmonogram i obsługa opóźnienia.** Alternatywy: Inny model strefy lub polityka zaległości po testach i akceptacji produktu. **Dowód zamknięcia:** Test restartu, Doze, force-stop, DST, utraty SIM, godziny 59/60/61 i anulowania bez podwójnej wysyłki.

**G05 — Kosz, retencja i blokowanie.** Alternatywy: Blokowanie systemowe tylko po osobnej ocenie skutków; inna retencja tylko po jawnej decyzji produktu i zmianie specyfikacji. **Dowód zamknięcia:** Test odzyskania, nowej wiadomości od ukrytego nadawcy, utraty roli, purge po >=30 dniach i zakresu operacji trwałego usunięcia.

**G06 — Tożsamość i aktywacja Chatra HD.** Alternatywy: Inny schemat identyfikacji po analizie nadużyć, przenoszenia numeru i odzyskiwania dostępu. **Dowód zamknięcia:** ADR tożsamości, weryfikacja numeru, brak enumeracji, uprawnienia do transferu i odporność na ponowne próby.

**G07 — Bezpieczeństwo i limity Chatra HD.** Alternatywy: E2EE wyłącznie po osobnym zatwierdzeniu protokołu, dystrybucji kluczy i obsługi linków; zmiana dostawcy/storage po analizie. **Dowód zamknięcia:** Polityka limity/TTL/koszt/retencja, threat model, testy dostępu, replay, tokenów, wygaśnięcia i cofnięcia.

**G08 — Chatra Pro i płatności.** Alternatywy: Inny katalog dodatków po uzgodnieniu; cenę i productId ustalić w CH045; brak zaszytej ceny i nieprawdziwych płatności w demo. **Dowód zamknięcia:** Zatwierdzony katalog darmowe/Pro, identyfikator produktu, oferta testowa sklepu, zwrot/przywracanie i testy rozliczeń.

**G09 — Brakujące ekrany i rozszerzenia UX.** Alternatywy: Każde odstępstwo od wizualnego wzorca S01–S10 wymaga osobnej akceptacji; nowych widoków nie podawać jako istniejących w HTML. **Dowód zamknięcia:** Katalog nowych ekranów, mapa UX01–UX06, testy treści, stanów i dostępności; podpisane makiety przed implementacją.

**G10 — Dystrybucja, operatorzy i wsparcie.** Alternatywy: Inny kanał dystrybucji lub węższa lista oficjalnie wspieranych konfiguracji wyłącznie po udokumentowanych testach. **Dowód zamknięcia:** Macierz wyników na fizycznych telefonach, wersje OS, operatorów/SIM, uprawnień Google Play i realne testy release; bez deklaracji wsparcia nieprzetestowanego operatora.

**Rejestr nie udaje akceptacji:** finalna zmiana minSdk, polityki Pro, operatorów i wideo/MMS wymaga podpisanej decyzji właściciela produktu; wynik urządzeniowy i kryteria platformowe nie mogą być zastąpione opisem.

## 3. Kontrakt transportów i funkcji

| ID i funkcja | Status | Główne zadania | Kontrakt |
| --- | --- | --- | --- |
| C01: SMS/MMS — kanał podstawowy | wymagany | `CH021`, `CH022`, `CH023`, `CH026`, `CH032` | Domyślna aplikacja SMS, rzeczywisty odbiór/wysyłka z jawnym SIM, bez RCS na Androidzie. |
| C02: Wiadomość do wielu i grupowy MMS | wymagany | `CH017`, `CH029`, `CH032` | Wiele odbiorców = domyślnie osobne SMS. Grupowy MMS to świadome przełączenie z widocznymi numerami uczestników. Nie podszywać się pod iMessage/RCS. |
| C03: Zdjęcia i filmy | wymagany z ograniczeniami operatora | `CH031`, `CH032`, `CH033`, `CH056` | Zdjęcia i obsługiwane wideo jako MMS tylko w limitach i formatach operatora/urządzenia, bez obietnicy jakości HD. Dla dużych wideo użytkownik wybiera inny transport lub dostaje zrozumiały błąd, nigdy cichą zmianę na płatny MMS. |
| C04: Chatra HD | wymagany etap pelny z backendem | `CH046`, `CH047`, `CH048`, `CH049`, `CH050`, `CH051`, `CH052`, `CH053` | Osobny backend i świadomy opt-in; oryginał, wariant bez lokalizacji, link za zgodą. Etap demonstracyjny i lokalny nie są pełnym HD. |
| C05: RCS / iMessage / VoIP / wideo-rozmowy | poza wydaniem android | `CH072` | Brak iMessage, brak wdrożonej interoperacyjnej sieci RCS na Androidzie, brak rozmów głosowych i wideo. CH072 to badanie, nie wymaganie wydania. |
| C06: Wygląd i zarządzanie rozmową | wymagany etap pelny | `CH065`, `CH066`, `CH067`, `CH068`, `CH069`, `CH070` | Podwidok kontaktu, galeria, kolaże, lokalna personalizacja, gesty i nieprzeczytane. Żaden z tych elementów nie zmienia protokołu SMS/MMS. |
| C07: Eksport i skrót rozmowy | opcjonalny po zatwierdzeniu | `CH071` | Sharesheet/eksport i shortcut nie blokują pełnego wydania bez decyzji o zakresie, prywatności i kosztach. |
| C08: Kosz | wymagany | `CH043`, `CH061` | 30 dni lokalnego odzyskiwania; skutki dla widoczności w innej aplikacji SMS wyjaśnione. |
| C09: Chatra Pro | wymagany z otwartym katalogiem | `CH045` | Jednorazowy zakup; podstawowe SMS/MMS, wyszukiwanie, harmonogram i dostępność nie są płatne. Szczegółowy katalog, identyfikatory i cena do bramki G08. |

### Ustalenia, które mogłyby zostać łatwo pomylone

- **Grupy:** osobne SMS do wielu numerów są domyślne; MMS grupowy wymaga jawnego wyboru i ostrzeżenia, że uczestnicy widzą swoje numery. Reakcje iMessage, usuwanie u odbiorcy czy wspólne tła nie są funkcją SMS.
- **Wideo/MMS:** jeżeli operator nie obsługuje wysyłki pliku w danym formacie/limicie, nie udajemy sukcesu. Kompresja lub transkodowanie tylko jako jawny wariant; oryginału nie niszczymy. Wynik na każdym polskim operatorze musi zostać zmierzony.
- **Wysyłka SMS:** opóźnienie, anulowanie i harmonogram są lokalne. Wyłączony telefon, niewłaściwa karta SIM, brak roli SMS i zasięgu mają odrębne stany.
- **HD i link:** to infrastruktura serwerowa i osobne zgody, z restrykcjami TTL, revocation i kosztów. Odbiór załącznika nie oznacza przeczytania SMS.
- **Pro:** jednorazowa płatność i brak reklam w rozmowach; nie ustawiamy ceny i nie ukrywamy podstawowego klienta za paywallem.

## 4. Decyzje wdrożeniowe, które wymagają dodatkowej bramki

| Obszar | Wariant startowy lub ograniczenie | Osoba odpowiedzialna (rola) | Zamknięcie |
| --- | --- | --- | --- |
| Android API i dystrybucja | minSdk 31 i Google Play to zatwierdzone w specyfikacji **założenia robocze**, nie dowód pełnej kompatybilności ani opublikowanej aplikacji. | Produkt / Android / Release | G02 i G10 / CH003, CH004, CH056, CH064 |
| Operatorzy w Polsce | Orange Polska, Play, Plus, T-Mobile Polska są **proponowaną minimalną macierzą testów**; brak fizycznych testów i brak deklaracji, że MMS/RCS działa w każdej konfiguracji. | QA telefonii | G10 / CH056 |
| Grupowy MMS | W pełnym wydaniu wymagane rozróżnienie „osobne SMS” vs „grupowy MMS” oraz świadome ujawnienie numerów uczestników. Powodzenie zależy od operatora, APN, urządzenia i poprawnych PDU. | Android telefonia | C02 i G10 / CH029, CH032, CH056 |
| Wideo MMS | Obsługa tylko dla formatów/rozmiarów potwierdzonych testami; kompresja nie może nadpisywać oryginału. Bez gwarancji jakości. Alternatywa: wybrany transfer HD z backendem. | Android media + QA | C03 i G10 / CH031, CH032, CH056 |
| Kosz i blokady | 30 dni kosza, lokalne tombstone, lokalne blokowanie i odzyskiwanie; usuwanie fizyczne dopiero po 30 dniach i przy roli SMS. | Produkt + Android dane | G05 / CH042, CH043, CH061 |
| Darmowe vs Pro | SMS/MMS, lokalne wyszukiwanie, harmonogram, podstawowe funkcje oraz dostępność pozostają bezpłatne; Pro jednorazowe z dodatkowymi motywami i regułami do ustalenia. Nie ma zatwierdzonej ceny. | Produkt + Billing | G08 / CH040, CH045 |
| Chatra HD | Wymaga niezależnego serwera i pełnego przepływu, nie jest samą zmianą kodeka MMS. Bez zweryfikowanego backendu nie oznaczać funkcji jako wykonanej. | Backend + bezpieczeństwo | G06/G07 / CH046–CH053 |
| iOS | Osobny tor badawczy TelephonyMessagingKit w UE (CH072). Nie obiecywać iMessage ani pełnej zgodności Apple Messages. | Produkt + architekt mobilny | CH072 / Raport API/operatorów i testy na iPhonie |
| Rozszerzenia UX | CH065–CH070 są wymagane do pełnego wydania, CH071 eksport/skrót opcjonalne; nie zmieniać referencji S01–S10. | UX + produkt | G09 / CH020, CH055, CH063 |

### Minimalna macierz testowa operatorów — do formalnego zatwierdzenia w G10

| Konfiguracja | Operator / urządzenie | Testy | Odpowiedzialność |
| --- | --- | --- | --- |
| Operator A | Orange Polska, fizyczny Android na wspieranym OS | SMS, multipart, Unicode, MMS obraz+wideo w ramach limitu, raporty, błędy | CH056 QA |
| Operator B | Play, fizyczny Android na wspieranym OS | SMS, MMS, APN, grupowy MMS, załączniki, statusy | CH056 QA |
| Operator C | Plus, fizyczny Android na wspieranym OS | SMS, MMS, przeciążenie/kolejka, kody, fallback z błędu | CH056 QA |
| Operator D | T-Mobile Polska, fizyczny Android na wspieranym OS | SMS, MMS, załączniki, timeout, retry i brak roli | CH056 QA |
| Scenariusz przekrojowy | Dual SIM / różni operatorzy, odbiorca na iPhonie, roaming PL/EU | Właściwy subscriptionId, SMS i grupowy MMS w obie strony, granice protokołu | CH024/CH032/CH056 QA |

To **lista testów**, a nie lista już potwierdzonej zgodności. Dane konkretnych telefonów, wersje Androida, karty i wyniki trzeba dopisać w CH056. Jeśli zakres ulegnie zawężeniu, musi być ujawniony użytkownikowi i zatwierdzony w G10.

## 5. Dziesięć ekranów: matryca 10 × 10 i mapa do funkcji

**Legenda:** W = element widoczny albo kontraktowo umieszczony w danym ekranie; N = wymagane nowe wejście/podwidok powiązane z ekranem; — = nie jest funkcją tego ekranu. Nie oznacza to, że wszystkie pola oznaczone „W” są już zaimplementowane.

| Ekran | Rola | Skrzynka | Rozmowa | Media/HD | Harmonogram | Wyszukiwanie | Grupy/SIM | Wygląd | Ustawienia | Rozszerzenia |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| S01 | W | — | — | — | — | — | — | — | W | — |
| S02 | — | W | — | — | — | W | — | — | — | N |
| S03 | — | — | W | W | — | — | — | — | — | N |
| S04 | — | — | — | W | W | — | — | — | — | — |
| S05 | — | — | — | — | W | — | — | — | — | — |
| S06 | — | — | — | — | W | — | — | — | — | — |
| S07 | — | — | — | — | — | W | — | — | — | — |
| S08 | — | — | — | — | — | — | W | — | — | — |
| S09 | — | — | — | — | — | — | — | W | — | N |
| S10 | — | — | — | — | — | — | — | — | W | N |

| Plansza | Zakres i znaczenie | Powiązane taski | Rozszerzenia UX |
| --- | --- | --- | --- |
| **S01 Start / rola SMS** | Rola SMS, ograniczenia protokołów, prywatność, pomoc | `CH010`, `CH021`, `CH044` | brak |
| **S02 Skrzynka** | Przypięte, nieprzeczytane, menu, filtrowanie, szkice | `CH011`, `CH022`, `CH023` | CH069 gesty + CH070 odczyt |
| **S03 Rozmowa** | SMS/MMS, dymki, SIM, status, kompozytor, karta HD | `CH012`, `CH026`, `CH027`, `CH028` | CH065 szczegóły; CH067 kolaż; CH070 skok |
| **S04 Dodatki/HD** | Zdjęcie, aparat, harmonogram, szablon, HD/kopia/MMS | `CH013`, `CH031`, `CH033`, `CH049` | CH066 galeria jako nowy podwidok |
| **S05 Planowanie** | Termin, SIM, spóźnienia, walidacja | `CH014`, `CH035`, `CH036` | brak |
| **S06 Zaplanowane** | Kolejka, opóźnione, edycja i status | `CH015`, `CH036`, `CH037` | brak |
| **S07 Wyszukiwanie** | Indeks lokalny, filtry, kody, skok do wyniku | `CH016`, `CH038`, `CH039` | CH065 wejście z kartą rozmowy; CH066 wynik załącznika |
| **S08 Nowa wiadomość** | Odbiorcy, osobne SMS/grupowe MMS, SIM | `CH017`, `CH029`, `CH032` | brak |
| **S09 Wygląd** | Szkło/Mgła/Grafit/Kontrast, ciemny, kontrast, reset | `CH018`, `CH040` | CH068 nadpisania per rozmowa |
| **S10 Ustawienia** | SIM, opóźnienie, HD, nieznani, blokady, kosz, Pro | `CH019`, `CH041`, `CH042`, `CH043`, `CH045` | CH071 tylko gdy osobno zatwierdzone |

**Uwaga o matrycy:** S07 ma wyszukiwanie globalne, a wejście z S03 do wyszukiwania konkretnej rozmowy przechodzi przez nowy CH065. S04 udostępnia wybór załącznika, zaś pełna galeria otrzymanych mediów jest nowym ekranem CH066. S08 posiada wejścia grupowe, ale to jeszcze nie implementacja RCS. S09 i S10 nie otrzymują cichych zmian layoutu.

## 6. Brakujące podwidoki/polityki, nieobecne w źródłowym HTML

- **CH020:** Menu skrzynki i akcji, szczegóły rozmowy, SIM, galeria, wybór mediów, szablony, HD, blokady, kosz, prywatność, pomoc, paywall i stany empty/error/permission.
- **CH065:** Karta kontaktu i grupy: numer, dialer, wyciszenie, personalizacja, załączniki, blokowanie; bez VoIP.
- **CH066:** Galeria per rozmowa: zdjęcia/wideo/linki/dokumenty, realne liczniki, skok do wiadomości źródłowej.
- **CH067:** Kolaże/stosy załączników jednej wiadomości, pełny podgląd, statusy.
- **CH068:** Nadpisania motywu tylko lokalnie dla jednej rozmowy, reset do globalnego, czytelność.
- **CH069:** Gesty i menu skrzynki z bezpiecznymi akcjami, undo i alternatywą dla czytnika ekranu.
- **CH070:** Nieprzeczytane, masowe oznaczanie i skok do pierwszej zaległej wiadomości.
- **CH071:** Opcjonalny eksport/udostępnienie/shortcut; prywatność i zgody (po zatwierdzeniu).
- **CH072:** Wyłącznie badanie TelephonyMessagingKit i Android API 37; nie jest obietnicą RCS/iMessage.

**Polityki brakujące do formalnego zamknięcia:** konkretny katalog Pro i cena (G08), limit filmu i maksymalna wielkość MMS w macierzy operatorów (G10), TTL/limity/cennik infrastruktury HD (G07), wymagania App Review/Google Play i polityki sklepu (G10), identyfikacja HD (G06), warunki blokady (G05), zgodność typografii i szkła (G01/G03).

## 7. Definition of Done etapu demonstracyjnego i pełnego wydania

### DEMO

**Definicja:** Dziesięć ekranów S01–S10 Liquid Glass na danych demonstracyjnych, nawigacja i stany UI; nie wysyła prawdziwych SMS/MMS, nie udaje backendu HD, nie uruchamia realnego zakupu. Zgodność obrazów w CH002/CH055.

**Kluczowe zadania:** CH002, CH006, CH007, CH008, CH009, CH010, CH011, CH012, CH013, CH014, CH015, CH016, CH017, CH018, CH019, CH020.

**Ograniczenie:** Nie oznacza aplikacji SMS ani gotowości publikacji.

### LOKALNY

**Definicja:** Na fizycznym urządzeniu: rola SMS, historia systemowa, wysyłanie i odbieranie SMS/MMS z prawidłową kartą SIM, harmonogram, wyszukiwanie, powiadomienia, błędy, grupy i kosz; brak wymogu działającego HD.

**Kluczowe zadania:** CH021, CH022, CH023, CH024, CH025, CH026, CH027, CH028, CH029, CH030, CH031, CH032, CH033, CH034, CH035, CH036, CH037, CH038, CH039, CH042, CH043.

**Ograniczenie:** Nie oznacza pełnego zakresu, nie uprawnia do claimów HD.

### PELNE_WYDANIE

**Definicja:** Cały wymagany zakres CH001–CH070, z potwierdzonym backendem HD, Pro, sześcioma rozszerzeniami UX CH065–CH070 oraz odbiorem CH054–CH064. Testy prawdziwej telefonii i Google Play wymagane.

**Kluczowe zadania:** CH045, CH046, CH047, CH048, CH049, CH050, CH051, CH052, CH053, CH054, CH055, CH056, CH057, CH058, CH059, CH060, CH061, CH062, CH063, CH064, CH065, CH066, CH067, CH068, CH069, CH070.

**Ograniczenie:** Dopóki warunki nie są spełnione, nie nazywać aplikacji gotową produkcyjnie.

### PO_WYDANIU

**Definicja:** CH071 eksport/skrót oraz CH072 badanie iOS/RCS wyłącznie po decyzji i nowych testach.

**Kluczowe zadania:** CH071, CH072.

**Ograniczenie:** Nie są bramką do pierwszego pełnego wydania.

### Zasada odbioru CH001

- [x] Rejestr G01–G10 zawiera wariant roboczy, rolę właściciela, zadania bramkujące i dowód finalnego zatwierdzenia.
- [x] minSdk, dystrybucja i wstępna macierz polskich operatorów są jawnie zapisane, wraz ze statusem wymagającym weryfikacji.
- [x] Granice grup MMS, wideo MMS, kosza, Pro i HD są opisane bez przypisywania im niepotwierdzonej funkcjonalności.
- [x] Demo, pełne wydanie i dodatkowe badania mają odrębne definicje.
- [x] Matryca 10 × 10 i mapa planów S01–S10 zawiera taski oraz CH065–CH072.
- [x] Brakujące ekrany i otwarte polityki wskazane osobno; nie twierdzimy, że występują w referencji HTML.
- [ ] **Do zamknięcia w kolejnych taskach:** fizyczne testy operatorów i ustalenia po spike'ach G01/G02/G03/G06/G07/G08/G10. Te bramki pozostają otwarte i nie blokują istnienia kompletnego rejestru CH001.

**Ograniczenie rezultatów CH001:** To zadanie ustanawia kontekst i dokumentację dla zespołu. Nie uruchamia implementacji aplikacji, testów na urządzeniach ani publicznej dystrybucji. Ostateczne „potwierdzenie” otwartych decyzji musi być dokonane przez wskazane role i udokumentowane w odpowiednich zadaniach.
