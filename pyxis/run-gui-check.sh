#!/bin/sh
# Opens a project in the built application, builds its scaffold tree and checks that
# choosing a scaffold selects the right number of table rows. Needs a display and a
# JDK 21+ with JavaFX, and a datawarrior_all.jar built by ./buildDataWarrior.
# Usage: pyxis/run-gui-check.sh project.dwar picture.png   (run from the repository root)
set -e
PROJECT=$(cd "$(dirname "$1")" && pwd)/$(basename "$1")
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
cat > "$OUT/tree.dwam" <<EOF
<macro name="pyxis-tree">
<task name="openFile">
fileName=$PROJECT
</task>
<task name="buildScaffoldTree">
structureColumn=Structure
minMolecules=2
</task>
</macro>
EOF
javac -nowarn -d "$OUT" -classpath datawarrior_all.jar pyxis/test/ScaffoldTreeGuiCheck.java
java -Xmx1500m -DskipUpdates=true -DskipMessages=true -Djava.util.prefs.userRoot="$OUT/prefs" \
  --add-exports java.base/jdk.internal.module=ALL-UNNAMED \
  -classpath "datawarrior_all.jar:$OUT" ScaffoldTreeGuiCheck "$OUT/tree.dwam" "$2"

cat > "$OUT/grid.dwam" <<EOF
<macro name="pyxis-grid">
<task name="openFile">
fileName=$PROJECT
</task>
</macro>
EOF
javac -nowarn -d "$OUT" -classpath datawarrior_all.jar pyxis/test/RGroupGridGuiCheck.java
java -Xmx1500m -DskipUpdates=true -DskipMessages=true -Djava.util.prefs.userRoot="$OUT/grid-prefs" \
  --add-exports java.base/jdk.internal.module=ALL-UNNAMED \
  -classpath "datawarrior_all.jar:$OUT" RGroupGridGuiCheck "$OUT/grid.dwam" "$2"

javac -nowarn -d "$OUT" -classpath datawarrior_all.jar pyxis/test/LiveSelectionFilterGuiCheck.java
java -Xmx1500m -DskipUpdates=true -DskipMessages=true -Djava.util.prefs.userRoot="$OUT/live-prefs" \
  --add-exports java.base/jdk.internal.module=ALL-UNNAMED \
  -classpath "datawarrior_all.jar:$OUT" LiveSelectionFilterGuiCheck "$OUT/grid.dwam" "${2%.png}-live-filter.png"

javac -nowarn -d "$OUT" -classpath datawarrior_all.jar pyxis/test/SwapSummaryGuiCheck.java
java -Xmx1500m -DskipUpdates=true -DskipMessages=true -Djava.util.prefs.userRoot="$OUT/swap-prefs" \
  --add-exports java.base/jdk.internal.module=ALL-UNNAMED \
  -classpath "datawarrior_all.jar:$OUT" SwapSummaryGuiCheck "$OUT/grid.dwam"

javac -nowarn -d "$OUT" -classpath datawarrior_all.jar pyxis/test/LockedColumnsGuiCheck.java
java -Xmx1500m -DskipUpdates=true -DskipMessages=true -Djava.util.prefs.userRoot="$OUT/lock-prefs" \
  --add-exports java.base/jdk.internal.module=ALL-UNNAMED \
  -classpath "datawarrior_all.jar:$OUT" LockedColumnsGuiCheck "$OUT/grid.dwam" "${2%.png}-locked-columns.png"
