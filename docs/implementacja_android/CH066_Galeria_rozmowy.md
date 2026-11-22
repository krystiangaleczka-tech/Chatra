# CH066 — galeria rozmowy

S03 → Szczegóły rozmowy → Otwórz galerię. Widok „Wspólne” ma zakładki
Wszystkie pliki, Zdjęcia, Filmy, Linki, Dokumenty i Pozostałe. Pokazuje licznik
dla wybranej zakładki i ładuje po 40 rekordów. Zmiana orientacji zachowuje
wybraną zakładkę, wczytane strony i pozycję listy. „Odśwież” przeładowuje
wybraną zakładkę po zmianach w historii lub pobraniu HD.

MMS korzysta z lokalnego indeksu części wiadomości. Chatra HD pokazuje pozycje
z manifestu, w tym oczekujące na pobranie, pobierane, wygasłe i niedostępne.
Miniaturki powstają dla widocznych wierszy, a widok pobiera tylko zmniejszony
obraz lub klatkę filmu. Przewinięcie listy nie wczytuje oryginałów wszystkich
plików. Linki HTTP(S) pochodzą z lokalnego indeksu wyszukiwania i pojawiają się
po zakończeniu indeksowania. Nie pobieramy podglądów z sieci.

„Pokaż w rozmowie” przekazuje identyfikator SMS, MMS lub HD do istniejącej
nawigacji historii. Przy oczekującym pliku HD pobieranie jest dostępne w
wiadomości źródłowej. Demo pokazuje dane przykładowe i przejście do wiadomości.

Przed otwarciem, zapisem i udostępnieniem aplikacja ponownie weryfikuje
dostępność oryginału. Po utracie pliku lub zgody pokazuje błąd. Otwarcie i
udostępnienie używają pojedynczej kopii w pamięci podręcznej Chatry z grantem
tylko do odczytu dla aplikacji wybranej przez użytkownika. Kopie starsze niż
24 godziny są usuwane przy kolejnym eksporcie. Zapis kopii korzysta z systemowego
wyboru dokumentu. Maksymalny rozmiar eksportowanego pliku to 250 MB.

Weryfikacja na emulatorze obejmuje nawigację z CH065, filtr zdjęć, pustą
zakładkę linków, zestawy 67 i 1000 pozycji z granicą HD/MMS, odfiltrowanie
wiadomości z kosza, brak oryginału, oraz kopię przez własny FileProvider i
grant do odczytu. Wyniki i zrzut galerii zapisuje GitHub Actions przy PR do
CH066. Odbiór na telefonach nadal obejmuje TalkBack, obrót, transfer prawdziwych
załączników MMS i otwieranie/zapis przez aplikacje innych dostawców.
