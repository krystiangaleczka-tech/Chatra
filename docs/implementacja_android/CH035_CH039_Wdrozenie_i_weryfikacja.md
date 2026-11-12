# CH035–CH039 — harmonogram i wyszukiwanie lokalne

Implementacja z 12.11.2026 na gałęzi `android/ch035-ch039`, na bazie `android/ch030-ch034` (`0a1b519`). PR jest zależny od CH030–CH034; nie zmienia otwartego odbioru MMS u operatorów. Wyniki i sumy artefaktów: [CH035_CH039_Android/verification.json](CH035_CH039_Android/verification.json).

## CH035–CH036

Room v5 przechowuje termin Instant, lokalną datę i strefę, politykę spóźnienia, wersję, losowy token i powiązanie z operacją zawierającą SIM oraz snapshot. Zlecenie zostaje zapisane i szkic zamknięty w jednej transakcji. Nieudana rejestracja alarmu pozostawia zlecenie z widocznym błędem. Zapis wymaga terminu co najmniej minutę w przyszłości i zgodności daty lokalnej z Instant; luka DST jest odrzucana przez repozytorium, a UI proponuje istniejącą godzinę. Przy nakładaniu godzin UI jawnie wybiera pierwsze wystąpienie.

Dokładny zegar to AlarmManager RTC_WAKEUP z `setExactAndAllowWhileIdle`. Każda wersja ma osobny immutable PendingIntent. WorkManager jest wykonawcą wybudzonym przez alarm, z ponowną kontrolą aktualnej wersji, terminu, roli, dostępu i SIM przed granicą wysyłania. Opóźnienie wykonawcy nie uprawnia do dowolnie późnej wysyłki. Duplikaty i stare alarmy nie tworzą drugiej próby. Stary worker nie zmienia nowego terminu na zaległy: kolejka sprawdza stan i wersję przed autoryzacją oraz transakcyjnym zajęciem operacji. Brak exact alarm nie blokuje ręcznej akcji Wyślij teraz.

Odtwarzanie obejmuje start procesu, boot/odblokowanie, aktualizację pakietu, zmianę czasu/strefy, SIM, roli i dostępu oraz okresową naprawę. Przed pierwszym odblokowaniem odbiornik zapisuje wyłącznie flagę w pamięci device protected; nie otwiera historii. Po restarcie polityka Zapytaj nie wysyła zaległości nawet przy niewielkim spóźnieniu; również zapisany worker, który ruszy przed rekoncyliacją boot, sprawdza numer uruchomienia telefonu. Świadoma opcja do 1 h dopuszcza granicę 60 min, ale nie 61 min. Zwykły alarm w tym samym uruchomieniu telefonu korzysta z istniejącej tolerancji 2 min. Poza oknem zlecenie wymaga decyzji. Utrata SIM nie wybiera karty zastępczej. Stan niepewny transportu nie wywołuje automatycznego retry.

Powiadomienie o decyzji ma neutralną treść i otwiera Zaplanowane. Odmowa powiadomień nie usuwa stanu w aplikacji. Android force stop blokuje działanie odbiorników i alarmów do ponownego uruchomienia aplikacji; nie obiecujemy wysyłania przy wyłączonym lub zatrzymanym telefonie. Stały Instant pozostaje ten sam po zmianie strefy.

## CH037

Lista live rozróżnia oczekujące zlecenia, decyzje i ostatnią udaną wysyłkę. Edycja pozwala zmienić treść, SIM, termin i politykę przed rozpoczęciem wysyłania. Zapis używa oczekiwanej wersji; drugi otwarty widok i poprzedni alarm tracą ważność. Snapshot zachowuje metadane i identyfikatory załączników. Zmiana odbiorców i plików wymaga nowej wiadomości w kompozytorze. Rozpoczętej lub wysłanej operacji nie można edytować.

Wyślij teraz przechodzi przez wspólną kolejkę. Zmień termin oraz usunięcie używają tego samego wersjonowania. Potwierdzenie usunięcia dotyczy zlecenia i zachowuje rozmowę. Usunięcie niepewnej pozycji zachowuje dowody transportu w historii i możliwość przyjęcia późnego sukcesu. Pewne błędy mogą mieć świadome ponowienie; niepewny wynik prowadzi do rozmowy bez automatycznej drugiej próby.

## CH038–CH039

Lokalny Room/FTS4 zawiera odtwarzalne dokumenty, metadane plików i hashe. SMS i MMS mają rozłączne identyfikatory. Aktualizowane są zmienione dokumenty, a usunięte są usuwane dopiero po udanym pełnym odczycie źródła. Skan referencji i aktualizacje działają na IO, partiami po 200, z postępem. Obserwatory historii i kontaktów wyzwalają aktualizację. Przy każdej emisji wyników ponownie czytana jest oryginalna wiadomość; stary cache nie pokazuje usuniętych lub zmienionych treści. Utrata roli/uprawnienia blokuje wyniki. Odbudowa naprawia również FTS przy niezmienionych hashach dokumentów. Nie przepisuje wtedy niezmienionych dokumentów: oznacza ich generację partiami i odświeża sam FTS. Usuwa to koszt 50 tys. zbędnych upsertów, ujawniony przez limit czasu w teście odbudowy pod obciążeniem.

