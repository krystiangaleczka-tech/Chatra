# CH002 — weryfikacja wzorców i typografii

**Wynik (aktualizacja 29.10.2026, wieczór):** audyt źródeł i 10 wzorców macOS z pierwszej części, a teraz także **render oryginału na fizycznym Androidzie** (Samsung Galaxy S26 Ultra, Chrome 156): 10 ekranów × 2 przebiegi, manifest urządzenia, **Rendered Fonts** i pomiar 161 elementów. Font rzeczywiście użyty na Androidzie: **Inter (osadzony web font)**. Technicznie CH002 jest wykonany; **G01 czeka na zatwierdzenie UX/QA** (sekcja 7), więc bramka nie jest zamknięta automatycznie.

## 1. Integralność oryginału

Niezależnie przeliczono SHA-256 **całego** oryginalnego HTML z GitHub (algorytm SHA-256 sprawdzony na wzorcu `abc`): `80e7652c4de8a483b39a38e6cd319524817af11986499f0f7aee28d4efa4cbe3`. Długość **3 996 110 bajtów**, zgodna z pierwotnym manifestem. Nie zmieniono oryginalnego HTML.

Istnieje 10 obrazów `screen-01.png` – `screen-10.png` opisanych jako **390×844 px** w [manifeście referencji](Chatra_Pakiet_referencyjny/manifest_referencji.json). To obrazy **Chrome/macOS, DPR 1**, nie Android. Ich SHA-256 znajdują się w źródłowym manifeście. Lokalny [walidator](../../scripts/ch002_verify_reference.mjs) sprawdził binarnie wszystkie 10 PNG (sygnatura, 390×844, SHA-256): **PASS**.

W oryginalnych zestawach [pomiary przeglądarki](Chatra_Pakiet_referencyjny/pomiary_przegladarki.json) i [style źródłowe](Chatra_Pakiet_referencyjny/style_zrodlowe.json) potwierdzono 10 par ekranów, **161** wybranych bounding boxów i **500** deklaracji elementów. Raport punktów charakterystycznych: [CH002_Pomiary_i_manifest.json](CH002_Pomiary_i_manifest.json).

## 2. Ramka prezentacyjna a aplikacja

Zbiorcza strona zawiera dziesięć `iframe`, po 390×844 CSS px. Zewnętrzne tło `#F5F4EF`, podpisy, odstępy plansz 48 px, border-radius 48 px i cień ramki są **elementami prezentacji**, nie interfejsem aplikacji. Porównywać zawartość poszczególnych `iframe`, przy czym oryginalny HTML pozostaje niezmieniony. Na Androidzie docelowy obszar projektu to 390×844 **dp**; nie są to automatycznie piksele fizyczne.

## 3. Pomiary zapisane w macOS Chrome (nie w Androidzie)

| Ekran | Liczba zmierzonych elementów | Przykładowy element: x, y, w, h [CSS px] |
| --- | ---: | --- |
| S01 | 8 | H1: 20, 130.734375, 350, 40 |
| S02 | 20 | H1: 20, 53.5, 149.921875, 41 |
| S03 | 12 | HEADER: 12, 50, 366, 50 |
| S04 | 16 | SECTION: 8, 196, 374, 640 |
| S05 | 16 | SECTION: 8, 120, 374, 716 |
| S06 | 16 | H1: 20, 52, 350, 41 |
| S07 | 18 | SECTION: 16, 264, 358, 133 |
| S08 | 15 | H1: 123.671875, 65, 142.65625, 20 |
| S09 | 19 | H1: 74, 56.5, 95.75, 33 |
| S10 | 21 | H1: 16, 52, 358, 41 |

**Konkretny problem box modelu:** S02 `height:44px` dla wyszukiwarki daje **46px z borderami**. Przy dolnym okrągłym przycisku 62px zawartości nie oznacza 62px końcowego przycisku (outer box 64px). Panel S04 ma **8,196,374,640**, a S05 **8,120,374,716**. Dlatego należy porównywać `getBoundingClientRect()` i `getComputedStyle()`, z uwzględnieniem `box-sizing`, paddingów, borderów, promieni, line-height i typografii. Nie zastępować ich arbitralnym rozmiarem Material.

## 4. Faktyczny font na Androidzie (dowód dla G01)

Źródło deklaruje `-apple-system, BlinkMacSystemFont, 'SF Pro Text', Inter, system-ui, sans-serif`. Na macOS stos kończy się na SF (systemowy). Na Androidzie trzy pierwsze nazwy nie istnieją, a bundel zawiera `@font-face` **Inter** (pliki z [fonty_web](Chatra_Pakiet_referencyjny/fonty_web)), więc przeglądarka wybiera Inter, zanim dojdzie do `system-ui`.

