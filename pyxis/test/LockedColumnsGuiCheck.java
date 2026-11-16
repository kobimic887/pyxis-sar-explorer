/*
 * Pyxis SAR Explorer GUI check for locked (frozen) table columns.
 * Opens a project in the real application, locks the first two columns, scrolls the table
 *  (of a table built by the check itself)
 * to the right and checks that the strip at the left edge shows exactly those columns and
 * that clicks on it select rows, sort and resize like clicks on the real columns.
 *
 * Usage: java -cp datawarrior_all.jar:<classes> LockedColumnsGuiCheck macro.dwam picture.png
 * GNU General Public License v3 or later, WITHOUT ANY WARRANTY.
 */

import com.actelion.research.datawarrior.DEFrame;
import com.actelion.research.datawarrior.DETable;
import com.actelion.research.datawarrior.DETableView;
import com.actelion.research.datawarrior.DataWarrior;
import com.actelion.research.datawarrior.DataWarriorLinux;
import com.actelion.research.datawarrior.pyxis.LockedColumns;
import com.actelion.research.table.model.CompoundTableEvent;
import com.actelion.research.table.model.CompoundTableModel;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;

public class LockedColumnsGuiCheck {
	private static DEFrame sFrame;
	private static DETableView sView;
	private static DETable sTable;
	private static volatile MouseEvent sLastTableEvent;
	private static volatile int sSortEvents;

