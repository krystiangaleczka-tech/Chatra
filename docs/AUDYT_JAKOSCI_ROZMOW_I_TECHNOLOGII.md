# Chatra — jakość rozmów audio i wideo oraz wybór technologii

Data opracowania: 2026-10-29  
Status: ustalenia z rozmowy i rekomendacje do audytu; bez implementacji i bez wyników własnych benchmarków.

## 1. Cel i zakres

Celem jest ustalenie, czy własna aplikacja może zapewnić lepszą jakość rozmów niż obserwowana w WhatsAppie oraz czy użycie Rusta, C/C++ albo assemblera może dać przewagę.

Punktem wyjścia jest subiektywna ocena użytkownika:

- WhatsApp: około 5,5–6/10 za jakość wideorozmów.
- FaceTime między iPhone’ami: około 8/10.
- Oczekiwanie: obraz i głos możliwie bliskie bezpośredniej rozmowie.

Te oceny opisują doświadczenie użytkownika. Nie są wynikiem porównawczych pomiarów ani uniwersalnym rankingiem aplikacji.

Dotychczasowy zakres Chatra to aplikacja SMS/MMS. Rozmowy internetowe są tutaj rozpatrywane jako możliwy dalszy kierunek. Ten dokument nie zatwierdza ich dodania do MVP ani nie zmienia zakresu projektu. Nie zastępuje również pełnego audytu Textry i pozostałych aplikacji wiadomości.

## 2. Czy można uzyskać znacznie lepszą jakość?

Tak, w określonych warunkach można zbudować aplikację oferującą bardzo wyraźny obraz i naturalny dźwięk. Uzyskanie przewagi nad WhatsAppem wymagałoby jednak porównania działającego prototypu na tych samych urządzeniach, przy takim samym oświetleniu i podobnych warunkach sieciowych.

„10/10” nie jest specyfikacją techniczną. Trzeba przełożyć to oczekiwanie na mierzalne wymagania: szczegółowość obrazu, płynność, opóźnienie, naturalność głosu, odporność na utratę pakietów i stabilność po dłuższej rozmowie.

Warunki wpływające na jakość:

| Element | Wpływ na rozmowę |
| --- | --- |
| Kamera i światło | Określają jakość obrazu jeszcze przed kompresją; szum i rozmycie utrudniają zachowanie szczegółów. |
| Obróbka obrazu | Ekspozycja, odszumianie i wygładzanie wpływają na wygląd twarzy. |
| Kodowanie | Kodek i jego ustawienia decydują, ile szczegółów mieści się w dostępnym strumieniu danych. |
| Sieć obu uczestników | Znaczenie mają upload, download, opóźnienie, jego zmienność oraz utrata pakietów. |
| Dekodowanie i wyświetlanie | Zbyt wolne przetwarzanie może powodować zacięcia i narastające opóźnienie. |
| Temperatura i energia | Telefon musi utrzymać wydajność przez całą rozmowę, a nie tylko przez pierwsze minuty. |

Wideorozmowa wymaga niewielkiego opóźnienia. Wielosekundowe buforowanie, przydatne przy oglądaniu filmów, utrudniałoby normalną wymianę zdań. Przy ograniczeniach sieci lub urządzenia potrzebna jest adaptacja jakości. WebRTC opisuje m.in. dostosowywanie liczby klatek i rozdzielczości do dostępnych zasobów. [1]

## 3. WhatsApp a FaceTime

Apple deklaruje FaceTime HD w rozdzielczości 1080p przez Wi-Fi lub 5G na obsługiwanych urządzeniach. Sama deklarowana rozdzielczość nie oznacza, że każde połączenie działa z taką jakością. [2]

Możliwym wyjaśnieniem korzystnego odbioru FaceTime jest kontrolowanie przez Apple sprzętu, systemu i aplikacji. Ułatwia to dopracowanie całego procesu na określonych urządzeniach. Jest to wniosek architektoniczny, a nie dowód na przyczynę różnicy zauważonej przez użytkownika.

WhatsApp również stosuje zaawansowane technologie. Meta opisuje wdrożenie AV1 w swoich aplikacjach rozmów, w tym WhatsAppie, oraz dobieranie kodeka do możliwości nadawcy i odbiorcy. Firma wskazuje także ograniczenia wydajności, zużycia energii i zmiennych warunków sieciowych. [3]

Wniosek dla Chatra: przewagę trzeba budować przez dopracowanie i pomiary całej rozmowy. Sam wybór AV1 albo deklaracja „HD” nie wykazują przewagi nad konkurencją.

## 4. Kodowanie i dekodowanie — dwa różne zadania

### Kodowanie u nadawcy

Koder kompresuje obraz z kamery przed wysłaniem. Dobiera sposób zapisania informacji, a przy kompresji stratnej część szczegółów zostaje pominięta.

Bardziej wydajna implementacja może pośrednio poprawić jakość, jeżeli pozwoli w dostępnym czasie:

- zastosować dokładniejszy algorytm kompresji;
- utrzymać wyższą rozdzielczość lub liczbę klatek;
- ograniczyć spadki wydajności podczas nagrzewania telefonu.

