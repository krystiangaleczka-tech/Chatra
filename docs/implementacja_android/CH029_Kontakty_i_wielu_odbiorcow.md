# CH029 — kontakty i wielu odbiorców (11.11.2026)

Status: **implementacja gotowa do przeglądu, nieodebrana na urządzeniu**. Gałąź `android/ch029-contacts-multi-recipient` bazuje na CH027–CH028 / PR #13.

## Implementacja

- Lista numerów z ContactsProvider bez zapisywania książki kontaktów do Room. Zgoda READ_CONTACTS jest odrębna i opcjonalna. Brak zgody lub błąd SecurityException pozostawia wpisywanie ręczne.
- Wyszukiwanie po nazwie (polskie znaki, wielkość liter), numerze i formacie telefonu; zapytanie skanuje książkę, a limit 100 dotyczy *wyników*, nie pierwszych 100 rekordów. ContentObserver reaguje na zmiany kontaktów, a `platform.changes` na zmianę zgody.
- Numer jest osobną pozycją nawet przy wielu numerach jednej osoby. Powtarzający się polski numer w formacie 9 cyfr i z `+48`/`0048` nie daje dwóch odbiorców, również przy trwałej kolejce.
- Domyślnie odbiorcy otrzymują osobne SMS. Trwała kolejka grupuje wyniki odbiorców w jednej operacji z niezależną historią rozmów; wcześniej wdrożone CH027 pozwala ponawiać wyłącznie pewnie nieudanych. Nie ma ujawniania listy pozostałych odbiorców w treści/odbiorcach SMS.
- Limit 20 unikalnych numerów jest sprawdzany już w edytorze; kolejne są odrzucane czytelnym błędem. Alfanumeryczne adresy nie są akceptowane jako numery wychodzące.
- Grupowy MMS pozostaje niedostępny w wariancie full do CH032. Nie uruchamiamy fałszywego transportu.

## Ograniczenia i QA

Zmiana nie jest potwierdzona kompilacją ani testami Androida w tym środowisku. Dodano testy jednostkowe deduplikacji i edytora. Do wykonania lokalnie:

```bash
cd android
./gradlew :core:model:test :app:testDemoDebugUnitTest :app:testFullDebugUnitTest :app:assembleFullDebug :app:lintFullDebug
```

Na fizycznym urządzeniu sprawdzić: dodanie 3 różnych osób, 2 numerów jednego kontaktu, numer lokalny/`+48`/`0048`, odmowę READ_CONTACTS, zmianę zgody w ustawieniach, aktualizację kontaktu po otwarciu edytora, częściowy błąd transportu i jawne retry bez ponownego SMS do odbiorcy, który już dostał wiadomość. Reguła 9 cyfr = polski numer jest lokalnym założeniem, a nie globalnym parserem E.164. Przed wersją wielokrajową wymaga rozstrzygnięcia kraju z SIM/ustawień i testów między krajami.
