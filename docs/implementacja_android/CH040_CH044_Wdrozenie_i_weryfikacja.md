# CH040–CH044 — wygląd, wysyłanie i porządek

Gałąź `android/ch040-ch044`, baza `ec6572a` (CH035–CH039), 12.11.2026.

## CH040

DataStore przechowuje wszystkie opcje S09 i osobną mapę nadpisań rozmów. Cztery kompozycje mają tokeny jasne i ciemne; Auto śledzi system, także dla pasków systemowych. Wspólne tokeny obejmują tekst, linki, powierzchnie, tła i szkło. Ograniczenie przezroczystości daje nieprzezroczyste powierzchnie; kontrast ma osobną paletę. Gęstość zmienia odstępy list i historii. Skala 1–7 mnoży skalę systemową, nie zastępuje jej. Podgląd rośnie przy dużym foncie. Opcje dostępności nie zależą od Pro.

W szczegółach rozmowy można nadpisać kompozycję albo wrócić do globalnego wyglądu. Reset globalny nie usuwa tych wyjątków; reset rozmowy usuwa wyłącznie jej nadpisania. Globalny duży tekst, kontrast i ograniczenie przezroczystości mają pierwszeństwo. Model i zapis pozwalają rozszerzyć nadpisania w CH068; własne zdjęcia tła, dowolne kolory i kształty dymków pozostają zakresem CH068. Wygląd jest lokalny i nie jest wysyłany odbiorcy SMS.

## CH041

SIM, opóźnienie 0/3/5/10 s, licznik, domyślny wariant plików i preferencja proponowania linku mają zapis DataStore. Edytor nowych wiadomości reaguje na zmianę licznika również przy niezmienionej SIM. Nowy wybór plików dziedziczy wariant ustawień, ale istniejący szkic zachowuje swój wybór. Domyślna Kopia bez lokalizacji tworzy oczyszczoną kopię już podczas importu do szkicu, z zachowaniem prywatnego oryginału. Nieobsługiwana konwersja kończy się błędem przed dołączeniem pliku. Zlecenie kolejki utrwala termin anulowania przy przyjęciu; późniejsza zmiana preferencji nie przepisuje operacji ani harmonogramu. Brak zapisanej SIM wymaga nowego wyboru.

HD i wysyłanie linków pozostają niedostępne do zadań serwera. Zapis preferencji nie jest zgodą na upload ani automatyczną zmianę transportu. UI opisuje ten stan. Wysłanie MMS nadal wymaga świadomego wyboru MMS i akcji Wyślij.

## CH042

Room przechowuje lokalne reguły według `recipientKey`: format lokalny PL, +48 i 0048 identyfikują ten sam numer. Nadawców alfanumerycznych też można blokować. Nieznany nadawca oznacza brak rozpoznanego kontaktu, nie spam; oznaczenie znika po rozpoznaniu kontaktu.

Blokada w szczegółach i w ustawieniach zapisuje te same dane. Ukrywa rozmowę ze Skrzynki oraz usuwa jej powiadomienia i skróty; SMS/MMS nadal trafiają do historii systemowej. Wyszukiwanie pozwala dotrzeć do zachowanej historii; rozmowa pokazuje blokadę. Odblokowanie przywraca widoczność i powiadomienia bez kasowania wiadomości. To blokada lokalna: nie modyfikuje systemowej listy blokad, nie blokuje połączeń ani dostarczenia przez operatora. Mutacje przy utracie roli/dostępu pokazują błąd.

## CH043

Migracja Room 5→6 dodaje `LocalBlock`, `TrashEntry` i `TrashRef`. Przeniesienie jest jedną transakcją zapisującą konkretny zbiór transport/ID/czas, tytuł i termin wygaśnięcia. To tombstone wiadomości, nie flaga całego wątku. SMS i MMS mają rozłączne klucze. Nowa wiadomość tego samego wątku nie należy do poprzedniego zakresu. Lokalne operacje kolejki i przyszłe wysyłki pozostają poza koszem; Chatra HD nie tworzy jeszcze wiadomości ani referencji wymagających usuwania.

Projekcje skrzynki, wiadomości, nieprzeczytanych, galerii, powiadomień i wyszukiwania respektują zakres kosza. Wyszukiwanie sprawdza ukrycie także przy odczycie oryginału; odbudowa usuwa dokumenty kosza z FTS. Przywracanie usuwa tylko tombstone, bez kopiowania wiadomości.

Odzyskiwanie trwa 30 dni. Automatyczne sprzątanie uruchamia się przy starcie, odzyskaniu dostępu i w okresowym workerze naprawy; Android może opóźnić pracę w tle. Nie otwieramy historii przed pierwszym odblokowaniem telefonu. Wcześniejsze trwałe usunięcie wymaga jawnego potwierdzenia w UI. Po rozpoczęciu purge przywracanie jest blokowane, a nieudana operacja pozostaje w koszu do ponowienia. Każde usunięcie ogranicza się do konkretnego ID i czasu wiadomości, sprawdza wynik w providerze oraz dopiero potem usuwa odtwarzalną projekcję. Pozostałe tombstone trwają do zakończenia całego zakresu. Powtórzenie po awarii jest idempotentne. Usunięcie rodzica MMS usuwa jego części przez provider. Nowe SMS/MMS tego samego wątku pozostają.

Kosz ukrywa tylko w Chatrze. Purge usuwa z historii systemowej, więc zmiana dotyczy także innych klientów SMS. Skasowanie danych aplikacji usuwa lokalne tombstone; wiadomości nadal istniejące w providerze mogą pojawić się po ponownym imporcie.

