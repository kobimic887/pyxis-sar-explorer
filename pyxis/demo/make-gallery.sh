#!/bin/sh
# Saves pictures of the SAR workflow windows for one project.
# Needs a display, a JDK 21+ with JavaFX and a datawarrior_all.jar built by ./buildDataWarrior.
# Usage: pyxis/demo/make-gallery.sh project.dwar output-directory value-column   (from the repository root)
set -e
PROJECT=$(cd "$(dirname "$1")" && pwd)/$(basename "$1")
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
printf '<macro name="open">\n<task name="openFile">\nfileName=%s\n</task>\n</macro>\n' "$PROJECT" > "$OUT/open.dwam"
javac -nowarn -d "$OUT" -classpath datawarrior_all.jar pyxis/demo/DemoGallery.java
java -Xmx1500m -DskipUpdates=true -DskipMessages=true -Djava.util.prefs.userRoot="$OUT/prefs" \
  --add-exports java.base/jdk.internal.module=ALL-UNNAMED \
  -classpath "datawarrior_all.jar:$OUT" DemoGallery "$OUT/open.dwam" "$2" "$3"
