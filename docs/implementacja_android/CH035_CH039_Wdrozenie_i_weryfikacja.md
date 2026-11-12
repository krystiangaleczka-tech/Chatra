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

Wyniki końcowe są zapisane w pliku verification.json. Test 50 tys. używa rzeczywistego Room/FTS i syntetycznego źródła; oddzielny test adaptera używa rzeczywistego Telephony Provider z dodaniem, edycją i usunięciem własnego SMS w AVD. Testy wyścigów harmonogramu używają kontrolowanego zegara i transportu. Jawne testy natywne są uruchamiane osobno od podstawowej suity. Wstępne przebiegi wykryły błędne cytowanie prefiksu FTS4, traktowanie AND jako słowa przy standardowej składni SQLite oraz escapowanie ukośnika MIME w JSON. Regresje obejmują wielowyrazowy prefiks, metadane obrazu i dokument text/plain. FTS używa niejawnego AND zgodnego z obiema konfiguracjami SQLite.

Odbiór urządzeń pozostaje otwarty: fizyczne dual SIM/eSIM, OEM, API 31, zabezpieczony locked boot, rzeczywisty force stop i odzyskanie roli oraz pełny TalkBack i wizualny odbiór G03/G09. Wspólny transport MMS nadal wymaga operatorów zgodnie z raportem CH030–CH034. Wyniki AVD nie stanowią potwierdzenia tych przypadków.

Kontrakty platformy: [dokładne alarmy](https://developer.android.com/develop/background-work/services/alarms), [specjalny dostęp w Androidzie 14](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms), [Room FTS4](https://developer.android.com/reference/androidx/room/Fts4), [składnia SQLite FTS3/4](https://www.sqlite.org/fts3.html).
