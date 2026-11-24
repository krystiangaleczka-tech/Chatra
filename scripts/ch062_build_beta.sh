#!/usr/bin/env bash
# Build a tester APK with every remote service explicitly disabled.
set -euo pipefail
if (( $# )); then
  printf 'Użycie: %s (bez argumentów; usługi zdalne pozostają wyłączone)\n' "$0" >&2
  exit 2
fi
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -n "$(git -C "$repo_root" status --porcelain)" ]]; then
  printf 'Zapisz zmiany w commicie przed budową bety, aby APK miało odtwarzalne źródło.\n' >&2
  exit 2
fi
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
tools="$sdk_root/build-tools/36.1.0"
if [[ -z "$sdk_root" || ! -x "$tools/apksigner" || ! -x "$tools/aapt2" ]]; then
  printf 'Ustaw ANDROID_HOME lub ANDROID_SDK_ROOT i zainstaluj Android Build Tools 36.1.0.
' >&2
  exit 2
fi
cd "$repo_root/android"
./gradlew :app:assembleFullDebug \
  -PchatraBackendOrigin= -PchatraHdPilot=false -PchatraPlayProduct= \
  -PchatraFirebaseApiKey= -PchatraFirebaseApplicationId= \
  -PchatraFirebaseSenderId= -PchatraFirebaseProjectId=

output="$repo_root/build/beta"
mkdir -p "$output"
stage="$(mktemp -d "$output/.stage.XXXXXX")"
trap 'rm -rf -- "$stage"' EXIT
cp app/build/outputs/apk/full/debug/app-full-debug.apk "$stage/chatra-full-debug.apk"
cd "$stage"
"$tools/apksigner" verify --print-certs chatra-full-debug.apk > SIGNING.txt
"$tools/aapt2" dump badging chatra-full-debug.apk > APK_METADATA.txt
grep -q "^package: name='app.chatra' " APK_METADATA.txt
grep -q '^application-debuggable' APK_METADATA.txt
sha256sum chatra-full-debug.apk > SHA256SUMS
sha256sum -c SHA256SUMS
git -C "$repo_root" rev-parse HEAD > SOURCE_COMMIT
cat > RELEASE_NOTES.txt <<'NOTES'
Chatra — beta SMS/MMS dla testerów, Android 12 lub nowszy.
HD, backend, FCM i zakupy Pro są wyłączone w tej kompilacji.
To pełny wariant: po nadaniu roli SMS może wysyłać wiadomości i zmieniać historię.
Kosz może trwale usuwać wiadomości po terminie retencji lub potwierdzeniu.
Używaj urządzenia QA i danych testowych. SMS/MMS podlegają taryfie operatora.
APK ma podpis debug. Zachowaj ten sam prywatny klucz do kolejnych aktualizacji;
inny podpis uniemożliwi aktualizację z zachowaniem danych.
NOTES
mv chatra-full-debug.apk SIGNING.txt APK_METADATA.txt SHA256SUMS SOURCE_COMMIT RELEASE_NOTES.txt "$output/"
printf 'Pakiet dla testerów: %s\n' "$output"