	public static void main(String[] args) throws Exception {
		Thread.setDefaultUncaughtExceptionHandler((t, e) -> { e.printStackTrace(); System.exit(1); });
		// -DpyxisNativeMain=true starts the program the way the packaged application does
		// (on a Mac that is a different entry point and look); the table is built below anyway.
		if (Boolean.getBoolean("pyxisNativeMain"))
			DataWarrior.main(new String[0]);
		else
			DataWarriorLinux.main(new String[] { args[0] });
		for (int i=0; i<120 && (DataWarrior.getApplication() == null || DataWarrior.getApplication().getFrameList().isEmpty()); i++)
			Thread.sleep(500);
		if (DataWarrior.getApplication() == null || DataWarrior.getApplication().getFrameList().isEmpty())
			fail("the application did not start within 60 seconds");
		Thread.sleep(1500);
		System.out.println("Application class: "+DataWarrior.getApplication().getClass().getSimpleName()
				+", look and feel: "+javax.swing.UIManager.getLookAndFeel().getName());

		// Projects differ in their number of columns; the check brings its own table so that
		// it behaves the same on every input: 16 columns, 20 rows, a distinct value per cell.
		SwingUtilities.invokeAndWait(() -> {
			DEFrame frame = DataWarrior.getApplication().getEmptyFrame("Lock fixture");
			CompoundTableModel t = frame.getTableModel();
			t.initializeTable(20, 16);
			for (int column=0; column<16; column++)
				t.setColumnName("Column "+(column+1), column);
			for (int row=0; row<20; row++)
				for (int column=0; column<16; column++)
					t.setTotalValueAt(Integer.toString((row+1)*100 + column*7 % 13), row, column);
			t.finalizeTable(CompoundTableEvent.cSpecifierDefaultFiltersAndViews, null);
			sFrame = frame;
			});
		Thread.sleep(1000);

		final CompoundTableModel model = sFrame.getTableModel();
		model.addCompoundTableListener(e -> { if (e.getType() == CompoundTableEvent.cChangeSortOrder) sSortEvents++; });

		final int[] viewX = new int[1];
		final int[] lockedWidth = new int[1];
		onEDT(() -> {
			sFrame.setSize(1500, 700);
			sFrame.validate();
			sView = sFrame.getMainFrame().getMainPane().getTableView();
			sTable = sView.getTable();
			sTable.addMouseListener(new MouseAdapter() {
				@Override public void mousePressed(MouseEvent e) { sLastTableEvent = e; }
				});
			// wide columns guarantee that the table can be scrolled far enough
			for (int i=0; i<sTable.getColumnCount(); i++)
				sTable.getColumnModel().getColumn(i).setPreferredWidth(160);
			sTable.doLayout();
			sView.getViewport().setViewPosition(new Point(0, 0));	// a saved project may open scrolled
			});
		settle();
		if (sTable.getColumnCount() < 4 || sTable.getRowCount() < 5)
			fail("need at least 4 columns and 5 rows, have "+sTable.getColumnCount()+" and "+sTable.getRowCount());

		// reference picture of the first two columns, taken before anything is locked
		final BufferedImage[] reference = new BufferedImage[1];
		onEDT(() -> {
			lockedWidth[0] = sTable.getColumnModel().getColumn(0).getWidth() + sTable.getColumnModel().getColumn(1).getWidth();
			reference[0] = paint(sTable, new Rectangle(0, 0, lockedWidth[0], sTable.getRowHeight()*3));
			});

		onEDT(() -> LockedColumns.setLockedCount(sView, sTable, 2));
		settle();
		if (LockedColumns.isShowing(sView))
			fail("locked columns must not be drawn while the table is at its left end");

		onEDT(() -> {
			int max = sTable.getWidth() - sView.getViewport().getExtentSize().width;
			viewX[0] = Math.min(max, lockedWidth[0] + 240);
			sView.getViewport().setViewPosition(new Point(viewX[0], 0));
			});
		settle();
		if (viewX[0] <= lockedWidth[0])
			fail("table cannot be scrolled beyond the locked columns (scroll range "+viewX[0]+", locked "+lockedWidth[0]+")");

		final JComponent strip = LockedColumns.getTableStrip(sView);
		final JComponent headerStrip = LockedColumns.getHeaderStrip(sView);
		if (!LockedColumns.isShowing(sView) || strip == null || !strip.isVisible() || !headerStrip.isVisible())
			fail("locked columns are not drawn after scrolling right");
		expect("strip sits at the left edge of the visible area", strip.getX(), viewX[0]);
		expect("strip is as wide as the two locked columns", strip.getWidth(), lockedWidth[0]);
		expect("strip is as tall as the table", strip.getHeight(), sTable.getHeight());
		expect("header strip sits at the left edge", headerStrip.getX(), viewX[0]);
		expect("header strip is as wide as the locked columns", headerStrip.getWidth(), lockedWidth[0]);

		// what the user sees at the left edge must be the first two columns, pixel for pixel
		final BufferedImage[] seen = new BufferedImage[1];
		final BufferedImage[] unlockedLook = new BufferedImage[1];
		onEDT(() -> seen[0] = paint(sTable, new Rectangle(viewX[0], 0, lockedWidth[0], sTable.getRowHeight()*3)));
		int different = differingPixels(reference[0], seen[0], lockedWidth[0]-3);	// last pixels carry the divider line
		if (different != 0)
			fail(different+" pixels of the locked strip differ from the real first two columns");

		// a click on the strip selects the row it is on, like a click on the real column
		final int[] selectedRow = new int[1];
		onEDT(() -> {
			Rectangle cell = sTable.getCellRect(3, 0, true);
			click(strip, 12, cell.y + cell.height/2, 1);
			selectedRow[0] = sTable.getSelectedRow();
			});
		settle();
		expect("click on the strip selects that row", selectedRow[0], 3);
		final int[] xAfterClick = new int[1];
		onEDT(() -> xAfterClick[0] = sView.getViewport().getViewPosition().x);
		expect("a click on a locked cell must not scroll the table back to the left", xAfterClick[0], viewX[0]);
		if (!LockedColumns.isShowing(sView))
			fail("locked columns vanished after a click on them");
		if (sLastTableEvent == null || LockedColumns.popupShift(sLastTableEvent) != viewX[0])
			fail("the table did not receive the strip click with the popup offset "+viewX[0]);
		if (sTable.columnAtPoint(sLastTableEvent.getPoint()) != 0)
			fail("strip click was not delivered to the first column");

		// saving a project collects every view's state; that must work while columns are locked
		onEDT(() -> new com.actelion.research.datawarrior.DERuntimeProperties(sFrame.getMainFrame()).learn());

		// a right click on the header strip opens the column menu where the user clicked,
		// and that menu offers to unlock
		final String[] popupProblem = new String[1];
		onEDT(() -> {
			long now = System.currentTimeMillis();
			headerStrip.dispatchEvent(new MouseEvent(headerStrip, MouseEvent.MOUSE_PRESSED, now, InputEvent.BUTTON3_DOWN_MASK,
					12, headerStrip.getHeight()/2, 1, true, MouseEvent.BUTTON3));
			});
		settle();
		onEDT(() -> {
			javax.swing.MenuElement[] path = javax.swing.MenuSelectionManager.defaultManager().getSelectedPath();
			if (path.length == 0 || !(path[0] instanceof javax.swing.JPopupMenu)) {
				popupProblem[0] = "no popup menu opened from the locked header";
				return;
				}
			javax.swing.JPopupMenu popup = (javax.swing.JPopupMenu)path[0];
			if (!popup.isShowing() || !headerStrip.isShowing()) {
				popupProblem[0] = "the column menu was created but is not showing (popup "+popup.isShowing()+", strip "+headerStrip.isShowing()+")";
				javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();
				return;
				}
			boolean hasUnlock = false;
			for (java.awt.Component c:popup.getComponents())
				if (c instanceof javax.swing.JMenuItem && "Unlock Columns".equals(((javax.swing.JMenuItem)c).getText()))
					hasUnlock = true;
			int expectedX = headerStrip.getLocationOnScreen().x + 12;
			int actualX = popup.getLocationOnScreen().x;
			if (!hasUnlock)
				popupProblem[0] = "the column menu opened from the strip lacks 'Unlock Columns'";
			else if (Math.abs(actualX - expectedX) > 4)
				popupProblem[0] = "column menu opened at x="+actualX+", the click was at x="+expectedX;
			javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();
			});
		if (popupProblem[0] != null)
			fail(popupProblem[0]);
		settle();

		// a click on the header strip sorts by that column
		int sortsBefore = sSortEvents;
		onEDT(() -> click(headerStrip, 12, headerStrip.getHeight()/2, 1));
		for (int i=0; i<40 && sSortEvents == sortsBefore; i++)
			settle();
		if (sSortEvents == sortsBefore)
			fail("a click on the locked header did not sort the table");

		// resizing a locked column resizes the strip
		onEDT(() -> {
			sTable.getColumnModel().getColumn(0).setPreferredWidth(sTable.getColumnModel().getColumn(0).getWidth() + 30);
			sTable.doLayout();
			sView.getViewport().setViewPosition(new Point(viewX[0], 0));	// sorting scrolled back to the left end
			sTable.repaint();
			});
		settle();
		onEDT(() -> paint(sTable, new Rectangle(sView.getViewport().getViewPosition().x, 0, 50, 50)));	// what a screen repaint does
		settle();
		final int[] widthNow = new int[1];
		onEDT(() -> widthNow[0] = sTable.getColumnModel().getColumn(0).getWidth() + sTable.getColumnModel().getColumn(1).getWidth());
		if (strip.getWidth() != widthNow[0])
			fail("strip follows a column resize: expected "+widthNow[0]+", found "+strip.getWidth()+" (visible "+strip.isVisible()
					+", viewport "+sView.getViewport().getExtentSize().width+" px wide at x="+sView.getViewport().getViewPosition().x
					+", column widths "+sTable.getColumnModel().getColumn(0).getWidth()+"/"+sTable.getColumnModel().getColumn(1).getWidth()+")");

		final File picture = new File(args[1]);
		onEDT(() -> {
			try { ImageIO.write(paint(sView, new Rectangle(0, 0, sView.getWidth(), Math.min(sView.getHeight(), 420))), "png", picture); }
			catch (Exception e) { throw new RuntimeException(e); }
			});

		// back at the left end the real columns are visible and the strip goes away
		onEDT(() -> sView.getViewport().setViewPosition(new Point(0, 0)));
		settle();
		if (LockedColumns.isShowing(sView))
			fail("strip still drawn at the left end");

		onEDT(() -> {
			sView.getViewport().setViewPosition(new Point(viewX[0], 0));
			LockedColumns.setLockedCount(sView, sTable, 0);
			});
		settle();
		if (LockedColumns.isShowing(sView) || LockedColumns.getLockedCount(sView) != 0)
			fail("unlocking did not remove the strip");
		onEDT(() -> unlockedLook[0] = paint(sTable, new Rectangle(viewX[0], 0, lockedWidth[0], sTable.getRowHeight()*3)));
		if (differingPixels(reference[0], unlockedLook[0], lockedWidth[0]-3) == 0)
			fail("after unlocking, the left edge still shows the first two columns");

		System.out.println("Locked columns: 2 columns, "+lockedWidth[0]+" px, table scrolled by "+viewX[0]
				+" px; strip pixel-identical to the real columns; row click, header menu at the click, view state for saving, header sort, resize, left end and unlock behave");
		System.out.println("LockedColumnsGuiCheck passed");
		System.exit(0);
		}