## CH044

Prywatność i pomoc są lokalne i dostępne z S01 oraz S10. Opisują provider, kopię indeksu FTS, pliki prywatne, brak automatycznych raportów, wyłączone kopie danych aplikacji, usuwanie danych, kosz i konsekwencje dla historii systemowej. Pomoc wyjaśnia SMS/MMS, brak RCS/iMessage, wybór SIM, opóźnienie, exact alarm, force stop i niepewny wynik transportu. Kontakt właściciela nie został wymyślony. Nie ma obietnicy E2EE. Link do [pomocy Google o uprawnieniach](https://support.google.com/android/answer/9431959?hl=pl) otwiera przeglądarkę wyłącznie po akcji użytkownika, bez treści rozmowy ani raportu.

## Weryfikacja

Końcowy build demo/full i oba pakiety instrumentacji: **PASS**. **91/91 JVM**, **39/39 przypadków aplikacji Android** (34,438 s) i **3/3 przypadki bazy** z migracją do v6 (3,558 s). Lint: 0 błędów, 17 ostrzeżeń demo i 47 full. Przebieg końcowy używa poprawionego testu oczekującego na StateFlow. Logi i sumy końcowych APK są w [CH040_CH044_Android/verification.json](CH040_CH044_Android/verification.json).

Próby Androida wykonano w AVD Android 16 / API 36 (`BP41.250916.009.A1`, arm64), wyłącznie z danymi syntetycznymi. Obejmują nowe blokady i kosz, restart magazynu DataStore, wszystkie dotychczasowe 21 regresji kolejki/providera, 3 regresje powiadomień, odczyt oryginalnego źródła wyszukiwania i 3 przypadki FTS oraz 4 regresje plików/szablonów. Weryfikacja domyślnej kopii bez lokalizacji używa tej samej ścieżki importu co kompozytor: bada GPS, komentarz EXIF, segmenty APP1, orientację obrazu oraz identyczne bajty zachowanego oryginału. Nie powtarzano benchmarku 50 tys. ani wysyłek u operatorów.

Obrazy końcowe: [reset S09](CH040_CH044_Android/s09-reset.png), [ciemny Grafit](CH040_CH044_Android/s09-dark-graphite.png), [Auto jasny](CH040_CH044_Android/s09-auto-light.png), [font 2.0](CH040_CH044_Android/s09-font2-top.png), [prywatność offline](CH040_CH044_Android/privacy-offline-font2.png) i [pomoc offline](CH040_CH044_Android/help-offline-font2.png). Auto zmieniło tło między RGB (23,26,39) a około (208,215,229); restart zachował ciemny Grafit. Otwarcie zewnętrznej pomocy rozwiązało się do pierwszego uruchomienia Chrome. Nie akceptowano regulaminu przeglądarki i nie oceniano ładowania zewnętrznej strony.

Na wariancie full dodano jeden syntetyczny SMS przez konsolę emulatora. Ręcznie sprawdzono nadpisanie Grafit i powrót do globalnego wyglądu, przeniesienie do kosza, odzyskanie, blokadę ze szczegółów i odblokowanie w ustawieniach. Następnie ponownie przeniesiono ten SMS i jawnie potwierdzono purge; lista pokazała pusty kosz. Dane tej próby usunięto. Zrzuty: [wygląd rozmowy](CH040_CH044_Android/conversation-local-graphite.png), [blokada](CH040_CH044_Android/blocked.png), [kosz](CH040_CH044_Android/trash.png).

Końcowa kontrola S09 zachowuje ramkę 390×844 dp, DPR 3,5 i fontScale 1,0. Porównanie do wcześniejszego natywnego S09 z 10.11 (API 35) ma próg 16/255; render bieżący jest na API 36. Zmiana ponad próg dotyczy 4 709 z 4 032 210 pikseli (**0,117%**). To regresja względem natywnego obrazu, a nie zamknięcie różnic względem HTML/G03. Duży font otrzymał dwie kolumny kompozycji i pionowe segmenty, gdy najszersza etykieta nie mieści się w równych kolumnach.

W przebiegu 35 przypadków jeden test nowej mapy nadpisań początkowo zakładał natychmiastową aktualizację StateFlow po zapisie DataStore. Poprawiono oczekiwanie na emisję i dodano kontrolę ponownie otwartego magazynu. Izolowany przypadek przeszedł w 0,109 s; aplikacji z tego powodu nie zmieniano. Historyczny błąd zachowuje `app-before-test-wait-fix.log`.

Pierwsza próba oględzin wykonywana przy równoległej kompilacji zakończyła się ANR System UI/launchera emulatora. Zakończono budowę, zatrzymano Gradle i uruchomiono AVD ponownie. Ten obraz (`environment-system-ui-anr.png`) dokumentuje nieudany pomiar środowiska, nie odbiór ekranu Chatry.

Wyniki końcowe i sumy APK: `CH040_CH044_Android/verification.json`. Pierwsze 7/7 nowych testów Androida przeszło przed końcowym rozszerzeniem nadpisań wyglądu; zachowano osobny log `organization-initial.log`. Ten przebieg nie jest przedstawiany jako test końcowego APK.

Pozostają odbiór fizyczny na OEM i API 31, TalkBack oraz akceptacja wizualna G03/G09. Próby operatorów MMS, serwer HD, kontakt wsparcia i Pro mają wcześniejsze lub następne zadania. Obecny etap nie zamyka tych odbiorów.

Kontrakty platformy: [migracje Room](https://developer.android.com/training/data-storage/room/migrating-db-versions). Migracja nie używa destrukcyjnego odtwarzania bazy.
