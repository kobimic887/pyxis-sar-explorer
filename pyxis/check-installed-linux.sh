#!/bin/bash
set -euo pipefail
mkdir -p packaged-check/installed
sudo apt-get install -y ./installers/*.deb 2>&1 | tee packaged-check/installed/install.log
trap 'sudo dpkg --remove pyxis-sar-explorer 2>&1 | tee packaged-check/installed/uninstall.log' EXIT
dpkg-query --status pyxis-sar-explorer | tee packaged-check/installed/package-status.txt
dpkg-query -L pyxis-sar-explorer > packaged-check/installed/package-files.txt
launcher=$(python3 - <<'PY'
from pathlib import Path
paths=Path('packaged-check/installed/package-files.txt').read_text().splitlines()
launchers=[p for p in paths if p.endswith('/bin/Pyxis SAR Explorer')]
assert len(launchers)==1, launchers
print(launchers[0])
for ext in ['dwar','dwam','dwat']:
 assert any(p.endswith('.desktop') and f'application/x-pyxis-{ext}' in Path(p).read_text() for p in paths), ext
PY
)
echo "PASS: DEB installed launcher: $launcher; desktop MIME associations registered"
xvfb-run -a python3 pyxis/check-packaged-app.py "$launcher" --evidence-dir packaged-check/installed/macro
xvfb-run -a python3 pyxis/check-packaged-app.py "$launcher" --document --evidence-dir packaged-check/installed/document