Nie oznacza to automatycznej przewagi każdego kodera programowego nad sprzętowym. Trzeba porównać jakość, opóźnienie i zużycie energii.

### Dekodowanie u odbiorcy

Dekoder odtwarza obraz z otrzymanego strumienia. Przyspieszenie dekodera pomaga wtedy, gdy odbiorca nie nadąża z przetwarzaniem: może ograniczyć zacięcia, opóźnienie i konieczność obniżania parametrów rozmowy.

Szybszy dekoder nie odzyska szczegółów usuniętych podczas kompresji. Skalowanie lub dodatkowa rekonstrukcja obrazu to osobne mechanizmy; nie należy utożsamiać ich z odzyskaniem oryginalnej informacji.

## 5. Co dają Rust, C/C++ i assembler?

| Technologia | Możliwe korzyści | Ograniczenia i zastosowanie |
| --- | --- | --- |
| Rust | Wydajny kod natywny, kontrola pamięci i ochrona przed wieloma błędami jej obsługi. | Dobry kandydat do nowych modułów. Nie gwarantuje większej szybkości niż C/C++ ani ostrzejszego obrazu. |
| C/C++ | Dostęp do dojrzałych bibliotek multimedialnych i szerokie możliwości optymalizacji. | Praktyczny wybór przy integracji istniejących silników i kodeków. Wymaga starannego zarządzania pamięcią. |
| Assembler | Precyzyjna optymalizacja wybranych obliczeń dla konkretnej architektury procesora. | Uzasadniony dla niewielkich fragmentów wskazanych przez pomiary. Pisanie całej aplikacji w assemblerze znacznie zwiększa nakład pracy. |

Rust zarządza pamięcią przez reguły własności sprawdzane przez kompilator. Nie wymaga garbage collectora jako podstawowego mechanizmu zarządzania pamięcią. Te właściwości pomagają w budowie niezawodnego kodu, ale nie zapewniają automatycznie przewidywalnego czasu wykonania całego systemu. [4]

Ręczny assembler nie jest sam w sobie gwarancją przyspieszenia. Znaczenie mają algorytm, organizacja pamięci, wykorzystanie instrukcji wektorowych i jakość kodu generowanego przez kompilator.

Takie optymalizacje istnieją już w bibliotekach. Dekoder AV1 dav1d ma implementację w C oraz optymalizacje assemblerowe dla różnych architektur, w tym procesorów ARM używanych w telefonach. Skorzystanie z gotowej biblioteki pozwala wykorzystać tę pracę bez samodzielnego odtwarzania całego dekodera. [5]

## 6. Znaczenie sprzętowych kodeków

Telefon może mieć wyspecjalizowane bloki do kodowania i dekodowania obsługiwanych formatów wideo. Android udostępnia informacje o sprzętowym przyspieszaniu oraz deklarowanych możliwościach wydajnościowych kodeków. [6]

WebRTC ma na Androidzie mechanizmy wyboru sprzętowych koderów i korzystania z systemowego interfejsu MediaCodec. Obsługa konkretnego formatu zależy od dostępnego urządzenia i implementacji. [7]

W praktyce:

- Interfejs w Kotlinie może korzystać z tego samego sprzętowego kodeka co aplikacja z modułami w Rust lub C/C++.
- Zmiana języka kodu sterującego tym samym kodekiem nie podnosi sama z siebie jakości kompresji.
- Ważne są dobór kodeka, ustawienia, przesyłanie klatek między modułami oraz ograniczenie zbędnego kopiowania danych.
- Sprzętowe przetwarzanie jest istotnym kandydatem do ograniczenia obciążenia i zużycia energii. Jego rzeczywistą jakość i opóźnienie trzeba sprawdzić na urządzeniach docelowych.

Osobno należy sprawdzać sprzętowe kodowanie i dekodowanie. Możliwość odtworzenia danego formatu nie oznacza automatycznie możliwości jego sprzętowego kodowania.

## 7. Jakość samego głosu

Dźwięk wymaga znacznie mniejszego strumienia danych niż wideo, co ułatwia przesyłanie głosu w wysokiej jakości. Nadal ograniczają go mikrofon, hałas, echo, utrata pakietów oraz przetwarzanie sygnału.

Opus jest istniejącym kodekiem zaprojektowanym m.in. do rozmów i wideokonferencji, z obsługą różnych przepływności i pasm audio. Nie trzeba projektować własnego kodeka, żeby rozpocząć prace nad naturalnym brzmieniem rozmów. [8]

Wyższa częstotliwość próbkowania albo większa przepływność nie są samodzielną gwarancją lepszego głosu. Należy sprawdzać również:

- naturalność brzmienia po odszumianiu;
- skuteczność usuwania echa;
- zachowanie, gdy obie osoby mówią jednocześnie;
- działanie z głośnikiem telefonu i zestawami słuchawkowymi.

## 8. Proponowany kierunek techniczny dla Chatra

