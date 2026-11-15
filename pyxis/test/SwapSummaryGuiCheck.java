/*
 * Pyxis SAR Explorer GUI check for "Summarise Substituent Swaps" and the SAR menu.
 * Builds a small table inside the real application, decomposes it through the real
 * R-group task, runs the swap summary and compares every output row with a count made
 * here directly from the table. Also checks that selecting a summary row selects the
 * molecules behind it, and that the SAR menu offers the whole workflow.
 *
 * Usage: java -cp datawarrior_all.jar:<classes> SwapSummaryGuiCheck macro.dwam
 * GNU General Public License v3 or later, WITHOUT ANY WARRANTY.
 */

import com.actelion.research.chem.Canonizer;
import com.actelion.research.chem.SmilesParser;
import com.actelion.research.chem.StereoMolecule;
import com.actelion.research.datawarrior.DEFrame;
import com.actelion.research.datawarrior.DataWarrior;
import com.actelion.research.datawarrior.DataWarriorLinux;
import com.actelion.research.datawarrior.pyxis.DETaskSummariseSubstituentSwaps;
import com.actelion.research.datawarrior.task.chem.DETaskDecomposeRGroups;
import com.actelion.research.table.model.CompoundTableEvent;
import com.actelion.research.table.model.CompoundTableModel;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

