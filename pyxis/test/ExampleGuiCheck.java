/*
 * Pyxis SAR Explorer GUI check for what a first-time user meets: the bundled example
 * project, the quick-start help page and the SAR menu entries for both; and for the two
 * view settings that are saved with a project: locked columns and a view that follows
 * the selection.
 *
 * It starts the built application without a file, opens the example through its macro
 * task code, runs the SAR overview on it, locks columns and restricts a view, saves the
 * project, reopens it and compares. It also opens a project written before these settings
 * existed.
 *
 * Usage: java -cp datawarrior_all.jar:<classes> ExampleGuiCheck older-project.dwar
 * GNU General Public License v3 or later, WITHOUT ANY WARRANTY.
 */

import com.actelion.research.datawarrior.DEFrame;
import com.actelion.research.datawarrior.DEMainPane;
import com.actelion.research.datawarrior.DERuntimeProperties;
import com.actelion.research.datawarrior.DETableView;
import com.actelion.research.datawarrior.DataWarrior;
import com.actelion.research.datawarrior.DataWarriorLinux;
import com.actelion.research.datawarrior.pyxis.DETaskOpenSARExample;
import com.actelion.research.datawarrior.pyxis.DETaskSAROverview;
import com.actelion.research.datawarrior.pyxis.LiveSelectionFilter;
import com.actelion.research.datawarrior.pyxis.LockedColumns;
import com.actelion.research.datawarrior.task.AbstractTask;
import com.actelion.research.datawarrior.task.file.DETaskOpenFile;
import com.actelion.research.table.CompoundTableSaver;
import com.actelion.research.table.model.CompoundTableListHandler;
import com.actelion.research.table.model.CompoundTableModel;
import com.actelion.research.table.view.CompoundTableView;
import com.actelion.research.table.view.JVisualization;
import com.actelion.research.table.view.VisualizationPanel;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ExampleGuiCheck {
	public static void main(String[] args) throws Exception {
		Thread.setDefaultUncaughtExceptionHandler((t, e) -> { e.printStackTrace(); System.exit(1); });
		DataWarriorLinux.main(new String[0]);
		for (int i=0; i<120 && (DataWarrior.getApplication() == null || DataWarrior.getApplication().getFrameList().isEmpty()); i++)
			Thread.sleep(500);
		Thread.sleep(1500);
		final DataWarrior application = DataWarrior.getApplication();
		final DEFrame first = application.getFrameList().get(0);

		// ---- menus and help page
		final List<String> sarItems = new ArrayList<>(), helpItems = new ArrayList<>();
		SwingUtilities.invokeAndWait(() -> {
			JMenuBar bar = first.getJMenuBar();
			for (int i=0; bar != null && i<bar.getMenuCount(); i++) {
				JMenu menu = bar.getMenu(i);
				if (menu == null)
					continue;
				List<String> list = "SAR".equals(menu.getText()) ? sarItems : "Help".equals(menu.getText()) ? helpItems : null;
				for (int j=0; list != null && j<menu.getItemCount(); j++) {
					JMenuItem item = menu.getItem(j);
					if (item != null)
						list.add(item.getText());
					}
				}
			});
		if (!sarItems.contains("Open Example Project") || !sarItems.contains("SAR Quick Start..."))
			fail("SAR menu lacks the example or the quick start; it has "+sarItems);
		// on a Mac the Help menu is the same one; the check runs the Linux/Windows entry point
		if (!helpItems.contains("SAR Quick Start..."))
			fail("Help menu lacks the quick start; it has "+helpItems);
		String help;
		try (InputStream in = ExampleGuiCheck.class.getResourceAsStream("/html/help/pyxis-sar.html")) {
			if (in == null)
				fail("the quick start page is not part of the program");
			help = new String(in.readAllBytes(), "UTF-8");
			}
		for (String menuItem:new String[] { "SAR Overview...", "Open Example Project", "Create R-Group Grid...", "Analyse Substituent Pairs...",
				"Export Visible Table to MS Word..." })
			if (!sarItems.contains(menuItem) || !help.contains(menuItem.replace("...", "")))
				fail("quick start page and SAR menu disagree about '"+menuItem+"'");

		// ---- the example opens through its macro task code
		Object task = application.getTaskFactory().createTaskFromCode(first, "openSARExample");
		if (!(task instanceof DETaskOpenSARExample))
			fail("macro task code 'openSARExample' gives "+task);
		((AbstractTask)task).executeAsSubtask(new Properties(), null);
		DEFrame example = null;
		for (int i=0; i<120 && example == null; i++) {
			Thread.sleep(500);
			for (DEFrame frame:application.getFrameList())
				if (frame.getTableModel().getTotalRowCount() != 0)
					example = frame;
			}
		if (example == null)
			fail("the example project did not open");
		Thread.sleep(2000);
		settle();
		final DEFrame source = example;
		final CompoundTableModel model = source.getTableModel();
		int value = model.findColumn("COX-2 IC50 (nM)");
		if (model.getTotalRowCount() != 288 || value == -1 || !model.isColumnTypeDouble(value) || model.findColumn("Structure") == -1)
			fail("example has "+model.getTotalRowCount()+" rows, value column "+value);
		int withValue = 0;
		for (int row=0; row<model.getTotalRowCount(); row++)
			if (!Float.isNaN(model.getTotalRecord(row).getDouble(value)))
				withValue++;
		if (withValue != 288)
			fail("only "+withValue+" of 288 example rows have a numerical IC50");
		String explanation = (String)model.getExtensionData(CompoundTableModel.cExtensionNameFileExplanation);
		if (explanation == null || !explanation.contains("ChEMBL") || !explanation.contains("Attribution-ShareAlike"))
			fail("the example does not name its source and licence");
		if (!source.getTitle().contains("COX-2"))
			fail("example window is titled '"+source.getTitle()+"'");

		// ---- the overview works on it
		Properties overview = new Properties();
		overview.setProperty("structureColumn", "Structure");
		overview.setProperty("valueColumn", "COX-2 IC50 (nM)");
		new DETaskSAROverview(source).executeAsSubtask(overview, null);
		Thread.sleep(3000);
		settle();
		int scaffoldColumn = model.findColumn("Scaffold");
		if (scaffoldColumn == -1 || model.findColumn("R1") == -1)
			fail("overview of the example created no Scaffold and R1 columns");
		TreeSet<String> scaffolds = new TreeSet<>();
		int decomposed = 0;
		for (int row=0; row<model.getTotalRowCount(); row++) {
			String s = model.getTotalValueAt(row, scaffoldColumn);
			if (s != null && !s.isEmpty()) {
				scaffolds.add(s);
				decomposed++;
				}
			}
		int swaps = -1;
		for (DEFrame frame:application.getFrameList())
			if (frame != source && frame.getTableModel().findColumn("From") != -1 && frame.getTableModel().findColumn("To") != -1)
				swaps = frame.getTableModel().getTotalRowCount();
		if (scaffolds.size() < 5 || swaps < 10)
			fail("overview of the example gives "+scaffolds.size()+" decomposition scaffolds and "+swaps+" swaps; too few to demonstrate anything");
		int rColumns = 0;
		for (int c=0; c<model.getTotalColumnCount(); c++)
			if (model.getColumnTitleNoAlias(c).matches("R[0-9]+"))
				rColumns++;

		// ---- settings that are saved with the project
		final JVisualization[] filtered = new JVisualization[1];
		SwingUtilities.invokeAndWait(() -> {
			DEMainPane mainPane = source.getMainFrame().getMainPane();
			DETableView table = mainPane.getTableView();
			LockedColumns.setLockedCount(table, table.getTable(), 2);
			mainPane.add2DView("Other plot", null);
			filtered[0] = mainPane.add2DView("Follows selection", null).getVisualization();
			select(model, 3, 4, 5, 6, 7);
			LiveSelectionFilter.setActive(filtered[0], mainPane.getListSelectionModel(), true);
			});
		settle();
		if (shown(model, filtered[0]) != 5)
			fail("restricted view shows "+shown(model, filtered[0])+" rows before saving, expected 5");

		File file = File.createTempFile("pyxis-example-", ".dwar");
		file.deleteOnExit();
		save(source, file);
		String text = new String(java.nio.file.Files.readAllBytes(file.toPath()), "UTF-8");
		if (!text.contains("pyxisLockedColumns") || !text.contains("pyxisFollowSelection"))
			fail("the saved project does not record the locked columns and the following view");

		final DEFrame reopened = new DETaskOpenFile(application).openFile(file, new Properties());
		for (int i=0; i<60 && reopened.getTableModel().getTotalRowCount() == 0; i++)
			Thread.sleep(500);
		Thread.sleep(2000);
		settle();
		final CompoundTableModel reopenedModel = reopened.getTableModel();
		final int[] locked = new int[1];
		final JVisualization[] reopenedView = new JVisualization[2];
		SwingUtilities.invokeAndWait(() -> {
			DEMainPane mainPane = reopened.getMainFrame().getMainPane();
			locked[0] = LockedColumns.getLockedCount(mainPane.getTableView());
			CompoundTableView v = mainPane.getView("Follows selection");
			if (v instanceof VisualizationPanel)
				reopenedView[0] = ((VisualizationPanel)v).getVisualization();
			v = mainPane.getView("Other plot");
			if (v instanceof VisualizationPanel)
				reopenedView[1] = ((VisualizationPanel)v).getVisualization();
			});
		if (locked[0] != 2)
			fail("reopened project has "+locked[0]+" locked columns, expected 2");
		if (reopenedView[0] == null || reopenedView[1] == null)
			fail("reopened project lacks the two plots");
		if (!LiveSelectionFilter.isActive(reopenedView[0]) || LiveSelectionFilter.isActive(reopenedView[1]))
			fail("after reopening, exactly the restricted view must follow the selection");
		if (shown(reopenedModel, reopenedView[0]) != 5)
			fail("reopened restricted view shows "+shown(reopenedModel, reopenedView[0])+" rows, expected the 5 it showed when saved");
		SwingUtilities.invokeAndWait(() -> select(reopenedModel, 10, 11));
		settle();
		if (shown(reopenedModel, reopenedView[0]) != 2 || shown(reopenedModel, reopenedView[1]) != 288)
			fail("after reopening, a new selection of 2 rows gives "+shown(reopenedModel, reopenedView[0])+" rows in the restricted view and "
					+shown(reopenedModel, reopenedView[1])+" in the other");
		int lists = 0;
		for (int i=0; i<reopenedModel.getListHandler().getListCount(); i++)
			if (reopenedModel.getListHandler().getListName(i).startsWith(LiveSelectionFilter.LIST_NAME))
				lists++;
		if (lists != 1)
			fail("reopened project has "+lists+" helper row lists, expected one");

		// switching the option off after reopening works as before
		SwingUtilities.invokeAndWait(() -> LiveSelectionFilter.setActive(reopenedView[0], reopened.getMainFrame().getMainPane().getListSelectionModel(), false));
		settle();
		if (shown(reopenedModel, reopenedView[0]) != 288
		 || reopenedModel.getListHandler().getListIndex(LiveSelectionFilter.LIST_NAME) != CompoundTableListHandler.LISTINDEX_NONE)
			fail("switching the option off after reopening left the view restricted or the helper list behind");

		// unlocking is saved too
		SwingUtilities.invokeAndWait(() -> {
			DETableView table = reopened.getMainFrame().getMainPane().getTableView();
			LockedColumns.setLockedCount(table, table.getTable(), 0);
			});
		File second = File.createTempFile("pyxis-example-unlocked-", ".dwar");
		second.deleteOnExit();
		save(reopened, second);
		String secondText = new String(java.nio.file.Files.readAllBytes(second.toPath()), "UTF-8");
		if (secondText.contains("pyxisLockedColumns") || secondText.contains("pyxisFollowSelection"))
			fail("a project without locked columns or following view still records them");

		// ---- a project written before these settings existed
		final DEFrame older = new DETaskOpenFile(application).openFile(new File(args[0]), new Properties());
		for (int i=0; i<60 && older.getTableModel().getTotalRowCount() == 0; i++)
			Thread.sleep(500);
		Thread.sleep(1500);
		settle();
		final int[] olderLocked = new int[1];
		SwingUtilities.invokeAndWait(() -> olderLocked[0] = LockedColumns.getLockedCount(older.getMainFrame().getMainPane().getTableView()));
		if (older.getTableModel().getTotalRowCount() == 0 || olderLocked[0] != 0)
			fail("older project: "+older.getTableModel().getTotalRowCount()+" rows, "+olderLocked[0]+" locked columns");

		System.out.println("Example: 288 COX-2 inhibitors, all with IC50; overview gives "+decomposed+" decomposed molecules, "
				+scaffolds.size()+" decomposition scaffolds, "+rColumns+" R columns and "+swaps+" swaps");
		System.out.println("Saved settings: 2 locked columns and a view following the selection (5 rows) survive save and reopen; "
				+"the reopened view follows a new selection; an older project opens with no locked columns");
		System.out.println("ExampleGuiCheck passed");
		System.exit(0);
		}

	private static void save(DEFrame frame, File file) throws Exception {
		final CountDownLatch saved = new CountDownLatch(1);
		final boolean[] ok = new boolean[1];
		CompoundTableSaver saver = new CompoundTableSaver(frame, frame.getTableModel(), frame.getMainFrame().getMainPane().getTable()) {
			public void finalStatus(File result) { ok[0] = (result != null); saved.countDown(); }
			};
		saver.saveNative(new DERuntimeProperties(frame.getMainFrame()), file, CompoundTableSaver.ROW_MASK_ALL, true);
		if (!saved.await(60, TimeUnit.SECONDS) || !ok[0])
			fail("saving "+file+" failed");
		}

	private static void select(CompoundTableModel model, int... selected) {
		for (int row=0; row<model.getTotalRowCount(); row++)
			model.getTotalRecord(row).setSelection(false);
		for (int row:selected)
			model.getTotalRecord(row).setSelection(true);
		model.invalidateSelectionModel();
		}

	/** Counts the rows a view considers part of itself, independent of its axis assignment. */
	private static int shown(final CompoundTableModel model, final JVisualization view) throws Exception {
		final int[] count = new int[1];
		SwingUtilities.invokeAndWait(() -> {
			int list = view.getLocalExclusionList();
			long mask = (list == CompoundTableListHandler.LISTINDEX_NONE) ? 0L : model.getListHandler().getListMask(list);
			for (int row=0; row<model.getTotalRowCount(); row++)
				if (model.isVisible(model.getTotalRecord(row)) && (mask == 0L || (model.getTotalRecord(row).getFlags() & mask) != 0))
					count[0]++;
			});
		return count[0];
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
