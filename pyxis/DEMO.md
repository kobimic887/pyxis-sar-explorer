# Five-minute demo

A walk through the SAR menu with the two SD-files that came with the SARvision training
text. The numbers below were measured with this program on those files; they describe
those files, not any other program's results.

Before you start: install the program, start it once, and have `Chembl_612285and6.sdf` and
`Amino Acids.sdf` at hand. The installers are not code-signed, so Windows and macOS ask
for confirmation on the first start (see *Installers* in the README).

## 1. Open the potency data (30 s)

**File → Open**, choose `Chembl_612285and6.sdf`. The program asks once ("Do you want the program to interpret those molecules to be racemic?") whether molecules
with a single stereo centre and no stereo information should be read as racemic; answer
**No** to keep the file as it is. You get 259 rows, three of them without a structure.

## 2. One click to the overview (1 min)

**SAR → SAR Overview...**, value `612285_Ki(nM)`, OK. In a few seconds:

- the table gains a **Scaffold** column and **R1–R3**;
- the **scaffold tree** opens: 95 scaffolds from 256 molecules. Each scaffold shows its
  molecule count, mean and median Ki, and a colour bar from blue (low Ki) to red (high);
- a second document, **Substituent Swaps**, lists 1,238 exchanges.

Things to show in the tree:

- double click a scaffold: its molecules are selected in the table;
- tick *Only molecules with exactly this scaffold* and double click again;
- right click a scaffold → **Edit Scaffold...** to add your own, or **Re-orient Family**;
- **Report To MS Word** writes the tree with pictures and statistics.

## 3. Which exchange matters (1 min)

Bring the **Substituent Swaps** document to the front. Each row is one exchange of a
substituent by another, pooled over all scaffolds: number of pairs, mean and median
change of Ki, fold change, how often Ki went up or down. Sort by *Pairs With Values* or
by *Mean Fold Change*. Select a row: the molecules behind it are selected in the source
table.

## 4. The grid (1 min)

Back in the source table: **SAR → Create R-Group Grid...**, rows `R1`, columns `R2`,
value `612285_Ki(nM)`, combine by median. The grid shows every R1/R2 combination of the
most common scaffold with its median Ki and molecule count; combinations nobody made say
*Empty*. Click a cell to select its molecules. In this file the acid and its tert-butyl
ester stand out against each other.

## 5. Working in the table (1 min)

- Right click the header of the second column → **Lock Columns Up To '...'**, then scroll
  right: the first columns stay in view while you look at the R-groups.
- **SAR → Analyse Substituent Pairs...** lists the individual pairs behind the swaps.
- Add two 2D views (right click a view's title → **New 2D-View**), then right click one of them → **Show Only
  Rows Selected In Other Views**: a lasso in one plot decides what the other shows.
- **SAR → Export Visible Table to MS Word...** for a table with structure pictures.

## 6. A collection without a common ring (1 min)

**File → Open** `Amino Acids.sdf` (345 rows, no question asked). Ring scaffolds leave 109
of these molecules out, because they have no ring. So: **SAR → SAR Overview...**, tick
*Use my own core*, **Draw Core...**, draw the amino acid backbone N–C–C(=O)–O, OK.
The R-groups are now defined around that core for the 303 molecules that contain it,
and the core leads the scaffold tree as a user-defined scaffold with 303 molecules.

## What not to claim

- This is a modified version of DataWarrior with Pyxis additions; it is not SARvision
  and has not been compared with SARvision's results.
- CDD Vault import exists but has only been tested against a simulated server.
- Locked columns, the live view filter and links between derived tables and their
  source last for the session; they are not stored in the project file.
