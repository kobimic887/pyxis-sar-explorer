/*
 * Pyxis SAR Explorer GUI check for the path a new user takes: open a raw SD-file through
 * the application's ordinary file import, answer the import questions, run SAR Overview.
 * Prints what happened so that it can be read by a person; fails if the file does not
 * load, the overview creates no scaffold tree, or an exception is thrown.
 *
 * Usage: java -cp datawarrior_all.jar:<classes> SdfOverviewGuiCheck file.sdf picture.png [value column]
 * GNU General Public License v3 or later, WITHOUT ANY WARRANTY.
 */

import com.actelion.research.datawarrior.DEFrame;
import com.actelion.research.datawarrior.DataWarrior;
import com.actelion.research.datawarrior.DataWarriorLinux;
import com.actelion.research.datawarrior.pyxis.DETaskSAROverview;
import com.actelion.research.datawarrior.pyxis.ScaffoldTreeDialog;
import com.actelion.research.datawarrior.task.file.DETaskOpenFile;
import com.actelion.research.table.model.CompoundTableModel;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class SdfOverviewGuiCheck {
	private static final List<String> sQuestions = new ArrayList<>();

	public static void main(String[] args) throws Exception {
		Thread.setDefaultUncaughtExceptionHandler((t, e) -> { e.printStackTrace(); System.exit(1); });
		final File sdf = new File(args[0]);
		DataWarriorLinux.main(new String[0]);
		for (int i=0; i<120 && (DataWarrior.getApplication() == null || DataWarrior.getApplication().getFrameList().isEmpty()); i++)
			Thread.sleep(500);
		Thread.sleep(1500);

		// answers import questions the way a cautious user would: keep the file as it is
		Thread answer = new Thread(() -> {
			while (true) {
				try { Thread.sleep(300); SwingUtilities.invokeAndWait(SdfOverviewGuiCheck::answerOpenQuestion); }
				catch (Exception e) { return; }
				}
			});
		answer.setDaemon(true);
		answer.start();

		DEFrame first = DataWarrior.getApplication().getFrameList().get(0);
		long start = System.currentTimeMillis();
		new DETaskOpenFile(DataWarrior.getApplication(), sdf.getAbsolutePath()).defineAndRun();

		DEFrame frame = null;
		for (int i=0; i<240 && frame == null; i++) {
			Thread.sleep(500);
			for (DEFrame f:DataWarrior.getApplication().getFrameList())
				if (f.getTableModel().getTotalRowCount() != 0 && !f.getTableModel().isEmpty())
					frame = f;
			}
		if (frame == null)
			fail("the SD-file did not load within two minutes");
		settle();
		final DEFrame source = frame;
		final CompoundTableModel model = source.getTableModel();
		long loadMillis = System.currentTimeMillis() - start;

		int structureColumn = -1;
		for (int c=0; c<model.getTotalColumnCount(); c++)
			if (model.isColumnTypeStructure(c) && structureColumn == -1)
				structureColumn = c;
		if (structureColumn == -1)
			fail("the loaded table has no structure column");
		int empty = 0;
		for (int row=0; row<model.getTotalRowCount(); row++)
			if (model.getTotalValueAt(row, structureColumn).isEmpty())
				empty++;
		System.out.println("Loaded "+sdf.getName()+": "+model.getTotalRowCount()+" rows ("+empty+" without structure), "
				+model.getTotalColumnCount()+" columns, "+loadMillis+" ms; import questions asked: "+sQuestions);

		String value = (args.length > 2 && !args[2].isEmpty()) ? args[2] : null;
		if (value != null && model.findColumn(value) == -1)
			fail("no column '"+value+"' in the loaded file");

		Properties overview = new Properties();
		overview.setProperty("structureColumn", model.getColumnTitleNoAlias(structureColumn));
		if (value != null)
			overview.setProperty("valueColumn", value);
		if (args.length > 3) {	// a core given as SMILES, e.g. NCC(=O)O for amino acids
			com.actelion.research.chem.StereoMolecule core = new com.actelion.research.chem.StereoMolecule();
			new com.actelion.research.chem.SmilesParser().parse(core, args[3]);
			overview.setProperty("core", DETaskSAROverview.encodeCore(core));
			}
		start = System.currentTimeMillis();
		new DETaskSAROverview(source).executeAsSubtask(overview, null);

		ScaffoldTreeDialog tree = null;
		for (int i=0; i<240 && tree == null; i++) {
			Thread.sleep(500);
			for (Window window:Window.getWindows())
				if (window instanceof ScaffoldTreeDialog && window.isShowing())
					tree = (ScaffoldTreeDialog)window;
			}
		if (tree == null)
			fail("SAR Overview opened no scaffold tree");
		settle();
		long overviewMillis = System.currentTimeMillis() - start;

		final ScaffoldTreeDialog treeDialog = tree;
		final int[] treeRows = new int[1];
		final String[] summary = new String[1];
		SwingUtilities.invokeAndWait(() -> {
			List<Component> all = new ArrayList<>();
			collect(treeDialog, all);
			for (Component c:all) {
				if (c instanceof JTree) {
					for (int i=0; i<Math.min(3, ((JTree)c).getRowCount()); i++)
						((JTree)c).expandRow(i);
					treeRows[0] = ((JTree)c).getRowCount();
					}
				if (c instanceof JLabel && summary[0] == null && ((JLabel)c).getText() != null && ((JLabel)c).getText().contains("scaffolds from"))
					summary[0] = ((JLabel)c).getText().replaceAll("<[^>]*>", "");
				}
			treeDialog.setSize(760, 900);
			treeDialog.validate();
			});
		settle();
		SwingUtilities.invokeAndWait(() -> {
			try {
				BufferedImage image = new BufferedImage(treeDialog.getWidth(), treeDialog.getHeight(), BufferedImage.TYPE_INT_RGB);
				Graphics2D g = image.createGraphics();
				treeDialog.paint(g);
				g.dispose();
				ImageIO.write(image, "png", new File(args[1]));
				}
			catch (Exception e) { throw new RuntimeException(e); }
			});

		int rColumns = 0, decomposed = 0;
		for (int c=0; c<model.getTotalColumnCount(); c++)
			if (model.getColumnTitleNoAlias(c).matches("R[0-9]+"))
				rColumns++;
		int scaffoldColumn = model.findColumn("Scaffold");
		for (int row=0; scaffoldColumn != -1 && row<model.getTotalRowCount(); row++)
			if (!model.getTotalValueAt(row, scaffoldColumn).isEmpty())
				decomposed++;
		int swaps = -1;
		for (DEFrame f:DataWarrior.getApplication().getFrameList())
			if (f != source && value != null && f.getTableModel().findColumn(value+" Mean Change") != -1)
				swaps = f.getTableModel().getTotalRowCount();

		System.out.println("SAR Overview in "+overviewMillis+" ms: tree says '"+summary[0]+"'; "+rColumns+" R-group columns, "
				+decomposed+" of "+model.getTotalRowCount()+" rows decomposed; swap summary "+(swaps == -1 ? "not created" : swaps+" exchanges"));
		if (treeRows[0] == 0 && summary[0] != null && !summary[0].startsWith("0 scaffolds"))
			fail("the tree window lists scaffolds in its summary but shows none");
		System.out.println("SdfOverviewGuiCheck passed");
		System.exit(0);
		}

	/** Presses "No" (or the only button) of an open question dialog and remembers its text. */
	private static void answerOpenQuestion() {
		for (Window window:Window.getWindows()) {
			if (!(window instanceof JDialog) || !window.isShowing())
				continue;
			List<Component> all = new ArrayList<>();
			collect((Container)window, all);
			JOptionPane pane = null;
			for (Component c:all)
				if (c instanceof JOptionPane)
					pane = (JOptionPane)c;
			if (pane == null)
				continue;
			AbstractButton no = null, any = null;
			for (Component c:all) {
				if (c instanceof AbstractButton) {
					any = (AbstractButton)c;
					if ("No".equalsIgnoreCase(((AbstractButton)c).getText()))
						no = (AbstractButton)c;
					}
				}
			sQuestions.add("'"+String.valueOf(pane.getMessage()).replace('\n', ' ')+"' -> "+(no != null ? "No" : any == null ? "?" : any.getText()));
			if (no != null) no.doClick(); else if (any != null) any.doClick();
			return;
			}
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
		System.out.println("FAILED: "+message+"; import questions asked: "+sQuestions);
		System.exit(1);
		}
	}
