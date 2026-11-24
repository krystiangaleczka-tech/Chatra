#!/usr/bin/env bash
set -euo pipefail
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]
[[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" == 37 ]]
./gradlew :app:installFullDebug :app:assembleFullDebugAndroidTest
adb install -r app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk
out=app/build/reports/ui/transport-api37.txt
mkdir -p "$(dirname "$out")"
adb shell am instrument -w -r -e class app.chatra.acceptance.TransportApiTest \
  app.chatra.test/androidx.test.runner.AndroidJUnitRunner | tee "$out"
grep -Eq '^OK \(1 test\)' "$out"
! grep -Eq 'INSTRUMENTATION_STATUS_CODE: -(3|4)|FAILURES' "$out"