Normalizacja obejmuje polskie litery, ł, wielkość liter, znaki łączone i ß. Zapytania używają prefiksów tokenów połączonych AND i związanych parametrów SQL. Emoji/interpunkcja mają dosłowne wyszukiwanie. Highlight wskazuje offsety UTF-16 w oryginale. Debounce wynosi 200 ms; nowe zapytanie anuluje poprzednie. Strony mają stabilny kursor czasu i ID, bez duplikowania wyników.

Ten sam silnik obsługuje wyszukiwanie globalne i konkretną rozmowę. Filtry tekstu, wątku, rzeczywistego nadawcy, dat, rodzaju pliku i SIM łączą się AND. Daty obejmują wybrany dzień końcowy i respektują DST (dni 23/25 h). Filtry są widoczne, usuwalne i zapisane w SavedStateHandle. Powrót z wyniku zachowuje zapytanie i załadowane strony. Źródło otwiera konkretną wiadomość; ekran rozmowy doładowuje starsze strony do kotwicy.

Kody wymagają kontekstu kod/PIN/OTP, przesyłki odpowiedniego opisu. Dowolna liczba ani numer telefonu nie stają się kodem. Kopiowana jest dokładna wartość z oryginału, również separator. Szybkie trafienie zachowuje źródło, nadawcę i pewność wzorca. UI informuje, że nadawca nie jest zweryfikowany. Wyszukiwanie mediów obejmuje metadane, bez OCR i sieci.

## Walidacja i granice odbioru

**87/87 testów JVM oraz ostatecznie 20/20 unikalnych przypadków Androida dla końcowej implementacji** (14 regresji nowych modułów, 3 bazy, 3 osobne próby natywne). Końcowy przebieg modułów uzyskał 13/14; pomiar 50 tys. przekroczył limit 300 s, a następnie przeszedł osobno na świeżym emulatorze po kompilacji pakietów i z zablokowanym usypianiem hosta, bez zmiany aplikacji ani asercji. Pierwsze indeksowanie trwało **58 766 ms**, a cały izolowany test z zapytaniami, paginacją i odbudową FTS **91,337 s**. Historyczny błąd i pozytywne ponowienie pozostają w oddzielnych logach. Wcześniejszy pełny zestaw 58 testów Androida przeszedł przed ostatnimi kontrolami wersji/boot i optymalizacją odbudowy; nie jest przedstawiany jako nowy pełny przebieg. Oba APK są zbudowane. Lint: zero błędów, 17 ostrzeżeń demo i 47 full. Sumy APK, fingerprint źródeł, wyniki per przypadek i logi są w pliku verification.json. Test 50 tys. używa rzeczywistego Room/FTS i syntetycznego źródła; oddzielny test adaptera używa rzeczywistego Telephony Provider z dodaniem, edycją i usunięciem własnego SMS w AVD. Testy wyścigów harmonogramu używają kontrolowanego zegara i transportu. Jawne testy natywne są uruchamiane osobno od podstawowej suity. Dokładny alarm w wymuszonym stanie IDLE wywołał jedną próbę i otrzymał SENT od emulowanego radia dla wirtualnego portu 5558. Po restarcie odzyskano ten sam przyszły termin z nowym numerem boot, bez próby wysyłania, po czym zlecenie anulowano. Pierwsza inspekcja rozpoczęta przed dostarczeniem BOOT nie przeszła; Android pominął to zdarzenie w trakcie instrumentacji. Powtórzenie nastąpiło po normalnym wejściu do aplikacji przed restartem i odczekaniu 120 s po boot, bez jawnego otwierania kontenera po restarcie przed inspekcją. Wstępne przebiegi wykryły błędne cytowanie prefiksu FTS4, traktowanie AND jako słowa przy standardowej składni SQLite oraz escapowanie ukośnika MIME w JSON. Regresje obejmują wielowyrazowy prefiks, metadane obrazu i dokument text/plain. FTS używa niejawnego AND zgodnego z obiema konfiguracjami SQLite.

Zrzuty [Zaplanowane](CH035_CH039_Android/scheduled.png) i [wyszukiwarki](CH035_CH039_Android/search.png) pochodzą z produkcyjnego wariantu full. Ręcznie sprawdzono też otwarcie źródła wyniku i zachowanie zapytania po Back. Po tych próbach zmieniono wyłącznie limit testu indeksu z 120 s na 300 s; produkcyjny APK pozostał ten sam.

## Odbiór na urządzeniu fizycznym i API 31 (12.11.2026)

