# CH045–CH049 · Pro i pilot Chatra HD

Gałąź `android/ch045-ch049`, baza `e922436`. Zakres obejmuje przygotowanie transferu, zakończone weryfikacją plików i zapisaniem koperty roboczej. Dostarczenie koperty, pobieranie, status odbiorcy i linki są w CH050–CH052.

## CH045 · Chatra Pro

Google Play Billing 9.1 działa wyłącznie w wariancie full z podanym produktem i adresem serwera. Cena pochodzi z podstawowej oferty Google Play. Paywall obsługuje anulowanie, oczekiwanie, niedostępność sklepu i przywracanie zakupów z konta Play. Serwer weryfikuje pakiet, produkt, ważność, konsumpcję i zwrot przez Play Developer API. Potwierdza ważną płatność na serwerze i przy ponowieniu sprawdza, czy potwierdzenie zostało zapisane. Token nie jest przechowywany w bazie Chatry. Potwierdzenie uprawnienia jest odświeżane po powrocie do aplikacji, po zakupie, co 15 min na pierwszym planie oraz na żądanie.

Roboczy katalog obejmuje Bursztyn i Las globalnie oraz per rozmowa. Gdy Play przestaje potwierdzać zakup, aplikacja wybiera bezpłatną kompozycję, zachowując preferencję do ewentualnego przywrócenia. Skala tekstu, kontrast, tryb matowy i cztery istniejące kompozycje działają niezależnie od Pro. Założono odtworzenie przez konto Play bez rejestracji HD. Katalog i identyfikator `chatra_pro` wymagają odbioru właściciela w G08 oraz aktywnej oferty w Play Console. [Projekt paywalla CH020](CH020_Katalog_projektow_i_stanow.md#chatra-pro-pro).

## CH046 i CH047 · Architektura, API i tożsamość

ADR ustala architekturę pojedynczej instancji w regionie UE, stany, koszty, retencję, odzyskiwanie i zagrożenia. Serwer przyjmuje dowód posiadania klucza P-256 i kod od Twilio Verify. Pozwala odpiąć instalację oraz ponownie powiązać numer. Sesje są krótkie i związane z kluczem. Klient podpisuje metodę, ścieżkę, treść, nagłówki operacji, czas i nonce. Przy odzyskaniu numeru klucz poprzedniej instalacji zostaje odwołany wraz z jej transferami. Zgoda na odkrywalność jest osobna. Sprawdzenie dotyczy jednego numeru, a wynik pozytywny wymaga odwzajemnionego wyboru. Kursory, błędy, idempotencja i podpis są częścią kontraktu. [ADR G06/G07/G08](adr/ADR-002_Chatra_HD.md), [OpenAPI](../../backend/openapi.json), [uruchomienie i podpis](../../backend/README.md).

## CH048 · Wznawialny upload

Prywatne porcje, limity, bilety na 5 min i sumy SHA-256 są sprawdzane na serwerze. Klient powtarza ten sam klucz manifestu i odczytuje offset. Błędna porcja nie przesuwa offsetu. Sprzątanie usuwa wygasłe transfery, a anulowanie po kluczu manifestu działa także po utracie odpowiedzi na utworzenie. Finalize czyta porcje w kolejności i zapisuje zweryfikowany stan razem z jedną kopertą roboczą w tej samej transakcji. Obcy właściciel nie odczyta statusu ani nie uzyska biletu.

## CH049 · Klient Androida

W pilocie użytkownik aktywuje HD w ustawieniach, sprawdza jednego odbiorcę z poziomu Dodatków i potwierdza upload oryginału lub już oczyszczonej kopii. Domyślnie transfer używa wyłącznie sieci nietaryfowej. Każde żądanie z takim ograniczeniem korzysta ze sprawdzonej sieci. WorkManager uruchamia krótkie przebiegi z ograniczeniami sieci, wznawia od offsetu, stosuje backoff i respektuje `Retry-After`. Kolejka zachowuje postęp oraz prywatne kopie aktywnych i nieudanych zleceń. Zmiana ekranu nie zatrzymuje pracy. Wstrzymanie i anulowanie unieważniają poprzednią generację workera. Po powrocie z awarii Android odtwarza stan z zapisu i planuje pracę ponownie. Stan „Pliki zweryfikowane na serwerze” pojawia się po finalize. Widok pokazuje ten wynik jako historię lokalną, a dostępność plików podlega retencji serwera. Brak HD i błędy nie powodują przełączenia na SMS/MMS. [Wzorzec S04](CH002_Android/run1/android-S04.png), [projekt ustawień HD CH020](CH020_Katalog_projektow_i_stanow.md#ustawienia-chatra-hd-hd).

Wybrano WorkManager i porcje 256 KiB dla zgodności z API 31 oraz wznawiania bez długiego serwisu w tle. Android może opóźnić pracę po wymuszonym zatrzymaniu lub przy wyczerpaniu limitu zadań. [Warianty zadań transferowych Androida](https://developer.android.com/develop/background-work/background-tasks/data-transfer-options).

## Weryfikacja

- Zbudowano APK demo, full i pakiet instrumentacji. Zestawy JVM przeszły 92 testy bez błędów i pominięć.
- Backend przeszedł 10 testów na danych syntetycznych. Obejmują dwa niezależne klucze instalacji, błędne i zużyte kody, rotację sesji, kontrolę właściciela, powrót od offsetu po restarcie, równoległe finalize, limity, retencję i obsługę odpowiedzi dostawców. Wygenerowany OpenAPI porównano z trasami serwera.
- Emulator API 31 zaliczył 4 próby kolejki HD z testowym transportem i 3 testy Room. Test kolejki ponownie otwiera zapis i wznawia przesyłanie od potwierdzonej porcji. Sprawdza też pauzę, anulowanie, przejścia po 401/413/429/503 i dwa równoległe transfery.
- Lint demo przeszedł. W pełnym wariancie nowszy Fragment zastąpił wersję 1.1 wnoszoną przez Play Billing. Zbudowano pełny APK i pakiet testowy z tą zmianą. Końcowa analiza lint full przerwała się na wewnętrznym błędzie Kotlin FIR w `HdQueue.kt`. Szerszy przebieg regresji na emulatorze został przerwany.
- Pozostają test licencjonowanego zakupu z Play Console, weryfikacja numeru przez prawdziwy SMS na dwóch urządzeniach i odbiór przełączenia Wi‑Fi na dane komórkowe z uruchomioną usługą. Wymagają też odbioru katalog Pro i decyzje G06–G08. Pilot nie został uruchomiony publicznie.
