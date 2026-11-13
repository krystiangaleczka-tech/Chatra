# Chatra HD i weryfikacja Pro

Backend pilota CH045–CH049. Python 3.12 z SQLite 3.35 lub nowszym. [ADR](../docs/implementacja_android/adr/ADR-002_Chatra_HD.md) określa model danych, limity i warunki wdrożenia. [OpenAPI](openapi.json) opisuje działające endpointy. Koperty po finalize są robocze i widoczne tylko dla właściciela transferu. CH050–CH052 obejmują dostarczenie, odbiór, statusy i linki.

## Uruchomienie

```sh
cd backend
python3.12 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
umask 077
export CHATRA_DB="$PWD/chatra.sqlite3"
export CHATRA_SECRET_HEX="$(python -c 'import secrets; print(secrets.token_hex(32))')"
uvicorn api:from_environment --factory --host 127.0.0.1 --no-access-log --limit-concurrency 64
```

Zachowaj `CHATRA_SECRET_HEX` między restartami z tą samą bazą, ponieważ służy do HMAC wiązań, sesji i biletów. Dla instancji dostępnej z internetu użyj szyfrowanego wolumenu, stabilnej nazwy HTTPS i zaufanego reverse proxy. Wyłącz zapisywanie ścieżek z query string, nagłówków i treści żądań. Proxy ma nadpisywać nagłówki przekazujące adres klienta. `--forwarded-allow-ips` może obejmować wyłącznie adresy zaufanego proxy. Dodatkowo limituj ruch na brzegu, przed API i odczytem ciała. Sprzątanie bazy odbywa się przy starcie i co 5 min.

| Zmienna | Znaczenie |
| --- | --- |
| `CHATRA_TWILIO_SERVICE`, `CHATRA_TWILIO_KEY`, `CHATRA_TWILIO_SECRET` | Verify Service SID, klucz i sekret dostawcy. Bez konfiguracji usługa nie wysyła SMS. Przy pilocie ustaw regiony docelowe i limity kosztu w Twilio. |
| `CHATRA_PLAY_PRODUCT` | Identyfikator jednorazowego produktu w Google Play dla `app.chatra`. Bez wartości endpoint zakupów zwraca 503. |
| Application Default Credentials | Tożsamość usługi z dostępem do sprawdzania i potwierdzania zakupów w Play Developer API. Nie umieszczaj klucza w APK ani repozytorium. |

W Androidzie konfiguracja pochodzi z właściwości Gradle. Przykład dla instancji testowej:

```sh
cd android
./gradlew :app:assembleFullDebug -PchatraBackendOrigin=https://hd.example.org -PchatraHdPilot=true
```

Dla zakupów ustaw również `-PchatraPlayProduct=chatra_pro` z tym samym identyfikatorem na serwerze i w Play. Oferta ma zawierać jeden podstawowy wariant jednorazowego zakupu, bez wypożyczenia i wielu sztuk. Potwierdź katalog i odbiór licencjonowanych testów przed konfiguracją sklepu publicznego. Wersja demo nie ma klienta HD, Billing ani uprawnienia internetowego.

## Podpis i transfer

Każde żądanie HD ma nagłówki `X-Chatra-Time` (sekundy Unix), `X-Chatra-Nonce` (32 małe cyfry szesnastkowe) i `X-Chatra-Signature` (DER ECDSA P-256 z SHA-256, base64url bez paddingu). Podpisz poniższe wiersze połączone LF, bez dodatkowego LF po ostatnim polu. SHA-256 zapisuj małymi cyframi szesnastkowymi. Nagłówki nieobecne w żądaniu dają pusty wiersz.

1. `CHATRA-HD-1`
2. Metoda HTTP.
3. Dokładna ścieżka z query string, w formie wysłanej na serwer.
4. SHA-256 ciała żądania, także dla pustego ciała.
5. `X-Chatra-Time`.
6. `X-Chatra-Nonce`.
7. SHA-256 poświadczenia. Pusty ciąg przy rozpoczęciu weryfikacji, ID wyzwania przy wysyłaniu kodu, ID instalacji przy otwieraniu sesji, a w pozostałych żądaniach token bez prefiksu `Bearer `.
8. `Idempotency-Key`.
9. `Upload-Offset`.
10. `X-Chunk-SHA256`.

Serwer sprawdza podpis, tolerancję czasu ±120 s i unikalność nonce w tym oknie. Wysłanie kodu wykorzystuje klucz publiczny DER SPKI w base64url. Ponowienie operacji musi mieć nowy nonce i ten sam klucz idempotencji oraz manifest. Wygaśnięcie sesji wymaga nowej sesji z tym samym kluczem instalacji. Nowy klucz wymaga OTP. Zweryfikowana instalacja musi włączyć odkrywalność przed sprawdzeniem wybranego odbiorcy. Wynik pozytywny wymaga odwzajemnionego wyboru.

Upload używa biletu powiązanego z właścicielem, transferem, plikiem i terminem. Po przerwaniu odczytaj offset. Porcja ma 256 KiB poza ostatnią. Finalize można powtórzyć. Anulowanie po kluczu żądania działa nawet przy utraconej odpowiedzi na utworzenie. Paginacja transferów jest według niezmiennego ID. Nowe pozycje mogą pojawić się przed kursorem, więc listę okresowo odświeżaj od początku.

## Testy

```sh
pip install pytest httpx
python -m pytest -q
```

Próby używają losowych kluczy i danych syntetycznych. Nie wymagają konta Play ani Twilio. Aby odtworzyć OpenAPI po zmianie endpointów, uruchom polecenie w katalogu `backend`.

```sh
python -c 'import json; from api import create_app; print(json.dumps(create_app(None).openapi(), indent=2))' > openapi.json
```
