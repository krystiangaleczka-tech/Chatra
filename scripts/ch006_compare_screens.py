#!/usr/bin/env python3
"""Compare all ten reference screens without changing goldens."""
import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageChops

SCREEN_IDS = tuple(f"S{i:02}" for i in range(1, 11))


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def compare(reference: Path, actual: Path, target: Path, threshold: int,
            *, resize_reference: bool = False, regions: list | None = None) -> dict:
    with Image.open(reference) as source, Image.open(actual) as captured:
        left, right = source.convert("RGB"), captured.convert("RGB")
        reference_size = left.size
        if left.size != right.size:
            if not resize_reference or left.width * right.height != left.height * right.width:
                raise ValueError(f"Dimension mismatch: {reference.name}: {left.size} != {right.size}")
            left = left.resize(right.size, Image.Resampling.LANCZOS)
        diff = ImageChops.difference(left, right)
        max_deltas = [max(rgb) for rgb in diff.get_flattened_data()]
        affected = sum(delta > threshold for delta in max_deltas)
        target.mkdir(parents=True, exist_ok=True)
        Image.blend(left, right, 0.5).save(target / "overlay.png")
        heatmap = Image.new("RGB", left.size)
        heatmap.putdata([(255, 0, 0) if delta > threshold else (0, 0, 0) for delta in max_deltas])
        heatmap.save(target / "diff.png")
        findings = []
        scale = left.width / 390
        for region in regions or []:
            x, y, w, h = (region[key] for key in ("x", "y", "w", "h"))
            box = (max(0, round(x * scale)), max(0, round(y * scale)),
                   min(left.width, round((x + w) * scale)), min(left.height, round((y + h) * scale)))
            if box[0] >= box[2] or box[1] >= box[3]:
                continue
            pixels = diff.crop(box).get_flattened_data()
            findings.append({"tag": region["tag"], "text": region["text"], "cssBox": [x, y, w, h],
                             "affectedFraction": sum(max(rgb) > threshold for rgb in pixels) / len(pixels)})
        return {
            "referenceSha256": sha256(reference),
            "actualSha256": sha256(actual),
            "width": left.width,
            "height": left.height,
            "thresholdPerChannel": threshold,
            "referenceSize": list(reference_size),
            "regions": findings,
            "totalPixels": len(max_deltas),
            "affectedPixels": affected,
            "affectedFraction": affected / len(max_deltas),
            "maxChannelDelta": max(max_deltas, default=0),
            "meanMaxChannelDelta": sum(max_deltas) / len(max_deltas) if max_deltas else 0,
        }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference-dir", type=Path, required=True)
    parser.add_argument("--actual-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--threshold", type=int, default=3)
    parser.add_argument("--resize-reference", action="store_true")
    parser.add_argument("--regions", type=Path, help="CH002 Android capture JSON with approved reference coordinates")
    args = parser.parse_args()
    if args.threshold < 0 or args.threshold > 255:
        parser.error("--threshold must be between 0 and 255")
    regions = {}
    if args.regions:
        manifest = json.loads(args.regions.read_text(encoding="utf-8"))
        regions = {screen["id"]: [region for region in screen["measured"] if region.get("found")]
                   for screen in manifest["runs"][0]["screens"]}
    reports = {}
    for screen_id in SCREEN_IDS:
        filename = f"android-{screen_id}.png"
        reference = args.reference_dir / filename
        actual = args.actual_dir / filename
        if not reference.is_file() or not actual.is_file():
            parser.error(f"Missing PNG for {screen_id}: {reference} or {actual}")
        reports[screen_id] = compare(reference, actual, args.output_dir / screen_id, args.threshold,
                                     resize_reference=args.resize_reference, regions=regions.get(screen_id))
    args.output_dir.mkdir(parents=True, exist_ok=True)
    report_path = args.output_dir / "report.json"
    report_path.write_text(json.dumps({"schemaVersion": 1, "screens": reports}, indent=2) + "\n", encoding="utf-8")
    for name, report in reports.items():
        print(f"{name}: {report['affectedFraction']:.2%} of pixels above threshold, {len(report['regions'])} regions")
    print(f"CH006 compare: {len(reports)} screens, "
          f"{sum(x['affectedPixels'] for x in reports.values())} changed pixels, report: {report_path}")


if __name__ == "__main__":
    main()