public class SwapSummaryGuiCheck {
	public static void main(String[] args) throws Exception {
		Thread.setDefaultUncaughtExceptionHandler((t, e) -> { e.printStackTrace(); System.exit(1); });
		DataWarriorLinux.main(new String[] { args[0] });
		for (int i=0; i<120 && (DataWarrior.getApplication() == null || DataWarrior.getApplication().getFrameList().isEmpty()); i++)
			Thread.sleep(500);
		Thread.sleep(1500);

		// benzenes and pyridines carrying H, F or Cl: the same exchanges occur on two scaffolds
		final String[] smiles = { "c1ccccc1", "Fc1ccccc1", "Clc1ccccc1", "c1ccncc1", "Fc1cccnc1", "Clc1cccnc1", "Fc1ccccc1" };
		final String[] ki = { "1", "2", "4", "10", "30", "", "6" };
		final DEFrame[] frameRef = new DEFrame[1];
		SwingUtilities.invokeAndWait(() -> {
			try {
				frameRef[0] = DataWarrior.getApplication().getEmptyFrame("Swap fixture");
				CompoundTableModel t = frameRef[0].getTableModel();
				t.initializeTable(smiles.length, 2);
				t.setColumnName("Structure", 0);
				t.setColumnName("Ki", 1);
				t.setColumnProperty(0, CompoundTableModel.cColumnPropertySpecialType, CompoundTableModel.cColumnTypeIDCode);
				StereoMolecule mol = new StereoMolecule();
				for (int row=0; row<smiles.length; row++) {
					new SmilesParser().parse(mol, smiles[row]);
					t.setTotalValueAt(new Canonizer(mol).getIDCode(), row, 0);
					t.setTotalValueAt(ki[row], row, 1);
					}
				t.finalizeTable(CompoundTableEvent.cSpecifierDefaultFiltersAndViews, null);
				}
			catch (Exception e) { throw new RuntimeException(e); }
			});
		final DEFrame source = frameRef[0];
		final CompoundTableModel model = source.getTableModel();

		Properties decompose = new Properties();
		decompose.setProperty("structureColumn", "Structure");
		decompose.setProperty("scaffoldMode", "murcko");
		decompose.setProperty("useExistingColumns", "false");
		new DETaskDecomposeRGroups(source).executeAsSubtask(decompose, null);
		settle();

		// independent expectation, straight from the decomposed table
		int scaffoldColumn = model.findColumn("Scaffold");
		List<Integer> rColumns = new ArrayList<>();
		for (int c=0; c<model.getTotalColumnCount(); c++)
			if (model.getColumnTitleNoAlias(c).matches("R[0-9]+"))
				rColumns.add(c);
		if (scaffoldColumn == -1 || rColumns.isEmpty())
			fail("decomposition did not create Scaffold and R columns");
		int kiColumn = model.findColumn("Ki");

		TreeMap<String,int[]> expectedPairs = new TreeMap<>();		// key -> { pairs, valued pairs }
		TreeMap<String,Double> expectedChangeSum = new TreeMap<>();
		TreeMap<String,TreeSet<Integer>> expectedRows = new TreeMap<>();
		for (int a=0; a<model.getTotalRowCount(); a++) {
			for (int b=a+1; b<model.getTotalRowCount(); b++) {
				if (model.getTotalValueAt(a, scaffoldColumn).isEmpty()
				 || !model.getTotalValueAt(a, scaffoldColumn).equals(model.getTotalValueAt(b, scaffoldColumn)))
					continue;
				int differing = -1, count = 0;
				for (int c:rColumns)
					if (!model.getTotalValueAt(a, c).equals(model.getTotalValueAt(b, c))) { differing = c; count++; }
				if (count != 1)
					continue;
				String x = model.getTotalValueAt(a, differing), y = model.getTotalValueAt(b, differing);
				int from = a, to = b;
				if (x.compareTo(y) > 0) { String s = x; x = y; y = s; from = b; to = a; }
				String key = x+"\t"+y;
				int[] n = expectedPairs.computeIfAbsent(key, k -> new int[2]);
				n[0]++;
				expectedRows.computeIfAbsent(key, k -> new TreeSet<>()).add(a);
				expectedRows.get(key).add(b);
				double va = model.getTotalOriginalDoubleAt(from, kiColumn), vb = model.getTotalOriginalDoubleAt(to, kiColumn);
				if (!Double.isNaN(va) && !Double.isNaN(vb)) {
					n[1]++;
					expectedChangeSum.merge(key, vb - va, Double::sum);
					}
				}
			}
		if (expectedPairs.isEmpty())
			fail("fixture produced no single-position pairs");

		Properties summary = new Properties();
		summary.setProperty("scaffoldColumn", "Scaffold");
		summary.setProperty("valueColumn", "Ki");
		summary.setProperty("minPairs", "1");
		DETaskSummariseSubstituentSwaps task = new DETaskSummariseSubstituentSwaps(source);
		task.executeAsSubtask(summary, null);
		settle();

		DEFrame resultFrame = task.getNewFrame();
		if (resultFrame == null)
			fail("the task did not open a summary table");
		final CompoundTableModel result = resultFrame.getTableModel();
		if (result.getTotalRowCount() != expectedPairs.size())
			fail("summary has "+result.getTotalRowCount()+" exchanges, table recount gives "+expectedPairs.size());
		if (!result.isColumnTypeStructure(result.findColumn("From")) || !result.isColumnTypeStructure(result.findColumn("To")))
			fail("From/To are not structure columns");

		int pooled = 0;
		for (int row=0; row<result.getTotalRowCount(); row++) {
			String key = result.getTotalValueAt(row, result.findColumn("From"))+"\t"+result.getTotalValueAt(row, result.findColumn("To"));
			int[] n = expectedPairs.get(key);
			if (n == null)
				fail("summary lists an exchange the table recount does not have");
			if (Integer.parseInt(result.getTotalValueAt(row, result.findColumn("Pairs"))) != n[0]
			 || Integer.parseInt(result.getTotalValueAt(row, result.findColumn("Pairs With Values"))) != n[1])
				fail("pair counts differ for an exchange: summary "+result.getTotalValueAt(row, result.findColumn("Pairs"))+", recount "+n[0]);
			String mean = result.getTotalValueAt(row, result.findColumn("Ki Mean Change"));
			if (n[1] == 0 ? !mean.isEmpty() : Math.abs(Double.parseDouble(mean) - expectedChangeSum.get(key) / n[1]) > 1e-4)
				fail("mean change differs for an exchange: summary '"+mean+"'");
			if (Integer.parseInt(result.getTotalValueAt(row, result.findColumn("Scaffolds"))) > 1)
				pooled++;
			if (row > 0 && Integer.parseInt(result.getTotalValueAt(row, result.findColumn("Pairs With Values")))
						 > Integer.parseInt(result.getTotalValueAt(row-1, result.findColumn("Pairs With Values"))))
				fail("exchanges are not ordered by supporting pairs");
			}
		if (pooled == 0)
			fail("no exchange was pooled over more than one scaffold, although benzene and pyridine share them");

		// selecting the first exchange must select exactly the molecules behind it
		final String firstKey = result.getTotalValueAt(0, result.findColumn("From"))+"\t"+result.getTotalValueAt(0, result.findColumn("To"));
		SwingUtilities.invokeAndWait(() -> {
			for (int row=0; row<result.getTotalRowCount(); row++)
				result.getTotalRecord(row).setSelection(row == 0);
			result.invalidateSelectionModel();
			});
		settle();
		TreeSet<Integer> selected = new TreeSet<>();
		for (int row=0; row<model.getTotalRowCount(); row++)
			if (model.getTotalRecord(row).isSelected())
				selected.add(row);
		if (!selected.equals(expectedRows.get(firstKey)))
			fail("selecting an exchange selected source rows "+selected+", expected "+expectedRows.get(firstKey));

		// the SAR menu is the front door for the workflow
		final List<String> items = new ArrayList<>();
		SwingUtilities.invokeAndWait(() -> {
			JMenuBar bar = source.getJMenuBar();
			for (int i=0; bar != null && i<bar.getMenuCount(); i++) {
				JMenu menu = bar.getMenu(i);
				if (menu != null && "SAR".equals(menu.getText()))
					for (int j=0; j<menu.getItemCount(); j++) {
						JMenuItem item = menu.getItem(j);
						if (item != null)
							items.add(item.getText());
						}
				}
			});
		for (String wanted:new String[] { "Build Scaffold Tree...", "Decompose R-Groups...", "Create R-Group Table...", "Create R-Group Grid...",
				"Analyse Substituent Pairs...", "Summarise Substituent Swaps...", "Analyse Activity Cliffs...", "Export Visible Table to MS Word..." })
			if (!items.contains(wanted))
				fail("SAR menu lacks '"+wanted+"'; it has "+items);

		// every macro task code the README documents must resolve to its task
		String[][] codes = {
				{ "sAROverview", "DETaskSAROverview" }, { "buildScaffoldTree", "DETaskBuildScaffoldTree" },
				{ "analyseSubstituentPairs", "DETaskAnalyseSubstituentPairs" }, { "summariseSubstituentSwaps", "DETaskSummariseSubstituentSwaps" },
				{ "createRGroupTable", "DETaskCreateRGroupTable" }, { "createRGroupGrid", "DETaskCreateRGroupGrid" },
				{ "exportVisibleTableToMSWord", "DETaskExportWord" }, { "importCDDVaultMolecules", "DETaskImportCddVault" },
				{ "reorientScaffoldFamily", "DETaskOrientScaffold" } };
		for (String[] code:codes) {
			Object created = DataWarrior.getApplication().getTaskFactory().createTaskFromCode(source, code[0]);
			if (created == null || !created.getClass().getSimpleName().equals(code[1]))
				fail("macro task code '"+code[0]+"' gives "+(created == null ? "nothing" : created.getClass().getSimpleName())+", expected "+code[1]);
			}

		System.out.println("Swap summary GUI: "+model.getTotalRowCount()+" molecules -> "+result.getTotalRowCount()+" exchanges, "
				+pooled+" pooled over 2 scaffolds; counts and mean change equal an independent recount; first exchange selects source rows "
				+selected+"; SAR menu has "+items.size()+" entries; "+codes.length+" macro task codes resolve");
		System.out.println("SwapSummaryGuiCheck passed");
		System.exit(0);
		}

	private static void settle() throws Exception {
		for (int i=0; i<4; i++) {
			Thread.sleep(250);
			SwingUtilities.invokeAndWait(() -> {});
			}
		}

	private static void fail(String message) {
		System.out.println("FAILED: "+message);
		System.exit(1);
		}
	}
