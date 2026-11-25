#!/usr/bin/env bash
# Build the release variant with a one-run QA signing key.
set -euo pipefail
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -n "$(git -C "$root" status --porcelain)" ]]; then
  printf 'Zapisz zmiany w commicie przed budową kandydata QA.\n' >&2
  exit 2
fi
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:?Ustaw ANDROID_HOME lub ANDROID_SDK_ROOT}}"
tools="$sdk/build-tools/36.1.0"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
umask 077
export CHATRA_QA_SIGNING_PASS
CHATRA_QA_SIGNING_PASS="$(openssl rand -hex 24)"
keytool -genkeypair -keyalg RSA -keysize 2048 -validity 30 -alias chatra-qa   -dname "CN=Chatra release QA" -keystore "$tmp/qa.p12" -storetype PKCS12   -storepass:env CHATRA_QA_SIGNING_PASS -keypass:env CHATRA_QA_SIGNING_PASS
cat > "$tmp/signing.properties" <<PROPERTIES
storeFile=$tmp/qa.p12
storePassword=$CHATRA_QA_SIGNING_PASS
keyAlias=chatra-qa
keyPassword=$CHATRA_QA_SIGNING_PASS
PROPERTIES
cd "$root/android"
./gradlew :app:assembleFullRelease :app:bundleFullRelease --no-configuration-cache   "-PchatraSigningPropertiesFile=$tmp/signing.properties" -PchatraVersionCode=2   -PchatraBackendOrigin= -PchatraHdPilot=false -PchatraPlayProduct=   -PchatraFirebaseApiKey= -PchatraFirebaseApplicationId=   -PchatraFirebaseSenderId= -PchatraFirebaseProjectId=
unset CHATRA_QA_SIGNING_PASS
out="$root/build/release-qa"
mkdir -p "$out"
cp app/build/outputs/apk/full/release/app-full-release.apk "$out/chatra-release-qa.apk"
cp app/build/outputs/bundle/fullRelease/app-full-release.aab "$out/chatra-release-qa.aab"
cd "$out"
"$tools/apksigner" verify --print-certs chatra-release-qa.apk > SIGNING.txt
jarsigner -J-Duser.language=en -J-Duser.country=US -verify chatra-release-qa.aab > AAB_SIGNING.txt
grep -q 'jar verified' AAB_SIGNING.txt
"$tools/aapt2" dump badging chatra-release-qa.apk > APK_METADATA.txt
grep -q "^package: name='app.chatra' versionCode='2' " APK_METADATA.txt
grep -q "^targetSdkVersion:'36'" APK_METADATA.txt
if grep -q '^application-debuggable' APK_METADATA.txt; then exit 1; fi
sha256sum chatra-release-qa.apk chatra-release-qa.aab > SHA256SUMS
sha256sum -c SHA256SUMS
git -C "$root" rev-parse HEAD > SOURCE_COMMIT
cat > RELEASE_NOTES.txt <<'NOTES'
Pakiet QA z kompilacji release. SMS/MMS, harmonogram i funkcje lokalne.
Usługi HD, Firebase i Play Billing wyłączone dla tej próby.
Podpis jednorazowym kluczem QA. Paczka przeznaczona do czystej instalacji testowej.
NOTES
