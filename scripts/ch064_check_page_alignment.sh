#!/usr/bin/env bash
# Check the directly installable APK and the ELF libraries of both release archives.
set -euo pipefail
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:?Ustaw ANDROID_HOME lub ANDROID_SDK_ROOT}}"
apk="${1:?Podaj APK}"
bundle="${2:?Podaj AAB}"

"$sdk/build-tools/36.1.0/zipalign" -c -P 16 4 "$apk"
printf 'APK: wyrównanie ZIP 16 KiB — OK\n'
library_file="$(mktemp)"
trap 'unlink "$library_file"' EXIT

for archive in "$apk" "$bundle"; do
  archive_entries="$(unzip -Z1 "$archive")"
  libraries=0
  while IFS= read -r entry; do
    [[ "$entry" =~ ^([^/]+/)?lib/[^/]+/[^/]+\.so$ ]] || continue
    unzip -p "$archive" "$entry" > "$library_file"
    program_headers="$(LC_ALL=C readelf -lW "$library_file")"
    alignments="$(awk '$1 == "LOAD" { print $NF }' <<< "$program_headers")"
    if [[ -z "$alignments" ]]; then
      printf '%s: brak segmentów ELF LOAD w %s\n' "$archive" "$entry" >&2
      exit 1
    fi
    while IFS= read -r alignment; do
      if ! [[ "$alignment" =~ ^0x[0-9a-fA-F]+$ ]] || (( alignment < 0x4000 )); then
        printf '%s: %s ma wyrównanie ELF LOAD %s poniżej 16 KiB\n' "$archive" "$entry" "$alignment" >&2
        exit 1
      fi
    done <<< "$alignments"
    printf '%s: %s — ELF LOAD OK\n' "${archive##*/}" "$entry"
    ((libraries += 1))
  done <<< "$archive_entries"
  printf '%s: %d bibliotek natywnych\n' "${archive##*/}" "$libraries"
done
