# CH016–CH018: S07 Wyszukiwanie, S08 Nowa wiadomość, S09 Wygląd

Data: 09.11.2026. Baza: `main` po scaleniu PR #9 (`bc39e54`).
Implementacja jest gotowa do przeglądu. [Sprawdzenie działającego demo w emulatorze
10.11.2026](CH016_CH018_Emulator_QA_2026-11-10.md) obejmuje poprawki, próby interakcji
i świeże zrzuty po zmianach. Końcowy odbiór na fizycznym telefonie pozostaje otwarty.

## CH016 — S07

- Ekran wyszukiwania z edytowalnym zapytaniem, czyszczeniem i anulowaniem;
  filtry kodów, przesyłek, załączników, nadawcy oraz ostatniego tygodnia.
- `SearchRepository` oddziela ekran od danych. Demo przeszukuje historię w pamięci;
  indeks produkcyjny i jego cykl życia pozostają w CH038.
- Normalizacja polskich znaków i wielkości liter; wyróżnienia obejmują wszystkie
  wystąpienia i zachowują zakresy oryginalnego tekstu, także z combining marks.
- Dwa szybkie trafienia mają oddzielne wartości i opisy przycisków Kopiuj.
  Używany jest systemowy schowek; wartości nie są logowane.
  Adres automatu pochodzi wyłącznie z metadanych fixture S07.
- Stany pustego zapytania, braku wyników, odczytu, indeksowania i błędu z ponowieniem.
- Wynik otwiera właściwą rozmowę i przewija do wskazanego `MessageRef`.
  Zapytanie i filtry są w `SavedStateHandle`, przewijanie w stanie zapisywalnym
  Compose; powrót z rozmowy zachowuje ekran wyszukiwania.

## CH017 — S08

- Osobny formularz nowej wiadomości: zawijane chipy, usuwanie konkretnego odbiorcy,
  ręczny numer, sugestie kontaktów, wieloliniowy kompozytor i wybór SIM.
- Walidacja wyklucza pustą treść, brak odbiorców/SIM, alfanumerycznych nadawców
  i niezatwierdzony numer w polu. Znormalizowane duplikaty nie tworzą nowych chipów.
- Osobne SMS są domyślne. Plan tworzy po jednej operacji i rozmowie na odbiorcę;
  świadomie wybrany MMS grupowy tworzy jedną operację `MMS`, także dla samego tekstu.
  Opis liczby operacji zmienia się wraz ze stanem.
- Podwójne stuknięcie jest blokowane. Przy częściowym błędzie w szkicu pozostają
  tylko odbiorcy, których zlecenia nie zostały przyjęte do kolejki.
- Anulowanie zachowuje szkic nowej wiadomości w osobnym modelu;
  `SavedStateHandle` odtwarza odbiorców, treść, tryb i SIM. Nie jest to jeszcze
  trwały magazyn szkiców nowej rozmowy odporny na force-stop (etap demo).
- Arkusz uwzględnia IME; długie chipy skracają nazwę, pozostawiając przycisk usuwania.

## CH018 — S09

- Nagłówek, podgląd 196 dp, cztery próbki, segmenty trybu/gęstości,
  suwak 1–7, przełączniki 52×32 i przywracanie ustawień domyślnych.
- Wybory zapisują `AppearancePrefs` przez istniejący `PreferenceStore`/DataStore.
  Tryb referencyjny używa osobnego stanu w pamięci, bez modyfikowania fixture.
- Podgląd reaguje na wybory; role radio/switch i zakres oraz bieżąca wartość suwaka
  są dostępne przez semantykę. Rozmiar tekstu i ograniczenie przezroczystości
  korzystają też z istniejących mechanizmów aplikacji.
- Mgła/Grafit/Kontrast, tryb ciemny/auto, gęstość i zwiększenie kontrastu mają
  jawnie opisany podgląd roboczy. Dokończenie tokenów i zastosowanie ich do całej
  aplikacji należy do CH020/CH040; nie deklarujemy gotowych wariantów produkcyjnych.
