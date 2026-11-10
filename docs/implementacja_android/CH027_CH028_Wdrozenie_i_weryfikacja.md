# CH027–CH028 — statusy, ponawianie, szkice i anulowanie

Implementacja z 10.11.2026 na bazie CH022–CH026 (PR #12). Odbiór telefonii na fizycznym urządzeniu pozostaje otwarty.

## Zachowanie

**CH027:** agregacja czeka na rozstrzygnięcie wszystkich części. Dowód wysłania lub doręczenia wygrywa z późnym błędem; brak raportu doręczenia pozostawia `SENT`. Ogólny błąd modemu i nieznany wynik callbacku oznaczają `DISPATCH_UNCERTAIN`, a błąd doręczenia nie daje prawa do zwykłego ponowienia. GSM TP-ST=0 potwierdza doręczenie; inne wartości i format CDMA nie są optymistycznie uznawane za sukces.

Próba zapisuje granicę `PREPARED → SUBMITTING → RETURNED`. Przerwanie przed `SUBMITTING` pozwala ustalić brak wywołania API. Przerwanie od tej granicy wymaga dowodu pozytywnego albo decyzji użytkownika. Brak rekordu i sam rekord outbox/failed nie dowodzą braku wysyłki. Rekoncyliacja porównuje własne ID providera, adres, treść, subscriptionId i datę; nie wyszukuje podobnych wiadomości. Zapis do providera nie cofa sent/delivered.

„Ponów” wymaga wyboru dostępnej SIM i tworzy nowe zlecenie tylko dla odbiorców z pewną porażką, z osobnym oknem anulowania. Unikalne `retryOf` zapobiega podwójnemu ponowieniu. Wysłani odbiorcy i częściowo wysłany SMS nie są powtarzani. Późne potwierdzenie pierwotnej próby unieważnia oczekujące ponowienie; wynik rodzica jest ponownie sprawdzany przed API. Dla niepewności i częściowego SMS dostępne są „Sprawdź status” oraz jawna kopia do szkicu z ostrzeżeniem o możliwym drugim doręczeniu. Żadna z tych akcji nie wysyła automatycznie.

**CH028:** oba edytory zapisują szkice bez opóźnienia 400 ms, w zakresie życia aplikacji. Zapis ma stabilny identyfikator i rewizję; starszy zapis nie nadpisuje nowszego. Szkic obejmuje treść, odbiorców i ich opisy, niedokończone pole odbiorcy, tryb, SIM z opisem, identyfikatory załączników, kolejność, rozmiar i wariant. Cykl życia plików, trwałe granty URI i kopie prywatne należą do CH031; obecny etap utrwala referencje i nie udaje dostępnego transportu załączników.

Przyjęcie zlecenia, niezmienny snapshot i zamknięcie identyfikatora szkicu są jedną transakcją. Spóźniony zapis nie wskrzesza przyjętego szkicu. Jeśli przed przyjęciem istniała już nowsza rewizja, zostaje zachowana pod nowym ID. Edycja jest blokowana na czas przyjmowania operacji, a błędy zapisu są pokazywane. Edytory odtwarzają szkice po ponownym uruchomieniu procesu i udostępniają zapisane wersje przez „Szkice”.

WorkManager służy do wybudzenia. Sam transport wymaga końca trwale zapisanego terminu monotonicznego i zgodnego `BOOT_COUNT`; zmiana zegara ściennego nie przyspiesza wysyłki. Worker ponownie sprawdza czas po przebudzeniu. CAS w transakcji rozstrzyga cancel przeciw dispatch. Skuteczne anulowanie przywraca pełny snapshot jako nowy szkic i nie nadpisuje tekstu pisanego później. Po granicy dispatch aplikacja nie obiecuje anulowania. Po restarcie urządzenia oraz dla starych zleceń bez terminu monotonicznego następuje `MISSED_NEEDS_DECISION`. Android może opóźnić zadanie w tle; force-stop wymaga ponownego uruchomienia aplikacji, aby WorkManager wznowił pracę.

Ręczna próba anulowania wykryła pusty zapis przesłaniający przywrócony szkic po ponownym uruchomieniu. Przy wyborze przywróconego szkicu pomijamy pusty edytor i utrwalamy wybraną wersję jako aktywną. Regresję sprawdza test nowego modelu rozmowy po anulowaniu. Oczekujące wiadomości do kilku osób pozostają widoczne w każdej rozmowie także po zmianie boot; projekcja wykorzystuje powiązania odbiorców.

Room v3 ma migrację v1→v2→v3 bez kasowania danych. Stare porażki z próbami bez dowodu bezpiecznej porażki stają się niepewne; dodatnie dowody z dokładnie powiązanego providera nadal mogą je rozstrzygnąć.

## Weryfikacja

**75 uruchomień testów JVM i 29 testów Androida (26 aplikacji + 3 bazy), bez błędów i pominięć.** Oba APK i lint obu wariantów przeszły.

Wyniki i zrzuty są w [katalogu QA](CH027_CH028_Android/). Końcowe liczby oraz przebieg emulatora zapisano w `verification.json`.

```bash
cd android
./gradlew :core:model:test :core:testing:test \
  :core:designsystem:testDebugUnitTest :app:testDemoDebugUnitTest :app:testFullDebugUnitTest \
  :app:assembleDemoDebug :app:assembleFullDebug :app:assembleFullDebugAndroidTest \
  :core:database:assembleDebugAndroidTest :app:lintDemoDebug :app:lintFullDebug \
  -Dorg.gradle.jvmargs='-Xmx1g -Dfile.encoding=UTF-8' --max-workers=2
# Izolowany emulator z domyślną rolą SMS i READ_PHONE_STATE:
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
adb install -r app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk
adb install -r core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk
adb shell am instrument -w -r app.chatra.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -r app.chatra.core.database.test/androidx.test.runner.AndroidJUnitRunner
```

Testy obejmują 2,9/3,0/3,1 s ze zmianą zegara ściennego, powtórne kliknięcia i workery, 12 wyścigów cancel/send, wznowienie w tym samym boot i decyzję po kontrolowanej zmianie licznika boot, zapis wszystkich pól, rewizje i zamknięte ID, przywrócenie bez nadpisania nowszego tekstu, granicę API, brak callbacku i raportu doręczenia, pewny/niepewny błąd, częściowy SMS, pomijanie udanych odbiorców, niedostępną i wybraną SIM, późne potwierdzenie przeciw ponowieniu, porównywalny/nieporównywalny rekord providera i migrację istniejących danych. Transport testów integracyjnych jest kontrolowany; prawdziwy PendingIntent nadal sprawdza receiver i wynik przed `goAsync`.

W działającej aplikacji potwierdzono odtworzenie odbiorcy, tekstu i SIM po force-stop, obrót i powrót do edytora. Ręczne anulowanie pozostawiło `CANCELLED`, zero prób transportu i tekst aktywny także po kolejnym uruchomieniu. Osobna próba użyła rzeczywistego SmsManagera AVD do wirtualnego portu `5556`: ekran rozmowy był zamknięty, WorkManager wykonał jedną próbę 87 ms po końcu 10-sekundowego okna, a callbacki potwierdziły `Sent` z resultCode `-1` oraz `Delivered`. To dowód transportu emulatora, bez sieci operatora. Zrzut rozmowy zachowuje również stary, błędnie oznaczony rekord QA z CH026; nowa próba ma wynik `DELIVERED`.

Fizyczne SIM/eSIM, operatorzy, zmiana rzeczywistej karty w trakcie próby, CDMA delivery reports oraz restart fizycznego telefonu wymagają dalszego odbioru. Test z kontrolowanym numerem subskrypcji sprawdza odrzucenie nieaktywnej karty; AVD z jedną SIM nie potwierdza przełączania dwóch modemów. G03, G09 i pełny TalkBack pozostają otwarte. CI Androida nadal nie jest skonfigurowane.

Kontrakty: [SmsManager](https://developer.android.com/reference/android/telephony/SmsManager), [WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work), [migracje Room](https://developer.android.com/training/data-storage/room/migrating-db-versions).
