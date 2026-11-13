#!/usr/bin/env bash
# Prints the CHANGELOG.md section for a version (e.g. 1.0.0). Fails if there is none.
set -euo pipefail
VERSION="${1:?usage: release-notes.sh <version>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

NOTES="$(awk -v v="$VERSION" '
  $0 ~ "^## \\[" v "\\]" { found=1; next }
  found && /^## \[/ { exit }
  found { print }
' "$ROOT/CHANGELOG.md" | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}')"

if [ -z "$(echo "$NOTES" | tr -d '[:space:]')" ]; then
  echo "No CHANGELOG.md entry found for version $VERSION" >&2
  exit 1
fi
printf '%s\n' "$NOTES"
