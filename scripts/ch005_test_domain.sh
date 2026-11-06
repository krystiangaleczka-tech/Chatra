#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
command -v kotlinc >/dev/null || { echo 'kotlinc required (JDK 17+)' >&2; exit 1; }
kotlinc \
  "$ROOT/core/model/src/main/kotlin/pl/chatra/core/model/Models.kt" \
  "$ROOT/core/model/src/main/kotlin/pl/chatra/core/model/Ports.kt" \
  "$ROOT/core/model/src/main/kotlin/pl/chatra/core/model/Transitions.kt" \
  "$ROOT/core/model/src/test/kotlin/pl/chatra/core/model/DomainContractTest.kt" \
  -include-runtime -d "$OUT/domain-tests.jar"
java -jar "$OUT/domain-tests.jar"
