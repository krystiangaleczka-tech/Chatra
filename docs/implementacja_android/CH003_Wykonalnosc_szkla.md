# CH003 — Wykonalność natywnego szkła (G03)

Data: 07.11.2026 · Urządzenie: Samsung Galaxy S26 Ultra (SM-S948B), Android 17 / API 37, 1440 × 3120, density 560 (DPR 3,5), 120 Hz · Build: `demoRelease` (R8, podpis kluczem debug wyłącznie do testu)

## Wynik w skrócie

**Rekomendacja G03: Jetpack Compose z własnym szkłem na `RenderEffect` (blur + saturacja) — bez WebView.** Potwierdzona na flagowcu (S26 Ultra, 120 Hz) i słabszym urządzeniu (Galaxy A15 5G, 90 Hz). Szkło jest zgodne z wzorcem (S01 3,4 %, S02 6,2 %, S03 6,7 %, arkusz S04 3,6 % pikseli > 16/255), tekst nad panelami jest ostry, nakładające się panele działają bez rekurencji, a na flagowcu koszt klatki jest akceptowalny. **Do formalnego zamknięcia G03 brakuje:** sprawdzenia na Androidzie 12 (API 31 — zgodność `RenderEffect`) i zatwierdzenia przez lidera Android UI + QA wydajności. Pierwotnie wysoki koszt pamięci graficznej (+76/+143 MB) zredukowano próbkowaniem rozmycia w obniżonej rozdzielczości — szkło kosztuje teraz +21/+27 MB względem matu, a spóźnione klatki są praktycznie na poziomie matu (sekcja „Optymalizacja”).

## Implementacja (stan CH008)

- `core/designsystem/glass`: `Backdrop` nagrywa scenę do `GraphicsLayer`; `Modifier.glass` rysuje pod panelem fragment sceny przez `RenderEffect.createBlurEffect` + macierz saturacji, przycina do kształtu, nakłada gradient powierzchni, refleks 1 px i obramowanie, a potem **ostrą** treść.
- Dwa poziomy sceny w `ChatraScreen`: karty i dymki próbkują samo tło; elementy pływające (nagłówek, dock, kompozytor, arkusz) próbkują tło razem z treścią pod sobą. Panel nigdy nie należy do sceny, którą próbkuje — brak rekurencji warstw (CH003, kryterium odbioru).
- Sigma CSS → promień HWUI: `r = (σ − 0,5) / 0,57735` (rozmycia 28 / 30 / 40 z wzorca).
- Matowy wariant (ograniczenie przezroczystości, S09) rysuje nieprzezroczystą powierzchnię bez rozmycia. W demo do testów: `--ez matte true`.
- Wymagane API: `RenderEffect` → **minSdk 31** (G02). Poniżej 31 dostępny byłby tylko wariant matowy.

## Zgodność ze wzorcem

Porównanie z goldenami Androida CH002 (`CH002_Android/run1`), tryb referencyjny 390 × 844 dp, piksele z różnicą kanału > 16/255:

| Ekran | Różnica | Uwagi |
| --- | --- | --- |
| S01 Start | 3,37 % | położenie bloków ± 1 dp (CH010) |
| S02 Skrzynka | 6,21 % | szkło kart, wyszukiwarki, filtra i docka |
| S03 Rozmowa | 6,65 % | szklane dymki, nagłówek i kompozytor nad przewijaną treścią |
| S04 Dodatki | 15,87 % (arkusz 3,63 %) | reszta to świadome odstępstwa: prawdziwa rozmowa pod arkuszem, CTA 54 dp |

Dwa kolejne rendery są identyczne bajtowo. Tekst nad panelami nie jest rozmyty (treść rysowana po warstwie rozmycia). Arkusz S04 nad szklanym nagłówkiem i dymkami S03 to przypadek nakładających się paneli — renderuje się poprawnie.

## Wydajność (120 Hz, budżet 8,3 ms)

