# CH030 — powiadomienia i wejścia systemowe, etap 1 (11.11.2026)

Stan: **PR roboczy, częściowa implementacja, bez odbioru urządzenia**. Baza: CH029 / PR #14.

- Kanały: SMS, błędy wysyłania, harmonogram i HD. Samo istnienie kanałów nie oznacza wdrożenia alertów błędów, alarmów lub HD.
- Niewyciszona rozmowa ma własne powiadomienie z niezmiennym identyfikatorem rozmowy i odrębnym PendingIntent. ID nie pochodzi z numeru telefonu ani treści SMS. Wariant publiczny na ekranie blokady ma neutralną treść; nie wyświetla nazwy kontaktu, nadawcy ani wiadomości.
- Zbiorczy licznik nadal uwzględnia nieprzeczytane wiadomości także w wyciszonych rozmowach; podwójny alert zbiorczy jest wyłączony. Jedynie przyrost nieprzeczytanych z nowym ID wiadomości w działającym procesie może wywołać alert.
- MainActivity obsługuje wejście przy zimnym uruchomieniu i przy ponownym Intent; nawigacja sprawdza rolę SMS, nie wysyła nic automatycznie. Parser odrzuca niepoprawny lokalny identyfikator.
- Dodano test wejścia z powiadomienia i odrzucenia niepoprawnej ścieżki. Nie uruchomiono testów instrumentation.

Pozostałe w CH030: szybka odpowiedź RemoteInput (wybór SIM, trwałe enqueue/undo), obsługa udostępniania, ustawienia ukrywania podglądów, czas trwania wyciszenia i jego aktualizacja, deep linki do innych rodzajów powiadomień, testy na ekranie blokady i po cold start z i bez zgody POST_NOTIFICATIONS. Istniejące SENDTO / RESPOND_VIA_MESSAGE zachowują szkic i wymagają ponownego testu regresyjnego.

Weryfikacja do wykonania:

```bash
cd android
./gradlew :app:testFullDebugUnitTest :app:assembleFullDebug :app:assembleFullDebugAndroidTest :app:lintFullDebug
# Na emulatorze z rolą SMS, z opcjonalnym READ_CONTACTS i POST_NOTIFICATIONS:
adb shell am instrument -w -r app.chatra.test/androidx.test.runner.AndroidJUnitRunner
```
