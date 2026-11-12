# CH030–CH034 — powiadomienia, pliki, MMS i szablony

Implementacja z 12.11.2026 na bazie `main` `0895fab` (scalone CH016–CH029). Gałąź: `android/ch030-ch034`. Wykorzystano i dokończono etap CH030 z PR #15; bazą kontaktów jest zweryfikowany PR #16, a nie alternatywny draft #14.

Wyniki końcowe, sumy APK i logi znajdują się w [CH030_CH034_Android](CH030_CH034_Android/verification.json). **Transport MMS w testach jest kontrolowany. Odbiór CH032 u operatorów na fizycznych SIM/eSIM pozostaje otwarty.**

## CH030

Kanały wiadomości, problemów wysyłania, harmonogramu i HD, powiadomienia per rozmowa oraz ciche podsumowanie liczą również wyciszone rozmowy. Błędy i niepewne operacje otwierają właściwą rozmowę. Harmonogram i usługa HD nie generują fikcyjnych zdarzeń: ich właściwe źródła dochodzą w kolejnych zadaniach.

Domyślnie treść jest ukryta. Ustawienie podglądów dotyczy prywatnego widoku powiadomienia; publiczna wersja pozostaje neutralna. Wyciszenie na godzinę, osiem godzin albo do odwołania ma trwały zapis i wygasa bez oznaczania wiadomości jako przeczytanej. Tworzone są skróty rozmów; ustawienia kanałów Androida zachowują pierwszeństwo.

Szybka odpowiedź ma osobną akcję dla wskazanej SIM i wymaga odblokowania urządzenia. Zapisuje szkic i korzysta z tej samej kolejki, okna anulowania oraz deduplikacji co edytor. Nie oferuje automatycznej odpowiedzi do grupowego MMS; taki transport wybiera się w aplikacji. Token odpowiedzi zostaje zużyty po przyjęciu, dzięki czemu ponowne wykonanie starej akcji nie wysyła drugiej kopii, a następna wiadomość może otrzymać nową odpowiedź.

SENDTO i RESPOND_VIA_MESSAGE nadal tworzą szkic. SEND/SEND_MULTIPLE importują wskazane `content://` do prywatnych plików, zachowując tekst oraz poprawnie skopiowaną część załączników w razie błędu. Nieprawidłowe URI, liczba odbiorców i rozmiar tekstu są odrzucane. Żaden intent nawigacyjny ani udostępnienie nie uruchamia wysyłki.

## CH031

Photo Picker i systemowy aparat nie wymagają szerokiej zgody na bibliotekę ani uprawnienia CAMERA. Własna kopia zawartości, podpis formatu, rzeczywisty rozmiar (maksymalnie 250 MiB) oraz SHA-256 powstają na IO. Zapis publikuje metadane dopiero po ukończeniu kopii; przerwanie usuwa plik tymczasowy. Pusty, uszkodzony lub nieobsługiwany plik nie trafia do szkicu. Utrata oryginalnego URI nie odbiera dostępu do prywatnej kopii.

Szkic utrwala kolejność i identyfikatory plików, a operacja zachowuje własny snapshot. Dodatki pozwalają usuwać, przesuwać, otwierać i udostępniać wskazany plik z krótkotrwałym prawem odczytu. FileProvider udostępnia wyłącznie podkatalogi plików multimedialnych, aparatu i PDU; baza, szkice i metadane nie są w jego korzeniach. Miniatury są dekodowane leniwie z ograniczoną rozdzielczością.

Czyszczenie przy uruchomieniu ma co najmniej dobę karencji. Chroni wszystkie zapisane szkice, snapshoty operacji, niepewne próby i oryginały aktywnych kopii. Usuwa stare nieużywane media, częściowe importy i pozostałości aparatu. Nie usuwa plików aktywnej operacji po samym zamknięciu edytora.

Port galerii dla CH066 ma strony do 100 rekordów, stabilny kursor, filtr zdjęć/filmów, licznik, źródłową wiadomość i transport. Sprawdza faktyczny dostęp do indeksowanego URI części MMS. Nie ładuje pełnych plików przy zliczaniu ani stronicowaniu. Widok galerii i dane HD należą do CH066 i dalszych zadań HD.

## CH032

Wysyłanie i pobieranie używają systemowego `SmsManager` dla konkretnej subskrypcji. SMS oraz MMS współdzielą trwałą kolejkę i granicę anulowania. Grupowy MMS wymaga jawnego trybu; osobne wiadomości zachowują osobne PDU oraz wyniki. Obsługiwane są tekst, JPEG/PNG/GIF i małe MP4/3GP mieszczące się w limicie; zdjęcia JPEG/PNG/WebP otrzymują oddzielną kopię JPEG dopasowaną do budżetu. Nie ma konwersji wideo ani obietnicy wysłania dowolnego pliku.

Limit pochodzi z konfiguracji operatora wybranej SIM i jest ograniczony do 1 MiB PDU. Brak SIM, danych, wyłączone MMS, limit pliku i roaming nie wyglądają jak sukces. Roaming jest obecnie wstrzymany. Błędy APN, konfiguracji i sieci danych mają osobne objaśnienia. Brak jednoznacznego wyniku po granicy API pozostaje niepewny; nie wywołuje automatycznej ponownej wysyłki. Pewne porażki mogą być ponowione, a spóźniony sukces rodzica anuluje oczekujące ponowienie.