Poniższe punkty są rekomendacją do ewentualnego prototypu rozmów, a nie zatwierdzonym zakresem wdrożenia:

1. **Interfejs Android:** Kotlin i Jetpack Compose.
2. **Silnik rozmów:** sprawdzone rozwiązanie, np. WebRTC, z analizą rzeczywiście dostępnych kodeków.
3. **Przetwarzanie wideo:** wykorzystanie sprzętowych kodeków tam, gdzie wyniki pomiarów uzasadniają ich wybór.
4. **Własne moduły natywne:** Rust albo C/C++ w miejscach wymagających konkretnej optymalizacji lub współdzielenia kodu.
5. **Assembler:** przede wszystkim wykorzystanie optymalizacji istniejących bibliotek; własne fragmenty dopiero po wykazaniu ograniczenia w pomiarach.

W pierwszym prototypie warto sprawdzić bardzo dobre 1080p przy 30 klatkach na sekundę. Większa liczba klatek lub rozdzielczość powinny wynikać z testów jakości, temperatury i zużycia energii.

Można rozważyć tryb maksymalnej jakości, który na dobrym łączu pozwala zużyć więcej danych i energii. Nadal potrzebna byłaby adaptacja do warunków po obu stronach oraz sprawne przywracanie jakości po chwilowym pogorszeniu sieci.

Takie rozmowy działałyby przez internet jako odrębna usługa. Wymagałyby obsługi po obu stronach, np. przez zgodne aplikacje lub wspieranego klienta internetowego. Nie są funkcją wynikającą z samej obsługi SMS/MMS.

## 9. Jak sprawdzić rzeczywistą przewagę?

Przed optymalizacją należy ustalić, co ogranicza jakość. Samo obciążenie procesora nie wystarczy do oceny całej rozmowy.

| Obszar | Co mierzyć lub porównywać |
| --- | --- |
| Kamera | Jakość źródła przy dobrym i słabym świetle. |
| Kodowanie | Czas przetwarzania klatek, kodek, przepływność i zachowanie szczegółów. |
| Sieć | Opóźnienie, zmienność opóźnienia, utratę pakietów i dostępny upload. |
| Dekodowanie | Czas przetwarzania, pomijane klatki i narastanie opóźnienia. |
| Odtwarzanie | Faktyczną płynność i synchronizację obrazu z głosem. |
| Dłuższa rozmowa | Temperaturę, zużycie baterii i spadki wydajności. |
| Adaptacja | Czas reakcji na pogorszenie sieci oraz powrotu do wyższej jakości. |
| Głos | Naturalność, echo, odszumianie i jednoczesne mówienie. |

Porównania z WhatsAppem należy wykonywać na tych samych telefonach i przy możliwie jednakowych warunkach. Porównanie z FaceTime wymaga osobnej serii na urządzeniach obsługujących tę usługę.

Testy powinny obejmować dobre Wi-Fi, sieć mobilną, celowo ograniczone łącze oraz dłuższe rozmowy. Wyniki muszą rozdzielać problemy sieci, kamery i wydajności urządzenia.

Nie ma obecnie podstaw do deklarowania procentowego zysku z Rusta, C/C++ lub assemblera ani do obiecywania jakości „10/10”. Realnym kryterium decyzji jest poprawa zmierzona w działającym prototypie.

## 10. Źródła

Źródła pierwotne wykorzystane w rozmowie. Parametry urządzeń i obsługa kodeków wymagają ponownej weryfikacji przy implementacji.

1. [WebRTC — Video Adaptation](https://webrtc.googlesource.com/src/+/refs/heads/main/video/g3doc/adaptation.md) — dostosowywanie jakości do dostępnych zasobów.
2. [Apple — iPhone 17, dane techniczne](https://www.apple.com/pl/iphone-17/specs/) — deklarowane możliwości FaceTime HD.
3. [Meta Engineering — Adopting AV1 for Real-Time Communication at Scale, 2026-06-22](https://engineering.fb.com/2026/06/22/video-engineering/adopting-av1-for-real-time-communication-rtc-meta/) — wdrożenie AV1, dobór kodeka i ograniczenia urządzeń.
4. [The Rust Programming Language — What Is Ownership?](https://doc.rust-lang.org/book/ch04-01-what-is-ownership.html) — model zarządzania pamięcią.
5. [VideoLAN — dav1d](https://github.com/videolan/dav1d) — dekoder AV1 i optymalizacje w C oraz assemblerze.
6. [Android Developers — Media codecs](https://developer.android.com/media/optimize/performance/codec) — informacje o sprzętowym przyspieszaniu i możliwościach kodeków.
7. [WebRTC — HardwareVideoEncoderFactory.java](https://webrtc.googlesource.com/src/+/refs/heads/main/sdk/android/api/org/webrtc/HardwareVideoEncoderFactory.java) — wybór sprzętowych koderów na Androidzie.
8. [IETF — RFC 6716: Definition of the Opus Audio Codec](https://datatracker.ietf.org/doc/html/rfc6716) — standard kodeka audio do zastosowań interaktywnych.
