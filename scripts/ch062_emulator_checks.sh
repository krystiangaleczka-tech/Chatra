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
remote_screenshot=/data/local/tmp/CH065-details.png
local_screenshot=app/build/reports/ui/CH065-details.png
adb shell rm -f "$remote_screenshot"
rm -f "$local_screenshot"
./gradlew :app:installFullDebug
adb shell cmd role add-role-holder --user 0 android.app.role.SMS app.chatra
for permission in READ_SMS SEND_SMS READ_PHONE_STATE POST_NOTIFICATIONS; do
  adb shell pm grant app.chatra "android.permission.$permission"
done
./gradlew :core:database:connectedDebugAndroidTest :app:connectedFullDebugAndroidTest
# The two app variants must not compete for the foreground/lifecycle on one device.
adb shell am force-stop app.chatra
./gradlew :app:connectedDemoDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.captureChatraQa=true
adb pull "$remote_screenshot" "$local_screenshot"
test -s "$local_screenshot"
adb shell rm -f "$remote_screenshot"
