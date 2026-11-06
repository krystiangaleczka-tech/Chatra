#!/usr/bin/env python3
"""Verify demo asset provenance and separation from the production APK."""
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parents[1]
original = root / "docs/implementacja_android/Chatra_Pakiet_referencyjny/Chatra — Liquid Glass.html"
fixtures = root / "build/ch006/screen-fixtures.json"
for variant, kind, name in [
    ("demo", "debug", "app-demo-debug.apk"),
    ("demo", "release", "app-demo-release-unsigned.apk"),
    ("production", "release", "app-production-release-unsigned.apk"),
]:
    apk = root / "app/build/outputs/apk" / variant / kind / name
    with ZipFile(apk) as archive:
        assets = [n for n in archive.namelist() if n.startswith("assets/")]
        if variant == "demo":
            assert archive.read("assets/reference.html") == original.read_bytes()
            assert archive.read("assets/screen-fixtures.json") == fixtures.read_bytes()
            assert len(json.loads(archive.read("assets/screen-fixtures.json"))["screens"]) == 10
        else:
            assert "assets/reference.html" not in assets
            assert "assets/screen-fixtures.json" not in assets
            assert all(n.startswith("assets/dexopt/") for n in assets), f"Unexpected production assets: {assets}"
    print(f"PASS {name}: asset separation, SHA-256 {hashlib.sha256(apk.read_bytes()).hexdigest()}")