	private static BufferedImage paint(JComponent c, Rectangle area) {
		BufferedImage image = new BufferedImage(Math.max(1, area.width), Math.max(1, area.height), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.translate(-area.x, -area.y);
		g.setClip(area);
		c.paint(g);
		g.dispose();
		return image;
		}

	private static int differingPixels(BufferedImage a, BufferedImage b, int width) {
		int count = 0;
		for (int y=0; y<Math.min(a.getHeight(), b.getHeight()); y++)
			for (int x=0; x<width; x++)
				if (a.getRGB(x, y) != b.getRGB(x, y))
					count++;
		return count;
		}

	private static void click(JComponent c, int x, int y, int clicks) {
		long now = System.currentTimeMillis();
		c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, now, InputEvent.BUTTON1_DOWN_MASK, x, y, clicks, false, MouseEvent.BUTTON1));
		c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, now, 0, x, y, clicks, false, MouseEvent.BUTTON1));
		c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_CLICKED, now, 0, x, y, clicks, false, MouseEvent.BUTTON1));
		}

	private static void expect(String what, int found, int expected) {
		if (found != expected)
			fail(what+": expected "+expected+", found "+found);
		}

	private static void onEDT(Runnable r) throws Exception {
		SwingUtilities.invokeAndWait(r);
		}

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
