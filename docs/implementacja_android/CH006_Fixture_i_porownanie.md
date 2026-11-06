# CH006 — katalog fixture i walidacja obrazów

**Stan 06.11.2026:** wykonano narzędzia i mapowanie źródła; weryfikacja na rendererze Android nadal oczekuje na CH004 i urządzenie.

1. `scripts/ch006_build_fixtures.mjs` odwzorowuje wszystkie 10 ekranów z `style_zrodlowe.json` bez ręcznego przepisywania i bez zmiany oryginalnego HTML. Utrwala stabilne ID elementów, teksty, style, atrybuty i warunki `pl-PL`, 390×844, Inter, fikcyjny zegar 29.10.2026.
2. `scripts/ch006_verify_fixtures.mjs` weryfikuje liczbę ekranów i elementów, kompletność znaczników scenariuszy, polskie znaki i powtarzalną serializację.
3. `scripts/ch006_compare_screens.py` porównuje 10 nowych PNG do CH002 goldens i tworzy raport z SHA-256, liczbą różniących się pikseli, mapą różnic i nakładką. `scripts/ch006_test_compare.py` testuje działanie na 10 sztucznych obrazach, z dokładnie jedną celową różnicą.
4. Markery stanu występują w oryginalnych ekranach: szkic (S02), błąd SMS i anulowanie po 3 s (S03), HD (S03–S04), spóźniony harmonogram (S06), ostrzeżenie grupowego MMS (S08). Są wyłącznie **reprodukcją źródła**, nie dowodem implementacji telekomunikacji.
5. Źródłowe `21/160 · 1 SMS` jest zachowane jako celowa niezgodność danych demonstracyjnych. CH025 wylicza licznik produkcyjnie i nie może używać tej wartości jako reguły.

Instrukcja: [core/testing/README.md](../../core/testing/README.md). Nie wykonano pomiaru GPU, renderu Android ani testów wysyłki/odbioru SMS. Żaden screen referencyjny nie został zmieniony.
