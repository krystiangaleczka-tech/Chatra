#!/usr/bin/env python3
"""Offline smoke test for the CH006 ten-screen image comparator."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile

from PIL import Image


def main():
    with tempfile.TemporaryDirectory(prefix="chatra-ch006-") as temporary:
        root = Path(temporary)
        source, actual, output = (root / p for p in ("reference", "actual", "report"))
        source.mkdir()
        actual.mkdir()
        for number in range(1, 11):
            name = f"android-S{number:02}.png"
            image = Image.new("RGB", (4, 3), (255, 255, 255))
            image.save(source / name)
            if number == 3:
                image.putpixel((1, 1), (250, 255, 255))
            image.save(actual / name)
        subprocess.run(
            [sys.executable, str(Path(__file__).with_name("ch006_compare_screens.py")),
             "--reference-dir", str(source), "--actual-dir", str(actual),
             "--output-dir", str(output)], check=True,
        )
        report = json.loads((output / "report.json").read_text(encoding="utf-8"))["screens"]
        assert len(report) == 10
        assert report["S03"]["affectedPixels"] == 1
        assert report["S03"]["maxChannelDelta"] == 5
        assert all(report[k]["affectedPixels"] == 0 for k in report if k != "S03")
        assert all((output / k / "overlay.png").is_file() for k in report)
        assert all((output / k / "diff.png").is_file() for k in report)
    print("CH006 comparator smoke test: 10 images, 1 intentional changed pixel PASS")


if __name__ == "__main__":
    main()
