#!/usr/bin/env bash
# Used after connectedFullDebugAndroidTest on a disposable AVD.
set -euo pipefail
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
test_apk="$1"
out="$2"
mkdir -p "$out"
adb install -r "$test_apk"
adb shell appops set app.chatra SCHEDULE_EXACT_ALARM allow

# Keep the target installed across phases. Gradle UTP may uninstall it after a run.
run_full_case() {
  local name="$1" method="$2"
  shift 2
  adb shell am instrument -w -r -e class "$method" "$@" app.chatra.test/androidx.test.runner.AndroidJUnitRunner | tee "$out/$name.txt"
  rg -q '^OK \(1 test\)' "$out/$name.txt"
  ! rg -q 'INSTRUMENTATION_STATUS_CODE: -(3|4)|FAILURES' "$out/$name.txt"
}
alarm=app.chatra.scheduling.NativeScheduleAlarmTest
run_full_case prepare-reboot "$alarm#prepareFutureScheduleForReboot" -e rebootPrepare true
adb shell am force-stop app.chatra
adb reboot
timeout 180 adb wait-for-device
timeout 180 bash -c 'until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d "\r")" == 1 ]]; do sleep 2; done'
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
# A force-stopped package stays stopped across boot. Explicitly open the app to exercise
# startup recovery for that case, then check persisted deadline, boot count and zero attempts.
adb shell am start -W -n app.chatra/app.chatra.MainActivity
run_full_case inspect-reboot "$alarm#inspectRebootRecoveryAndCancel" -e rebootInspect true

recovery=app.chatra.acceptance.InstallationRecoveryTest
run_full_case prepare-reset "$recovery#seedPrivateDataAndProvider" -e resetQa true
adb shell pm clear app.chatra | tee "$out/clear-private-data.txt"
rg -q '^Success' "$out/clear-private-data.txt"
adb shell cmd role add-role-holder --user 0 android.app.role.SMS app.chatra
adb shell pm grant app.chatra android.permission.READ_SMS
adb shell pm grant app.chatra android.permission.SEND_SMS
run_full_case inspect-reset "$recovery#systemHistorySurvivesWithoutLocalOperations" -e resetQa true
