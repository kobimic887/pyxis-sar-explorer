#!/bin/sh
# Copies the installers built by CI to names that do not change between versions.
# Usage: pyxis/stable-installer-names.sh <directory with installers> <output directory>
# Fails if an installer cannot be classified or two would get the same name.
set -e
IN=$1
OUT=$2
mkdir -p "$OUT"
for FILE in "$IN"/*; do
  [ -f "$FILE" ] || continue
  BASE=$(basename "$FILE")
  case "$BASE" in
    *.msi) NAME=Pyxis-SAR-Explorer-windows-x64.msi ;;
    *.deb) NAME=Pyxis-SAR-Explorer-linux-x64.deb ;;
    *arm64*.dmg|*aarch64*.dmg) NAME=Pyxis-SAR-Explorer-macos-arm64.dmg ;;
    *x64*.dmg|*x86_64*.dmg) NAME=Pyxis-SAR-Explorer-macos-x64.dmg ;;
    *) echo "cannot classify installer: $BASE" >&2; exit 1 ;;
  esac
  if [ -e "$OUT/$NAME" ]; then
    echo "two installers map to $NAME (second: $BASE)" >&2
    exit 1
  fi
  cp "$FILE" "$OUT/$NAME"
done
for NAME in Pyxis-SAR-Explorer-windows-x64.msi Pyxis-SAR-Explorer-linux-x64.deb Pyxis-SAR-Explorer-macos-arm64.dmg Pyxis-SAR-Explorer-macos-x64.dmg; do
  [ -f "$OUT/$NAME" ] || { echo "missing installer: $NAME" >&2; exit 1; }
done
