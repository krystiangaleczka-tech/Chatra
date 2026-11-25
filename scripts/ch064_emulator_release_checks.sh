#!/usr/bin/env bash
# Disposable AVD only. Covers Doze, package upgrade and a clean release install.
set -euo pipefail
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
out="$root/android/app/build/reports/ui/release-qa"
mkdir -p "$out"
cd "$root/android"
run_case() {
  local name="$1" method="$2"
  shift 2
  adb shell am instrument -w -r -e class "$method" "$@" app.chatra.test/androidx.test.runner.AndroidJUnitRunner | tee "$out/$name.txt" || return 1
  grep -Eq '^OK \(1 test\)' "$out/$name.txt" || return 1
  ! grep -Eq 'INSTRUMENTATION_STATUS_CODE: -(3|4)|FAILURES' "$out/$name.txt"
}
grant_sms_role() {
  adb shell cmd role add-role-holder --user 0 android.app.role.SMS app.chatra
  for permission in READ_SMS SEND_SMS RECEIVE_SMS READ_PHONE_STATE POST_NOTIFICATIONS; do
    adb shell pm grant app.chatra "android.permission.$permission"
  done
}

status=0
grant_sms_role
adb shell appops set app.chatra SCHEDULE_EXACT_ALARM allow
(
  trap 'adb shell dumpsys deviceidle unforce; adb shell dumpsys battery reset; adb shell input keyevent KEYCODE_WAKEUP' EXIT
  adb shell input keyevent KEYCODE_HOME || exit 1
  adb shell input keyevent KEYCODE_SLEEP || exit 1
  adb shell dumpsys battery unplug || exit 1
  adb shell dumpsys deviceidle force-idle | tee "$out/deviceidle.txt" || exit 1
  run_case native-doze app.chatra.scheduling.NativeScheduleAlarmTest#exactWakeUsesSharedQueueOnceWithNoVisibleActivity     -e nativeAlarm true -e assertDeepIdle true
) || status=1

adb shell wm dismiss-keyguard
recovery=app.chatra.acceptance.InstallationRecoveryTest
run_case before-update "$recovery#seedPrivateDataAndProvider" -e resetQa true
adb shell dumpsys package app.chatra | sed -n '/versionCode=/p' > "$out/version-before.txt"
grep -q 'versionCode=1 ' "$out/version-before.txt"
./gradlew :app:assembleFullDebug -PchatraVersionCode=2
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
run_case after-update "$recovery#apkUpdatePreservesLocalStateWithoutSending" -e resetQa true -e upgradeQa true || status=1

# The release APK is signed by a different key. Install afresh in this test environment.
release="$root/build/release-qa"
test "$(cat "$release/SOURCE_COMMIT")" = "$(git -C "$root" rev-parse HEAD)"
(cd "$release" && sha256sum -c SHA256SUMS)
adb uninstall app.chatra
adb install "$release/chatra-release-qa.apk"
grant_sms_role
adb shell am start -W -n app.chatra/app.chatra.MainActivity > "$out/release-start.txt"
grep -q '^Status: ok' "$out/release-start.txt"
message="Chatra release QA SMS"
adb emu sms send +12025550163 "$message"
# The emulated modem sends through SMS_DELIVER. Require a visible inbox row before accepting.
for attempt in {1..10}; do
  if adb shell uiautomator dump /data/local/tmp/release-qa.xml >/dev/null 2>&1 &&
      adb exec-out cat /data/local/tmp/release-qa.xml > "$out/release-incoming.xml" &&
      grep -Fq "$message" "$out/release-incoming.xml"; then
    adb shell rm -f /data/local/tmp/release-qa.xml
    printf 'Signed release showed one synthetic SMS in its inbox.\n' | tee "$out/release-incoming.txt"
    exit "$status"
  fi
  sleep 2
done
adb logcat -d -t 500 AndroidRuntime:E '*:S' > "$out/release-runtime.txt"
exit 1
