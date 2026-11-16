/*
 * Pyxis SAR Explorer GUI check for the task dialogs a user sees first: SAR Overview,
 * Summarise Substituent Swaps and Create R-Group Grid. The other checks run the tasks with
 * ready-made settings; this one builds each dialog panel in the real application, reads
 * its default settings, applies settings back to it, runs the task with exactly what the
 * dialog returned, and saves a picture of each panel.
 *
 * Usage: java [-DpyxisNativeMain=true] -cp datawarrior_all.jar:<classes> DialogsGuiCheck macro.dwam picture-prefix
 * GNU General Public License v3 or later, WITHOUT ANY WARRANTY.
 */

import com.actelion.research.chem.Canonizer;
import com.actelion.research.chem.SmilesParser;
import com.actelion.research.chem.StereoMolecule;
import com.actelion.research.datawarrior.DEFrame;
import com.actelion.research.datawarrior.DataWarrior;
import com.actelion.research.datawarrior.DataWarriorLinux;
import com.actelion.research.datawarrior.pyxis.DETaskCreateRGroupGrid;
import com.actelion.research.datawarrior.pyxis.DETaskSAROverview;
import com.actelion.research.datawarrior.pyxis.DETaskSummariseSubstituentSwaps;
import com.actelion.research.datawarrior.task.ConfigurableTask;
import com.actelion.research.table.model.CompoundTableEvent;
import com.actelion.research.table.model.CompoundTableModel;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Properties;

public class DialogsGuiCheck {
	private static String sPrefix;

