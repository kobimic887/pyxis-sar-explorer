/*
 * Pyxis SAR Explorer GUI check for "Show Only Rows Selected In Other Views".
 * Opens a project in the real application, adds two 2D views, restricts the second one
 * to the live selection and checks how many rows each view shows as the selection changes.
 *
 * Usage: java -cp datawarrior_all.jar:<classes> LiveSelectionFilterGuiCheck macro.dwam picture.png
 * GNU General Public License v3 or later, WITHOUT ANY WARRANTY.
 */

import com.actelion.research.datawarrior.DEFrame;
import com.actelion.research.datawarrior.DEMainPane;
import com.actelion.research.datawarrior.DataWarrior;
import com.actelion.research.datawarrior.DataWarriorLinux;
import com.actelion.research.datawarrior.pyxis.LiveSelectionFilter;
import com.actelion.research.table.model.CompoundTableListHandler;
import com.actelion.research.table.model.CompoundTableModel;
import com.actelion.research.table.view.JVisualization;
import com.actelion.research.table.view.VisualizationPanel2D;
import com.actelion.research.table.view.VisualizationPoint;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;

public class LiveSelectionFilterGuiCheck {
	private static DEFrame sFrame;
	private static CompoundTableModel sModel;
	private static JVisualization sSource, sFiltered;

	public static void main(String[] args) throws Exception {
		Thread.setDefaultUncaughtExceptionHandler((t, e) -> { e.printStackTrace(); System.exit(1); });
		DataWarriorLinux.main(new String[] { args[0] });

		for (int i=0; i<120 && sFrame == null; i++) {
			Thread.sleep(500);
			DataWarrior application = DataWarrior.getApplication();
			if (application != null)
				for (DEFrame frame:application.getFrameList())
					if (frame.getTableModel().getTotalRowCount() != 0)
						sFrame = frame;
			}
		if (sFrame == null)
			fail("the project did not open within 60 seconds");
		Thread.sleep(1500);	// let the loader finish creating the document's own views

		sModel = sFrame.getTableModel();
		final int rows = sModel.getTotalRowCount();
		if (rows < 4)
			fail("need at least 4 rows, project has "+rows);

		onEDT(() -> {
			DEMainPane mainPane = sFrame.getMainFrame().getMainPane();
			VisualizationPanel2D source = mainPane.add2DView("Source plot", null);
			VisualizationPanel2D filtered = mainPane.add2DView("Filtered plot", null);
			sSource = source.getVisualization();
			sFiltered = filtered.getVisualization();
			select(0, 1, 2);
			LiveSelectionFilter.setActive(sFiltered, mainPane.getListSelectionModel(), true);
			});
		settle();
		expect("filter on: filtered view follows the selection", shown(sFiltered), 3);
		expect("filter on: the other view still shows every row", shown(sSource), rows);
		if (!LiveSelectionFilter.isActive(sFiltered) || LiveSelectionFilter.isActive(sSource))
			fail("only the filtered view may report the option as active");

		onEDT(() -> select(1, 3));
		settle();
		expect("changed selection: filtered view updates", shown(sFiltered), 2);
		expect("changed selection: the other view is unaffected", shown(sSource), rows);
		expectVisible(1, true);
		expectVisible(3, true);
		expectVisible(0, false);

		onEDT(() -> select());
		settle();
		expect("empty selection: filtered view is empty", shown(sFiltered), 0);

		onEDT(() -> select(0, 1, 2, 3));
		settle();
		expect("selection of four rows", shown(sFiltered), 4);

		final File picture = new File(args[1]);
		onEDT(() -> {
			try {
				java.awt.Component c = sFiltered.getParent();
				BufferedImage image = new BufferedImage(Math.max(1, c.getWidth()), Math.max(1, c.getHeight()), BufferedImage.TYPE_INT_RGB);
				Graphics2D g = image.createGraphics();
				c.paint(g);
				g.dispose();
				ImageIO.write(image, "png", picture);
				}
			catch (Exception e) { throw new RuntimeException(e); }
			});

		onEDT(() -> LiveSelectionFilter.setActive(sFiltered, sFrame.getMainFrame().getMainPane().getListSelectionModel(), false));
		settle();
		expect("filter off: all rows are back", shown(sFiltered), rows);
		if (LiveSelectionFilter.isActive(sFiltered))
			fail("option still reported active after switching it off");
		if (sModel.getListHandler().getListIndex(LiveSelectionFilter.LIST_NAME) != CompoundTableListHandler.LISTINDEX_NONE)
			fail("the helper row list was not removed after the last view stopped using it");

		System.out.println("Live selection filter: "+rows+" rows; filtered view showed 3, 2, 0, 4 then "+rows
				+" rows; the other view always showed "+rows);
		System.out.println("LiveSelectionFilterGuiCheck passed");
		System.exit(0);
		}

	/** Replaces the row selection by the given rows, the way any view does it. */
	private static void select(int... selected) {
		for (int row=0; row<sModel.getTotalRowCount(); row++)
			sModel.getTotalRecord(row).setSelection(false);
		for (int row:selected)
			sModel.getTotalRecord(row).setSelection(true);
		sModel.invalidateSelectionModel();
		}

	/** Counts the rows a view considers part of itself, independent of its axis assignment. */
	private static int shown(JVisualization view) throws Exception {
		final int[] count = new int[1];
		onEDT(() -> {
			try {
				int list = view.getLocalExclusionList();
				long mask = (list == CompoundTableListHandler.LISTINDEX_NONE) ? 0L : sModel.getListHandler().getListMask(list);
				for (int row=0; row<sModel.getTotalRowCount(); row++)
					if (sModel.isVisible(sModel.getTotalRecord(row)) && (mask == 0L || (sModel.getTotalRecord(row).getFlags() & mask) != 0))
						count[0]++;

				// and the view's own marker bookkeeping must agree with that
				Field field = JVisualization.class.getDeclaredField("mPoint");
				field.setAccessible(true);
				VisualizationPoint[] point = (VisualizationPoint[])field.get(view);
				java.lang.reflect.Method visible = JVisualization.class.getDeclaredMethod("isVisibleInModel", VisualizationPoint.class);
				visible.setAccessible(true);
				int byView = 0;
				for (VisualizationPoint p:point)
					if ((Boolean)visible.invoke(view, p))
						byView++;
				if (byView != count[0])
					throw new RuntimeException("view shows "+byView+" markers, row list says "+count[0]);
				}
			catch (RuntimeException e) { throw e; }
			catch (Exception e) { throw new RuntimeException(e); }
			});
		return count[0];
		}

	private static void expectVisible(int row, boolean expected) throws Exception {
		final boolean[] is = new boolean[1];
		onEDT(() -> {
			int list = sFiltered.getLocalExclusionList();
			is[0] = (sModel.getTotalRecord(row).getFlags() & sModel.getListHandler().getListMask(list)) != 0;
			});
		if (is[0] != expected)
			fail("row "+row+" shown in filtered view: "+is[0]+", expected "+expected);
		}

	private static void expect(String what, int found, int expected) {
		if (found != expected)
			fail(what+": expected "+expected+", found "+found);
		}

	private static void onEDT(Runnable r) throws Exception {
		SwingUtilities.invokeAndWait(r);
		}

	/** Selection and list events are delivered later on the event thread. */
	private static void settle() throws Exception {
		for (int i=0; i<3; i++) {
			Thread.sleep(150);
			SwingUtilities.invokeAndWait(() -> {});
			}
		}

	private static void fail(String message) {
		System.out.println("FAILED: "+message);
		System.exit(1);
		}
	}