- Do czasu CH019 korzeń Ustawień ma tytuł, dock i wejście do Wyglądu.
  Pełny ekran S10 pozostaje osobnym zadaniem.

## Wspólna nawigacja

`Navigator.Saver` zachowuje trzy stosy i aktywną zakładkę, w tym arkusze,
zapytanie początkowe i kotwicę wiadomości. `SaveableStateHolder` zachowuje stan
przewijania ekranów opuszczanych przy nawigacji. Zapis adresów tras poprawnie
obsługuje znaki separatorów oraz Unicode.

## Weryfikacja automatyczna

57 testów JVM: `core:model` 37, `core:testing` 13, `core:designsystem` 2,
modele ekranów/nawigacja `app` 5. Wszystkie bez błędów.
Build `demoDebug` i lint `demoDebug` zakończone powodzeniem.

Nowe przypadki obejmują normalizację i zakresy wyróżnień, cztery trafienia S07,
filtry oraz usunięcie rozmowy z wyników; osobne SMS i tekstowy MMS grupowy;
odtworzenie szkicu, usuwanie konkretnego chipa, podwójne Wyślij, częściowy błąd
kolejki i ponowienie bez duplikatów; blokowanie pustego/niepoprawnego formularza;
odtworzenie zapytania i filtrów, błąd wyszukiwania z ponowieniem oraz trzy stosy
nawigacji z kotwicą wiadomości.

```bash
cd android
./gradlew :core:model:test :core:testing:test \
  :core:designsystem:testDebugUnitTest :app:testDemoDebugUnitTest \
  :app:assembleDemoDebug :app:lintDemoDebug
```

Integralność źródłowych fixture CH006 i odtwarzalność ich eksportu także sprawdzone:

```bash
node scripts/ch006_verify_fixtures.mjs
node scripts/ch006_build_fixtures.mjs
node scripts/ch006_build_fixtures.mjs --verify
```

## Porównanie obrazu i pozostały odbiór

Na S26 Ultra (SM-S948B, API 37, DPR 3,5) zainstalowano demo i wykonano pierwsze
zrzuty S07–S09 w ramce 390×844. Są zapisane w
[`CH016_CH018_Android`](CH016_CH018_Android/pierwszy_pomiar.json).
Wzorców CH002 nie zmieniano.

| Ekran | Pierwszy pomiar: piksele z różnicą >16/255 |
| --- | ---: |
| S07 | 8,438% |
| S08 | 13,693% |
| S09 | 4,347% |

**To wyniki przed korektami, nie wynik końcowy.** Na ich podstawie usunięto
zbędną zewnętrzną kartę formularza S08, poprawiono wyróżnienia S07, placeholder
numeru, akcje dodawania sugestii, próbkę Kontrastu i tło wyłączonych przełączników.
Pozostawiono w pełni widoczny przycisk Anuluj w S07, podczas gdy golden częściowo
wypycha go poza ramkę. Na 09.11 po korektach ADB przestało widzieć urządzenie;
ponowne zrzuty i sprawdzenie S01–S06 wykonano 10.11 w emulatorze API 35, zgodnie
z [nowym raportem](CH016_CH018_Emulator_QA_2026-11-10.md).

Do odbioru na telefonie: oba przyciski Kopiuj i powrót z wyniku ze scrollem;
1/3/wielu odbiorców, długa nazwa, IME, MMS i zmiana SIM; suwak 1/4/7, reset,
TalkBack i odtworzenie stanu po rekreacji. Testy modeli nie zastępują tych prób.

Uruchomienie poszczególnych plansz po instalacji demo:

```bash
adb shell am start -n app.chatra.demo/app.chatra.MainActivity --ez frame true --es screen S07
# Analogicznie S08 i S09; pomiędzy planszami: adb shell am force-stop app.chatra.demo
```

Wariant `full` nadal nie ma adaptera `createContainer` i nie jest objęty tym
buildem. Workflow CH006 w repozytorium ma filtr ścieżek fixture; nie uruchamia
testów ani builda Androida dla tej zmiany. G03 pozostaje otwarta.