Uruchomiono ten sam produkcyjny APK full (`c124e1f5…eb6b`, identyczny z zainstalowanym) na Samsungu SM-S948B z Androidem 17 (API 37, `S948BXXU5BZJA`). Chatra była domyślną aplikacją SMS z prawdziwą historią. Aktywna była jedna karta: eSIM w slocie 1 (sub 1), slot fizyczny pusty. Nowa klasa `DeviceAcceptanceTest` uruchamia każdy przypadek wyłącznie na jawne polecenie i nigdy na AVD. Odbiorcę podaje operator argumentem `recipient`, więc numer nie trafia do kodu ani do logów. Wyszukiwanie używa indeksu w pamięci i czyta provider tylko do odczytu. Logi: [device/](CH035_CH039_Android/device/).

- **Prawdziwa historia providera:** historia gotowa po 6,5 s. Pierwsze indeksowanie 6 159 dokumentów zajęło 6,0 s, ponowne bez zmian 5,4 s. Pierwsza strona 40 wyników przychodziła w 82–159 ms, następna w 76–150 ms, bez powtórzeń między stronami. Filtry Kody, Przesyłki i Załączniki odpowiadały w 108–307 ms. Logowane są wyłącznie liczby, bez treści.
- **50 tys. dokumentów na sprzęcie:** syntetyczny test Room/FTS: pierwsze indeksowanie 29,9 s, cały test z paginacją i odbudową FTS 59,1 s.
- **Prawdziwy zaplanowany SMS (CH035):** termin +70 s, polityka Zapytaj, eSIM. Wynik: dokładnie 1 próba, SENT 37 ms po terminie. Test wymagał przyznania Chatrze specjalnego dostępu „Alarmy i przypomnienia” (`SCHEDULE_EXACT_ALARM`), który na Androidzie 14+ jest domyślnie wyłączony. Bez niego przypadek był pomijany, a nie oblewany; teraz brak dostępu lub roli kończy test błędem.
- **Rzeczywisty force stop (CH036):** termin +90 s, `am force-stop` przed terminem. Po 2 min od terminu pakiet nadal był w stanie `stopped=true`, zlecenie w stanie SCHEDULED i bez prób. Po ponownym uruchomieniu odzyskanie przestawiło zlecenie na MISSED_NEEDS_DECISION. Przez kolejne 5 s nie było żadnej próby, po czym zlecenie usunięto.
- **Restart z PIN-em (CH036):** termin +75 s, restart przez adb, odblokowanie PIN-em przez użytkownika o 18:26:51, termin 18:27:03. Telefon wstał przed terminem, więc alarm odtworzony dla nowego numeru boot (21) wysłał wiadomość raz, 498 ms po terminie (DELIVERED). Instant pozostał bez zmian.
- **Przebieg w trakcie:** pierwsza próba z terminem dokładnie +60 s została poprawnie odrzucona przez walidację („co najmniej minutę w przyszłości”). Skrypt mimo to zrestartował telefon; od tej pory restart następuje tylko po zapisanym zleceniu. Pierwsza inspekcja zakwalifikowała termin jako „przegapiony”, bo porównywała go z chwilą inspekcji zamiast ze startem bootu. Poprawiono klasyfikację testu i powtórzono inspekcję na tych samych danych; aplikacji nie zmieniano.

**API 31** (AVD `google_apis_playstore` arm64, Android 12 `SE1A.220203.002.A1`), logi w [api31/](CH035_CH039_Android/api31/):

- Regresje harmonogramu i wyszukiwania: 13/14 przy 2 GB RAM. Ten sam pomiar 50 tys. przekroczył limit 300 s, tak jak na wcześniejszym AVD.
- Baza: 3/3.
- Przy 2 GB watchdog zabił system_server (zablokowany StorageManagerService, zagłodzenie I/O hosta) w trakcie próby Doze. To zdarzenie emulatora, nie aplikacji.
- Po restarcie AVD z 4 GB RAM i 4 rdzeniami: izolowany pomiar 50 tys. przeszedł (pierwsze indeksowanie 184,3 s, cały test 333,6 s, po prekompilacji `speed`), a dokładny alarm w wymuszonym deep IDLE dał jedną próbę i SENT na wirtualny port 5558.
- Restart z odzyskaniem na API 31 nie był powtarzany.

**Nadal otwarte:**

- fizyczne dual SIM (druga aktywna karta i wybór/utrata SIM);
- stan przed pierwszym odblokowaniem po restarcie: Samsung zgłosił zakończenie bootu dopiero przy odblokowaniu, więc okno LOCKED_BOOT nie zostało zarejestrowane;
- termin przypadający na czas wyłączenia telefonu na sprzęcie (pokryty testem AVD i JVM);
- odzyskanie roli SMS;
- inne OEM;
- TalkBack oraz wizualny odbiór G03/G09;
- transport MMS u operatorów zgodnie z raportem CH030–CH034.

Kontrakty platformy: [dokładne alarmy](https://developer.android.com/develop/background-work/services/alarms), [specjalny dostęp w Androidzie 14](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms), [Room FTS4](https://developer.android.com/reference/androidx/room/Fts4), [składnia SQLite FTS3/4](https://www.sqlite.org/fts3.html).
