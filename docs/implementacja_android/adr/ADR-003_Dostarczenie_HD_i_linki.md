# ADR-003 · Dostarczenie HD, linki i retencja

Data: 13.11.2026. Uzupełnienie [ADR-002](ADR-002_Chatra_HD.md) dla CH050–CH054. Implementacja pilota jest lokalnie zweryfikowana; publiczne uruchomienie nadal wymaga odbioru G06/G07, dostawców i polityki danych. Brak E2EE.

## Transakcje i tożsamość

Finalize atomowo zapisuje pliki, kopertę oraz outbox nadawcy i odbiorcy w SQLite. Jedno losowe ID koperty pozostaje stałe przy utracie odpowiedzi i ponowieniach. Skrzynka ma monotoniczną sekwencję i kursor HMAC związany z instalacją. Zdarzenia envelope/sent/downloaded/revoked mają TTL 30 dni; pliki TTL 7 dni. Klient zapisuje stronę i kursor w jednej transakcji Room v7. Po utracie lub wygaśnięciu kursora odtwarza skrzynkę, deduplikuje po ID i respektuje trwałe znaczniki lokalnego usunięcia.

Zmiana względem CH047: zweryfikowany numer routingu jest dodatkowo przechowywany jako AES-GCM z losowym nonce, AAD=ID instalacji i kluczem wyprowadzonym z sekretu usługi. HMAC nadal służy do wyszukiwania i limitów. Routing wymaga odtworzenia adresu nadawcy, aby przypisać kopertę do właściwej rozmowy. Numer nie trafia do push ani publicznego manifestu. Podmiana klucza/urządzenia odwołuje stare wiązanie i usuwa jego transfery; drugi uczestnik dostaje zdarzenie cofnięcia. Lokalnie pobrana historia nie jest automatycznie przenoszona na nowe urządzenie. Migracja dawnych kopert zachowuje porcje i klucze idempotencji, ale dawne wiązania muszą ponownie zweryfikować numery przed doręczeniem.

## Push i pobieranie

Opcjonalny FCM HTTP v1 wysyła tylko niejawny identyfikator zdarzenia, z normalnym priorytetem i wspólnym collapse key. Stan pochodzi z uwierzytelnionej skrzynki, więc utrata, scalenie lub powtórzenie push są bezpieczne. Durable outbox ponawia po błędach dostawcy; nieaktualne tokeny są usuwane. Android nie inicjalizuje FCM przed aktywacją HD i nie zawiera analityki. Oprócz push synchronizuje przy starcie, ręcznie i okresowo przez WorkManager.

Odbiorca sam rozpoczyna pobieranie. Domyślna sieć jest nietaryfowa; zgoda na transfer taryfowy jest osobna. Prywatne porcje 256 KiB mają trwałe offsety. Pełny SHA-256 i rozmiar muszą się zgadzać, a importer Androida niezależnie rozpoznaje format. Kolejne lokalne pliki są zapisywane w Room, przed receipt. Awaria po zapisaniu receipt na serwerze powoduje idempotentne powtórzenie, bez ponownego pobierania. Push nie ustawia downloaded, a downloaded nie ustawia read. Widoczna karta HD ma osobny lokalny stan odczytu.

## Link

Docelowy numer odbiorcy linku pozostaje w telefonie. Serwer dostaje `delivery=link`, pliki i świadomą zgodę na utworzenie linku. Token składa się z losowego ID i 256-bitowego sekretu HMAC; baza trzyma jego HMAC. Uprawniony właściciel może odtworzyć wynik tego samego żądania po utraconej odpowiedzi. Klucz wygasłego lub cofniętego linku nie jest ponownie używany. Android ustawia 24 h; API dopuszcza 60 s–7 dni, nie dłużej niż ważność plików. Cofnięcie lub skasowanie transferu blokuje każdą następną porcję.

Zamiast planowanej osobnej domeny używamy jednej domeny HTTPS z API i wydzieloną stroną `/r/`. Unikamy CORS i przekazywania sekretu między domenami. Strona ma CSP default-src none, skrypty i style wyłącznie własne, no-referrer, no-store, noindex i brak trackerów. Sekret w URL jest fragmentem, usuwanym przez replaceState i przechowywanym wyłącznie w pamięci strony; serwer otrzymuje go w POST. Po odświeżeniu należy ponownie otworzyć oryginalny link. To świadomy kompromis: dalszy podział domen wymaga osobnego modelu tokenów i przeglądu.

Ważny token ujawnia liczbę, rozmiary i typy plików; nie ujawnia numerów ani nazw źródłowych. Pobranie w przeglądarce jest ograniczone do 25 MiB na plik, sprawdzane SHA-256 i zapisywane jako attachment. Posiadacz linku może przekazać go dalej. Odwołanie nie może usunąć już zapisanej kopii odbiorcy. Przygotowanie SMS wymaga dodatkowego potwierdzenia, potem otwiera szkic z rzeczywistym terminem wygaśnięcia. Nie wysyła SMS automatycznie.

## Usuwanie i eksploatacja

TTL blokuje odczyt bez czekania na worker. Sprzątanie co 5 min usuwa porcje z aktywnej bazy, a dziennik zdarzeń umożliwia poinformowanie drugiej strony o wygaśnięciu. Audyt ma wyłącznie dzienne liczniki rodzajów operacji, przechowywane 30 dni. Limity uwierzytelnionego pobierania i publicznych źródeł chronią pochodzenie plików; reverse proxy i limity dostawców ograniczają połączenia oraz koszt OTP. Lokalny health probe alarmuje o opóźnionym sprzątaniu, push, limitach i zapełnieniu. Produkcyjny monitor musi konsumować jego kod wyjścia.

Pilot nadal nie wykonuje backupu HD. Odtworzenie przypadkowego snapshotu wymaga offline bariery `restore.py --invalidate-bindings`, która usuwa odtworzone dane użytkowników przed otwarciem sieci. Tę samą barierę stosujemy podczas rotacji głównego sekretu. Pozwala to uniknąć wskrzeszenia usuniętego obiektu lub cofniętego linku ze starej kopii. Docelowe odtwarzanie bez utraty danych wymaga niezależnego, monotonicznego dziennika odwołań i pozostaje poza pilotażem. Sekrety są dostarczane z magazynu sekretów przez montowany plik/env; środowiska mają odrębne bazy, tożsamości i konfiguracje dostawców. Nie wykonano wdrożenia produkcyjnego ani operacji na koncie dostawcy.

## Dane lokalne

Room v7 dodaje historię HD, kursor, zakres kosza HD i znaczniki trwałego usunięcia. Kosz przechowuje konkretny zakres SMS/MMS/HD; nowsze wiadomości są widoczne. Częściowe usuwanie providera zachowuje zakres do wznowienia. Trwałe usunięcie HD blokuje odtworzenie tej samej koperty przy resecie kursora. Galeria przechowuje relację do MessageRef.Hd i odmawia otwarcia źródła ukrytego w koszu. Prywatne pliki lokalnej historii są chronione przed GC także po wyłączeniu konfiguracji sieciowej. Kopie chmurowe i transfer danych aplikacji pozostają wyłączone.

Dokumentacja integracji: [FCM HTTP v1](https://firebase.google.com/docs/cloud-messaging/send/v1-api), [klient Android](https://firebase.google.com/docs/cloud-messaging/android/get-started). Klient używa wersji Firebase Messaging 25.1.3 i nadal obsługiwanego w niej API tokenów; przejście na nowe API rejestracji wymaga jednoczesnej aktualizacji kontraktu serwera.
