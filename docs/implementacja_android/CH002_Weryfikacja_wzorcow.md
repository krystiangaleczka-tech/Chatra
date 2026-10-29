# CH002 — weryfikacja wzorców i typografii

**Wynik:** ukończony audyt źródeł, 10 istniejących wzorców macOS i geometrii; **niezamknięty warunek referencyjnego renderu Android**. To częściowe wykonanie CH002, a nie akceptacja G01.

## 1. Integralność oryginału

Niezależnie przeliczono SHA-256 **całego** oryginalnego HTML z GitHub (algorytm SHA-256 sprawdzony na wzorcu `abc`): `80e7652c4de8a483b39a38e6cd319524817af11986499f0f7aee28d4efa4cbe3`. Długość **3 996 110 bajtów**, zgodna z pierwotnym manifestem. Nie zmieniono oryginalnego HTML.

Istnieje 10 obrazów `screen-01.png` – `screen-10.png` opisanych jako **390×844 px** w [manifeście referencji](Chatra_Pakiet_referencyjny/manifest_referencji.json). To obrazy **Chrome/macOS, DPR 1**, nie Android. Ich SHA-256 znajdują się w źródłowym manifeście. W tej sesji nie zweryfikowano binarnie PNG (złącze nie zwraca binarnego bloba); do tego służy [walidator lokalny](../../scripts/ch002_verify_reference.mjs).

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

## 4. Faktyczny font (decyzja G01) pozostaje nieustalony

Źródło deklaruje `-apple-system, BlinkMacSystemFont, 'SF Pro Text', Inter, system-ui, sans-serif`. `getComputedStyle(...).fontFamily` **nie identyfikuje** kroju użytego przez silnik: to jedynie lista fallbacków. Nie potwierdzono rzeczywistego fontu na Androidzie ani porównania ze źródłowym macOS. **Inter 400–800 to wariant roboczy, nie zatwierdzony zamiennik.** Osadzone fragmenty WOFF2 i licencję OFL pozostawiono bez zmian.

## 5. Manifest wymaganego testu Android

| Parametr | Stan / warunek |
| --- | --- |
| Rozmiar obszaru projektu | 390×844 CSS px w iframe; cel aplikacji Android 390×844 dp |
| Model urządzenia, build, API, density, fizyczne px | **Nieustalone**; odczytać na urządzeniu |
| Browser / WebView | Android Chrome lub wybrany wzorzec; zapisać dokładną wersję |
| Język, fontScale, wygląd | `pl-PL`, 1.0, jasny, przykładowy zegar 29.10.2026 |
| Animacje | Wyłączone w warunkach porównania; źródło aplikacji nietknięte |
| Font rzeczywiście użyty | **Nieustalony**; Rendered Fonts / `CSS.getPlatformFontsForNode` |
| Dwie próby renderu × 10 ekranów | **Nieprzeprowadzone** |
| Golden Android i zatwierdzenie G01 | **Nie przeprowadzono** |

**Przebieg:** sklonować repo → `node scripts/ch002_verify_reference.mjs` (lokalna integralność HTML i PNG). Na Androidzie `adb shell getprop ro.build.version.sdk`, `adb shell wm size`, `adb shell wm density`, `adb shell settings get system font_scale`; zapisać wersję Chrome, locale i DPR. Oryginał wyświetlić przez lokalny serwer HTTP oraz `adb reverse tcp:8765 tcp:8765`. Do zdalnego DevTools zastosować `adb forward tcp:9222 localabstract:chrome_devtools_remote` i `chrome://inspect/#devices`. Mierzyć wewnętrzne iframe, sprawdzić **Rendered Fonts** (nie samo CSS), rozdzielić ramkę prezentacji, zebrać po dwa zrzuty każdego z 10 ekranów w tym samym stanie i porównać piksele, baseline i SSIM. Dokumentować wyjątki i zatwierdzenie UX/QA.

**Kryteria natywnej zgodności:** geometria i baseline do 1 jednostki projektu, kolory wnętrza do 3/255, komplet 10 ekranów, osobne ROI ikon/tekstu/szkła; SSIM 0.99 pomocniczo, a nie zamiast przeglądu. Pełne progi są w [specyfikacji Android](Chatra_Dokumentacja_Android.md).

## 6. Status kontrolny

| Warunek CH002 | Status |
| --- | --- |
| Nie zmieniono oryginału, przeliczono SHA-256 i rozmiar | **ZALICZONE** |
| Skompletowano manifest 10 obrazów z macOS i pomiary źródłowe | **ZALICZONE dla istniejących danych** |
| Rozdzielono wrapper od aplikacji i zidentyfikowano border-box | **ZALICZONE w audycie** |
| Przygotowano odtwarzalny walidator, procedurę i manifest docelowy | **ZALICZONE** |
| Ponowny rendering i 2 identyczne ujęcia na realnym Androidzie | **BLOKADA: brak urządzenia / emulatora** |
| Ustalono faktycznie użyty font i baseline na Androidzie | **BLOKADA: brak Android DevTools** |
| Ostateczne zamrożenie G01 i CH002 | **NIEZALICZONE** |

**Status w backlogu:** `in_progress_blocked_android_reference`. Bez tych dowodów nie wolno pisać „CH002 done” ani uznawać eksperymentów CH003 za zgodne 1:1.
