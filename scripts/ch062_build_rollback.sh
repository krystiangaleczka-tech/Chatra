#!/usr/bin/env bash
# Rebuild the previous QA source at a higher versionCode so Android can preserve data.
set -euo pipefail
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
previous_source=894b034134e8870378846ef8c6cfde1c52b5eb5e
cd "$repo_root"
if ! git cat-file -e "$previous_source^{commit}" 2>/dev/null; then
  git fetch --no-tags --depth=1 origin "$previous_source"
fi
temp_dir="$(mktemp -d)"
cleanup() {
  if [[ -e "$temp_dir/previous/.git" ]]; then
    git worktree remove --force "$temp_dir/previous"
  fi
  rmdir -- "$temp_dir"
}
trap cleanup EXIT
git worktree add --detach "$temp_dir/previous" "$previous_source"
ORG_GRADLE_PROJECT_chatraVersionCode=3 bash "$temp_dir/previous/scripts/ch062_build_beta.sh"
rollback="$temp_dir/previous/build/beta"
grep -q "^package: name='app.chatra' versionCode='3' " "$rollback/APK_METADATA.txt"
test "$(cat "$rollback/SOURCE_COMMIT")" = "$previous_source"
output="$repo_root/build/rollback-qa"
mkdir -p "$output"
cp "$rollback"/{chatra-full-debug.apk,APK_METADATA.txt,SHA256SUMS,SIGNING.txt,SOURCE_COMMIT} "$output/"
