# CH050–CH054 · Dostarczenie HD i weryfikacja bazy

13.11.2026, gałąź `android/ch045-ch054`. Razem z [CH045–CH049](CH045_CH049_Wdrozenie_i_weryfikacja.md) obejmuje dziesięć zadań po CH044. Poprzednia część została zapisana w commitach `6cc6655`–`270c0cb`; ten zakres dodaje dostarczenie, odbiór, link, retencję i regresję.

## Zmiana zachowania

CH050: finalize tworzy jedną kopertę i trwałe zdarzenia dla obu uprawnionych instalacji. Klient synchronizuje strony i kursor atomowo w Room. Powtórzony push jest tylko sygnałem; nie tworzy wiadomości ani statusu pobrania. Odbiorca wybiera pobranie przez sieć nietaryfową lub świadomie dopuszcza taryfową. WorkManager zachowuje częściowe pliki, sprawdza pełny hash, importuje rzeczywisty format i potwierdza dopiero komplet zapisanych plików. Odpięcie lub zmiana właściciela wyłącza stare pobrania i zachowuje tylko dostępność lokalnych kopii. Nadawca widzi osobno wysłanie i potwierdzone pobranie. Lokalny odczyt karty pozostaje odrębny.

CH051: po osobnej zgodzie można utworzyć link dla odbiorcy bez potwierdzonej dostępności HD. Numer tej osoby pozostaje lokalny. Link domyślnie działa 24 h; można odwołać dostęp i usunąć pliki. Strona nie ma trackerów, nie umieszcza sekretu w query/Referer/logach, udostępnia metadane dopiero po autoryzacji i weryfikuje SHA-256 przed zapisem pliku. Osobna akcja przygotowania SMS otwiera zwykły szkic z terminem ważności; wysyłka wymaga zatwierdzenia w kompozytorze.

CH052: autoryzacja każdej porcji respektuje TTL i odwołanie przed cyklicznym usunięciem. Wygasłe klucze linków nie dają się wskrzesić ponowieniem tego samego żądania. Audyt ma dzienne liczniki bez danych użytkownika, health probe i progi alertów. Bariera odtwarzania usuwa stare wiązania i dane przed uruchomieniem odtworzonej kopii. Zaszyfrowany numer routingu, konfiguracja sekretu z pliku/env i separacja środowisk są opisane w [ADR-003](adr/ADR-003_Dostarczenie_HD_i_linki.md) oraz [instrukcji operacyjnej](../../backend/README.md).

CH053: rzeczywiste wiadomości i pliki HD są częścią rozmowy, podglądu skrzynki, liczby nieprzeczytanych i galerii z MessageRef.Hd. Karta pokazuje prawdziwe miniatury oraz dostępność/pobieranie/zapis/potwierdzanie/wygaśnięcie. Błąd integralności lub formatu zatrzymuje automatyczne próby i daje jawne ponowienie. Oryginał, kopia bez lokalizacji i MMS zachowują wcześniejsze jawne warianty. Niedostępny backend nie udaje odbiorcy bez Chatry. Fallback do MMS i linku wymaga działania użytkownika. Odbiór HD nie dopisuje fikcyjnego SMS do providera.

CH054: Room v7 ma migracje z każdej opublikowanej wersji, wspólny kosz SMS/MMS/HD i trwałe znaczniki usunięcia. Testy sprawdzają deduplikację, ustawienia przypięcia/wyciszenia/nieprzeczytania, relacje mediów, awarie częściowego sprzątania, restart pobierania i utratę odpowiedzi. [Workflow CI](../../.github/workflows/chatra-verification.yml) obejmuje backend, domenę, Android Lint, oba buildy, migracje i instrumentację. Workflow jest przygotowany; wynik zdalnego uruchomienia będzie dostępny dopiero po publikacji zmian.

## Wynik lokalnej weryfikacji

