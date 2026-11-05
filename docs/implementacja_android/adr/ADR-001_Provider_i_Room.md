# ADR-001: Telephony Provider jako historia, Room jako metadane i projekcja

**Status:** przyjęty (CH005, 05.11.2026) · **Zadania:** CH005, CH022, CH038, CH054

## Kontekst

Chatra jest domyślną aplikacją SMS. Systemowy Telephony Provider przechowuje historię SMS i MMS także dla innych aplikacji, kopii zapasowych i przeniesień. Chatra potrzebuje dodatkowo szkiców, operacji wysyłki z oknem anulowania, harmonogramu, przypięć, kosza, indeksu wyszukiwania i wiadomości HD, których provider nie modeluje.

## Decyzja

1. **Provider jest kanoniczną historią SMS/MMS.** Nie importujemy historii przez kasowanie ani przepisywanie rekordów. Porażka indeksowania nie usuwa SMS.
2. **Room trzyma wyłącznie dane Chatry:** `ConversationMeta`, `ProviderMessageRef`, `Draft`, `SendOperation`/`SendRecipient`/`SendAttempt`/`SendPart`, `ScheduledMessage`, `Attachment`, `HdEnvelope`, `SearchDocument`, `BlockRule`, `Template`. Preferencje są w DataStore.
3. **Rekord providera identyfikuje para (rodzaj, ID)** (`ProviderRef`), bo te same liczby istnieją w tabelach SMS i MMS. ID providera nie jest globalną tożsamością poza instalacją.
4. **Rozmowa Chatry ma własny `ConversationId` (UUID)** i opcjonalny `providerThreadId`; wątki grupowe identyfikuje `participantsKey` (posortowany zbiór znormalizowanych adresów) razem z thread ID providera.
5. **Indeks wyszukiwania jest odtwarzalną projekcją**, nie niezależną historią: po zmianach z innej aplikacji (ContentObserver) i po odzyskaniu roli przeprowadzamy rekoncyliację.
6. **Przejścia stanu wysyłki** odbywają się w transakcji Room z porównaniem oczekiwanego stanu i wersji (`SendOperation.transition`). Między Room a telephony nie ma atomowej transakcji, więc niepewność kończy się stanem `DISPATCH_UNCERTAIN` i decyzją użytkownika, bez automatycznego ponawiania.

## Granice modułów

| Moduł | Zależy od | Zawiera |
| --- | --- | --- |
| `core:model` | — (Kotlin, coroutines) | Typowane ID, encje domeny, maszyna stanów wysyłki, harmonogram, wybór SIM, kontrakty repozytoriów i transportów |
| `core:designsystem` | — | Tokeny, Inter, ikony, tła CSS, renderer szkła, komponenty |
| `app` | wszystkie | Activity, nawigacja, ekrany, warianty `demo` (fixture, bez wysyłki) i `full` |

Kolejne moduły (`core:database`, `core:telephony`, `core:scheduling`) dochodzą w zadaniach, które ich potrzebują. Funkcje (feature) nie zależą od siebie nawzajem; komunikują się przez kontrakty z `core:model`.

## Konsekwencje

- UI działa z repozytorium demonstracyjnym (fixture CH006) albo z providerem, bez zmian w ekranach.
- Logika wysyłki i harmonogramu jest testowalna bez Androida (testy JVM w `core:model`).
- Usunięcie wątku w innej aplikacji wymaga odświeżenia projekcji; metadane Chatry dla nieistniejącego wątku są sprzątane przy rekoncyliacji.
