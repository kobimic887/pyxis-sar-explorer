#!/bin/sh
# Headless checks for the Pyxis additions. Needs only a JDK 21+ (no JavaFX).
# Usage: pyxis/run-tests.sh [extra.sdf ...]   (run from the repository root)
set -e
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
javac -nowarn -d "$OUT" -classpath "lib/*" \
  src/com/actelion/research/datawarrior/pyxis/ScaffoldHierarchy.java \
  src/com/actelion/research/datawarrior/pyxis/SubstituentPairs.java \
  src/com/actelion/research/datawarrior/pyxis/DocxTableWriter.java \
  pyxis/test/ScaffoldHierarchyTest.java pyxis/test/SubstituentPairsTest.java pyxis/test/DocxTableWriterTest.java
java -classpath "$OUT:lib/*" ScaffoldHierarchyTest "$@"
java -classpath "$OUT:lib/*" SubstituentPairsTest

java -Djava.awt.headless=true -classpath "$OUT:lib/*" DocxTableWriterTest
