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
cd "$repo_root/android"
./gradlew :app:assembleFullDebug \
  -PchatraBackendOrigin= -PchatraHdPilot=false -PchatraPlayProduct= \
  -PchatraFirebaseApiKey= -PchatraFirebaseApplicationId= \
  -PchatraFirebaseSenderId= -PchatraFirebaseProjectId=

output="$repo_root/build/beta"
mkdir -p "$output"
cp app/build/outputs/apk/full/debug/app-full-debug.apk "$output/chatra-full-debug.apk"
cd "$output"
sha256sum chatra-full-debug.apk > SHA256SUMS
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
printf 'Pakiet dla testerów: %s\n' "$output"
