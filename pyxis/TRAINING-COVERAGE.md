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
| Edit scaffold structure | Supported | Scaffold context menu → Edit Scaffold. Adds a user-defined root with full-molecule substructure matches; original nodes stay intact. Nodes are window-local; export retains their identity/provenance, not an editable tree session. |
| Orient molecules relative to scaffold | Supported | Context menu → Re-orient Family. Existing DataWarrior template coordinate generation, limited to family molecule keys; records a macro. Double-click continues to select molecules. |
| Table heatmap | Supported | Existing column-header Set Background Color control. |
| Reorder, format and hide columns | Supported | Existing table/header controls. |
| Lock columns while horizontally scrolling | Missing | No frozen-column API in DETable/DETableView or OpenChemLib JTableWithRowNumbers. The latter owns the scroll-pane row header. A split-table implementation would need synchronized headers, rendering/highlights, selection, row sizes, filters, hidden columns, editing and export. Deferred under the instruction not to rewrite the table. |
| Export visible table to MS Word | Supported | File → Export → Visible Table to MS Word. DOCX with embedded PNG structures; caps of 1,000 rows, 30 columns, 10,000 cells; no heatmap/style reproduction. |
| Numeric subsets, marking and table filtering | Supported | Existing numerical filters, selection and named row lists. Controls differ from the training text. |
| Scaffold subset statistics | Supported | Pick a numeric column plus all/visible/list rows, then Refresh. Count includes missing values; mean/median use finite values. Window controls are not macro recorded. |
| Data grid with values and heatmap blocks | Partly | Existing Structure View grid with column labels and coloring. No new dedicated per-value heatmap-block controls matching the training workflow. |
| R-Group table | Partly | New macro task creates a derived document for one decomposed scaffold, with R1..Rn, molecule, ID and selected values. Edits cannot affect source; it is not an intrinsically read-only, live-linked view. |
| Scaffold-centric single-position substituent pairs | Supported | Existing pair analysis on decomposed R columns, optional values/difference/ratio. Output is a derived document, not a live linked analysis view; no combined molecular difference drawing. |
| Scaffold-hopping pairs | Supported | Same substituents, different scaffold mode. Ordered R-group keys must agree at every position, scaffold keys must differ. Requires consistent decomposition numbering; no positional remapping. |
| 2D scatter, axes, coloring, tooltips, regression | Supported | Existing 2D View and statistical/marker controls. |
| Rotatable 3D scatter with three axes | Supported | Existing 3D View. |
| Dock/move views and use one to filter another | Partly | Existing dockable views and explicit graphical filter/lasso linking. This uses shared row visibility rather than independent SARvision “Subset 2” panels per view. |

## Verification added

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
