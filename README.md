## Pyxis SAR Explorer

*Pyxis SAR Explorer* is a modified version of *DataWarrior* published by Pyxis Discovery
under the same license (GNU GPL v3 or later). Everything DataWarrior does is still here;
the original README follows below. It is an independent program and makes no claim to
reproduce the results of any other SAR software.

### What Pyxis added
The analysis tasks are in the **Chemistry** menu and can be recorded in macros.

* **Build Scaffold Tree...** (`buildScaffoldTree`) opens a window with a hierarchical tree of
  the scaffolds of all molecules. Small scaffolds shared by many molecules are at the top,
  larger and rarer ones below them, each with its molecule count. Double click a scaffold
  to select its molecules in the table. *Tree As New Table* writes the tree into a new
  document for saving and export.
  Method: every molecule is reduced to its Murcko scaffold without stereo information; the
  parents of a scaffold are obtained by removing one ring at a time, as long as the rest
  stays connected; a scaffold is shown below the parent that covers the most molecules.
  Ring-free molecules have no scaffold and are reported separately.
  Choose a numerical column and all rows, visible rows or a row list, then press Refresh
  for a subset snapshot: family count, finite value count, mean and median. Missing and
  infinite values do not enter mean/median. Tree As New Table includes the snapshot;
  refresh again after filters/data change. Right-click a scaffold to edit a copy in the
  structure editor; the added user-defined root matches full molecules by substructure
  (not Murcko ancestry) and is marked in the tree/export. These nodes live in this window.
  Re-orient Family uses DataWarrior's Generate 2D-Atom-Coordinates template mechanism,
  limited to the family molecule keys; it changes coordinates, not chemistry. This action
  is macro-recordable, including the scaffold coordinates and family keys. These window controls are not macro recorded.
* **Analyse Substituent Pairs...** (`analyseSubstituentPairs`) lists all pairs of molecules
  that share a scaffold and differ at exactly one R-group position, with both values of a
  chosen numerical column, their difference and their ratio. Run *Decompose R-Groups...*
  first; it creates the Scaffold and R1, R2, ... columns this analysis reads.
  The second mode, *Same substituents, different scaffold*, compares the stored canonical
  structure keys in the ordered R1..Rn columns: every position must match, and the
  scaffold keys must differ. Empty and null mean the same absent group; explicit
  hydrogen is a distinct key. It does not infer equivalent attachment positions or
  align independently numbered decompositions. Use a consistently numbered decomposition
  with the same R positions; a missing position is not inferred from an empty cell.
  Values compare original numerical data, not log-display coordinates. Both modes
  consider all rows, including filtered rows, and cap output at 200,000 pairs.

* **Create R-Group Table...** (`createRGroupTable`) copies all rows with one chosen
  decomposed scaffold into a new document: Scaffold, numbered R-group columns, molecule,
  chosen ID (or source row number), and selected value columns. Run Decompose R-Groups
  first. This is a **derived copy**, named in the window title: edits there do not
  modify the source and it does not refresh from the source. This reuses the existing
  decomposition rather than computing a second, potentially inconsistent one.

* **File → Export → Visible Table to MS Word...** (`exportVisibleTableToMSWord`)
  writes a new `.docx` containing visible rows and displayed columns in their current
  order, with structures rendered as PNG images. This is a plain landscape table,
  not a copy of heatmaps or interactive views. Export is capped at 1,000 data rows,
  30 columns and 10,000 cells; filter/hide columns for larger datasets. It uses Java's
  ZIP writer and Office Open XML, with no new dependency. Existing files are refused.

### What Pyxis changed
* Window titles and the About text name the program Pyxis SAR Explorer.
* The program does not contact the DataWarrior update server and does not update itself
  from it, because that would replace it with an unmodified DataWarrior.

