# CH019–CH021 — zakres i weryfikacja

Stan z 10.11.2026. Zmiany bazują na CH016–CH018 (PR #10).

## CH019 — S10 Ustawienia

Ekran odtwarza kartę Pro, sekcje wysyłania, wyglądu i porządku, prywatność oraz dock. Podsumowania wynikają z preferencji, kart SIM i modelu liczników. Każda pozycja ma osobną akcję. Licznik części jest niezależnym przełącznikiem; opóźnienie, domyślna SIM i wariant HD zapisują preferencje. Wygląd otwiera S09.

Blokady i kosz mają oznaczone listy demonstracyjne; odblokowanie i odzyskanie zmieniają ich liczniki. Pełna implementacja blokowania i retencji należy do CH042/CH043. Wariant full nie udaje tych operacji. Brak produktu sklepu pokazuje własny komunikat. `[CENA]` występuje wyłącznie w referencyjnym demo, nigdy jako oferta produkcyjna.

Emulator API 35: potwierdzono zmianę opóźnienia 3→5 s i podsumowania, zmianę wartości przełącznika, odblokowanie 3→2 oraz odzyskanie 4→3. Nie jest to odbiór obrazu na S26 ani pełna próba dostępności. Porównanie S10 z goldenem, krótki ekran i duży font pozostają do końcowej weryfikacji.

## CH020 — projekty do akceptacji

[Katalog](CH020_Katalog_projektow_i_stanow.md) i [plansza SVG](CH020_Projekty.svg) zawierają 18 uzupełniających projektów, siedem stanów, akcje, fokus, mapę zadań oraz tokeny ośmiu palet. Źródło stanów i podgląd Compose korzystają z tego samego JSON. Walidator przeszedł; planszę wyrenderowano w Chrome i obejrzano, w tym po poprawce etykiet wyboru godziny.

**G09 pozostaje otwarte.** Katalog to propozycje do przeglądu produktu/UX. Nie oznacza wdrożonych zakupów, eksportu, serwera HD ani zatwierdzenia nowych motywów. Pickery w S05/S06 pozostają systemowe do wdrożenia zaakceptowanego projektu.

## CH021 — systemowa rola i zgody

Wariant `full` ma rzeczywisty adapter RoleManager i pełny manifest kwalifikujący: SENDTO dla sms/smsto/mms/mmsto, chronione SMS_DELIVER i WAP_PUSH_DELIVER oraz usługę RESPOND_VIA_MESSAGE. Demo nadal nie ma niebezpiecznych uprawnień SMS.

Rola, dostęp do SMS, kontakty, odczyt SIM i powiadomienia są rozdzielone. Żądania pojawiają się po użyciu konkretnej funkcji. Odmowa zachowuje możliwość używania pozostałych funkcji; brak możliwości kolejnej prośby prowadzi do ustawień aplikacji. Powrót odświeża stan. Utrata roli wycofuje chroniony interfejs do Startu i blokuje transport. Test regresji sprawdza ponowne użycie istniejącego modelu Startu po cofnięciu roli.

Zdjęcia wybiera systemowy Photo Picker, bez zgody na całą bibliotekę. SENDTO przyjmuje wyłącznie obsługiwane schematy i poprawne numery, ogranicza rozmiar treści i liczbę odbiorców; zapisuje prywatny szkic. Nawigacja zawiera jego identyfikator, nie treść. Żadne wejście nie wysyła automatycznie.

Odbiorniki rewalidują rolę i zgodę także w zadaniu asynchronicznym. Na tym etapie zapisują PDU w prywatnej, atomowej skrzynce wejściowej; składanie i zapis historii należą do CH023, MMS do CH032. RESPOND_VIA_MESSAGE zachowuje szkic odpowiedzi i nie symuluje transportu. Pełny wariant jasno ostrzega, że ten etap nie zastępuje codziennego klienta SMS.

Wariant full buduje się od CH021. Istniejące wiadomości nie są importowane ani modyfikowane przez ten etap. Próby rzeczywistego dialogu roli, wszystkich odmów, zmiany roli i receiverów pozostają otwarte do końcowej weryfikacji. Start emulatora i instalację obu APK potwierdzono, lecz próby systemowego ekranu były zakłócane zawieszaniem środowiska przy niedoborze miejsca/pamięci. Nie zaliczamy nieukończonych prób.

## Walidacja

66 uruchomień testów JVM bez błędów: model 41, fixture 13, designsystem 2, app demo 8 i app full 2. Dwa testy modelu organizacji uruchamiają się w obu wariantach. Końcowy build i lint obu wariantów przeszły (`BUILD SUCCESSFUL`, 159 zadań Gradle).

```bash
python3 scripts/ch020_validate_catalog.py --render
cd android
./gradlew :core:model:test :core:testing:test \
  :core:designsystem:testDebugUnitTest \
  :app:testDemoDebugUnitTest :app:testFullDebugUnitTest \
  :app:assembleDemoDebug :app:assembleFullDebug \
  :app:lintDemoDebug :app:lintFullDebug
```

Odbiór na fizycznym telefonie, dual SIM, G03, pełny TalkBack oraz G09 pozostają otwarte. Testy jednostkowe i kwalifikujący manifest nie zastępują prób telefonii.

Podstawy adaptera: [Telephony](https://developer.android.com/reference/android/provider/Telephony), [RoleManager](https://developer.android.com/reference/android/app/role/RoleManager), [prośby o uprawnienia](https://developer.android.com/training/permissions/requesting), [Photo Picker](https://developer.android.com/training/data-storage/shared/photopicker).

## Uzupełnienie podczas CH022–CH026

Wykonano porównanie S10 w tej samej ramce 390×844 dp i DPR 3,5: **6,1180% pikseli z różnicą >16/255** względem goldena CH002. Zrzut pochodzi z emulatora API35, golden z S26 API37. Wynik obejmuje różnice geometrii, ikon i renderera; nie stanowi odbioru 1:1. [Zrzut i pomiar](CH022_CH026_Android/s10-comparison.json).

Potwierdzono w emulatorze rzeczywisty dialog roli, anulowanie i ponowne nadanie; po cofnięciu do Messages oraz ponownym wejściu Start blokuje chroniony interfejs. Po próbach przywrócono Messages. Rozwinięcie pełnego klienta, testy platformowe i dalsze dowody są w [raporcie CH022–CH026](CH022_CH026_Wdrozenie_i_weryfikacja.md). G09 i odbiór fizyczny pozostają otwarte.

Sprawdzono także wszystkie osiem tras S10 na 320×640 dp przy fontScale 1,3, w tym dotarcie do Prywatności nad dockiem. Pro poza ramką fixture nie pokazuje `[CENA]`. Rejestr prób i zrzut małego ekranu są w katalogu QA CH022–CH026.
