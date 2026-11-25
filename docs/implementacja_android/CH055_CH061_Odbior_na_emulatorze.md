# Odbiór Chatry na emulatorze

Zakres obejmuje otwarte pozycje CH055, CH057, CH060 i CH061. Osobne badanie CH072 obejmuje dokumentację i próbę publicznego API transportu na emulatorze. [Raport transportów](CH072_Transporty_operatorowe.md)

| Zadanie | Co mierzy lub sprawdza CI | Czego jeszcze wymaga pełny odbiór |
| --- | --- | --- |
| CH055 | Dziesięć ekranów w 390 × 844 dp. Obrazy aplikacji, nakładki 50%, mapy różnic i statystyki obszarów referencyjnych CH002. | Ręczne rozliczenie rozbieżności 1:1 i renderowania na fizycznym GPU. Zrzuty HD i dostawców poza fixture. |
| CH057 | Osobny przebieg rzeczywistego restartu emulatora po zatrzymaniu procesu. Aplikacja zachowuje termin, ponownie rejestruje alarm i nie tworzy próby wysyłki przed terminem. Istniejąca instrumentacja sprawdza wyścigi, utratę warunków, zmianę strefy i granice 59, 60 i 61 minut. | Restart z zabezpieczoną blokadą, Doze producentów i zachowanie fizycznego modemu. |
| CH060 | Galeria przy 360 dp i tekście 2,0, personalizacja przy 390 dp i 2,0 oraz alternatywa gestów przez akcję dostępności przy 430 dp i 1,3. Kontrast i redukcja przezroczystości są włączone. Odświeżanie galerii przy dużym tekście trafia do osobnego wiersza. | Ręczny odsłuch TalkBack, zewnętrzna klawiatura, IME, oba systemowe tryby nawigacji i wcięcia ekranów. |
| CH061 | Migracje Room z historycznych wersji i przerwanie transakcji 6→7. Ponowna próba zachowuje szkic, zaplanowany termin i kosz. APK wyklucza dane aplikacji z backupu i transferu. Po wyczyszczeniu lokalnych danych proces widzi SMS z Androida, a prywatny szkic i harmonogram już nie istnieją. | Aktualizacja poprzednio wydanego APK oraz próba transferu i odzyskania na fizycznym urządzeniu. |

W S04 demo przewija cały wybór mediów i przypina przycisk wysyłki o wysokości co najmniej 54 dp. W referencji HTML przycisk jest ściśnięty u dołu. S05 pozwala zawijać etykiety kart SIM i zwiększa wysokość przycisków przy długim tekście. Test przy 360 dp i skali 2,0 wybiera MMS w S04, a w S05 drugą kartę SIM i politykę spóźnienia. Przegląd wyglądu na fizycznym GPU i uzgodnienie różnic z referencją wciąż są częścią odbioru.

Zrzuty CH055 są robione z obszaru aplikacji po pokazaniu danych danego ekranu. Porównanie skaluje kopię zatwierdzonych referencji z 1365 × 2954 px do proporcjonalnego 780 × 1688 px metodą Lanczos. W raporcie są oryginalne skróty SHA-256 oraz zakresy obszarów odpowiadające pomiarom z CH002. Udział różniących się pikseli jest pomiarem, bez arbitralnego progu zaliczenia. Goldeny pozostają w repozytorium w oryginalnym rozmiarze. [Kontrakt referencji](CH002_Weryfikacja_wzorcow.md)

Workflow publikuje raporty w artefakcie `android-verification`. Katalog `app/build/reports/ui/reference` zawiera dziesięć zrzutów, porównania i JSON. Katalog `recovery` zawiera wyjścia instrumentacji przed i po restarcie oraz czyszczeniu danych. Osobny plik `transport-api37.txt` zawiera wynik próby klasy AMTS. Skrypty instrumentujące mają warunek jednorazowego emulatora, ponieważ test CH061 czyści dane aplikacji i wstawia syntetyczny SMS do testowego providera.
