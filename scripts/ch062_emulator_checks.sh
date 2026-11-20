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
trap 'adb pull /sdcard/Android/data/app.chatra.demo/files/qa/. app/build/reports/ui/ || true' EXIT
./gradlew :app:installFullDebug
adb shell cmd role add-role-holder --user 0 android.app.role.SMS app.chatra
for permission in READ_SMS SEND_SMS READ_PHONE_STATE POST_NOTIFICATIONS; do
  adb shell pm grant app.chatra "android.permission.$permission"
done
./gradlew :core:database:connectedDebugAndroidTest :app:connectedFullDebugAndroidTest
# The two app variants must not compete for the foreground/lifecycle on one device.
adb shell am force-stop app.chatra
./gradlew :app:connectedDemoDebugAndroidTest
