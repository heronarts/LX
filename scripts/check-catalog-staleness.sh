#!/bin/bash
#
# Reports catalog entries (src/main/resources/catalog/*.md) whose sourceSha256
# no longer matches the current source file, or whose source file is missing.
#
# This is informational only, for catalog maintainers. It is NOT a build gate:
# contributors editing a pattern/effect/modulator are never required to update
# or even look at its catalog entry. This script always exits 0.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CATALOG_DIR="$ROOT_DIR/src/main/resources/catalog"

checked=0
stale=0
missing=0

for f in "$CATALOG_DIR"/*.md; do
  [ -e "$f" ] || continue
  base=$(basename "$f")
  [ "$base" = "README.md" ] && continue

  class=$(awk -F': *' '/^class:/{print $2; exit}' "$f")
  sourcePath=$(awk -F': *' '/^sourcePath:/{print $2; exit}' "$f")
  expectedSha=$(awk -F': *' '/^sourceSha256:/{print $2; exit}' "$f")

  checked=$((checked+1))

  fullpath="$ROOT_DIR/$sourcePath"
  if [ ! -f "$fullpath" ]; then
    echo "MISSING-SOURCE: $class ($sourcePath)"
    missing=$((missing+1))
    continue
  fi

  actualSha=$(shasum -a 256 "$fullpath" | awk '{print $1}')
  if [ "$actualSha" != "$expectedSha" ]; then
    echo "STALE: $class"
    stale=$((stale+1))
  fi
done

echo "$checked entries checked, $stale stale, $missing missing source"
exit 0
