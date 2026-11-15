/*
 * Pyxis SAR Explorer GUI check. Starts the real application with a macro that opens a
 * file and runs 'Build Scaffold Tree', then drives the scaffold tree window:
 * chooses the first scaffold, presses 'Select Molecules' and compares the table's row
 * selection with the scaffold's molecule count. Also saves a picture of the window.
 *
 * Usage: java -cp datawarrior_all.jar:<classes> ScaffoldTreeGuiCheck macro.dwam picture.png
 * GNU General Public License v3 or later, WITHOUT ANY WARRANTY.
 */

import com.actelion.research.datawarrior.DEFrame;
import com.actelion.research.datawarrior.DataWarrior;
import com.actelion.research.datawarrior.DataWarriorLinux;
import com.actelion.research.datawarrior.pyxis.ScaffoldHierarchy;
import com.actelion.research.datawarrior.pyxis.ScaffoldTreeDialog;
import com.actelion.research.table.model.CompoundTableModel;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ScaffoldTreeGuiCheck {
	public static void main(String[] args) throws Exception {
		DataWarriorLinux.main(new String[] { args[0] });

		ScaffoldTreeDialog dialog = null;
		for (int i=0; i<120 && dialog == null; i++) {
			Thread.sleep(500);
			for (Window window:Window.getWindows())
				if (window instanceof ScaffoldTreeDialog && window.isShowing())
					dialog = (ScaffoldTreeDialog)window;
			}
		if (dialog == null)
			fail("the scaffold tree window did not open within 60 seconds");

		final ScaffoldTreeDialog d = dialog;
		final String[] result = new String[1];
		SwingUtilities.invokeAndWait(() -> {
			try {
				List<Component> all = new ArrayList<>();
				collect(d.getContentPane(), all);
				JTree tree = null;
				JButton select = null;
				JButton asTable = null;
				JButton refresh = null;
				JCheckBox exact = null;
				for (Component c:all) {
					if (c instanceof JButton && "Refresh".equals(((JButton)c).getText())) refresh = (JButton)c;
					if (c instanceof JTree) tree = (JTree)c;
					if (c instanceof JButton && "Select Molecules".equals(((JButton)c).getText())) select = (JButton)c;
					if (c instanceof JButton && "Tree As New Table".equals(((JButton)c).getText())) asTable = (JButton)c;
					if (c instanceof JCheckBox) exact = (JCheckBox)c;
					}
				if (tree == null || select == null || exact == null || asTable == null) {
					result[0] = "tree, select button or checkbox missing";
					return;
					}
				if (tree.getRowCount() == 0) {
					result[0] = "the tree is empty";
					return;
					}

				CompoundTableModel tableModel = ((DEFrame)d.getOwner()).getTableModel();
				tree.expandRow(0);
				tree.setSelectionRow(0);
				ScaffoldHierarchy.Node node = (ScaffoldHierarchy.Node)
						((DefaultMutableTreeNode)tree.getPathForRow(0).getLastPathComponent()).getUserObject();

				if (refresh == null) throw new AssertionError("Refresh missing");
				refresh.doClick();
				select.doClick();
				int family = countSelected(tableModel);
				exact.setSelected(true);
				select.doClick();
				int exactCount = countSelected(tableModel);

				if (family != node.getMemberCount())
					result[0] = "family selection is "+family+" rows, scaffold reports "+node.getMemberCount();
				else if (exactCount != node.getExactMemberCount())
					result[0] = "exact selection is "+exactCount+" rows, scaffold reports "+node.getExactMemberCount();
				else if (family == 0 || family > tableModel.getTotalRowCount())
					result[0] = "implausible family size "+family;

				// Exercise the editor's accepted-structure path in the real window.
				d.addEditedScaffold(new com.actelion.research.chem.IDCodeParser().getCompactMolecule(node.getIDCode()));
				DefaultMutableTreeNode root = (DefaultMutableTreeNode)tree.getModel().getRoot();
				ScaffoldHierarchy.Node custom = (ScaffoldHierarchy.Node)((DefaultMutableTreeNode)root.getLastChild()).getUserObject();
				if (!custom.isUserDefined() || custom.getMemberCount() < family) throw new AssertionError("user scaffold membership");

				// 'Tree As New Table' must open a document with one row per scaffold of the tree
				int scaffolds = countNodes((DefaultMutableTreeNode)tree.getModel().getRoot()) - 1;
				asTable.doClick();
				CompoundTableModel treeTable = null;
				for (DEFrame frame:DataWarrior.getApplication().getFrameList())
					if (frame != d.getOwner() && frame.getTableModel().getTotalRowCount() != 0)
						treeTable = frame.getTableModel();
				if (result[0] == null) {
					if (treeTable != null && !Double.toString(node.getMemberCount()).equals(treeTable.getTotalValueAt(0, treeTable.findColumn("Subset Count")))) throw new AssertionError("subset count export");
					if (treeTable == null)
						result[0] = "'Tree As New Table' did not open a filled document";
					else if (treeTable.getTotalRowCount() != scaffolds)
						result[0] = "tree table has "+treeTable.getTotalRowCount()+" rows for "+scaffolds+" scaffolds";
					else if (!treeTable.isColumnTypeStructure(treeTable.findColumn("Scaffold")))
						result[0] = "the tree table's Scaffold column is not a structure column";
					else if (!Integer.toString(node.getMemberCount()).equals(treeTable.getTotalValueAt(0, treeTable.findColumn("Molecules"))))
						result[0] = "first tree table row does not carry the first scaffold's molecule count";
					}

				BufferedImage image = new BufferedImage(d.getWidth(), d.getHeight(), BufferedImage.TYPE_INT_RGB);
				Graphics2D g = image.createGraphics();
				d.paint(g);
				g.dispose();
				ImageIO.write(image, "png", new File(args[1]));

				System.out.println("rows in table: "+tableModel.getTotalRowCount()+", tree rows: "+tree.getRowCount()
						+", first scaffold family: "+family+", exact: "+exactCount+", scaffolds written to new table: "+scaffolds);
				}
			catch (Exception e) {
				result[0] = e.toString();
				}
			});

		if (result[0] != null)
			fail(result[0]);
		CompoundTableModel model = ((DEFrame)d.getOwner()).getTableModel();
		int structure = model.findColumn("Structure");
		String[] original = new String[model.getTotalRowCount()];
		for (int r=0; r<original.length; r++) original[r] = model.getTotalValueAt(r, structure);
		java.util.Properties config = new java.util.Properties();
		config.setProperty("structureColumn", "Structure");
		config.setProperty("members", original[0]);
		String scaffold = ScaffoldHierarchy.murckoScaffoldIDCode(model.getChemicalStructure(model.getTotalRecord(0), structure, 0, null));
		config.setProperty("scaffolds", scaffold);
		config.setProperty("automatic", "false");
		new com.actelion.research.datawarrior.pyxis.DETaskOrientScaffold((DEFrame)d.getOwner()).executeAsSubtask(config, null);
		for (int r=0; r<original.length; r++)
			if (!original[r].equals(model.getTotalValueAt(r, structure))) fail("orientation changed chemical identity");
		int coordinates = model.getChildColumn(structure, CompoundTableModel.cColumnType2DCoordinates);
		if (coordinates < 0 || model.getTotalValueAt(0, coordinates).isEmpty()) fail("orientation did not write coordinates");
		System.out.println("User scaffold added/exported; template orientation preserved all "+original.length+" molecule identities");
		checkPairTask();
		System.out.println("ScaffoldTreeGuiCheck passed");
		System.exit(0);
		}

	private static void checkPairTask() throws Exception {
		DEFrame input = DataWarrior.getApplication().getEmptyFrame("Hopping fixture");
		CompoundTableModel table = input.getTableModel();
		table.initializeTable(3, 5);
		String[] titles = {"Scaffold", "R1", "R2", "Ki", "Structure"};
		for (int c=0; c<5; c++) table.setColumnName(titles[c], c);
		table.setColumnProperty(4, CompoundTableModel.cColumnPropertySpecialType, CompoundTableModel.cColumnTypeIDCode);
		for (int c=0; c<3; c++) table.setColumnProperty(c, CompoundTableModel.cColumnPropertySpecialType, CompoundTableModel.cColumnTypeIDCode);
		com.actelion.research.chem.StereoMolecule benzene = new com.actelion.research.chem.StereoMolecule();
		new com.actelion.research.chem.SmilesParser().parse(benzene, "c1ccccc1");
		String a = new com.actelion.research.chem.Canonizer(benzene).getIDCode();
		new com.actelion.research.chem.SmilesParser().parse(benzene, "c1ccncc1");
		String b = new com.actelion.research.chem.Canonizer(benzene).getIDCode();
		for (int r=0; r<3; r++) {
			table.setTotalValueAt(r == 1 ? b : a, r, 0);
			table.setTotalValueAt(a, r, 1);
			table.setTotalValueAt(r == 1 ? b : a, r, 4);
			table.setTotalValueAt("", r, 2);
			table.setTotalValueAt(Integer.toString(2+r*2), r, 3);
			}
		table.finalizeTable(com.actelion.research.table.model.CompoundTableEvent.cSpecifierDefaultFiltersAndViews, null);
		java.util.Properties config = new java.util.Properties();
		config.setProperty("scaffoldColumn", "Scaffold"); config.setProperty("valueColumn", "Ki"); config.setProperty("mode", "hopping");
		com.actelion.research.datawarrior.pyxis.DETaskAnalyseSubstituentPairs task = new com.actelion.research.datawarrior.pyxis.DETaskAnalyseSubstituentPairs(input);
		SwingUtilities.invokeAndWait(() -> {
			task.createDialogContent(); task.setDialogConfiguration(config);
			if (!"hopping".equals(task.getDialogConfiguration().getProperty("mode"))) throw new AssertionError("hopping mode dialog roundtrip");
			});
		task.executeAsSubtask(config, null);
		CompoundTableModel output = null;
		for (DEFrame f:DataWarrior.getApplication().getFrameList()) if (f.getTitle().startsWith("Scaffold Hopping Pairs")) output = f.getTableModel();
		if (output == null || output.getTotalRowCount() != 2 || !output.isColumnTypeStructure(output.findColumn("Scaffold B"))) fail("hopping task output");
		System.out.println("Hopping GUI configuration and task: 3 input rows -> 2 pairs with scaffold A/B structures");
		config.setProperty("moleculeColumn", "Structure"); config.setProperty("scaffold", a); config.setProperty("valueColumns", "Ki");
		com.actelion.research.datawarrior.pyxis.DETaskCreateRGroupTable copy = new com.actelion.research.datawarrior.pyxis.DETaskCreateRGroupTable(input);
		SwingUtilities.invokeAndWait(() -> { copy.createDialogContent(); copy.setDialogConfiguration(config); if (!a.equals(copy.getDialogConfiguration().getProperty("scaffold"))) throw new AssertionError("R-group dialog roundtrip"); });
		copy.executeAsSubtask(config,null);
		CompoundTableModel derived = null;
		for (DEFrame f:DataWarrior.getApplication().getFrameList()) if (f.getTitle().startsWith("R-Group Table (derived copy)")) derived = f.getTableModel();
		if (derived == null || derived.getTotalRowCount()!=2 || derived.findColumn("Ki")<0 || derived.findColumn("R2")<0 || !a.equals(derived.getTotalValueAt(1,0))) fail("derived R-group table");
		derived.setTotalValueAt("999",0,derived.findColumn("Ki"));
		if (!"2".equals(table.getTotalValueAt(0,3))) fail("derived copy changed source");
		System.out.println("R-group GUI/task: 2 matching rows, structures and Ki copied; source unchanged by derived edits");
		}

	private static int countNodes(DefaultMutableTreeNode node) {
		int count = 1;
		for (int i=0; i<node.getChildCount(); i++)
			count += countNodes((DefaultMutableTreeNode)node.getChildAt(i));
		return count;
		}

	private static int countSelected(CompoundTableModel tableModel) {
		int count = 0;
		for (int row=0; row<tableModel.getTotalRowCount(); row++)
			if (tableModel.getTotalRecord(row).isSelected())
				count++;
		return count;
		}

	private static void collect(Container container, List<Component> list) {
		for (Component c:container.getComponents()) {
			list.add(c);
			if (c instanceof Container)
				collect((Container)c, list);
			}
		}

	private static void fail(String message) {
		System.out.println("FAILED: "+message);
		System.exit(1);
		}
	}
