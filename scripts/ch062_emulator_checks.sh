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
status=0
./gradlew :core:database:connectedDebugAndroidTest :app:connectedFullDebugAndroidTest || status=1
# The two app variants must not compete for the foreground/lifecycle on one device.
adb shell am force-stop app.chatra
# Leave room for the 430 dp adaptive test and independently constrain each Compose viewport.
(
  trap 'adb shell wm size reset; adb shell wm density reset' EXIT
  adb shell wm size 864x1900
  adb shell wm density 320
  for n in {01..10}; do
    adb shell rm -f "/data/local/tmp/android-S$n.png"
  done
  ./gradlew :app:connectedDemoDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.captureChatraQa=true \
    -Pandroid.testInstrumentationRunnerArguments.captureChatraReferences=true
) || status=1
for screenshot in "${screenshots[@]}"; do
  adb pull "/data/local/tmp/$screenshot" "app/build/reports/ui/$screenshot" || status=1
  test -s "app/build/reports/ui/$screenshot" || status=1
  adb shell rm -f "/data/local/tmp/$screenshot"
done

bash ../scripts/ch055_emulator_screens.sh || status=1

# The package and test runner must remain installed across an actual device restart.
./gradlew :app:installFullDebug :app:assembleFullDebugAndroidTest
adb shell cmd role add-role-holder --user 0 android.app.role.SMS app.chatra
for permission in READ_SMS SEND_SMS READ_PHONE_STATE POST_NOTIFICATIONS; do
  adb shell pm grant app.chatra "android.permission.$permission"
done
bash ../scripts/ch057_emulator_restart.sh \
  app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk \
  "$PWD/app/build/reports/ui/recovery" || status=1
if [[ "${1:-}" == "--verify-beta" ]]; then
  bash ../scripts/ch062_beta_emulator_smoke.sh || status=1
fi
exit "$status"
