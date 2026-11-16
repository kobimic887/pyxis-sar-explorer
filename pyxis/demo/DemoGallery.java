/*
 * Pyxis SAR Explorer picture tool. Opens a project in the real application, runs the SAR
 * workflow on it and saves pictures of the resulting windows. Nothing here is a check;
 * it only shows what the application displays for a given project.
 *
 * Usage: pyxis/demo/make-gallery.sh project.dwar output-directory value-column [light]
 * GNU General Public License v3 or later, WITHOUT ANY WARRANTY.
 */

import com.actelion.research.datawarrior.DEFrame;
import com.actelion.research.datawarrior.DETableView;
import com.actelion.research.datawarrior.DataWarrior;
import com.actelion.research.datawarrior.DataWarriorLinux;
import com.actelion.research.datawarrior.pyxis.DETaskAnalyseSubstituentPairs;
import com.actelion.research.datawarrior.pyxis.DETaskBuildScaffoldTree;
import com.actelion.research.datawarrior.pyxis.DETaskCreateRGroupGrid;
import com.actelion.research.datawarrior.pyxis.DETaskSummariseSubstituentSwaps;
import com.actelion.research.datawarrior.pyxis.LockedColumns;
import com.actelion.research.datawarrior.task.chem.DETaskDecomposeRGroups;
import com.actelion.research.table.model.CompoundTableModel;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class DemoGallery {
	private static File sDirectory;

	public static void main(String[] args) throws Exception {
		sDirectory = new File(args[1]);
		sDirectory.mkdirs();
		final String valueColumn = args[2];

		// -DpyxisNativeMain=true starts the program the way the packaged application does; that
		// entry point takes no file argument on a Mac, so the project is opened through the task.
		if (Boolean.getBoolean("pyxisNativeMain")) {
			DataWarrior.main(new String[0]);
			for (int i=0; i<120 && (DataWarrior.getApplication() == null || DataWarrior.getApplication().getFrameList().isEmpty()); i++)
				Thread.sleep(500);
			Thread.sleep(1500);
			String macro = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[0])));
			String project = macro.substring(macro.indexOf("fileName=")+9, macro.indexOf('\n', macro.indexOf("fileName=")));
			new com.actelion.research.datawarrior.task.file.DETaskOpenFile(DataWarrior.getApplication(), project).defineAndRun();
			}
		else
			DataWarriorLinux.main(new String[] { args[0] });
		DEFrame source = null;
		for (int i=0; i<120 && source == null; i++) {
			Thread.sleep(500);
			if (DataWarrior.getApplication() != null)
				for (DEFrame frame:DataWarrior.getApplication().getFrameList())
					if (frame.getTableModel().getTotalRowCount() != 0)
						source = frame;
			}
		if (source == null)
			throw new IllegalStateException("project did not open");
		Thread.sleep(2000);
		if (args.length > 3 && args[3].equals("light")) {	// default is the dark Graphite look
			SwingUtilities.invokeAndWait(() -> DataWarrior.getApplication().setLookAndFeel(DataWarrior.LookAndFeel.NEBULA));
			Thread.sleep(1500);
			}
		final DEFrame frame = source;
		final CompoundTableModel model = frame.getTableModel();
		if (model.findColumn(valueColumn) == -1)
			throw new IllegalArgumentException("no column '"+valueColumn+"'");
		SwingUtilities.invokeAndWait(() -> { frame.setSize(1500, 900); frame.validate(); });

		// fresh R-group decomposition on the Murcko scaffold of every molecule
		if (model.findColumn("Scaffold") == -1) {
			Properties decompose = new Properties();
			decompose.setProperty("structureColumn", "Structure");
			decompose.setProperty("scaffoldMode", "murcko");
			decompose.setProperty("useExistingColumns", "false");
			new DETaskDecomposeRGroups(frame).executeAsSubtask(decompose, null);
			settle();
			}

		// 1. scaffold tree with statistics of the value column
		Properties tree = new Properties();
		tree.setProperty("structureColumn", "Structure");
		tree.setProperty("minMolecules", "2");
		new DETaskBuildScaffoldTree(frame).executeAsSubtask(tree, null);
		final Window treeWindow = waitForNewWindow("Scaffold Tree");
		SwingUtilities.invokeAndWait(() -> {
			List<Component> all = new ArrayList<>();
			collect(treeWindow, all);
			for (Component c:all)
				if (c instanceof JComboBox && contains((JComboBox<?>)c, valueColumn))
					((JComboBox<?>)c).setSelectedItem(valueColumn);
			for (Component c:all)
				if (c instanceof JButton && "Refresh".equals(((JButton)c).getText()))
					((JButton)c).doClick();
			for (Component c:all)
				if (c instanceof JTree) {
					((JTree)c).expandRow(0);
					((JTree)c).setSelectionRow(1);
					}
			treeWindow.setSize(760, 900);
			treeWindow.validate();
			});
		settle();
		save(treeWindow, "1-scaffold-tree.png");

		// 2. R-group grid of the most frequent scaffold
		List<String> rColumns = new ArrayList<>();
		for (int c=0; c<model.getTotalColumnCount(); c++)
			if (model.getColumnTitleNoAlias(c).matches("R[0-9]+"))
				rColumns.add(model.getColumnTitleNoAlias(c));
		if (rColumns.size() >= 2) {
			Properties grid = new Properties();
			grid.setProperty("scaffoldColumn", "Scaffold");
			grid.setProperty("rowColumn", rColumns.get(0));
			grid.setProperty("columnColumn", rColumns.get(1));
			grid.setProperty("valueColumn", valueColumn);
			grid.setProperty("aggregate", "median");
			grid.setProperty("order", "frequency");
			new DETaskCreateRGroupGrid(frame).executeAsSubtask(grid, null);
			final Window gridWindow = waitForNewWindow("Grid");
			SwingUtilities.invokeAndWait(() -> { gridWindow.setSize(1300, 850); gridWindow.validate(); });
			settle();
			save(gridWindow, "2-r-group-grid.png");
			}

		// 3. substituent swap summary and 4. substituent pairs
		Properties swaps = new Properties();
		swaps.setProperty("scaffoldColumn", "Scaffold");
		swaps.setProperty("valueColumn", valueColumn);
		swaps.setProperty("minPairs", "2");
		DETaskSummariseSubstituentSwaps swapTask = new DETaskSummariseSubstituentSwaps(frame);
		swapTask.executeAsSubtask(swaps, null);
		settle();
		saveTable(swapTask.getNewFrame(), "3-substituent-swaps.png");

		Properties pairs = new Properties();
		pairs.setProperty("scaffoldColumn", "Scaffold");
		pairs.setProperty("valueColumn", valueColumn);
		DETaskAnalyseSubstituentPairs pairTask = new DETaskAnalyseSubstituentPairs(frame);
		pairTask.executeAsSubtask(pairs, null);
		settle();
		saveTable(pairTask.getNewFrame(), "4-substituent-pairs.png");

		// 5. source table with the first two columns locked and scrolled to the R-groups
		SwingUtilities.invokeAndWait(() -> {
			DETableView view = frame.getMainFrame().getMainPane().getTableView();
			LockedColumns.setLockedCount(view, view.getTable(), 2);
			int max = view.getTable().getWidth() - view.getViewport().getExtentSize().width;
			view.getViewport().setViewPosition(new Point(Math.max(0, max), 0));
			});
		settle();
		saveTable(frame, "5-locked-columns.png");

		System.out.println("pictures written to "+sDirectory);
		System.exit(0);
		}

	private static boolean contains(JComboBox<?> box, String item) {
		for (int i=0; i<box.getItemCount(); i++)
			if (item.equals(box.getItemAt(i)))
				return true;
		return false;
		}

	private static Window waitForNewWindow(String titlePart) throws Exception {
		for (int i=0; i<60; i++) {
			Thread.sleep(500);
			for (Window window:Window.getWindows())
				if (window.isShowing() && window instanceof JDialog && ((JDialog)window).getTitle() != null
				 && ((JDialog)window).getTitle().contains(titlePart))
					return window;
			}
		throw new IllegalStateException("no window titled '"+titlePart+"' appeared");
		}

	private static void saveTable(DEFrame frame, String name) throws Exception {
		if (frame == null)
			throw new IllegalStateException("no result window for "+name);
		SwingUtilities.invokeAndWait(() -> { frame.setSize(1500, 800); frame.validate(); });
		settle();
		final DETableView[] view = new DETableView[1];
		SwingUtilities.invokeAndWait(() -> view[0] = frame.getMainFrame().getMainPane().getTableView());
		save(view[0], name);
		}

	private static void save(Component c, String name) throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			try {
				BufferedImage image = new BufferedImage(c.getWidth(), Math.min(c.getHeight(), 900), BufferedImage.TYPE_INT_RGB);
				Graphics2D g = image.createGraphics();
				c.paint(g);
				g.dispose();
				ImageIO.write(image, "png", new File(sDirectory, name));
				}
			catch (Exception e) { throw new RuntimeException(e); }
			});
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
	}
