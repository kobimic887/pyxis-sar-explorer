Pyxis SAR Explorer is a desktop program for structure–activity analysis: scaffold tree, R-group decomposition and grid, substituent pairs and swaps, on top of everything DataWarrior does. It is a modified version of [DataWarrior](https://openmolecules.org/datawarrior/), licensed under the GNU GPL v3; the complete source is this repository at this tag.

## Download

| System | File |
| --- | --- |
| Windows (64-bit) | `Pyxis-SAR-Explorer-windows-x64.msi` |
| macOS, Apple silicon (M-series) | `Pyxis-SAR-Explorer-macos-arm64.dmg` |
| macOS, Intel | `Pyxis-SAR-Explorer-macos-x64.dmg` |
| Linux (Debian/Ubuntu) | `Pyxis-SAR-Explorer-linux-x64.deb` |

Java is included; nothing else needs to be installed.

## First start

The installers are not code-signed, so the system asks for confirmation once.

- **Windows:** if *Windows protected your PC* appears, choose **More info**, then **Run anyway**.
- **macOS:** drag the app to Applications and open it. When macOS refuses, open **System Settings → Privacy & Security**, scroll down and choose **Open Anyway**. On macOS 14 and earlier, right-click the app and choose **Open** instead.
- **Linux:** `sudo apt install ./Pyxis-SAR-Explorer-linux-x64.deb`

## Try it

**SAR → Open Example Project** opens 288 COX-2 inhibitors from ChEMBL. Then **SAR → SAR Overview...** with the value `COX-2 IC50 (nM)`. **SAR → SAR Quick Start...** explains the rest.
