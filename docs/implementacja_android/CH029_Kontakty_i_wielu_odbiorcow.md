# CH029 — kontakty i wielu odbiorców

Wdrożenie CH029 na bazie `android/ch027-ch028`, commit `e80600a`. Zakres obejmuje kontakty, tożsamość odbiorców, osobne SMS i uruchomienie dialera.

## Wdrożenie

- SystemContacts czyta numery i nazwy na IO, obserwuje Contacts Provider oraz zmiany zgody. Kontakt z kilkoma numerami daje kilka pozycji; odmowa pozostawia ręczne wpisanie numeru. Zmiany kontaktów odświeżają również tytuły rozmów.
- Porównanie odbiorców dla regionu PL łączy lokalne 9 cyfr, +48 oraz 0048. Nie zmienia adresu wysyłki ani numerów zagranicznych. E.164 z kontaktów jest używane tylko wtedy, gdy zwraca je provider.
- Sugestie wyszukują po nazwie i numerze w całym katalogu, pokazując maksymalnie 50 trafień. Wybrany numer znika z sugestii, również w innym zapisie.
- Domyślny tryb osobnych SMS zachowuje osobne rozmowy oraz wspólną trwałą operację z wynikami każdego odbiorcy. Retry obejmuje wyłącznie pewne błędy, pomijając udanych odbiorców. Numer alfanumeryczny lub nieprawidłowy jest odrzucany.
- Szczegóły rozmowy oferują ACTION_DIAL tylko dla poprawnych numerów, ze wskazaniem konkretnego numeru. Aplikacja nie wykonuje połączenia.

Tryb grupowego MMS nadal wymaga jawnego wyboru i informuje o widoczności numerów. W pełnej aplikacji wysyłka MMS/załączników pozostaje zablokowana do CH032; CH029 nie uruchamia transportu MMS ani importu plików z CH031.

## Weryfikacja

Wyniki buildów, JVM i ukierunkowanych testów Androida są zapisane w `CH029_Android/verification.json`. Testy Androida używają izolowanego emulatora API 31, prawdziwego Contacts/SMS Provider oraz atrapy wysyłki. Nie wysyłają wiadomości do fizycznych odbiorców. Pokrywają zmiany kontaktu, kilka numerów, odmowę dostępu, deduplikację +48/lokalny numer oraz częściowy sukces trzech odbiorców z retry wyłącznie błędnego.

Wynik: **76/76 JVM oraz 3/3 unikalne testy Androida zaliczone**, build demo/full i test APK poprawny; lint bez błędów (8 ostrzeżeń demo, 21 full). Pierwsze dwie próby pominęły testy odbiorców podczas inicjalizacji wirtualnej subskrypcji; oba przypadki zaliczono po jej uruchomieniu. Szczegóły i logi są w wynikach maszynowych.
