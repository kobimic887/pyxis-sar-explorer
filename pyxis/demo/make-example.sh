#!/bin/sh
# Rebuilds the example project shipped inside the program from pyxis/demo/cox2-chembl.tsv.
# Needs a display, a JDK 21+ with JavaFX and a datawarrior_all.jar built by ./buildDataWarrior.
# Usage: pyxis/demo/make-example.sh   (from the repository root)
set -e
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
javac -nowarn -d "$OUT" -classpath datawarrior_all.jar pyxis/demo/MakeExample.java
java -Xmx1500m -DskipUpdates=true -DskipMessages=true -Djava.util.prefs.userRoot="$OUT/prefs" \
  --add-exports java.base/jdk.internal.module=ALL-UNNAMED \
  -classpath "datawarrior_all.jar:$OUT" MakeExample pyxis/demo/cox2-chembl.tsv "$(pwd)/src/html/pyxis/cox2-example.dwar"