Potwierdzenie z DevTools (`CSS.getPlatformFontsForNode`, czyli Rendered Fonts, nie samo CSS) dla **wszystkich węzłów z tekstem** na 10 ekranach: jedyny użyty krój to `Inter (Inter-Regular) [web font]`, łącznie 5 078 glifów; zero glifów z fontów systemowych. Szczegóły per ekran: [android_capture.json](CH002_Android/android_capture.json).

- Wszystkie wagi korzystają z **tego samego pliku** dla danego zakresu Unicode, czyli Inter jest fontem zmiennym (stąd PostScript `Inter-Regular` także dla 600/700).
- Bundel deklaruje twarze tylko dla wag **400, 500, 600, 700**. Style używają też **800** (3 razy: plakietka „HD” w S03, S04 i S10). Według reguł dopasowania CSS Chrome wybiera wtedy twarz 700, bez sztucznego pogrubienia. To wniosek z reguł, a nie osobny pomiar. Wzorzec Androida ma więc „HD” w wadze 700, a macOS w SF 800.
- Systemowy `font_scale` telefonu = 0.9 **nie wpływa** na render: DPR = 3.5 = 560/160, a wysokości boksów tekstu są równe macOS (np. H1 S01: 40 px na obu).

## 5. Manifest testu Android (wykonany)

| Parametr | Wartość zmierzona |
| --- | --- |
| Urządzenie | Samsung **SM-S948B** (Galaxy S26 Ultra), połączenie adb przez Wi-Fi |
| System | Android **17**, API **37**, build `CP2A.260605.016.S948BXXS4BZIG` |
| Ekran | 1440×3120 px fizycznie, density **560** (override; fizyczna 600), DPR w Chrome **3.5** |
| Przeglądarka | **Chrome 156.0.8078.49** (`com.android.chrome`), WebView systemu 156.0.8078.47 |
| Obszar projektu | iframe 390×844 CSS px → zrzut **1365×2954 px** (390×844 × 3.5) |
| Język, strefa | `pl-PL`, Europe/Warsaw (wymuszone w karcie przez DevTools; systemowo też pl-PL) |
| Wygląd | System w trybie ciemnym. W karcie wymuszono `prefers-color-scheme: light` i wyłączono auto-dark; ustawień telefonu **nie zmieniano** |
| fontScale | System 0.9; efektywnie w treści **1.0** (sekcja 4) |
| Animacje | `prefers-reduced-motion: reduce`; przed zrzutem 0 działających animacji w każdym iframe |
| Zegar i dane | Statyczne teksty oryginału (np. 29.10.2026 w treści); bez wirtualnego czasu |
| Ramka | W pamięci karty zdjęto prezentacyjny `border-radius` i cień iframe (jak przy wzorcach macOS); plik HTML nietknięty |
| Dwa przebiegi × 10 ekranów | **Wykonane**: przeładowanie strony z pominięciem cache między przebiegami |

**Przebieg (odtwarzalny):** `node scripts/ch002_verify_reference.mjs` → serwer `python3 -m http.server 8765` w katalogu pakietu → `adb reverse tcp:8765 tcp:8765`, `adb forward tcp:9222 localabstract:chrome_devtools_remote` → otwarcie strony intentem (`adb shell am start -a android.intent.action.VIEW -d <url> --ez create_new_tab true -p com.android.chrome`; Chrome na Androidzie nie tworzy kart przez DevTools) → `node scripts/ch002_android_capture.mjs` → `python3 scripts/ch002_android_compare.py`. Skrypt zamyka swoją kartę po pomiarze.

## 6. Wyniki: stabilność, geometria i obraz

| Ekran | Przebieg 1 vs 2 | Dopasowane | ≤ 1 px | Maks. \|Δ\| vs macOS [CSS px] | Piksele > 3/255 vs macOS | SSIM (globalny) | Glify Inter |
| --- | --- | ---: | ---: | --- | ---: | ---: | ---: |
| S01 | identyczne bajty | 8/8 | 0 | 9.73 (H2 „CZEGO NIE OBSŁUGUJE”) | 28.22 % | 0.399 | 529 |
| S02 | 55 px, 1 > 3/255, max 45 | 20/20 | 11 | 7.68 (H1 „Rozmowy”) | 17.41 % | 0.565 | 632 |
| S03 | identyczne bajty | 12/12 | 7 | 2.14 (DIV „Zdjęcie 1”) | 15.12 % | 0.751 | 502 |
| S04 | identyczne bajty | 16/16 | 11 | 22.43 (A „Wyślij 4 zdjęcia…”) | 23.53 % | 0.649 | 353 |
| S05 | identyczne bajty | 16/16 | 10 | 4.11 (H2 „Zaplanuj wysyłkę”) | 13.15 % | 0.785 | 389 |
| S06 | identyczne bajty | 16/16 | 6 | 22.43 (A „PON. 9:00 Marek…”) | 33.48 % | 0.315 | 807 |
| S07 | 227 px, 24 > 3/255, max 61 | 18/18 | 8 | 42.70 (INPUT wyszukiwania) | 20.67 % | 0.645 | 524 |
| S08 | 407 px, 2 > 3/255, max 41 | 15/15 | 2 | 4.33 (H1 „Nowa wiadomość”) | 15.44 % | 0.462 | 460 |
| S09 | identyczne bajty | 19/19 | 8 | 12.53 (BUTTON „Przywróć ustawienia…”) | 13.01 % | 0.689 | 272 |
| S10 | 1414 px, 39 > 3/255, max 62 | 21/21 | 6 | 12.29 (BUTTON „Zablokowani…”) | 27.07 % | 0.430 | 610 |

