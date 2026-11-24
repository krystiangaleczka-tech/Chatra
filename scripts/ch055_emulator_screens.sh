#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
out="$repo_root/android/app/build/reports/ui/reference"
mkdir -p "$out/actual"
adb shell getprop ro.build.fingerprint > "$out/build-fingerprint.txt"
for n in {01..10}; do
  name="android-S$n.png"
  adb pull "/data/local/tmp/$name" "$out/actual/$name"
  test -s "$out/actual/$name"
  adb shell rm -f "/data/local/tmp/$name"
done
test "$(sha256sum "$out"/actual/*.png | cut -d' ' -f1 | sort -u | wc -l)" -eq 10
reference="$repo_root/docs/implementacja_android/CH002_Android"
python3 scripts/ch006_compare_screens.py --reference-dir "$reference/run1" --actual-dir "$out/actual" \
  --output-dir "$out/comparison" --resize-reference --regions "$reference/android_capture.json"
