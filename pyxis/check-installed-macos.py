#!/usr/bin/env python3
"""Mount the real DMG, copy its app, and query its visible main window."""
import os
from pathlib import Path
import plistlib
import shutil
import subprocess
import time

out = Path('packaged-check/installed').resolve()
out.mkdir(parents=True, exist_ok=True)
mount = out / 'mounted'
mount.mkdir(exist_ok=True)
dmg = next(Path('installers').glob('*.dmg')).resolve()
env = dict(os.environ, PAGER='cat')
# hdiutil's license prompt reads stdin. Ignore yes's SIGPIPE once attach completes.
answer = subprocess.Popen(['yes'], stdout=subprocess.PIPE)
try:
    subprocess.run(['hdiutil', 'attach', str(dmg), '-nobrowse', '-mountpoint', str(mount)],
                   stdin=answer.stdout, env=env, check=True)
finally:
    answer.stdout.close()
    answer.terminate()
    answer.wait()
app = out / 'Pyxis SAR Explorer.app'
try:
    subprocess.run(['ditto', str(next(mount.glob('*.app'))), str(app)], check=True)
finally:
    subprocess.run(['hdiutil', 'detach', str(mount)], check=True)
info = plistlib.loads((app / 'Contents/Info.plist').read_bytes())
associations = info.get('CFBundleDocumentTypes', [])
assert associations, 'DMG app is missing document associations'
assert info['CFBundleIdentifier'] == 'com.pyxisdiscovery.sarexplorer', info['CFBundleIdentifier']
extensions = {ext for item in info.get('UTExportedTypeDeclarations', [])
              for ext in item.get('UTTypeTagSpecification', {}).get('public.filename-extension', [])}
assert {'dwar', 'dwam', 'dwat'} <= extensions, extensions
(out / 'document-associations.txt').write_text(str(associations))
launcher = app / 'Contents/MacOS' / info['CFBundleExecutable']
with (out / 'launcher.log').open('w') as log:
    process = subprocess.Popen([str(launcher)], stdout=log, stderr=subprocess.STDOUT)
    try:
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise RuntimeError('Copied DMG app exited before showing its main window')
            script = f'''tell application "System Events"
set targetProcess to first process whose unix id is {process.pid}
return name of every window of targetProcess
end tell'''
            result = subprocess.run(['osascript', '-e', script], capture_output=True, text=True)
            (out / 'window-query.txt').write_text(result.stdout + result.stderr)
            # Require the empty document's exact main title, not the About splash title.
            titles = [title.strip() for title in result.stdout.split(',')]
            if result.returncode == 0 and 'Pyxis SAR Explorer' in titles:
                print('PASS: copied DMG application main window: ' + result.stdout.strip())
                break
            time.sleep(2)
        else:
            raise RuntimeError('No Pyxis main window observed: ' + result.stdout + result.stderr)
    finally:
        process.terminate()
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
print('NOT PROVED: macOS Finder open-file delivery and macro execution; startup/window title checked only')
