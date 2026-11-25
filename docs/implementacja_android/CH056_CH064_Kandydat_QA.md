# Kandydat QA do wydania Androida

| Pozycja | Próba w CI | Pozostały zakres odbioru |
| --- | --- | --- |
| CH056 | Emulowany modem dostarcza syntetyczny SMS do zainstalowanego wariantu release. Test sprawdza widoczną wiadomość w Skrzynce. | Sieci operatorów, MMS, dwie karty SIM i roaming na telefonach. |
| CH057 | Emulator wchodzi w głęboki Doze. Dokładny alarm uruchamia jedną próbę SMS do wirtualnego numeru emulatora. Test potwierdza stan uśpienia także po wysłaniu. | Ograniczenia producentów, alarmy po zabezpieczonym restarcie i fizyczny modem. |
| CH060 | W galerii przy 360 dp strzałka przenosi fokus między zakładkami, a Enter wybiera zakładkę i aktualizuje wyniki. | TalkBack i pozostałe przepływy na fizycznych urządzeniach. |
| CH061 | Android instaluje pakiet o numerze 2 na pakiecie 1 z tym samym podpisem. Test sprawdza rolę SMS, szkic, harmonogram, kosz i historię. Oczekuje zera prób wysyłki zapisanego zlecenia. | Aktualizacja z wcześniejszego produkcyjnego APK i transfer między telefonami. Test bazy z wersjami 1–6 jest osobną próbą migracji Room. Oba APK w teście aktualizacji powstają z tego samego źródła. |
| CH064 | Podpisane APK i AAB wariantu release z R8. CI weryfikuje podpisy, SHA-256 i wyrównanie dla stron 16 KiB, a następnie instaluje APK na emulatorze. | Docelowy klucz i kanał, formularz uprawnień, Dane bezpieczeństwa, polityka prywatności i smoke test w kanale odbiorców. |

## Pakiet i podpis

Artefakt `chatra-release-qa` zawiera paczki, SHA-256, SHA źródła, publiczny certyfikat, metadane APK i notatkę testową. Klucz QA powstaje na czas jednego przebiegu i jest usuwany po kompilacji. APK jest przeznaczony do czystej instalacji na urządzeniu testowym. Aktualizacja istniejącej instalacji wymaga tego samego certyfikatu Androida. Wersja 2 dotyczy próby QA. W docelowym kanale numer wersji rośnie z każdą publikacją, a klucz przesyłania AAB jest zarządzany zgodnie z konfiguracją Play App Signing właściciela. [Podpisywanie aplikacji](https://developer.android.com/studio/publish/app-signing)

## Google Play

Artefakt QA zawiera `PAGE_ALIGNMENT.txt`. Raport opisuje wyrównanie wpisów APK zweryfikowane przez `zipalign` oraz segmentów `LOAD` bibliotek natywnych w APK i AAB zweryfikowane przez `readelf`. Test uruchomienia wymaga obrazu systemu ze stronami 16 KiB. [Obsługa stron 16 KiB](https://developer.android.com/guide/practices/page-sizes)

- Obecne wymaganie dla nowych aplikacji i aktualizacji telefonicznych to API 36. Wariant release deklaruje `targetSdkVersion=36`. [Wymagania target API](https://support.google.com/googleplay/android-developer/answer/11926878)
- Chatra prosi o rolę domyślnej aplikacji SMS. Uprawnienia SMS i odbioru MMS wymienione w manifeście służą funkcji komunikatora operatorowego. W Play Console trzeba złożyć deklarację tych uprawnień i opisać utratę dostępu po oddaniu roli. [Zasady uprawnień SMS](https://support.google.com/googleplay/android-developer/answer/16558241)
- Dane bezpieczeństwa i polityka prywatności muszą odzwierciedlać przesyłanie danych w faktycznej konfiguracji. Bieżący pakiet QA używa lokalnej historii bez aktywnego backendu, HD, Firebase i Play Billing. Deklaracje dla kompilacji z tymi usługami powinny obejmować ich konkretne przepływy danych. [Dane bezpieczeństwa](https://support.google.com/googleplay/android-developer/answer/10787469)

## Roboczy opis lokalnego wydania

Chatra to domyślna aplikacja do SMS i MMS na Androida. Porządkuj rozmowy, wyszukuj wiadomości na telefonie, używaj szkiców i planuj wysyłkę z wybranej karty SIM. Dostępność SMS i MMS zależy od urządzenia i operatora.

Zrzuty oznaczone S01–S10 służą odbiorowi interfejsu. Do sklepu należy użyć obrazów z właściwego zakresu wydania i rozliczyć otwarte odchyłki wizualne S04 i S05.
