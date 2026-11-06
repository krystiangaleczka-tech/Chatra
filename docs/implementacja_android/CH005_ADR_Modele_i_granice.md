# CH005 — ADR: modele, źródła prawdy i granice architektury

**Stan:** kontrakty domeny i testy JVM przygotowane; integracja Android/Room należy do CH004 i CH022. **Data:** 2026-11-06. **Decyzja:** warstwa `core/model` jest niezależna od Androida, bazy i UI.

## Co zostało zaimplementowane

- Typowane UUID dla wątku, szkicu, operacji, próby, harmonogramu, załącznika, HD i skrótu. `MessageRef.Provider(kind, id, threadId)` rozróżnia ID SMS i MMS, nawet gdy wartości liczbowe są równe. `MessageRef.Hd` jest osobną przestrzenią identyfikatorów.
- `Conversation`, lokalne `ConversationSettings`, `Draft`, `Attachment` z odnośnikiem do źródłowej wiadomości, `SendOperation`, `SendAttempt`, `SendPart`, `ScheduledMessage` zachowujący `Instant`, `LocalDateTime` i `ZoneId`.
- `TransportKind` i oddzielne `SmsTransport`, `MmsTransport`, `HdTransport`; wynik rozróżnia przyjęcie przez warstwę platformy, pewną odmowę i nieznaną akceptację. Żaden wynik transportu nie oznacza automatycznie odczytu wiadomości przez odbiorcę.
- Porty źródeł: `ProviderHistory`, `ConversationRepository`, `DraftRepository`, `SendOperationRepository`, `ScheduleRepository`, `SubscriptionSource`, `Clock`. Stan UI i akcje opierają routing na typowanych identyfikatorach. Błędy domenowe nie ujawniają treści.
- Reguły dozwolonych przejść stanu; nie można anulować po `DISPATCHING` ani cofnąć `DELIVERED` do `SENT`. `DISPATCH_UNCERTAIN` nie jest automatycznie ponawiany.

## Źródła prawdy i migracje

1. **Telephony Provider** jest jedynym kanonicznym źródłem SMS/MMS. Nie przechowujemy drugiej historii treści w Room; zapytania po providerze muszą uwzględniać rodzaj (`SMS`/`MMS`) i identyfikator. `ProviderHistory` to port odczytu, a adapter systemowy powstaje w CH021–CH023.
2. **Room** ma przechowywać szkice, migawki operacji i prób, harmonogramy, lokalne ustawienia wątku, metadane załączników, odtwarzalny indeks i wiadomości HD. Rekoncyliacja z providerem aktualizuje projekcję bez kasowania historii. Bez `fallbackToDestructiveMigration` dla danych użytkownika.
3. **Wątek i jego metadane**: null w `themeOverride` i `unreadOverride` oznacza dziedziczenie; `pinnedAt`, wyciszenie, preferencja `subscriptionId`, skrót i identyfikatory źródeł mediów nie kopiują SMS. Po usunięciu wątku adapter usuwa osierocone lokalne ustawienia, przypięcia, wyciszenie, nieprzeczytane, skrót i odtwarzalny indeks. Nie powinien usuwać wiadomości providera bez osobnej, jawnej operacji.
4. **Migracje**: najpierw utworzyć schemat `v1` (CH022), potem w migracjach dodawać pola nullable z neutralną wartością `null`; backfill tylko odtwarzalnych projekcji. Nowa wersja danych musi być odczytywalna po przerwanym procesie migracji, a zniknięcie roli SMS blokuje dostęp do providera, nie wymazuje lokalnej bazy.
5. **Kosz**: lokalne tombstone konkretnych wiadomości jest osobnym kontraktem CH043, nie flagą `trashedAt` na całym wątku. Nowy SMS do ukrytego wątku ma pozostać widoczny.

## Przepływ wysyłki i granice bezpieczeństwa

- Use case utrwala niemutowalną migawkę payloadu oraz `PENDING_UNDO` z deadline. Deadline ma być sprawdzany przez trwałego wykonawcę, nie UI; proces `dispatch` musi przejąć operację przez atomowy CAS stanu i wersji w Room.
- Potwierdzony `cancel` wygrywa wyścig tylko przed przejściem do `DISPATCHING`. Po przekazaniu do systemu nie istnieje transakcja atomowa między Room a SMS/MMS API. Crash może oznaczać `DISPATCH_UNCERTAIN`, nigdy automatyczny retry.
- Każda próba i każda część ma osobny identyfikator callbacku; wynik wieloczęściowy/per odbiorca agreguje CH026–CH027. `FAILED` wymaga wyraźnego wyboru ponowienia i nowej identyfikowalnej próby/operacji, nie reanimowania starego rekordu.
- Osobne SMS do wielu odbiorców są domyślne. Grupowy MMS to jawny wybór z uprzedzeniem o widoczności numerów. Chatra HD działa tylko po aktywacji, przez oddzielny port; nie obiecujemy RCS/iMessage, E2EE ani rozmów.
- Nie logować treści SMS, numerów, kluczy linków HD, pełnych URI ani payload snapshot. `technicalSummary()` zwraca wyłącznie niepoufne dane techniczne.

## Weryfikacja i ograniczenia

Test: `bash scripts/ch005_test_domain.sh` (JDK 17+ i `kotlinc`, bez Gradle i bez Android SDK). Obejmuje ID SMS/MMS, niezależność metadanych, walidacje, bezpieczne stany cancel/dispatch/delivered/uncertain, modele harmonogramu i wejścia transportu. **To test kontraktów JVM, nie test integracji SMS/MMS, Room ani UI**. Podłączenie do builda projektu nastąpi w CH004; transakcje, migracje i testy Androida w CH022/CH026–CH028.

Decyzje G02/G03/G04/G06/G07 nie zostały tu zatwierdzone. Zadanie CH005 nie zmienia 10 goldenów ekranów, ich CSS ani fontów.
