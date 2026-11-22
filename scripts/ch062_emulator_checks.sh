#!/usr/bin/env bash
# Disposable emulator only: full telephony tests may modify the SMS provider.
set -euo pipefail
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root/android"
if [[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ]]; then
  printf 'Te testy wymagają emulatora QA; nie uruchamiaj ich na prywatnym telefonie.\n' >&2
  exit 2
fi
mkdir -p app/build/reports/ui
screenshots=(CH065-details.png CH066-gallery.png)
for screenshot in "${screenshots[@]}"; do
  adb shell rm -f "/data/local/tmp/$screenshot"
  rm -f "app/build/reports/ui/$screenshot"
done
./gradlew :app:installFullDebug
adb shell cmd role add-role-holder --user 0 android.app.role.SMS app.chatra
for permission in READ_SMS SEND_SMS READ_PHONE_STATE POST_NOTIFICATIONS; do
  adb shell pm grant app.chatra "android.permission.$permission"
done
./gradlew :core:database:connectedDebugAndroidTest :app:connectedFullDebugAndroidTest
# The two app variants must not compete for the foreground/lifecycle on one device.
adb shell am force-stop app.chatra
./gradlew :app:connectedDemoDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.captureChatraQa=true
for screenshot in "${screenshots[@]}"; do
  adb pull "/data/local/tmp/$screenshot" "app/build/reports/ui/$screenshot"
  test -s "app/build/reports/ui/$screenshot"
  adb shell rm -f "/data/local/tmp/$screenshot"
done
