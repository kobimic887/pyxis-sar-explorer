/*
 * Pyxis SAR Explorer GUI check for "SAR Overview". Builds a small table inside the real
 * application, runs the overview on it and checks that all three steps happened: R-group
 * columns were created, the scaffold tree window opened with the value column chosen for
 * its statistics, and a swap summary document appeared.
 *
 * Usage: java -cp datawarrior_all.jar:<classes> SAROverviewGuiCheck macro.dwam
 * GNU General Public License v3 or later, WITHOUT ANY WARRANTY.
 */

import com.actelion.research.chem.Canonizer;
import com.actelion.research.chem.SmilesParser;
import com.actelion.research.chem.StereoMolecule;
import com.actelion.research.datawarrior.DEFrame;
import com.actelion.research.datawarrior.DataWarrior;
import com.actelion.research.datawarrior.DataWarriorLinux;
import com.actelion.research.datawarrior.pyxis.DETaskSAROverview;
import com.actelion.research.datawarrior.pyxis.ScaffoldTreeDialog;
import com.actelion.research.table.model.CompoundTableEvent;
import com.actelion.research.table.model.CompoundTableModel;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class SAROverviewGuiCheck {
	public static void main(String[] args) throws Exception {
		Thread.setDefaultUncaughtExceptionHandler((t, e) -> { e.printStackTrace(); System.exit(1); });
		DataWarriorLinux.main(new String[] { args[0] });
		for (int i=0; i<120 && (DataWarrior.getApplication() == null || DataWarrior.getApplication().getFrameList().isEmpty()); i++)
			Thread.sleep(500);
		Thread.sleep(1500);

		final String[] smiles = { "c1ccccc1", "Fc1ccccc1", "Clc1ccccc1", "c1ccncc1", "Fc1cccnc1", "Clc1cccnc1", "CCO" };
		final String[] ki = { "1", "2", "4", "10", "30", "", "5" };
		final DEFrame[] frameRef = new DEFrame[1];
		SwingUtilities.invokeAndWait(() -> {
			try {
				frameRef[0] = DataWarrior.getApplication().getEmptyFrame("Overview fixture");
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
		int framesBefore = DataWarrior.getApplication().getFrameList().size();

		Properties overview = new Properties();
		overview.setProperty("structureColumn", "Structure");
		overview.setProperty("valueColumn", "Ki");
		new DETaskSAROverview(source).executeAsSubtask(overview, null);

		ScaffoldTreeDialog tree = null;
		for (int i=0; i<60 && tree == null; i++) {
			Thread.sleep(500);
			for (Window window:Window.getWindows())
				if (window instanceof ScaffoldTreeDialog && window.isShowing() && window.getOwner() == source)
					tree = (ScaffoldTreeDialog)window;
			}
		settle();

		if (model.findColumn("Scaffold") == -1 || model.findColumn("R1") == -1)
			fail("step 1: no Scaffold and R1 columns after the overview");
		if (tree == null)
			fail("step 2: the scaffold tree window did not open");

		final ScaffoldTreeDialog treeDialog = tree;
		final boolean[] statisticsChosen = new boolean[1];
		SwingUtilities.invokeAndWait(() -> {
			List<Component> all = new ArrayList<>();
			collect(treeDialog, all);
			for (Component c:all)
				if (c instanceof JComboBox && "Ki".equals(((JComboBox<?>)c).getSelectedItem()))
					statisticsChosen[0] = true;
			});
		if (!statisticsChosen[0])
			fail("step 2: the tree does not show statistics of the chosen value column");

		// the tree's Word report: one table row and one picture per scaffold, plus the header row
		final java.io.File report = java.io.File.createTempFile("pyxis-scaffold-report", ".docx");
		report.delete();
		final int[] reported = new int[1];
		SwingUtilities.invokeAndWait(() -> {
			try { reported[0] = treeDialog.writeReport(report); }
			catch (Exception e) { throw new RuntimeException(e); }
			});
		int pictures = 0, tableRows = 0;
		String documentXml = null;
		try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(report)) {
			for (java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
				java.util.zip.ZipEntry entry = entries.nextElement();
				if (entry.getName().startsWith("word/media/") && entry.getName().endsWith(".png")) {
					if (javax.imageio.ImageIO.read(zip.getInputStream(entry)) == null)
						fail("report: a picture is not a readable PNG");
					pictures++;
					}
				if (entry.getName().equals("word/document.xml"))
					documentXml = new String(zip.getInputStream(entry).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
				}
			}
		if (documentXml == null)
			fail("report: no word/document.xml");
		for (int at = documentXml.indexOf("<w:tr>"); at != -1; at = documentXml.indexOf("<w:tr>", at+1))
			tableRows++;
		if (reported[0] < 2 || pictures != reported[0] || tableRows != reported[0]+1)
			fail("report: "+reported[0]+" scaffolds, "+pictures+" pictures, "+tableRows+" table rows");
		if (!documentXml.contains("Median Ki") || !documentXml.contains("ring removal"))
			fail("report: statistics header or scaffold origin missing");
		report.delete();

		DEFrame swaps = null;
		for (DEFrame frame:DataWarrior.getApplication().getFrameList())
			if (frame != source && frame.getTableModel().findColumn("Ki Mean Change") != -1)
				swaps = frame;
		if (swaps == null || swaps.getTableModel().getTotalRowCount() == 0)
			fail("step 3: no swap summary document (documents before "+framesBefore+", now "+DataWarrior.getApplication().getFrameList().size()+")");

		// running it again must reuse the decomposition instead of adding a second one
		int columnsBefore = model.getTotalColumnCount();
		new DETaskSAROverview(source).executeAsSubtask(overview, null);
		settle();
		if (model.getTotalColumnCount() != columnsBefore)
			fail("a second run added columns: "+columnsBefore+" -> "+model.getTotalColumnCount());

		System.out.println("SAR overview: "+model.getTotalRowCount()+" molecules -> R-groups, tree with Ki statistics, "
				+swaps.getTableModel().getTotalRowCount()+" swaps; Word report with "+reported[0]+" scaffolds and pictures; second run reuses the decomposition");
		System.out.println("SAROverviewGuiCheck passed");
		System.exit(0);
		}

	private static void collect(Container container, List<Component> list) {
		for (Component c:container.getComponents()) {
			list.add(c);
			if (c instanceof Container)
				collect((Container)c, list);
			}
		}

	private static void settle() throws Exception {
		for (int i=0; i<5; i++) {
			Thread.sleep(300);
			SwingUtilities.invokeAndWait(() -> {});
			}
		}

	private static void fail(String message) {
		System.out.println("FAILED: "+message);
		System.exit(1);
		}
	}
