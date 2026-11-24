#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
out="$repo_root/android/app/build/reports/ui/reference"
mkdir -p "$out/actual"
orig_size="$(adb shell wm size | tr -d '\r' | sed -n 's/^Override size: //p')"
orig_density="$(adb shell wm density | tr -d '\r' | sed -n 's/^Override density: //p')"
orig_immersive="$(adb shell settings get secure immersive_mode_confirmations | tr -d '\r')"
restore_display() {
  adb shell wm size "${orig_size:-reset}"
  adb shell wm density "${orig_density:-reset}"
  if [[ "$orig_immersive" == null ]]; then
    adb shell settings delete secure immersive_mode_confirmations
  else
    adb shell settings put secure immersive_mode_confirmations "$orig_immersive"
  fi
}
trap restore_display EXIT
adb shell wm size 780x1688
adb shell wm density 320
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
# A newly provisioned Android displays an instructional SystemUI overlay on the first
# immersive activity. Pre-confirm it for this AVD so the captures show the app.
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell getprop ro.build.fingerprint > "$out/build-fingerprint.txt"
for n in {01..10}; do
  adb shell am force-stop app.chatra.demo
  adb shell am start -W -n app.chatra.demo/app.chatra.MainActivity --ez frame true --es screen "S$n" > "$out/start-S$n.txt"
  grep -q '^Status: ok' "$out/start-S$n.txt"
  sleep 1
  adb exec-out screencap -p > "$out/actual/android-S$n.png"
done
# Identical captures would indicate a launch or screen-selection failure.
test "$(sha256sum "$out"/actual/*.png | cut -d' ' -f1 | sort -u | wc -l)" -eq 10
reference="$repo_root/docs/implementacja_android/CH002_Android"
python3 scripts/ch006_compare_screens.py --reference-dir "$reference/run1" --actual-dir "$out/actual" \
  --output-dir "$out/comparison" --resize-reference --regions "$reference/android_capture.json"