### Installers
`.github/workflows/pyxis-build.yml` builds the application and makes installers for
Windows (.msi), macOS (.dmg) and Linux (.deb) with a bundled Java runtime. A tag named
`pyxis-v1.2.3` publishes them as a GitHub release. They are not code-signed. CI also launches the bundled application image on Windows
and Linux with an absolute-path macro. It requires a displayed, nonempty scaffold tree,
a nonempty exported substituent-pair table, and a clean exit within 150 seconds.
The installers wrap that same tested application image. The `packaged-evidence-*`
artifacts retain the macro, launcher log, CSV and runtime architecture. macOS skips
this macro check because its entry point does not handle command-line file arguments.

### Checking a change
```
pyxis/run-tests.sh                 # scaffold tree and pair logic; any JDK 21+
./buildDataWarrior                 # needs a JDK 21+ that includes JavaFX
pyxis/run-gui-check.sh test_data/sar/simpleSAR.dwar tree.png
```
The source of the additions is in `src/com/actelion/research/datawarrior/pyxis`.

---

## DataWarrior
*DataWarrior* is a program for interactive data analysis and visualization. While it is
widely used by data analysts in general, it is particular useful for cheminformaticians,
because chemical structures and reactions are native datatypes and because of its rich
cheminformatics functionality.

*DataWarrior* runs on all major platforms. Pre-built installers exist for Linux, Macintosh and Windows.
Software installers, documentation, sample data, and a support forum are available on
https://openmolecules.org/datawarrior.

*DataWarrior* is built on the open-source projects *OpenChemLib* and *FXMolViewer*. 

### Dependencies
Apart from a working JDK21 or higher that includes JavaFX, *DataWarrior* needs various free-to-use/open-source
dependencies. All required dependency files are provided as part of this project in the ./lib folder.
The most important ones are:
* OpenChemLib: Cheminformatics base functionality to handle molecules and reactions
* FXMolViewer: 3D-molecule & protein visualization, editing, interaction using JavaFX
  (includes Sunflow ray-tracer and MMTF to download and parse binary structure files from the PDB-database)
* Batik: Support for SVG vector graphics
* Database connectors for: MySQL, PostgreSQL, Oracle, Microsoft SQL-Server
* Opsin: IUPAC name conversion to chemical structures
* Substance Look&Feel: professionally designed user interface skin
* Java Expression Parser: for calculating new column data using custom equations

### How to download the project
git clone https://github.com/thsa/datawarrior.git

### How to build the project
On Linux or Macintosh just run the 'buildDataWarrior' shell script.

### How to run the project
After building the project just run the 'runDataWarrior' shell script.

### Platform Integration
Ideally, *DataWarrior* should be installed in a platform specific way that registers its file
extentions and causes proper file icon display. Installers for Linux, Macintosh, and Windows,
which include proper platform integration, can be downloaded from
https://openmolecules.org/datawarrior/download.html.

This platform integration for Windows is not (yet) part of this project. Explanations and
scripts of how to build installers for Linux and Macintosh with proper platform integration
can be found in the *linux* and *macosx* directories.

If you intend to run *DataWarrior* from self compiled source code and if you cannot or don't
want to do the platform integration yourself, then you may still install *DataWarrior* with the
official installer and then replace the original datawarrior_all.jar file with a freshly
built one.

### How to contribute
Contact the author under the e-mail shown on https://openmolecules.org/about.html


### License
*DataWarrior*. Copyright (C) 2023 Thomas Sander & Idorsia Pharmaceuticals Ltd.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.


### Supported by

![YourKit-logo](https://www.yourkit.com/images/yklogo.png)

YourKit supports open source projects with innovative and intelligent tools 
for monitoring and profiling Java and .NET applications.
YourKit is the creator of [YourKit Java Profiler](https://www.yourkit.com/java/profiler/),
[YourKit .NET Profiler](https://www.yourkit.com/dotnet-profiler/),
and [YourKit YouMonitor](https://www.yourkit.com/youmonitor/).
