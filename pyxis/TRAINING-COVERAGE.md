# Training workflow coverage

This maps only the steps in the supplied SARvision training text. “Supported” means
an equivalent Pyxis/DataWarrior operation exists, **not** matching SARvision results
or identical controls. Existing upstream features below were inspected in source/help;
they were not all manually retested in this change.

| Training step | Status | Pyxis operation / boundary |
| --- | --- | --- |
| Import SDF and SMILES CSV | Supported | Existing file import; SDF stereo prompts may require interaction. |
| Download molecules from CDD Vault | Partly | New explicit-ID import, up to 100 molecules. Local HTTP mocks only; **not yet tested against a live vault**. No assay/batch flattening or whole-vault download. |
| Edit table text and molecule cells | Supported | Existing Table View cell editor. |
| Identify scaffolds with default settings | Supported | Chemistry → Build Scaffold Tree. Murcko reduction, stereo removed, connected one-ring-removal parents; independent method. |
| Hierarchy, counts, double-click family selection | Supported | Tree plus exact/family selection and derived tree table. |
| Edit scaffold structure | Supported | Scaffold context menu → Edit Scaffold. Adds a user-defined root with full-molecule substructure matches; original nodes stay intact. Tree configuration and user nodes persist in the project; Build Scaffold Tree offers the saved settings on reopen and recomputes memberships. Missing statistics columns/lists fall back to count-only/all rows. |
| Orient molecules relative to scaffold | Supported | Context menu → Re-orient Family. Existing DataWarrior template coordinate generation, limited to family molecule keys; records a macro. Double-click continues to select molecules. |
| Table heatmap | Supported | Existing column-header Set Background Color control. |
| Reorder, format and hide columns | Supported | Existing table/header controls. |
| Lock columns while horizontally scrolling | Supported | Column header popup → Lock Columns Up To '<column>' / Unlock Columns. The locked columns are redrawn at the left edge while the table is scrolled right, by the table's own painting; clicks on them select rows, sort, resize and open popups as on the real columns. Saved with the project. Editing a locked cell scrolls the table back to it first. Not shown when the locked columns would take three quarters of the visible width or more. |
| Export visible table to MS Word | Supported | File → Export → Visible Table to MS Word. DOCX with embedded PNG structures; caps of 1,000 rows, 30 columns, 10,000 cells; no heatmap/style reproduction. |
| Numeric subsets, marking and table filtering | Supported | Existing numerical filters, selection and named row lists. Controls differ from the training text. |
| Scaffold subset statistics | Supported | Pick a numeric column plus all/visible/list rows, then Refresh. Count includes missing values; mean/median use finite values. Statistics column/subset persist in the document; window controls are not macro recorded. |
| Data grid with values and heatmap blocks | Supported as an R-group combination grid | Create R-Group Grid: two structure axes within one scaffold, selected numerical value and aggregation, counts, empty combinations, continuous heatmap, frequency/mean order, source selection and PNG/derived-table exports. Snapshot rather than an editable data table; no result parity claimed. |
| R-Group table | Partly | New macro task creates a derived document for one decomposed scaffold, with R1..Rn, molecule, ID and selected values. Selection follows original record identity while the source is open; source scaffold-tree selection filters linked R-group tables. Edits stay independent; data are a snapshot and links do not survive reopening. Not an intrinsically read-only view. |
| Scaffold-centric single-position substituent pairs | Supported | Existing pair analysis on decomposed R columns, optional values/difference/ratio. Output is a derived data snapshot with session-local selection links to both original molecules; no combined molecular difference drawing. |
| Scaffold-hopping pairs | Supported | Same substituents, different scaffold mode. Ordered R-group keys must agree at every position, scaffold keys must differ. Requires consistent decomposition numbering; no positional remapping. Session-local pair selection selects both original molecules. |
| 2D scatter, axes, coloring, tooltips, regression | Supported | Existing 2D View and statistical/marker controls. |
| Rotatable 3D scatter with three axes | Supported | Existing 3D View. |
| Dock/move views and use one to filter another | Supported for graph views | View popup → Show Only Rows Selected In Other Views restricts one 2D/3D view to the live selection without affecting other views. Tables and structure views cannot be restricted per view; they follow the shared filters. Saved with the project: after reopening the view shows the saved rows and follows the selection again. |

## Verification added

- `ExampleGuiCheck`: built application started without a file; the SAR and Help menus offer the example and the quick start, and the quick-start page names the menu entries that exist; the example opens through its macro task code with 288 rows, all with a numerical IC50, and names its source and licence; SAR Overview on it gives 40 decomposition scaffolds (55 in the tree), 7 R columns and 1,677 swaps. Two locked columns and a view following the selection are saved, reopened and compared (the reopened view shows the 5 saved rows and follows a new selection); switching both off removes them from the next save; a project written before these settings opens with no locked columns. The packaged-application check opens the example in every installer build. The quick-start page was viewed once in the program's help window.
- Light look: with locked columns, each locked cell's own background (alternating row shading, user colours) is now painted in the strip; viewed once in the Nebula look and compared by pixel colour.

