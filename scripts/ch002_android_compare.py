#!/usr/bin/env python3
"""CH002: porównanie dwóch renderów Androida ze sobą i z wzorcami macOS.

Wejście: wynik scripts/ch002_android_capture.mjs (android_capture.json, run1/, run2/).
Wyjście: porownanie.json i plansza macOS | Android do przeglądu wizualnego.
Wymaga Pillow i numpy. Uruchamiać z katalogu głównego repozytorium.
"""
import hashlib
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path("docs/implementacja_android")
OUT = ROOT / "CH002_Android"
REF = ROOT / "Chatra_Pakiet_referencyjny"
TOLERANCE = 3  # kolory wnętrza do 3/255 (specyfikacja Android)


def rgb(path):
    return np.asarray(Image.open(path).convert("RGB")).astype(np.int16)


def ssim(a, b):
    # Globalny SSIM na luminancji: wskaźnik pomocniczy, nie zastępuje przeglądu.
    def y(x):
        return 0.299 * x[..., 0] + 0.587 * x[..., 1] + 0.114 * x[..., 2]
    a, b = y(a.astype(np.float64)), y(b.astype(np.float64))
    c1, c2 = (0.01 * 255) ** 2, (0.03 * 255) ** 2
    ma, mb = a.mean(), b.mean()
    cov = ((a - ma) * (b - mb)).mean()
    return float(((2 * ma * mb + c1) * (2 * cov + c2)) / ((ma**2 + mb**2 + c1) * (a.var() + b.var() + c2)))


def main():
    capture = json.loads((OUT / "android_capture.json").read_text())
    manifest = json.loads((REF / "manifest_referencji.json").read_text())
    run1, run2 = capture["runs"][0], capture["runs"][1]
    screens, sheet = [], []
    for i, s1 in enumerate(run1["screens"]):
        sid, s2 = s1["id"], run2["screens"][i]
        a, b = rgb(OUT / s1["png"]), rgb(OUT / s2["png"])
        delta = np.abs(a - b).max(axis=2)
        stability = {
            "identical_bytes": s1["sha256"] == s2["sha256"],
            "run2_sha256": s2["sha256"],
            "pixels_total": int(delta.size),
            "pixels_changed": int((delta > 0).sum()),
            "pixels_over_tolerance": int((delta > TOLERANCE).sum()),
            "max_channel_delta": int(delta.max()),
        }
        found = [m for m in s1["measured"] if m["found"]]
        worst = max(found, key=lambda m: max(abs(m["dx"]), abs(m["dy"]), abs(m["dw"]), abs(m["dh"])))
        geometry = {
            "matched": f"{len(found)}/{len(s1['measured'])}",
            "within_1px": sum(max(abs(m["dx"]), abs(m["dy"]), abs(m["dw"]), abs(m["dh"])) <= 1 for m in found),
            "max_abs_delta_css_px": round(max(abs(worst["dx"]), abs(worst["dy"]), abs(worst["dw"]), abs(worst["dh"])), 2),
            "worst_element": {k: worst[k] for k in ("tag", "text", "dx", "dy", "dw", "dh")},
            "first_rows_changed_y": [round(m["dy"], 2) for m in found[:3]],
        }
        mac_path = REF / manifest["screens"][i]["path"]
        mac = Image.open(mac_path).convert("RGB")
        android_dp = Image.open(OUT / s1["png"]).convert("RGB").resize(mac.size, Image.LANCZOS)
        diff = np.abs(np.asarray(mac).astype(np.int16) - np.asarray(android_dp).astype(np.int16)).max(axis=2)
        versus_macos = {
            "macos_png": str(mac_path.relative_to(ROOT)),
            "android_downscaled_to": list(mac.size),
            "pixels_over_tolerance_pct": round(100 * float((diff > TOLERANCE).mean()), 2),
            "mean_channel_delta": round(float(diff.mean()), 2),
            "ssim_luma_global": round(ssim(np.asarray(mac), np.asarray(android_dp)), 4),
        }
        fonts = run1["fonts"][i]["glyphsByFont"]
        screens.append({"id": sid, "title": s1["title"], "android_png": s1["png"], "android_png_sha256": s1["sha256"],
                        "size_px": [s1["width"], s1["height"]], "stability": stability, "geometry_vs_macos": geometry,
                        "image_vs_macos": versus_macos, "rendered_fonts": fonts})
        sheet.append((sid, mac, android_dp))

    # Plansza: dla każdego ekranu macOS (lewo) | Android przeskalowany do 390×844 (prawo).
    w, h, gap, label = 390, 844, 24, 28
    board = Image.new("RGB", (5 * (2 * w + gap) + 4 * gap, 2 * (h + label) + gap), "white")
    draw = ImageDraw.Draw(board)
    for k, (sid, mac, android_dp) in enumerate(sheet):
        x, y = (k % 5) * (2 * w + 2 * gap), (k // 5) * (h + label + gap)
        draw.text((x, y + 6), f"{sid}  macOS Chrome (SF)", fill="black")
        draw.text((x + w + gap, y + 6), f"{sid}  Android Chrome (Inter)", fill="black")
        board.paste(mac, (x, y + label))
        board.paste(android_dp, (x + w + gap, y + label))
    board_path = OUT / "plansza_macos_vs_android.jpg"
    board.save(board_path, quality=88)

    report = {
        "task": "CH002",
        "tolerance_channel": TOLERANCE,
        "capture": "android_capture.json",
        "board": board_path.name,
        "board_sha256": hashlib.sha256(board_path.read_bytes()).hexdigest(),
        "screens": screens,
    }
    (OUT / "porownanie.json").write_text(json.dumps(report, ensure_ascii=False, indent=1))
    for s in screens:
        st, g, v = s["stability"], s["geometry_vs_macos"], s["image_vs_macos"]
        print(f"{s['id']} stable={'=' if st['identical_bytes'] else '~'} changed={st['pixels_changed']} >tol={st['pixels_over_tolerance']} "
              f"geom {g['matched']} ≤1px={g['within_1px']} max={g['max_abs_delta_css_px']} | vs macOS >tol={v['pixels_over_tolerance_pct']}% "
              f"ssim={v['ssim_luma_global']} | fonts={list(s['rendered_fonts'])}")


if __name__ == "__main__":
    main()
