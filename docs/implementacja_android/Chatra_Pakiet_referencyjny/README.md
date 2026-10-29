# Pakiet referencyjny Chatra

Oryginalny HTML jest niezmienionym plikiem użytkownika. SHA256 80e7652c4de8a483b39a38e6cd319524817af11986499f0f7aee28d4efa4cbe3.

Ekrany PNG mają dokładnie 390 × 844 px. Zapisano je w Chrome na macOS przy DPR 1, bez ingerencji w zawartość ekranów. Usunięto jedynie prezentacyjny promień i cień iframe; wysokość podpisów plansz ustawiono na całkowitą wartość, aby uniknąć zaokrąglenia zrzutu do 845 px. Są to referencje renderu dostarczonego pliku, nie wyniki implementacji Androida. Typografia zależy od systemowego stosu fontów; przed odbiorem natywnym obowiązuje zadanie CH002.

Szablony HTML są odczytem wewnętrznych dokumentów i służą do analizy. Mają identyfikatory zasobów bundlera, więc do interaktywnego otwarcia należy używać oryginalnego pliku. style_zrodlowe.json zachowuje deklaracje inline. pomiary_przegladarki.json zawiera faktyczne obszary wybranych elementów i style computed; różnice wobec deklarowanych wysokości wynikają m.in. z content-box i obramowania. W odwzorowaniu liczy się wynik renderowania.

Ikony SVG zachowują ścieżki źródłowe; atrybut sc-camel-view-box został znormalizowany do viewBox. Występuje currentColor oraz warianty rozmiarów i koloru. Miniatury zdjęć są gradientowymi placeholderami źródłowego projektu.

Fonty web to niezmienione fragmenty WOFF2 z oryginału, a nie kompletna biblioteka fontów Androida. Zachowano komentarz licencyjny OFL. Ustalenie zgodnych TTF/OTF, metryk i wymaganych praw dla wybranego fontu należy do CH007.
