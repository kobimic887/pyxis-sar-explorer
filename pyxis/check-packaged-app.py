#!/usr/bin/env python3
"""Run the actual jpackage launcher; retain the macro, logs and checked CSV for CI."""
import csv
import os
from pathlib import Path
import re
import signal
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
out = root / 'packaged-check'
out.mkdir(exist_ok=True)
macro = out / 'tasks.dwam'
pairs = out / 'pairs.csv'
pairs.unlink(missing_ok=True)
# SARTestWithEZ has a real single-position pair; simpleSAR only differs at multiple positions.
# Neither fixture starts with Scaffold/R-group columns.
# Path.as_posix() gives native Windows drive paths with forward slashes, including spaces.
macro.write_text(f'''<macro name="packaged-pyxis-check">
<task name="openFile">
fileName={(root / 'test_data/sar/SARTestWithEZ.dwar').as_posix()}
</task>
<task name="buildScaffoldTree">
structureColumn=Structure
minMolecules=1
</task>
<task name="decomposeRGroups">
structureColumn=Structure
scaffoldMode=murcko
useExistingColumns=false
</task>
<task name="waitSomeSeconds">
seconds=3
</task>
<task name="analyseSubstituentPairs">
scaffoldColumn=Scaffold
</task>
<task name="waitSomeSeconds">
seconds=3
</task>
<task name="exportCSVFile">
fileName={pairs.as_posix()}
</task>
<task name="waitSomeSeconds">
seconds=3
</task>
<task name="exitProgram">
saveChanges=no
</task>
</macro>
''', encoding='utf-8')
launcher = Path(sys.argv[1]).resolve()
print(f'Launching packaged application: {launcher}', flush=True)
with (out / 'launcher.log').open('w', encoding='utf-8') as log:
    process = subprocess.Popen([str(launcher), macro.as_posix()], stdout=log,
                               stderr=subprocess.STDOUT, start_new_session=os.name != 'nt')
    try:
        # Popen.wait waits for GUI-subsystem executables too; shell invocation alone does not.
        code = process.wait(timeout=150)
    except subprocess.TimeoutExpired:
        if os.name == 'nt':
            subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], check=False)
        else:
            os.killpg(process.pid, signal.SIGKILL)
        process.wait()
        raise RuntimeError('Packaged application did not exit within 150 seconds')
    finally:
        print((out / 'launcher.log').read_text(encoding='utf-8', errors='replace'), flush=True)
if code != 0:
    raise RuntimeError(f'Packaged application exited with status {code}')
log_text = (out / 'launcher.log').read_text(encoding='utf-8', errors='replace')
if not re.search(r'Pyxis scaffold tree displayed: [1-9][0-9]* scaffolds', log_text):
    raise RuntimeError('No evidence that Build Scaffold Tree displayed a nonempty tree')
with pairs.open(encoding='utf-8-sig', newline='') as stream:
    reader = csv.DictReader(stream)
    # DataWarrior annotates chemical columns with their exported encoding.
    reader.fieldnames = [name.removesuffix(' [idcode]') for name in (reader.fieldnames or [])]
    required = {'Molecule A', 'Molecule B', 'Scaffold', 'Position', 'Substituent A', 'Substituent B'}
    if not required.issubset(reader.fieldnames or []):
        raise RuntimeError(f'Export is not a substituent-pair table: {reader.fieldnames}')
    rows = list(reader)
    if not rows or not all(row['Position'] and row['Scaffold'] for row in rows):
        raise RuntimeError('Substituent-pair export has no valid data rows')
print(f'PASS: packaged Build Scaffold Tree displayed; Analyse Substituent Pairs exported {len(rows)} pairs')