Scenariusz: 20 przeciągnięć (10 w każdą stronę, 250 ms), `dumpsys gfxinfo` po resecie, dwa przebiegi na wariant. „Jank” = klatki po terminie (`Frame deadline missed`).

| Scenariusz | Wariant | Klatki | Jank | Klatka p50 / p90 / p99 | GPU p50 / p90 / p99 | Pamięć graficzna |
| --- | --- | --- | --- | --- | --- | --- |
| S03 przewijanie rozmowy | szkło | 835 / 812 | 0,96 % / 0,86 % | 14 / 18–22 / 26 ms | 9 / 12–16 / 15–21 ms | 185 MB |
| | mat | 830 / 900 | 0,36 % / 0,56 % | 15–16 / 16–17 / 18–19 ms | 11–12 / 14 / 15–17 ms | 109 MB |
| S04 arkusz nad rozmową, przewijanie wariantów | szkło | 676 / 675 | 3,25 % / 3,85 % | 12 / 17–19 / 46–48 ms | 6 / 11–12 / 25 ms* | 262 MB |
| | mat | 690 / 691 | 2,03 % / 2,03 % | 10 / 11–15 / 22–30 ms | 6 / 7–10 / 14–15 ms | 119 MB |

\* Jeden przebieg zgłosił GPU p99 = 4950 ms — to wartość graniczna histogramu `gfxinfo` (brak danych), nie rzeczywisty czas.

S02 w normalnym trybie nie przewija się (lista mieści się na ekranie 891 dp), więc nie dała pomiaru przewijania; dock nad treścią jest pokryty przez S03 (nagłówek i kompozytor nad przewijaną listą).

Wnioski (przed optymalizacją):
- Mediana klatki szkła nie jest gorsza niż matu — koszt pojawia się w ogonie (p90/p99) i w liczbie spóźnionych klatek: S03 +0,5 pp, S04 +1,5 pp.
- **Pamięć graficzna: szkło +76 MB (S03) i +143 MB (S04)** względem matu. Każdy panel trzyma własną warstwę rozmycia w pełnej rozdzielczości z marginesem 3σ. Na flagowcu to akceptowalne, na urządzeniu z 4–6 GB RAM — ryzyko.

## Optymalizacja: rozmycie w obniżonej rozdzielczości (07.11.2026)

Warstwa rozmycia każdego panelu jest nagrywana w skali `k = clamp(16 px / σ, ¼, 1)` (σ w pikselach), rozmywana z σ·k i rysowana z powrotem w skali 1/k. Przy DPR 3,5 wszystkie szkła wzorca (σ 20–40 dp = 70–140 px) mają k = ¼, czyli 1/16 pikseli. Rozmycia ≤ 16 px zostają w pełnej rozdzielczości. Kod: `downsample()` w `core/designsystem/glass/Glass.kt`, test `DownsampleTest`.

Obraz: różnica względem pełnej rozdzielczości ≤ 1/255 na kanał; wyniki porównania z goldenami S01–S04 bez zmian (3,37 / 6,21 / 6,65 / 15,86 %).

| Scenariusz | Wariant | Jank | Klatka p50 / p90 / p99 | GPU p50 / p90 / p99 | Pamięć graficzna |
| --- | --- | --- | --- | --- | --- |
| S03 | szkło, pełna rozdzielczość | 0,86–0,96 % | 14 / 18–22 / 26 ms | 9 / 12–16 / 15–21 ms | 185 MB |
| | **szkło, k = ¼** | **0,34–0,35 %** | 13–15 / 17–18 / 20–21 ms | 8–10 / 12 / 14–16 ms | **129–132 MB** |
| | mat | 0,36–0,56 % | 15–16 / 16–17 / 18–19 ms | 11–12 / 14 / 15–17 ms | 109 MB |
| S04 | szkło, pełna rozdzielczość | 3,25–3,85 % | 12 / 17–19 / 46–48 ms | 6 / 11–12 / 25 ms | 262 MB |
| | **szkło, k = ¼** | **2,34–2,47 %** | 12 / 16 / 30–31 ms | 6 / 10 / 13–14 ms | **146 MB** |
| | mat | 2,03 % | 10 / 11–15 / 22–30 ms | 6 / 7–10 / 14–15 ms | 119 MB |

