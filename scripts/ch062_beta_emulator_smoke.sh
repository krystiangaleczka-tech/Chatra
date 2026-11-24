#!/usr/bin/env bash
set -euo pipefail
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
beta="$repo_root/build/beta"
out="$repo_root/android/app/build/reports/ui/beta-smoke.txt"
mkdir -p "$(dirname "$out")"
(cd "$beta" && sha256sum -c SHA256SUMS)
expected_head="$(git -C "$repo_root" rev-parse HEAD)"
test "$(cat "$beta/SOURCE_COMMIT")" = "$expected_head"
adb install -r "$beta/chatra-full-debug.apk"
adb install -r "$repo_root/android/app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk"
adb shell cmd role add-role-holder --user 0 android.app.role.SMS app.chatra
adb shell pm grant app.chatra android.permission.READ_SMS

setting() { adb shell settings get global "$1" | tr -d '\r'; }
airplane="$(setting airplane_mode_on)"
wifi="$(setting wifi_on)"
data="$(setting mobile_data)"
restore_network() {
  if [[ "$airplane" != 1 ]]; then adb shell cmd connectivity airplane-mode disable; fi
  if [[ "$wifi" == 1 ]]; then adb shell svc wifi enable; fi
  if [[ "$data" == 1 ]]; then adb shell svc data enable; fi
}
trap restore_network EXIT
adb shell cmd connectivity airplane-mode enable
adb shell svc wifi disable
adb shell svc data disable
adb shell am instrument -w -r -e betaSmoke true -e class app.chatra.acceptance.BetaInstallSmokeTest \
  app.chatra.test/androidx.test.runner.AndroidJUnitRunner | tee "$out"
grep -Eq '^OK \(1 test\)' "$out"
! grep -Eq 'INSTRUMENTATION_STATUS_CODE: -(3|4)|FAILURES' "$out"