| Obszar | Wynik |
| --- | --- |
| Backend Python 3.12 | 18 testów przeszło; EC P-256 i SQLite rzeczywiste, dostawcy zastąpieni kontrolowanymi atrapami |
| 4 obrazy / 38,2 MB | Rzeczywiste PNG z pikselami; upload, restart SQLite, pobranie i porównanie pełnych hashy czterech plików |
| Android JVM | 88 testów modelu, fixture i aplikacji full/demo przeszło |
| Room na emulatorze | 4 testy przeszły, obejmując migrację 1→7 oraz każdą z wersji 2–6→7 |
| Pełna instrumentacja aplikacji | 82 przypadki: 73 zaliczone, 9 scenariuszy sprzętowych/reboot wymagających jawnych argumentów pominiętych; wynik OK |
| HD na Androidzie | 9 testów uploadu i skrzynki: przerwanie, wznowienie, błędny hash, utrata receipt, reset kursora, kosz, źródło galerii i odpięcie instalacji |
| Kompilacja i Lint | full/demo Kotlin, APK full i testowe, Lint full/demo przeszły |
| Strona linku | Chromium w izolowanym kontekście, mobilny viewport 390×844; poprawny i błędny token, zapis po SHA-256, ponowne pobranie, brak poziomego overflow; przyciski 48 px |

[Logi wykonania](CH050_CH054_Android/) zawierają końcowy wynik backendu, JVM/Lint, migracji i instrumentacji.

Testy wykonano na emulatorze API 35 `ChatraFive`, w trybie read-only z tymczasową warstwą danych. Nowy emulator API 36 wymagał więcej miejsca niż dostępne na hoście, dlatego nie czyszczono danych istniejących urządzeń użytkownika. Nowe testy pobierania używają rzeczywistych PNG, prywatnego importera Androida i sprawdzonych plików. Kontrolowany transport wstrzykuje awarie; nie wysyła SMS ani push do rzeczywistych osób.

Kontrola UI na emulatorze objęła rzeczywisty wiersz HD w skrzynce, zmianę nieprzeczytanych po wejściu do rozmowy, trzy miniatury z +1 oraz otwarcie czwartego oryginału w zewnętrznej przeglądarce zdjęć. [Skrzynka](CH050_CH054_Android/hd-inbox.png), [karta](CH050_CH054_Android/hd-conversation.png), [czwarty plik](CH050_CH054_Android/hd-open-file.png). Są to cztery małe PNG QA; scenariusz 38,2 MB jest osobną próbą API z rzeczywistymi obrazami, sprawdzonymi także niezależnym dekoderem Pillow.

Backend obejmuje: powtórny finalize i receipt, restart, odbiorcę offline, cudzą kopertę/skrzynkę, cudzy kursor, brak receipt po push, retry push, cofnięcie linku i wiązania, dokładną granicę TTL, idempotencję wygasłego linku, migrację dawnych draftów, reset kursora, odtworzenie snapshotu, brak tokenów/numerów w przechwyconych logach i licznik alarmowy. Zależności testów zapisano w `backend/requirements-dev.txt`; OpenAPI ma kontrolę zgodności w CI.

Pełna regresja 82 przypadków objęła wersję po korekcie karty i błędu integralności. Ostatnia zmiana odpinania została sprawdzona na końcowym APK osobną serią wszystkich 9 testów HD; pozostała część aplikacji nie zmieniała się po pełnej regresji.

## Do odbioru na środowisku skonfigurowanym

Implementacja nie oznacza uruchomienia publicznej usługi. Pozostają G06/G07 (dostawcy, polityka, brak E2EE, region, monitoring, sekrety) i G08 dla Pro oraz rzeczywiste próby Play sandbox/acknowledgement/restore/refund. Potrzebne są konta i konfiguracje Twilio oraz FCM. Nie uruchamiano produkcyjnego serwera ani nie zmieniano kont dostawców.

Nie wykonano jeszcze próby dwóch fizycznych telefonów z rzeczywistym FCM/OTP ani pobrania w Safari iOS. Scenariusz 38,2 MB został zweryfikowany na prawdziwym API i SQLite, a restart/receipt/import osobno w Androidzie z kontrolowanym transportem. Pełny przepływ dwóch telefonów z HTTPS i dostawcami pozostaje odbiorem integracyjnym. TLS i zaszyfrowany wolumen nie są E2EE. Odwołanie linku nie usuwa kopii już pobranej przez odbiorcę. Pilot nie ma wysokiej dostępności ani zachowującego dane restore.

Polecenia odtworzenia kontroli:

```sh
cd backend
python -m pip install -r requirements-dev.txt
python -m pytest -q
cd ../android
./gradlew :core:model:test :core:testing:test :app:testFullDebugUnitTest :app:testDemoDebugUnitTest :app:lintFullDebug :app:lintDemoDebug
./gradlew :core:database:connectedDebugAndroidTest :app:connectedFullDebugAndroidTest
```

Instrumentacja pełnej aplikacji wymaga roli SMS i uprawnień na **emulatorze QA**. Instrukcje nie są przeznaczone do kasowania historii prywatnego telefonu.