**Stabilność (overlay dwóch renderów):** 6/10 ekranów bajtowo identycznych. W pozostałych 4 różnice to szum rasteryzacji szkła i gradientów: w każdym ekranie najwyżej **39 z 4 032 210 pikseli** (≤ 0,001 %) przekracza 3/255, a reszta mieści się w 1–2/255. Wzorzec uznano za stabilny w granicy tolerancji kolorów. Przy powtórnych uruchomieniach te same ekrany przyjmują jedną z kilku wersji rastra, a nie dowolny wynik.

**Geometria vs macOS:** dopasowano wszystkie **161/161** elementów pomiarowych, z czego 69 mieści się w 1 px. Różnice wynikają z metryk Inter wobec SF: Inter jest szerszy, więc część wierszy łamie się inaczej i treść poniżej przesuwa się w pionie (S04, S06 do +22 px; S10 +12 px). W S01 nagłówki są wyżej o ~9 px, a w S07 pole wyszukiwania jest szersze o 43 px, bo przycisk „Anuluj” jest węższy. **Geometrii z macOS nie wolno więc używać jako goldenów Androida**; wiążące są pomiary z Androida w `android_capture.json`.

**Obraz vs macOS** (Android przeskalowany do 390×844): 13–33 % pikseli różni się o więcej niż 3/255, a globalny SSIM wynosi 0.32–0.79. Tło i szkło wizualnie się pokrywają (punktowe próbki tła w S01 są równe co do 1–2/255); widoczne różnice dotyczą tekstu i przesunięć po zmianie łamania. Plansza do przeglądu UX: [plansza_macos_vs_android.jpg](CH002_Android/plansza_macos_vs_android.jpg).

**Golden Android:** [CH002_Android/run1](CH002_Android/run1) (10 PNG 1365×2954, SHA-256 w [porownanie.json](CH002_Android/porownanie.json)). PNG drugiego przebiegu nie są w repozytorium; ich SHA-256 i statystyki różnic zapisano w `porownanie.json`, a skrypt odtwarza je w ~1 min.

## 7. Status kontrolny

| Warunek CH002 | Status |
| --- | --- |
| Nie zmieniono oryginału, przeliczono SHA-256 i rozmiar | **ZALICZONE** |
| Skompletowano manifest 10 obrazów z macOS i pomiary źródłowe; PNG zweryfikowane binarnie lokalnie | **ZALICZONE** |
| Rozdzielono wrapper od aplikacji i zidentyfikowano border-box | **ZALICZONE** |
| Render 390×844 na realnym Androidzie, 10 ekranów | **ZALICZONE** (S26 Ultra, Chrome 156) |
| Dwa kolejne rendery, overlay stabilny | **ZALICZONE w tolerancji** (6/10 identyczne, reszta ≤ 39 px > 3/255) |
| Ustalono faktycznie użyty font i geometrię na Androidzie | **ZALICZONE**: Inter (web font), 161/161 elementów |
| Manifest warunków (API, density, fontScale, renderer, locale) | **ZALICZONE** |
| Uzgodniony wariant fontu = zatwierdzenie G01 przez UX/QA | **CZEKA NA DECYZJĘ** |

**Rekomendacja dla G01:** przyjąć **Inter (zmienny, 400–700, z jawnie dodaną wagą 800 dla „HD” albo świadomie 700)** jako font Androida i **goldeny z Androida** z tego raportu jako wzorzec testów 1:1. SF Pro nie jest alternatywą: licencja Apple ogranicza go do platform Apple. Jeżeli wymagana jest typografia identyczna z renderem macOS, potrzebny jest licencjonowany font o metrykach SF, co jest osobnym zadaniem (CH007).

**Status w backlogu:** `android_reference_verified_awaiting_g01_approval`. Po zatwierdzeniu G01 przez właściciela (UX + Android UI + QA) CH002 można oznaczyć jako ukończony, a CH003 może porównywać prototypy do goldenów Androida.