Koszt szkła względem matu spadł z +76/+143 MB do **+21/+27 MB**, a p99 klatki S04 z 46–48 do 30–31 ms (poziom matu). Wspólna warstwa dla paneli o tej samej scenie nie jest już potrzebna.

## Słabsze urządzenie: Galaxy A15 5G (07.11.2026)

SM-A156B, MediaTek Dimensity 6100+ (MT6835), 3,5 GB RAM, Android 16 / API 36, 1080 × 2340, density 420, **90 Hz (budżet 11,1 ms)**. Ten sam build release. Po zmianie rozmiaru ekranu współrzędne przeciągnięć przeliczone proporcjonalnie.

| Scenariusz | Szkło, pełna rozdzielczość | Szkło, k = ¼ | **Szkło, k = ¼ + cache nagrania (stan końcowy)** | Mat |
| --- | --- | --- | --- | --- |
| S03 przewijanie rozmowy — jank | 0,56–1,46 % | 0,48 %¹ | **0,28–0,48 %** | 0,15 % |
| S03 pamięć graficzna | 94 MB | 55 MB | **54–56 MB** | 46 MB |
| S04 przeciąganie w arkuszu — jank | 30,4–33,7 % | 10,5–11,5 % | **8,6–10,6 %** | 0,2 % |
| S04 pamięć graficzna | 134–136 MB | 66–69 MB | **65–68 MB** | 53 MB |
| S04 stukanie w warianty — jank | — | — | **24,6 %** | **24,6 %** |

¹ Pierwszy przebieg po instalacji (24,9 %) to rozgrzewka (kompilacja ART/shaderów), pominięty.

Analiza (framestats i atrace, S04 przeciąganie): układ i nagrywanie < 1 ms na klatkę; różnica jest na RenderThread — wysyłanie komend 24 ms (szkło) vs 8 ms (mat), w tym renderowanie warstw (`flush layers`) 7,0 vs 2,5 ms na klatkę. Lista wariantów prawie się nie przewija, więc przeciąganie to głównie systemowe rozciąganie krawędzi (overscroll), przy którym HWUI ponownie renderuje warstwy z rozmyciem. W spoczynku aplikacja nie rysuje żadnych klatek.

Wnioski dla A15:
- Optymalizacje działają także tu: pamięć szkła z +41/+82 MB do **+8/+14 MB** względem matu, jank S04 trzykrotnie mniejszy.
- **Przewijanie rozmowy (S03) mieści się w kryterium** (≤ 5 %): 0,3–0,5 %.
- **Zwykła obsługa arkusza S04 (stuknięcia) nie zależy od szkła** — szkło i mat dają identyczne 24,6 % (30 spóźnionych klatek na 30 stuknięć).
- **Dochodzenie (S26 Ultra, 07.11.2026):** to cecha każdej pojedynczej zmiany stanu w całej aplikacji, nie arkusza ani szkła. Każde stuknięcie daje 2 klatki, z których ok. 1 jest spóźniona — tak samo w S04 (warianty) i S02 (filtr „Wszystkie/Nieprzeczytane”: 50 % w obu wariantach). Wątek UI ≈ 5 ms na stuknięcie (rekompozycja 1,7, układ 1,7, nagrywanie 2,6 ms), natomiast **GPU 12–15 ms na klatkę po bezczynności** (w ciągłym przewijaniu ≈ 9 ms). Sprawdzone i wykluczone: szkło (mat — bez zmian), cienie CSS (build bez cieni — bez zmian), ponowny układ tekstów przy zmianie obramowania (stała szerokość — bez zmian), osobna warstwa treści paneli (bez zmian). Stuknięcia w miejsce bez akcji nie generują klatek. Prawdopodobna przyczyna: koszt GPU pierwszej klatki po bezczynności (taktowanie GPU) przy częściowym przerysowaniu sceny z gradientami tła — do potwierdzenia profilerem GPU (Android GPU Inspector). Wpływ na użytkownika: jedna klatka opóźnienia reakcji na stuknięcie, nie przycinanie ruchu.
- Jedyny przypadek ponad kryterium to rozciąganie krawędzi listy w arkuszu (≈ 10 % vs 0,2 % w macie). Krótki efekt przy krawędzi, nie przewijanie treści; akceptowalny, do ponownej oceny, jeśli UX uzna go za istotny (opcja: wyłączyć overscroll tylko w arkuszach albo mat arkusza na urządzeniach < 120 Hz).

