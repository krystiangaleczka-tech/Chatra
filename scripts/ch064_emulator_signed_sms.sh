#!/usr/bin/env bash
# Install the signed QA APK and exercise the native inbox on a disposable emulator.
set -euo pipefail
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
release="$root/build/release-qa"
case "${1:-}" in
  --verify-role-change) report_dir=release-qa ;;
  --require-16k) report_dir=release-16k ;;
  *) printf 'Podaj --verify-role-change albo --require-16k.\n' >&2; exit 2 ;;
esac
out="$root/android/app/build/reports/ui/$report_dir"
mkdir -p "$out"
if [[ "$1" == "--require-16k" ]]; then
  adb shell getconf PAGE_SIZE | tr -d '\r' | tee "$out/page-size.txt"
  grep -qx '16384' "$out/page-size.txt"
fi

test "$(cat "$release/SOURCE_COMMIT")" = "$(git -C "$root" rev-parse HEAD)"
(cd "$release" && sha256sum -c SHA256SUMS)
if adb shell pm path app.chatra | grep -q '^package:'; then
  adb uninstall app.chatra
fi
adb install "$release/chatra-release-qa.apk"

grant_role() {
  adb shell cmd role add-role-holder --user 0 android.app.role.SMS app.chatra
  for permission in READ_SMS SEND_SMS RECEIVE_SMS READ_PHONE_STATE POST_NOTIFICATIONS; do
    adb shell pm grant app.chatra "android.permission.$permission"
  done
}
start_app() {
  adb shell am start -W -n app.chatra/app.chatra.MainActivity > "$out/$1-start.txt"
  grep -q '^Status: ok' "$out/$1-start.txt"
}
wait_for_ui() {
  local name="$1" required_text="$2" forbidden_text="${3:-}"
  for attempt in {1..10}; do
    if adb shell uiautomator dump /data/local/tmp/chatra-release-qa.xml >/dev/null 2>&1 &&
        adb exec-out cat /data/local/tmp/chatra-release-qa.xml > "$out/$name.xml" &&
        grep -Fq "$required_text" "$out/$name.xml" &&
        { [[ -z "$forbidden_text" ]] || ! grep -Fq "$forbidden_text" "$out/$name.xml"; }; then
      adb shell rm -f /data/local/tmp/chatra-release-qa.xml
      return 0
    fi
    sleep 2
  done
  adb logcat -d -t 500 AndroidRuntime:E '*:S' > "$out/$name-runtime.txt"
  printf 'Nie potwierdzono oczekiwanego ekranu: %s\n' "$name" >&2
  return 1
}

grant_role
start_app release
message="Chatra release QA SMS"
adb emu sms send +12025550163 "$message"
wait_for_ui release-incoming "$message"
printf 'Signed release showed the synthetic SMS in its inbox.\n' | tee "$out/release-incoming.txt"

if [[ "$1" == "--verify-role-change" ]]; then
  adb shell input keyevent KEYCODE_HOME
  adb shell cmd role remove-role-holder --user 0 android.app.role.SMS app.chatra
  start_app role-removed
  wait_for_ui role-removed 'Spokojne SMS i MMS. Bez reklam w rozmowach i bez zakładania konta.' "$message"
  adb shell input keyevent KEYCODE_HOME
  grant_role
  start_app role-restored
  wait_for_ui role-restored "$message"
  printf 'Removing the SMS role hid the inbox. Restoring it showed the same SMS.\n' | tee "$out/role-change.txt"
fi
