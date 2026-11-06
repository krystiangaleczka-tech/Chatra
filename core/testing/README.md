# CH006: deterministyczne fixture ekranów i porównywanie PNG

Wykorzystujemy **istniejący pakiet źródłowy** w `docs/implementacja_android/Chatra_Pakiet_referencyjny/style_zrodlowe.json`. Nie tworzymy nowych treści ekranów ani nie modyfikujemy 10 goldenów Androida.

## Tworzenie danych

```bash
node scripts/ch006_verify_fixtures.mjs
node scripts/ch006_build_fixtures.mjs
node scripts/ch006_build_fixtures.mjs --verify
```

Wynik to `build/ch006/screen-fixtures.json` z 10 ekranami, stabilnymi ID `S01-E001` itd., dokładnym tekstem, deklaracjami CSS i etykietami źródłowymi. Domyślny zegar: 29.10.2026, 15:00 w Warszawie. Język `pl-PL`, referencyjne 390×844, Inter. Wariant demonstracyjny **nie korzysta z sieci ani kontaktów telefonu**. Etykiety `SIM 1 · Prywatna` i `SIM 2 · Praca` to fixture, nie prawdziwe subskrypcje.

Scenariusze mają identyfikatory i odniesienia do konkretnych elementów (szkic, SMS z błędem, okno anulowania, karta HD, przeterminowany harmonogram, ostrzeżenie MMS grupowego). Dosłowny błędny licznik `21/160 · 1 SMS` to **wyjątek wyłącznie dla goldenów S03**; produkcyjny licznik CH025 musi korzystać z Android API, a nie z tego tekstu.

Plik wyjściowy jest **generowany**, nie jest drugim edytowalnym źródłem prawdy. Nie należy go edytować ręcznie. `--verify` porównuje bajty z regeneracją i kończy się błędem w przypadku zmian. `--source` i `--output` służą wyłącznie do testowania innego pliku wejściowego/ścieżki wynikowej.

## Porównanie renderu z goldenami Androida

```bash
python -m pip install 'Pillow>=12'
python scripts/ch006_compare_screens.py \
  --reference-dir docs/implementacja_android/CH002_Android/run1 \
  --actual-dir /sciezka/do/nowych/png \
  --output-dir build/ch006/comparison
python scripts/ch006_test_compare.py
```

W katalogu `/sciezka/do/nowych/png` muszą istnieć pliki `android-S01.png` do `android-S10.png`. Dla każdej pary powstają `overlay.png`, `diff.png` i wspólny `report.json`. Różnica per piksel to maksimum trzech kanałów RGB; próg domyślny wynosi 3/255. Skrypt **odrzuca rozbieżne wymiary**, nie przeskalowuje obrazów, nie ustala automatycznie akceptacji UX. Mierniki określają wyłącznie różnicę obrazów, a nie jakość renderu na urządzeniu.

## Granica zadania

Fixture i narzędzia raportowania są gotowe. **W CH006 pozostaje** wpięcie fixture do renderera Android/Compose po CH004, uruchomienie bez danych telefonu, powtarzalne zrzuty 10 ekranów z fizycznego Androida i przejście przez raport różnic. Bez tego task pozostaje `in_progress`, a nie `completed`.