Sprawdzone, ale bez efektu: osobna warstwa graficzna dla treści panelu (przerysowanie treści bez ponownego rysowania szkła) — wynik bez zmian, wycofane. Zostawione: nagrywanie warstwy rozmycia tylko po zmianie położenia lub rozmiaru panelu (zmiany sceny przechodzą przez RenderNode) — obraz identyczny co do piksela z wersją bez cache, także po przewinięciu.

## Skrzynka z setkami rozmów na A15 (14.11.2026)

Build `fullDebug` (QA), ~450 prawdziwych wątków, przewijanie środka listy w obie strony. GPU = czas `waiting for GPU completion` z `atrace -a app.chatra.qa gfx view` (bufor `-b 8192`), budżet 90 Hz = 11,1 ms.

Wcześniej (osobna poprawka) lista rozmów stała się wirtualizowana, a szkło karty rysuje się tylko pod widocznym fragmentem. Zostało ograniczenie GPU: **10,4–10,7 ms p50 / 11,3–11,6 ms p90** na klatkę, czyli na granicy budżetu.

Pomiar składników (każdy wyłączony osobno, build eksperymentalny): gradienty tła −2,7 ms, cienie −1,8, gradient powierzchni karty −1,8, rozmycie −0,8, obramowanie −0,6, refleks −0,4, ikony ≈ 0. Kosztem nie jest samo rozmycie, tylko liczba pełnoekranowych przebiegów malowanych od nowa w każdej klatce przesuwania treści.

Zmiana: statyczne warstwy są trzymane przez HWUI jako gotowe tekstury (`CompositingStrategy.Offscreen`).
- Tło sceny w `ChatraScreen` (gdy ma warstwy radialne) — dotyczy wszystkich ekranów. Szkło nadal próbkuje nagranie sceny.
- Szkło karty rozmów w S02 (cień, rozmycie, gradient, refleks, obramowanie) w jednej warstwie wielkości listy. W środku listy karta stoi, więc warstwa nie jest renderowana ponownie.

| Scenariusz | Przed | Po |
| --- | --- | --- |
| Przewijanie środka listy — GPU p50 / p90 | 10,4–10,7 / 11,3–11,6 ms | **4,9 / 5,5 ms** |
| Przewijanie przy górze listy — GPU p50 / p90 | 8,6–9,4 / 11,8–12,2 ms (tło już w teksturze, karta bez warstwy) | **4,1 / 7,3 ms** |
| Powrót z wyszukiwania (chowanie IME) — `gfxinfo` GPU p50 / p90 | 13–17 / 20–21 ms | **7 / 9 ms** |

Obraz: wariant z warstwami i bez nich różni się najwyżej o 3/255 na kanale (0 % pikseli > 2); demo S02 w trybie referencyjnym identyczne co do bajtu. Pamięć: dwie tekstury wielkości ekranu (≈ 2 × 10 MB na 1080 × 2340); cała pamięć GPU aplikacji na S02 ≈ 52 MB.

**Przewijanie przy górze listy (14.11.2026).** Po powyższej zmianie zostało ≈ 16–18 % spóźnionych klatek, ograniczonych przez wątek UI. Pomiar składników (flagi eksperymentalne, 3 × przeciągnięcie w obie strony przy górze listy):

