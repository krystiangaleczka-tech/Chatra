#!/usr/bin/env python3
"""Validate the CH020 design contract and render its review board as native SVG."""
import argparse
import base64
import calendar
from html import escape
import json
from pathlib import Path
import textwrap

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "android/app/src/main/assets"
STATES = {"ready", "empty", "loading", "error", "disabled", "permission", "confirmation"}


def luminance(color):
    parts = [int(color[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    parts = [p / 12.92 if p <= .04045 else ((p + .055) / 1.055) ** 2.4 for p in parts]
    return sum(p * weight for p, weight in zip(parts, (.2126, .7152, .0722)))


def contrast(a, b):
    light, dark = sorted((luminance(a), luminance(b)), reverse=True)
    return (light + .05) / (dark + .05)


def validate():
    catalog = json.loads((ASSETS / "ch020_catalog.json").read_text())
    tokens = json.loads((ASSETS / "ch020_tokens.json").read_text())
    plan = json.loads((ROOT / "docs/implementacja_android/Chatra_Plan_zadan.json").read_text())
    tasks = {t["id"] for t in plan["tasks"]}
    assert catalog["approval"] == "projekt_do_akceptacji_G09"
    assert len(catalog["designs"]) == 18
    assert len({d["id"] for d in catalog["designs"]}) == 18
    for design in catalog["designs"]:
        assert set(design["states"]) == STATES, design["id"]
        assert all(design["states"].values()) and design["actions"]
        assert {"initial", "order", "dismiss", "announcements"} == set(design["focus"])
        assert set(design["tasks"]) <= tasks
        assert design["approval"] == "projekt_do_akceptacji"
    assert len(tokens["palettes"]) == 8
    for palette in tokens["palettes"]:
        for foreground, background in [("text", "background"), ("secondary", "background"), ("accent", "background"), ("onAccent", "accent")]:
            ratio = contrast(palette[foreground], palette[background])
            assert ratio >= 4.5, (palette["composition"], palette["mode"], foreground, ratio)
    assert all(d["rowMinDp"] >= 48 and d["touchMinDp"] >= 48 for d in tokens["density"].values())
    return catalog, tokens


def board(catalog, target):
    svg = ['<svg xmlns="http://www.w3.org/2000/svg" width="1242" height="5400" viewBox="0 0 1242 5400">']
    fonts = []
    for weight, filename in [(400, "inter_regular.ttf"), (600, "inter_semibold.ttf")]:
        data = base64.b64encode((ROOT / "android/core/designsystem/src/main/res/font" / filename).read_bytes()).decode()
        fonts.append(f"@font-face{{font-family:Inter;src:url(data:font/ttf;base64,{data});font-weight:{weight}}}")
    svg.append('<style>' + ''.join(fonts) + 'text{font-family:Inter,sans-serif} .muted{fill:#3A4054}</style>')
    svg.append('<defs><linearGradient id="bg" x2=".8" y2="1"><stop stop-color="#B9D3FF"/><stop offset=".55" stop-color="#EDF0F7"/><stop offset="1" stop-color="#E4D9F1"/></linearGradient><linearGradient id="photo" x2="1" y2="1"><stop stop-color="#A9C7E8"/><stop offset="1" stop-color="#F3C6A8"/></linearGradient></defs>')

    def text(x, y, value, size=15, weight=400, color=None):
        attrs = f' fill="{color or "#0B0D14"}"'
        svg.append(f'<text x="{x}" y="{y}" font-size="{size}" font-weight="{weight}"{attrs}>{escape(str(value))}</text>')

    def paragraph(x, y, value, width=43, size=14):
        for i, line in enumerate(textwrap.wrap(value, width=width)):
            text(x, y + i * (size + 6), line, size)

    def rect(x, y, w, h, fill="white", radius=20, stroke="#FFFFFF"):
        svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" fill="{fill}" stroke="{stroke}"/>')

    def row(y, label, detail="", action="›"):
        rect(18, y, 354, 64, "#FFFFFF", 18)
        text(34, y + 25, label, 15, 600)
        if detail: text(34, y + 47, detail, 12)
        text(334, y + 35, action, 17, 600, "#0058D6")

    def button(y, label="Zatwierdź", primary=True):
        rect(18, y, 354, 48, "#0A6CFF" if primary else "#FFFFFF", 24)
        text(38, y + 30, label, 16, 600, "#FFFFFF" if primary else "#0058D6")

    rect(0, 0, 1242, 5400, "#F5F6FA", 0)
    text(24, 40, "CH020 · projekty uzupełniające Liquid Glass", 24, 600)
    text(24, 70, "Propozycje do akceptacji G09 · 18 widoków · stany i fokus w katalogu JSON · S01–S10 pozostają bez zmian", 13)
    for i, d in enumerate(catalog["designs"]):
        x, y = 18 + i % 3 * 408, 105 + i // 3 * 875
        svg.append(f'<g transform="translate({x},{y})">')
        rect(0, 0, 390, 844, "url(#bg)", 36)
        rect(18, 32, 40, 40); text(30, 58, "‹", 24, 600)
        paragraph(72, 55, d["title"], 27, 17)
        paragraph(24, 115, d["purpose"], 43, 13)
        id = d["id"]
        if id == "date":
            rect(18, 190, 354, 340); text(36, 225, "‹     Październik 2026       ›", 17, 600)
            for j, day in enumerate(["Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd"]): text(32 + j * 48, 265, day, 12, 600)
            for r, week in enumerate(calendar.monthcalendar(2026, 10)):
                for c, day in enumerate(week):
                    if day:
                        if day == 31: rect(25 + c * 48, 280 + r * 40, 36, 36, "#0A6CFF", 18)
                        text(33 + c * 48, 305 + r * 40, day, 14, 600 if day == 31 else 400, "#FFFFFF" if day == 31 else None)
            button(560, "Wybierz 31 października")
        elif id == "time":
            rect(18, 200, 354, 190); text(50, 245, "Godzina", 15, 600); text(232, 245, "Minuta", 15, 600)
            rect(36, 265, 135, 80, "#EDF0F7"); rect(216, 265, 135, 80, "#EDF0F7")
            text(66, 319, "10", 40, 600); text(184, 319, ":", 32); text(248, 319, "00", 40, 600)
            paragraph(28, 430, "Strefa urządzenia. Przy zmianie czasu pokażemy nieistniejącą lub powtórzoną godzinę przed zapisem.")
            button(550, "Wybierz 10:00")
        elif id == "gallery":
            for n in range(9):
                rect(18 + n % 3 * 121, 205 + n // 3 * 121, 112, 112, "url(#photo)", 18)
            button(600, "Przejdź do wiadomości")
        elif id == "local-appearance":
            rect(18, 190, 354, 196); rect(30, 225, 270, 40); text(42, 250, "Dotarły, wyglądają super!", 14)
            rect(125, 285, 230, 40, "#0A6CFF"); text(137, 310, "To w sobotę o 18:00?", 14, color="#FFFFFF")
            for n, label in enumerate(["Szkło", "Mgła", "Grafit", "Kontrast"]):
                rect(18 + n * 91, 410, 81, 84); text(24 + n * 91, 460, label, 12, 600)
            row(520, "Dziedzicz globalny", "Domyślnie włączone", "✓"); button(650, "Zapisz wygląd tej rozmowy")
        elif id == "link":
            rect(18, 205, 354, 310); rect(34, 225, 322, 150, "url(#photo)")
            text(38, 410, "Zdjęcie · oryginał", 18, 600); text(38, 443, "Rozmiar i ważność z serwera", 13)
            paragraph(38, 475, "Dostęp tylko do pliku z tego linku.", 34, 13); button(550, "Pobierz"); button(615, "Otwórz w Chatrze", False)
        elif id == "pro":
            row(205, "Chatra Pro", "Dodatkowe kompozycje i reguły", "")
            paragraph(28, 315, "Podstawowe SMS/MMS, wyszukiwanie, harmonogram i dostępność pozostają darmowe.")
            rect(18, 410, 354, 115); paragraph(35, 445, "Oferta sklepu jest teraz niedostępna. Nie pokazujemy zmyślonej ceny.", 38)
            button(565, "Wróć do ustawień", False)
        elif id == "sim":
            row(205, "SIM 1 · Prywatna", "Aktywna karta", "●"); row(281, "SIM 2 · Praca", "Aktywna karta", "○")
            paragraph(28, 420, "Jeżeli zapamiętana karta zniknęła, wymagamy nowego wyboru. Numer telefonu karty może być niedostępny.")
            button(560, "Wybierz SIM 1")
        else:
            labels = {
                'inbox-menu': [("Oznacz wszystkie jako przeczytane", ""), ("Przypnij / odepnij", "Akcja wątku"), ("Przenieś do kosza", "Wymaga potwierdzenia"), ("Pomoc", "")],
                'conversation': [("Ola Nowak", "Kontakt lub uczestnicy"), ("Media", "Zdjęcia i pliki z rozmowy"), ("Wygląd tej rozmowy", "Dziedzicz globalny"), ("Wyciszenie", "Wyłączone"), ("Pierwsza nieprzeczytana", "Przejdź do wiadomości")],
                'templates': [("Będę za chwilę", "Podgląd treści szablonu"), ("Proszę o kontakt", "Podgląd treści szablonu"), ("Dodaj szablon", "Tytuł i treść")],
                'hd': [("Oryginał HD", "Bez kompresji · wymaga aktywacji"), ("Kopia bez lokalizacji", "Usunięcie danych GPS"), ("MMS", "Limit operatora"), ("Odbiorca bez Chatry", "Zapytaj o link")],
                'blocked': [("+48 500 000 001", "Odblokuj"), ("+48 500 000 002", "Odblokuj"), ("Dodaj numer", "Lokalna blokada wiadomości")],
                'trash': [("Rozmowa 1", "Odzyskaj"), ("Rozmowa 2", "Odzyskaj"), ("Rozmowa 3", "Odzyskaj"), ("Retencja: 30 dni", "Nowe wiadomości nie są usuwane")],
                'help': [("SMS, MMS i Chatra HD", "Transport i ograniczenia"), ("Telefon był wyłączony", "Decyzja o zaległej wysyłce"), ("Prywatność i uprawnienia", "Zgody są osobne")],
                'privacy': [("Dostęp do SMS", "Najpierw rola SMS"), ("Kontakty", "Sugestie odbiorców"), ("Karty SIM", "Stan telefonu"), ("Powiadomienia", "Osobna zgoda"), ("Wybrane zdjęcia", "Systemowy picker, bez całej galerii")],
                'gestures': [("Przesunięcie w prawo", "Oznacz jako przeczytane"), ("Przesunięcie w lewo", "Wybierz akcję"), ("Menu dostępności", "Te same działania bez gestów")],
                'unread': [("Pierwsza nieprzeczytana", "Skok bez oznaczania całej historii"), ("Oznacz jako nieprzeczytane", "Jawne nadpisanie użytkownika"), ("Wszystkie jako przeczytane", "Licznik z aktualnych danych")],
                'export': [("Zakres", "Wybrane wiadomości"), ("Format i odbiorca", "Potwierdź przed udostępnieniem"), ("Skrót rozmowy", "Bez treści w identyfikatorze")],
            }.get(id, [])
            for n, (label, detail) in enumerate(labels): row(205 + n * 76, label, detail)
            if len(labels) < 5: button(650, d['actions'][0])
        button(733, "Anuluj / Wróć", False)
        text(23, 818, f"{d['id']} · projekt CH020 · do akceptacji", 11)
        svg.append('</g>')
    svg.append('</svg>')
    target.write_text(''.join(svg))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--render', action='store_true')
    args = parser.parse_args()
    catalog, tokens = validate()
    if args.render:
        board(catalog, ROOT / 'docs/implementacja_android/CH020_Projekty.svg')
    print(f'PASS CH020: {len(catalog["designs"])} designs × 7 states, focus/actions/task mapping; {len(tokens["palettes"])} palettes with contrast ≥4.5; touch targets ≥48 dp')


if __name__ == '__main__':
    main()