Dziennik `PREPARING → PREPARED → SUBMITTING → RETURNED` poprzedza wywołanie transportu. Token i identyfikator próby są sprawdzane w callbacku. Potwierdzony sukces nie cofa się po późnym błędzie. WAP_PUSH trafia do trwałej skrzynki; plik pobranego PDU i własny klucz w providerze umożliwiają odtworzenie zapisu bez drugiego rekordu wiadomości. Nieudane pobrania są widoczne w Prywatności i wymagają świadomego ponowienia. Po przerwaniu wysyłki brak callbacku nie jest interpretowany jako dowód braku wysłania.

Room v4 dodaje typ powiązania `SMS/MMS`; migracja v1→v2→v3→v4 zachowuje dane. Ten sam liczbowy identyfikator w obu tabelach providera nie łączy różnych wiadomości. Odbierany MMS staje się nieprzeczytany dopiero po zapisaniu wszystkich części. Własny numer zgłoszony przez SIM jest pomijany przy budowaniu uczestników; gdy operator nie udostępnia tego numeru, skład grupy wymaga dalszego odbioru na urządzeniu.

Kodek PDU jest wydzielony z [android-smsmms](https://github.com/klinker41/android-smsmms/tree/f6fa7d379444dc11d700fe6178b418bd76cfb88f/library/src/main/java/com/google/android/mms/pdu_alt), Apache-2.0, ze wskazaniem commit i hashami źródeł. Upstream jest stary; dalsze utrzymanie tej wydzielonej części należy do projektu. Nie importowano jego transportu, manifestu ani zależności. Zmiany ograniczają długości, liczbę części i zagnieżdżenia; nieprawidłowy PDU, również z asercją starego parsera, jest błędem dekodowania. Licencja i informacje o zmianach są w repozytorium oraz APK.

## CH033

Oryginał oznacza dokładne bajty wybrane przez użytkownika z pickera lub aparatu. Aplikacja nie deklaruje odzyskania metadanych, których picker nie udostępnił. Oryginalny plik i jego SHA-256 nie zmieniają się przy tworzeniu kopii.

Kopia bez lokalizacji obsługuje JPEG, PNG i WebP. Dekoduje obraz, uwzględnia orientację i zapisuje nowy JPEG zawierający wyłącznie piksele: bez EXIF/GPS, XMP, IPTC i komentarzy źródła. Konwersja jest stratna i ogranicza dłuższy bok do 4096 px. Kopia ma własny ID, opis i odwołanie do oryginału; wybór oryginałów przywraca źródłowe pliki. GIF, HEIC i filmy mogą pozostać oryginałami, ale żądanie kopii bez lokalizacji daje jawny błąd. Nie ma nieprawdziwej obietnicy sanitacji wideo.

Test sprawdza niezależnie segmenty JPEG (brak APP1), brak znaczników GPS/komentarza, orientację oraz niezmienność bajtów źródła.

## CH034

Katalog szablonów działa lokalnie: dodanie, edycja, usunięcie z potwierdzeniem, stan pusty i zapis po restarcie procesu. Wstawianie dopisuje treść na końcu kompozytora; nie zastępuje istniejącej treści ani nie zmienia odbiorców, SIM i plików. Jedno otwarcie selektora przyjmuje jedno wstawienie, także przy podwójnym stuknięciu. Nie ma synchronizacji z chmurą.

## Walidacja i granice odbioru

**81/81 testów JVM oraz 47/47 unikalnych testów Androida (44 aplikacji + 3 bazy), bez błędów i pominięć.** Dodatkowy przebieg jednego testu po odmowie POST_NOTIFICATIONS również przeszedł, więc łącznie wykonano 48 uruchomień Androida. Oba APK i lint obu wariantów przeszły; lint raportuje 17 ostrzeżeń demo i 42 full, zero błędów. Szczegóły zapisano w `CH030_CH034_Android/verification.json`. Testy obejmują realny Telephony Provider w izolowanym AVD API 35, atrapy transportów, PDU grupy i częściowe wyniki, spóźniony sukces, wznowienie bez ponowienia, pobranie i pojedynczy zapis MMS, strony galerii, prywatność powiadomień, wyciszenia, szybką odpowiedź i duplikaty, trwałość plików, metadane, szablony i migrację.

Wstępne przebiegi wykryły asercję dekodera dla błędnych danych, status `read` po przejściu draft→inbox oraz brak izolacji danych w teście odpowiedzi. Poprawki mają regresje. Cofnięcie POST_NOTIFICATIONS przez system kończy proces aplikacji, dlatego odmowa jest sprawdzana osobnym uruchomieniem instrumentation, po cofnięciu zgody z hosta.

Pozostają: fizyczne MMS w obu kierunkach i grupy u operatorów, dual SIM/eSIM, zachowanie usług operatora po restarcie i roaming; próby rzeczywistego braku miejsca/przerwanego strumienia oraz duże pliki na docelowym urządzeniu; pełny odbiór TalkBack, mały ekran, API 31 i akceptacja UI G03/G09. Testy kontrolowanego transportu nie zastępują tych wyników. Chatra HD pozostaje oddzielną, jeszcze nieuruchomioną usługą.

Kontrakty platformy: [SmsManager](https://developer.android.com/reference/android/telephony/SmsManager), [Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker), [powiadomienia i odpowiedź](https://developer.android.com/develop/ui/compose/notifications/create-notification), [ExifInterface](https://developer.android.com/jetpack/androidx/releases/exifinterface).