| Wariant | Spóźnione klatki | doFrame p50 | `AndroidOwner:draw` p50 |
| --- | --- | --- | --- |
| stan wyjściowy | 16,0 % | 25 ms | 2,1 ms (p90 14,5) |
| karta zamrożona | 3,6 % | 15 ms | 4,4 ms |
| bez cieni | 4,0 % | 18 ms | 4,9 ms |
| szkło nie przerysowuje się przy ruchu / bez rozmycia | 17,4 / 16,9 % | 22 / 24 ms | — |

Przyczyny i zmiany:
1. Fragment karty zmieniał wysokość w każdej klatce (góra jechała z listą, dół stał za ekranem), co wymuszało ponowne wygenerowanie dużego rozmytego cienia. Teraz, gdy jeden brzeg karty jest poza ekranem, fragment ma stałą wysokość (ekran + zapas) i tylko się przesuwa: **16 % → 7–7,7 %**.
2. Każdy szklany panel przy ruchu przerysowywał węzeł tylko po to, by przesunąć próbkę sceny. Teraz scena jest w osobnej warstwie, a przesunięcie to jej właściwość `translationX/Y` (RenderNode); HWUI i tak przelicza rozmycie. `AndroidOwner:draw` p50 5,7 → 1,6 ms, **7–7,7 % → 3,2–3,3 %**. Środek listy bez zmian (3,6 %).

Obraz: stary i nowy `GlassNode` — demo S02, S03, S07 oraz skrzynka QA po powtarzalnym przeciągnięciu (`input motionevent` z zatrzymaniem palca, bez rzutu) identyczne co do bajtu.

**Release na A15 (14.11.2026).** Te same scenariusze na `fullRelease` (R8, bez `debuggable`), zbudowanym lokalnie w pakiecie `app.chatra.qa` z kluczem debug i wgranym przez `adb install -r` (bez kompilacji AOT z profilu bazowego). Po dwa przebiegi:

| Scenariusz | `fullDebug` | `fullRelease` |
| --- | --- | --- |
| Przewijanie przy górze listy — spóźnione klatki | 3,2–3,3 % | **1,0–1,1 %** |
| ↳ `AndroidOwner:draw` p50 / `doFrame` p50 | 1,6 ms / — | 0,6 ms / 11 ms |
| Przewijanie środka listy — spóźnione klatki | 3,6 % | **0–0,2 %** |
| ↳ GPU p50 / p90 | 4,9 / 5,5 ms | 5,1 / 5,7 ms |
| Powrót z wyszukiwania — `gfxinfo` p50 / p90 | 36 / 65 ms (7 % spóźnionych) | **22–24 / 27–34 ms** (1,7–4,7 %) |

Wątek UI w release jest 2–3 × szybszy; GPU bez zmian, więc w środku listy ograniczeniem pozostaje rysowanie (≈ 5 ms z budżetu 11,1 ms).

## Ryzyka i działania

1. **Minimalne API (31)**: słabszy sprzęt jest już zmierzony (A15, powyżej), ale na Androidzie 16. Brakuje sprawdzenia `RenderEffect` i wyglądu na Androidzie 12 (emulator albo urządzenie) — ryzyko głównie zgodności, nie wydajności. Przełącznik S09 (mat) zostaje jako wyjście awaryjne.
2. **WebView jako kontrola**: niewykonane. Natywne szkło osiąga zgodność 3–7 % bez mostu WebView, więc wariant hybrydowy nie jest potrzebny do wierności, a dokładałby powierzchnię bezpieczeństwa (G03: „brak dostępu treści z sieci do mostu natywnego”). Rekomendacja: nie rozwijać.

## Status

`poc_completed_awaiting_api31_and_approval` — POC, optymalizacja oraz pomiar flagowca i słabszego urządzenia wykonane, rekomendacja Compose. G03 pozostaje otwarta do sprawdzenia na API 31 i zatwierdzenia (Lider Android UI + QA wydajności). Surowe liczby: [CH003_Pomiary.json](CH003_Pomiary.json).
