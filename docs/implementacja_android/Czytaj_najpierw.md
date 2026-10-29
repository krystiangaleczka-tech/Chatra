# Dokumentacja Chatra na Androida

Pakiet przygotowany 29 października 2026 na podstawie dostarczonego pliku Chatra — Liquid Glass.html.

## Zawartość

- Chatra_Dokumentacja_Android.docx — specyfikacja dziesięciu ekranów, kontrakt odwzorowania 1 do 1, style i pomiary, architektura, integracja SMS/MMS, harmonogram, model danych, HD, prywatność oraz testy. Załącznik zawiera obrazy wszystkich ekranów.
- Chatra_Backlog_Android.docx — 64 szczegółowe zadania z priorytetami, rolami, zależnościami, orientacyjnym nakładem i kryteriami odbioru.
- Wersje Markdown obu dokumentów — do pracy w repozytorium i dalszej edycji.
- Chatra_Plan_zadan.json — ten sam backlog w postaci danych; 64 zadania i 229 zależności. Nakład łącznie 155–240 osobodni dla całego zespołu, nie dni kalendarzowych.
- Chatra_Pakiet_referencyjny — oryginalny HTML, 10 obrazów 390 × 844, pomiary przeglądarki, szablony, style inline, teksty, ścieżki SVG i osadzone fonty web z informacją licencyjną.

## Jak zacząć realizację

Najpierw zamknąć CH001–CH003: zakres, rzeczywisty font i renderer wzorca oraz wykonalność szkła. Następnie wdrożyć podstawy projektu i system wizualny. Kolejne zadania prowadzić zgodnie z zależnościami; UI może być rozwijane niezależnie od telefonii po ustaleniu wspólnych modeli.

W dokumentacji rozróżniono dane odczytane ze wzorca, zaproponowane zachowanie aplikacji oraz decyzje, których plik HTML nie zawiera. Dotyczy to zwłaszcza ceny i zakresu Pro, backendu i bezpieczeństwa HD, pełnego ciemnego motywu oraz podwidoków ustawień. Wymaganie zgodności z dostarczonym wyglądem nie zostało zastąpione standardowym interfejsem Material.

To dokumentacja wdrożenia. Pakiet nie zawiera gotowego APK ani raportu testów istniejącej natywnej aplikacji. Zdjęcia w projekcie są gradientowymi placeholderami; zrzuty HTML wykonano w Chrome na macOS. Przed odbiorem Androida obowiązuje zamrożenie środowiska i fontu w CH002.