	public static void main(String[] args) throws Exception {
		Thread.setDefaultUncaughtExceptionHandler((t, e) -> { e.printStackTrace(); System.exit(1); });
		sPrefix = args[1];
		if (Boolean.getBoolean("pyxisNativeMain"))
			DataWarrior.main(new String[0]);	// the entry point of the packaged program, e.g. the Mac one
		else
			DataWarriorLinux.main(new String[] { args[0] });
		for (int i=0; i<120 && (DataWarrior.getApplication() == null || DataWarrior.getApplication().getFrameList().isEmpty()); i++)
			Thread.sleep(500);
		Thread.sleep(1500);
		System.out.println("Application class: "+DataWarrior.getApplication().getClass().getSimpleName()
				+", look and feel: "+javax.swing.UIManager.getLookAndFeel().getName());

		final String[] smiles = { "c1ccccc1", "Fc1ccccc1Cl", "Fc1ccc(Cl)cc1", "Fc1ccccc1Cl", "Clc1ccccc1", "Fc1ccccc1", "CCO" };
		final String[] ki = { "1", "2", "3", "4", "8", "16", "5" };
		final DEFrame[] frameRef = new DEFrame[1];
		SwingUtilities.invokeAndWait(() -> {
			try {
				frameRef[0] = DataWarrior.getApplication().getEmptyFrame("Dialog fixture");
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

		// ---- SAR Overview: defaults, then the same dialog with a value and a drawn core
		final DETaskSAROverview overview = new DETaskSAROverview(source);
		final Properties overviewDefaults = new Properties();
		final Properties overviewWithCore = new Properties();
		SwingUtilities.invokeAndWait(() -> {
			try {
				if (!overview.isConfigurable())
					throw new AssertionError("SAR Overview refuses a table with structures");
				interactive(overview);
				JComponent panel = overview.createDialogContent();
				overview.setDialogConfigurationToDefault();
				overviewDefaults.putAll(overview.getDialogConfiguration());
				picture(panel, "overview-default");

				Properties wanted = new Properties();
				wanted.setProperty("structureColumn", "Structure");
				wanted.setProperty("valueColumn", "Ki");
				StereoMolecule benzene = new StereoMolecule();
				new SmilesParser().parse(benzene, "c1ccccc1");
				wanted.setProperty("core", DETaskSAROverview.encodeCore(benzene));
				overview.setDialogConfiguration(wanted);
				overviewWithCore.putAll(overview.getDialogConfiguration());
				boolean ticked = false;
				java.util.ArrayDeque<java.awt.Component> queue = new java.util.ArrayDeque<>();
				queue.add(panel);
				while (!queue.isEmpty()) {
					java.awt.Component c = queue.poll();
					if (c instanceof javax.swing.JCheckBox)
						ticked |= ((javax.swing.JCheckBox)c).isSelected();
					if (c instanceof java.awt.Container)
						for (java.awt.Component child:((java.awt.Container)c).getComponents())
							queue.add(child);
					}
				if (!ticked)
					throw new AssertionError("SAR Overview shows a core but 'Use my own core' is not ticked");
				picture(panel, "overview-with-core");
				}
			catch (Exception e) { throw new RuntimeException(e); }
			});
		if (!"Structure".equals(overviewDefaults.getProperty("structureColumn")) || overviewDefaults.containsKey("valueColumn")
		 || overviewDefaults.containsKey("core"))
			fail("SAR Overview defaults are "+overviewDefaults+", expected the structure column only");
		if (!"Ki".equals(overviewWithCore.getProperty("valueColumn")) || overviewWithCore.getProperty("core") == null
		 || !overviewWithCore.getProperty("core").split(" ")[0].equals(overviewDefaultsCore()))
			fail("SAR Overview dialog did not keep value and core: "+overviewWithCore);
		if (!overview.isConfigurationValid(overviewDefaults, true) || !overview.isConfigurationValid(overviewWithCore, true))
			fail("SAR Overview rejects the settings its own dialog returned");

		// run with exactly what the default dialog returned plus the value the user would pick
		Properties run = new Properties();
		run.putAll(overviewDefaults);
		run.setProperty("valueColumn", "Ki");
		new DETaskSAROverview(source).executeAsSubtask(run, null);
		settle();
		if (model.findColumn("Scaffold") == -1 || model.findColumn("R1") == -1 || model.findColumn("R2") == -1)
			fail("overview with dialog defaults did not create Scaffold, R1 and R2");

		// ---- Summarise Substituent Swaps
		checkTask(new DETaskSummariseSubstituentSwaps(source), "swaps", "scaffoldColumn", "Scaffold", "valueColumn", "Ki");

		// ---- Create R-Group Grid
		checkTask(new DETaskCreateRGroupGrid(source), "grid", "scaffoldColumn", "Scaffold", "rowColumn", "R1", "columnColumn", "R2",
				"valueColumn", "Ki");

		System.out.println("Dialogs: SAR Overview (default and with value and core), Summarise Substituent Swaps and Create R-Group Grid "
				+"built, returned usable defaults and ran with them");
		System.out.println("DialogsGuiCheck passed");
		System.exit(0);
		}

	private static String overviewDefaultsCore() throws Exception {
		StereoMolecule benzene = new StereoMolecule();
		new SmilesParser().parse(benzene, "c1ccccc1");
		return DETaskSAROverview.encodeCore(benzene).split(" ")[0];
		}

	/** Builds the dialog, checks that its defaults name the expected columns, and runs the task with them. */
	private static void checkTask(final ConfigurableTask task, final String name, final String... expected) throws Exception {
		final Properties defaults = new Properties();
		SwingUtilities.invokeAndWait(() -> {
			try {
				if (!task.isConfigurable())
					throw new AssertionError(name+": task refuses a decomposed table with a value column");
				interactive(task);
				JComponent panel = task.createDialogContent();
				task.setDialogConfigurationToDefault();
				defaults.putAll(task.getDialogConfiguration());
				task.setDialogConfiguration(defaults);	// what reopening the dialog does
				Properties again = task.getDialogConfiguration();
				if (!again.equals(defaults))
					throw new AssertionError(name+": settings change when applied back to the dialog: "+defaults+" -> "+again);
				picture(panel, name);
				}
			catch (Exception e) { throw new RuntimeException(e); }
			});
		for (int i=0; i<expected.length; i+=2)
			if (!expected[i+1].equals(defaults.getProperty(expected[i])))
				fail(name+": default "+expected[i]+" is '"+defaults.getProperty(expected[i])+"', expected '"+expected[i+1]+"' ("+defaults+")");
		if (!task.isConfigurationValid(defaults, true))
			fail(name+": task rejects the settings its own dialog returned: "+defaults);
		int windows = java.awt.Window.getWindows().length + DataWarrior.getApplication().getFrameList().size();
		task.executeAsSubtask(defaults, null);
		settle();
		if (java.awt.Window.getWindows().length + DataWarrior.getApplication().getFrameList().size() <= windows)
			fail(name+": running with the dialog's defaults opened no result window");
		}

	/**
	 * Marks the task as started from the menu rather than from a macro editor, so that the
	 * panel is built the way a user sees it (fixed choices instead of free text fields).
	 */
	private static void interactive(ConfigurableTask task) throws Exception {
		java.lang.reflect.Field field = com.actelion.research.datawarrior.task.AbstractTask.class.getDeclaredField("mIsInteractive");
		field.setAccessible(true);
		field.setBoolean(task, true);
		}

	/** Shows the panel in a packed dialog, as the task framework does, and saves what it looks like. */
	private static void picture(JComponent panel, String name) throws Exception {
		JDialog dialog = new JDialog();
		dialog.getContentPane().add(panel);
		dialog.pack();
		dialog.setVisible(true);
		BufferedImage image = new BufferedImage(Math.max(1, dialog.getContentPane().getWidth()), Math.max(1, dialog.getContentPane().getHeight()),
				BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		dialog.getContentPane().paint(g);
		g.dispose();
		ImageIO.write(image, "png", new File(sPrefix+"-"+name+".png"));
		if (image.getWidth() < 200 || image.getHeight() < 60)
			throw new AssertionError(name+": dialog panel is only "+image.getWidth()+" x "+image.getHeight()+" pixels");
		dialog.getContentPane().remove(panel);
		dialog.dispose();
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
