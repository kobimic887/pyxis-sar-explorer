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
		System.out.println("ScaffoldTreeGuiCheck passed");
		System.exit(0);
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