- `DialogsGuiCheck`: builds the SAR Overview, Summarise Substituent Swaps and Create R-Group Grid dialog panels in the built application as a user gets them, checks their default settings, applies settings back (SAR Overview with a value and a drawn core), and runs each task with exactly what its dialog returned. The panels were viewed as pictures.
- Mac entry point: `DialogsGuiCheck`, `LockedColumnsGuiCheck` and the picture tool were run once with `-DpyxisNativeMain=true` on macOS, which starts `DataWarriorOSX` in its Night Shade look, and passed; CI runs the Linux/Windows entry point only.

- Scale: the scaffold tree logic took 3.9 s for 22,348 rows headless (the two supplied collections repeated 37 times, so only 377 distinct scaffolds; a real collection of that size has more and will be slower). The application's windows were not tested at that size.
- Light look and feel: tree, grid and tables were rendered once in the Nebula look and viewed.

- `SdfOverviewGuiCheck` (run by hand on the two supplied SD-files, which are not in the repository): the ordinary file import loads `Chembl_612285and6.sdf` (259 rows, 3 without structure; one stereo question, answered No) and `Amino Acids.sdf` (345 rows, no question); SAR Overview gives 95 scaffolds and 1,238 swaps for the first, and for the second 65 ring scaffolds with 109 ring-free molecules, or 303 decomposed molecules around a drawn amino-acid core.

- `RGroupGridTest`: duplicate/missing/nonfinite values, hydrogen, all five aggregations, empty combinations, ordering and both axis/cell caps.
- `RGroupGridGuiCheck`: built-app decomposition of five molecules; known cell mean 3/count 3 (two finite values), hydrogen mean 1/count 1, empty cell, exact mouse-click selection; PNG decode; three-cell table saved/reopened with every value and structure type compared; Word ZIP reread; source-close detach.


- `SAROverviewGuiCheck`: built application; one overview run creates Scaffold/R columns, opens the tree with the chosen value selected for statistics and a swap summary document; the tree's Word report has one table row and one readable PNG per scaffold; a second run adds no columns.

- `LockedColumnsGuiCheck`: built application, 256-row project; two columns locked and the table scrolled right: the strip at the left edge is pixel-identical to the real first two columns, a click on it selects that row in the first column, a click on its header sorts, a column resize resizes it, it disappears at the left end and after unlocking. Popup placement is checked only through the offset handed to the table, not by opening a popup.

- `LiveSelectionFilterGuiCheck`: built application, two 2D views; the restricted view shows 3, 2, 0 and 4 rows as the selection changes while the other keeps every row; the view's own marker bookkeeping agrees; switching off restores all rows and removes the helper list.
- `SwapSummaryTest` and `SwapSummaryGuiCheck`: pooling over scaffolds, merged direction, missing and zero values; in the built application every summary row's pair counts and mean change equal a recount made directly from the decomposed table, selecting a row selects exactly its source molecules, and the SAR menu lists the workflow.

- `ScaffoldTreeGuiCheck` source links: sort both documents with duplicate structures, select original records, select both members of a pair, filter/clear linked rows, ignore deleted originals, detach on source close. Renders the linked table in-process.

- `ScaffoldSessionTest`: Unicode/multiline property roundtrip, malformed and truncated refusal.
- `ScaffoldTreeGuiCheck`: saves a real `.dwar`, reopens it through the app loader, compares all five session inputs, rebuilds the tree and compares scaffold identities, counts and user-node provenance; renders reopened task/tree PNGs.

- `ScaffoldHierarchyTest`: subset statistics, missing/nonfinite values and empty subset;
  user-defined full-molecule substructure membership, plus existing scaffold checks.
- `SubstituentPairsTest`: hopping equality, arity, position order, null/empty, absent
  scaffolds, same-core exclusions and output caps, plus existing single-position pairs.
- `DocxTableWriterTest`: reopens the ZIP, parses every XML/relationship part, counts
  rows/media references, decodes the PNG, tests size caps/overwrite refusal. On macOS
  it also converts to text with `textutil` and checks preserved Unicode.
- `CddVaultClientTest`: loopback HTTP server only; documented molecule path and token
  header, molfile/SMILES/stereo, ID mapping, malformed/missing structures, auth/not-found
  errors, redirect refusal and input checks. No live vault evidence.
- `ScaffoldTreeGuiCheck`: built application and real scaffold context menu/editor;
  family/exact selection, user node/export, coordinate task and preserved chemistry;
  hopping dialog/task, derived R-group copy/source independence; filtered/hidden-column
  Word export with image counts; CDD dialog properties/offline table mapping; refreshed
  all/visible/list statistics. Renders tree and task panels in-process to PNG.

Upstream evidence: `src/html/help/views.html` (table colors/order/hiding, Structure View),
`src/html/help/basics.html` (filters/row lists/explicit view filtering), existing
`DETaskSetStatisticalViewOptions`, `DETable`, `DETableView`, `DEInteractiveSARDialog`,
and `DETaskDecomposeRGroups`. No Altoris installer was installed, run or inspected.
