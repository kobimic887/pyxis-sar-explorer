#!/usr/bin/env python3
"""Run the actual jpackage launcher; retain the macro, logs and checked CSV for CI."""
import argparse
import csv
import os
from pathlib import Path
import re
import signal
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('launcher')
parser.add_argument('--document', action='store_true', help='Launch a .dwar with an embedded autostart macro')
parser.add_argument('--evidence-dir', default='packaged-check')
args = parser.parse_args()
out = root / args.evidence_dir
out.mkdir(parents=True, exist_ok=True)
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
<task name="addRowNumbers">
columnName=Check Value
firstNumber=1
</task>
<task name="sAROverview">
structureColumn=Structure
valueColumn=Check Value
</task>
<task name="waitSomeSeconds">
seconds=3
</task>
<task name="createRGroupGrid">
scaffoldColumn=Scaffold
rowColumn=R1
columnColumn=R2
valueColumn=Check Value
aggregate=mean
order=frequency
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
<task name="openSARExample">
</task>
<task name="waitSomeSeconds">
seconds=3
</task>
<task name="exitProgram">
saveChanges=no
</task>
</macro>
''', encoding='utf-8')
launch_file = macro
if args.document:
    # The macro acts on the document loaded by the launcher, without an openFile task.
    # Thus a nonempty tree/export proves the .dwar argument reached the file loader.
    tasks = re.sub(r'<task name="openFile">.*?</task>\n', '', macro.read_text(), flags=re.S)
    tasks = tasks.replace('<macro name="packaged-pyxis-check">',
                          '<macro name="packaged-pyxis-check" auto-start="true">')
    document = (root / 'test_data/sar/SARTestWithEZ.dwar').read_text()
    document, count = re.subn(r'<datawarrior macroList>.*?</datawarrior macroList>',
                             lambda _: '<datawarrior macroList>\n' + tasks + '</datawarrior macroList>',
                             document, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError('Fixture lacks its expected macro list')
    launch_file = out / 'launch-document.dwar'
    launch_file.write_text(document, encoding='utf-8')
launcher = Path(args.launcher).resolve()
print(f'Launching packaged application: {launcher}', flush=True)
with (out / 'launcher.log').open('w', encoding='utf-8') as log:
    process = subprocess.Popen([str(launcher), launch_file.as_posix()], stdout=log,
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
# The fixture has no numerical column, so the macro adds row numbers as the value.
# SAR Overview decomposes the R-groups and builds the tree; these lines are printed by the
# tasks themselves, so they prove that the task names resolve inside the packaged program.
if 'Pyxis SAR overview finished: ring scaffolds, value' not in log_text:
    raise RuntimeError('No evidence that the sAROverview macro task ran to its end')
if not re.search(r'Pyxis R-group grid displayed: [1-9][0-9]* x [1-9][0-9]*', log_text):
    raise RuntimeError('No evidence that the createRGroupGrid macro task displayed a grid')
# The example project travels inside the program; this line is printed after it was copied out.
if not re.search(r'Pyxis SAR example opened: [1-9][0-9]{3,} bytes', log_text):
    raise RuntimeError('No evidence that the bundled example project is part of the packaged program')
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
print(f'PASS: packaged SAR Overview built the scaffold tree, R-group grid displayed; Analyse Substituent Pairs exported {len(rows)} pairs; bundled example opened')

if args.document:
    print('PASS: .dwar launch argument loaded document and executed its embedded macro')
