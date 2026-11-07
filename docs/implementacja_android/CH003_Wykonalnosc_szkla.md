# CH003 — Wykonalność natywnego szkła (G03)

Data: 07.11.2026 · Urządzenie: Samsung Galaxy S26 Ultra (SM-S948B), Android 17 / API 37, 1440 × 3120, density 560 (DPR 3,5), 120 Hz · Build: `demoRelease` (R8, podpis kluczem debug wyłącznie do testu)

## Wynik w skrócie

**Rekomendacja G03: Jetpack Compose z własnym szkłem na `RenderEffect` (blur + saturacja) — bez WebView.** Szkło jest zgodne z wzorcem (S01 3,4 %, S02 6,2 %, S03 6,7 %, arkusz S04 3,6 % pikseli > 16/255), tekst nad panelami jest ostry, nakładające się panele działają bez rekurencji, a na flagowcu koszt klatki jest akceptowalny. **Do formalnego zamknięcia G03 brakuje dwóch dowodów:** pomiaru na urządzeniu z docelowego minimum (API 31, średnia/niska półka) i zatwierdzenia przez lidera Android UI + QA wydajności. Pierwotnie wysoki koszt pamięci graficznej (+76/+143 MB) zredukowano próbkowaniem rozmycia w obniżonej rozdzielczości — szkło kosztuje teraz +21/+27 MB względem matu, a spóźnione klatki są praktycznie na poziomie matu (sekcja „Optymalizacja”).

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

## Ryzyka i działania

1. **Urządzenie z minimum (API 31, średnia półka)**: powtórzyć tabelę; kryterium wejścia proponowane: jank ≤ 5 % w S03/S04, p90 ≤ 2 × budżet, pamięć graficzna ≤ 150 MB. Przy niespełnieniu — automatycznie mat na urządzeniach `isLowRamDevice` / niskiej klasy wydajności (z przełącznikiem S09).
2. **WebView jako kontrola**: niewykonane. Natywne szkło osiąga zgodność 3–7 % bez mostu WebView, więc wariant hybrydowy nie jest potrzebny do wierności, a dokładałby powierzchnię bezpieczeństwa (G03: „brak dostępu treści z sieci do mostu natywnego”). Rekomendacja: nie rozwijać.

## Status

`poc_completed_awaiting_min_device_and_approval` — POC, optymalizacja i pomiar flagowca wykonane, rekomendacja Compose. G03 pozostaje otwarta do pomiaru na urządzeniu z minimum i zatwierdzenia (Lider Android UI + QA wydajności). Surowe liczby: [CH003_Pomiary.json](CH003_Pomiary.json).
