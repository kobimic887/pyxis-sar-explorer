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
| Lock columns while horizontally scrolling | Missing | Re-examined the shared-model/selection JTable row-header approach. Still unimplemented: DETableView routes sorting/editing/popups only for its own table/header (lines 283–348); DETable visibility assumes every absent displayable column has a private hidden-column entry (lines 1077–1151); custom decorations/header groups paint only its own viewport (lines 649–843). A companion JTable needs changes to those paths, in addition to preserving the existing row-number header. This inspection does not prove a rewrite is necessary; no frozen-column GUI behaviour is verified. |
| Export visible table to MS Word | Supported | File → Export → Visible Table to MS Word. DOCX with embedded PNG structures; caps of 1,000 rows, 30 columns, 10,000 cells; no heatmap/style reproduction. |
| Numeric subsets, marking and table filtering | Supported | Existing numerical filters, selection and named row lists. Controls differ from the training text. |
| Scaffold subset statistics | Supported | Pick a numeric column plus all/visible/list rows, then Refresh. Count includes missing values; mean/median use finite values. Statistics column/subset persist in the document; window controls are not macro recorded. |
| Data grid with values and heatmap blocks | Partly | Existing Structure View grid with column labels and coloring. No new dedicated per-value heatmap-block controls matching the training workflow. |
| R-Group table | Partly | New macro task creates a derived document for one decomposed scaffold, with R1..Rn, molecule, ID and selected values. Selection follows original record identity while the source is open; source scaffold-tree selection filters linked R-group tables. Edits stay independent; data are a snapshot and links do not survive reopening. Not an intrinsically read-only view. |
| Scaffold-centric single-position substituent pairs | Supported | Existing pair analysis on decomposed R columns, optional values/difference/ratio. Output is a derived data snapshot with session-local selection links to both original molecules; no combined molecular difference drawing. |
| Scaffold-hopping pairs | Supported | Same substituents, different scaffold mode. Ordered R-group keys must agree at every position, scaffold keys must differ. Requires consistent decomposition numbering; no positional remapping. Session-local pair selection selects both original molecules. |
| 2D scatter, axes, coloring, tooltips, regression | Supported | Existing 2D View and statistical/marker controls. |
| Rotatable 3D scatter with three axes | Supported | Existing 3D View. |
| Dock/move views and use one to filter another | Partly | Existing dockable views and explicit graphical filter/lasso linking. This uses shared row visibility rather than independent SARvision “Subset 2” panels per view. |

## Verification added

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
