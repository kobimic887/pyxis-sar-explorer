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
	private static String picturePrefix;
	public static void main(String[] args) {
        try { runCheck(args); }
        catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    private static void runCheck(String[] args) throws Exception {
		picturePrefix = args[1].replaceFirst("\\.png$", "");
		Thread.setDefaultUncaughtExceptionHandler((thread, error) -> { error.printStackTrace(); System.exit(1); });
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
				Rectangle bounds=tree.getRowBounds(0);
				tree.dispatchEvent(new java.awt.event.MouseEvent(tree,java.awt.event.MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),0,bounds.x+10,bounds.y+10,1,true,java.awt.event.MouseEvent.BUTTON3));
				JMenuItem edit=null;
				for (javax.swing.MenuElement element:MenuSelectionManager.defaultManager().getSelectedPath())
					if (element instanceof JPopupMenu) for (Component item:((JPopupMenu)element).getComponents())
						if (item instanceof JMenuItem && "Edit Scaffold...".equals(((JMenuItem)item).getText())) edit=(JMenuItem)item;
				if (edit==null) throw new AssertionError("scaffold context menu missing");
				javax.swing.Timer accept=new javax.swing.Timer(300,e -> {
					for (Window window:Window.getWindows()) if (window.isShowing() && window instanceof com.actelion.research.gui.editor.SwingEditorDialog) {
						List<Component> controls=new ArrayList<>(); collect((Container)window,controls);
						for (Component control:controls) if (control instanceof JButton && "OK".equals(((JButton)control).getText())) { ((JButton)control).doClick(); return; }
						throw new AssertionError("structure editor OK missing");
					}
					});
				accept.setRepeats(false); accept.start(); edit.doClick();
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
		com.actelion.research.datawarrior.pyxis.DETaskOrientScaffold orient = new com.actelion.research.datawarrior.pyxis.DETaskOrientScaffold((DEFrame)d.getOwner());
		SwingUtilities.invokeAndWait(() -> { orient.createDialogContent(); orient.setDialogConfiguration(config); if (!original[0].equals(orient.getDialogConfiguration().getProperty("members"))) throw new AssertionError("orientation macro lost family keys"); });
		orient.executeAsSubtask(config, null);
		for (int r=0; r<original.length; r++)
			if (!original[r].equals(model.getTotalValueAt(r, structure))) fail("orientation changed chemical identity");
		int coordinates = model.getChildColumn(structure, CompoundTableModel.cColumnType2DCoordinates);
		if (coordinates < 0 || model.getTotalValueAt(0, coordinates).isEmpty()) fail("orientation did not write coordinates");
		System.out.println("User scaffold added/exported; template orientation preserved all "+original.length+" molecule identities");
		checkPersistence((DEFrame)d.getOwner());
		checkPairTask();
		System.out.println("ScaffoldTreeGuiCheck passed");
		System.exit(0);
		}

    private static String treeSignature(JTree tree) {
        List<String> nodes=new ArrayList<>();
        java.util.Enumeration<?> all=((DefaultMutableTreeNode)tree.getModel().getRoot()).preorderEnumeration();
        while(all.hasMoreElements()) {
            Object value=((DefaultMutableTreeNode)all.nextElement()).getUserObject();
            if(value instanceof ScaffoldHierarchy.Node) {
                ScaffoldHierarchy.Node n=(ScaffoldHierarchy.Node)value;
                nodes.add(n.getIDCode()+"|"+n.getMemberCount()+"|"+n.getExactMemberCount()+"|"+n.isUserDefined());
            }
        }
        java.util.Collections.sort(nodes); return nodes.toString();
    }

    private static String treeSignature(List<Component> controls) {
        List<String> trees=new ArrayList<>();
        for(Component c:controls) if(c instanceof JTree) trees.add(treeSignature((JTree)c));
        return trees.toString();
    }

    // Row-visibility events fire deferred (CompoundTableModel.updateVisibleRecords uses fireEventsLater);
    // poll with bounded EDT round-trips until the visible row count reaches the expected value, so the
    // assertions do not depend on platform event timing. Returns false on timeout.
    private static boolean waitForVisibleRows(CompoundTableModel model,int expected) throws Exception {
        for(int i=0;i<40;i++) {
            final int[] count=new int[1];
            SwingUtilities.invokeAndWait(() -> count[0]=model.getRowCount());
            if(count[0]==expected) return true;
            Thread.sleep(50);
            }
        return false;
    }

    private static void checkPersistence(DEFrame source) throws Exception {
        String name = com.actelion.research.datawarrior.pyxis.ScaffoldSession.NAME;
        String[] expectedTree={null};
        SwingUtilities.invokeAndWait(() -> {
            for (Window w:Window.getWindows()) if (w instanceof ScaffoldTreeDialog && w.getOwner()==source) {
                List<Component> controls=new ArrayList<>(); collect((Container)w,controls);
                for (Component c:controls) if (c instanceof JTree) expectedTree[0]=treeSignature((JTree)c);
                for (Component c:controls) if (c instanceof JComboBox) {
                    JComboBox<?> box=(JComboBox<?>)c;
                    if (box.getItemCount()>1) box.setSelectedIndex(1);
                }
            }
        });
        java.util.Properties before = (java.util.Properties)source.getTableModel().getExtensionData(name);
        if (before == null || before.getProperty("userScaffolds", "").isEmpty()) fail("session not captured");
        File file = File.createTempFile("pyxis-session-", ".dwar"); file.deleteOnExit();
        java.util.concurrent.CountDownLatch saved = new java.util.concurrent.CountDownLatch(1);
        com.actelion.research.table.CompoundTableSaver saver = new com.actelion.research.table.CompoundTableSaver(source, source.getTableModel(), source.getMainFrame().getMainPane().getTable()) {
            public void finalStatus(File result) { if (result == null) fail("save failed"); saved.countDown(); }
        };
        saver.saveNative(new com.actelion.research.datawarrior.DERuntimeProperties(source.getMainFrame()), file, com.actelion.research.table.CompoundTableSaver.ROW_MASK_ALL, true);
        if (!saved.await(30, java.util.concurrent.TimeUnit.SECONDS)) fail("save timeout");
        DEFrame reopened = new com.actelion.research.datawarrior.task.file.DETaskOpenFile(DataWarrior.getApplication()).openFile(file, new java.util.Properties());
        if (!before.equals(reopened.getTableModel().getExtensionData(name))) fail("saved/reopened session differs");
        var task = new com.actelion.research.datawarrior.pyxis.DETaskBuildScaffoldTree(reopened);
        SwingUtilities.invokeAndWait(() -> {
            JComponent content = task.createDialogContent(); task.setDialogConfiguration(task.getRecentConfiguration());
            if (!before.getProperty("minMolecules").equals(task.getDialogConfiguration().getProperty("minMolecules"))) throw new AssertionError("reopen default minimum");
            renderPanel(reopened, content, "reopened-settings");
        });
        task.executeAsSubtask(task.getRecentConfiguration(), null);
        SwingUtilities.invokeAndWait(() -> {});
        SwingUtilities.invokeAndWait(() -> {
            for (Window w:Window.getWindows()) if (w instanceof ScaffoldTreeDialog && w.getOwner()==reopened) {
                List<Component> controls = new ArrayList<>(); collect((Container)w, controls);
                for (Component c:controls) if (c instanceof JTree) {
                    if (!treeSignature((JTree)c).equals(expectedTree[0])) throw new AssertionError("reopened tree membership differs");
                    DefaultMutableTreeNode root=(DefaultMutableTreeNode)((JTree)c).getModel().getRoot();
                    boolean user=false;
                    for (int n=0;n<root.getChildCount();n++) if (((ScaffoldHierarchy.Node)((DefaultMutableTreeNode)root.getChildAt(n)).getUserObject()).isUserDefined()) user=true;
                    if (!user) throw new AssertionError("restored user scaffold missing");
                }
                try { BufferedImage image=new BufferedImage(w.getWidth(),w.getHeight(),BufferedImage.TYPE_INT_RGB); Graphics2D g=image.createGraphics(); w.paint(g); g.dispose(); ImageIO.write(image,"png",new File(picturePrefix+"-reopened.png")); } catch(Exception e) { throw new RuntimeException(e); }
            }
        });
        if (!before.equals(reopened.getTableModel().getExtensionData(name))) fail("rebuilt session differs");
        System.out.println("Scaffold session: saved .dwar, reopened, compared all properties and rebuilt user scaffolds");
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
			JComponent content = task.createDialogContent(); task.setDialogConfiguration(config);
			renderPanel(input,content,"pairs");
			if (!"hopping".equals(task.getDialogConfiguration().getProperty("mode"))) throw new AssertionError("hopping mode dialog roundtrip");
			});
		task.executeAsSubtask(config, null);
		CompoundTableModel output = null;
		for (DEFrame f:DataWarrior.getApplication().getFrameList()) if (f.getTitle().startsWith("Scaffold Hopping Pairs")) output = f.getTableModel();
		if (output == null || output.getTotalRowCount() != 2 || !output.isColumnTypeStructure(output.findColumn("Scaffold B"))) fail("hopping task output");
		System.out.println("Hopping GUI configuration and task: 3 input rows -> 2 pairs with scaffold A/B structures");
		config.setProperty("moleculeColumn", "Structure"); config.setProperty("scaffold", a); config.setProperty("valueColumns", "Ki");
		com.actelion.research.datawarrior.pyxis.DETaskCreateRGroupTable copy = new com.actelion.research.datawarrior.pyxis.DETaskCreateRGroupTable(input);
		SwingUtilities.invokeAndWait(() -> { JComponent content=copy.createDialogContent(); copy.setDialogConfiguration(config); renderPanel(input,content,"rgroup"); if (!a.equals(copy.getDialogConfiguration().getProperty("scaffold"))) throw new AssertionError("R-group dialog roundtrip"); });
		copy.executeAsSubtask(config,null);
		CompoundTableModel derived = null;
		for (DEFrame f:DataWarrior.getApplication().getFrameList()) if (f.getTitle().startsWith("R-Group Table (derived copy)")) derived = f.getTableModel();
		if (derived == null || derived.getTotalRowCount()!=2 || derived.findColumn("Ki")<0 || derived.findColumn("R2")<0 || !a.equals(derived.getTotalValueAt(1,0))) fail("derived R-group table");
		derived.setTotalValueAt("999",0,derived.findColumn("Ki"));
		if (!"2".equals(table.getTotalValueAt(0,3))) fail("derived copy changed source");
		System.out.println("R-group GUI/task: 2 matching rows, structures and Ki copied; source unchanged by derived edits");
		java.nio.file.Path word = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "pyxis-visible-table-"+System.nanoTime()+".docx");
		SwingUtilities.invokeAndWait(() -> {
			com.actelion.research.datawarrior.pyxis.DETaskExportWord wordTask=new com.actelion.research.datawarrior.pyxis.DETaskExportWord(input);
			JComponent content=wordTask.createDialogContent(); java.util.Properties path=new java.util.Properties(); path.setProperty("fileName",word.toString()); wordTask.setDialogConfiguration(path); renderPanel(input,content,"word");
			input.getMainFrame().getMainPane().getTableView().setColumnVisibility(table.findColumn("R2"), false);
			int flag=table.getUnusedRowFlag(true);
			table.getTotalRecord(1).setFlag(flag);
			table.updateExternalExclusion(flag,false,true);
			});
		com.actelion.research.datawarrior.pyxis.DETaskExportWord.export(input,word);
		javax.xml.parsers.DocumentBuilderFactory factory=javax.xml.parsers.DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
		try (java.util.zip.ZipFile zip=new java.util.zip.ZipFile(word.toFile())) {
			var document=factory.newDocumentBuilder().parse(zip.getInputStream(zip.getEntry("word/document.xml")));
			String ns="http://schemas.openxmlformats.org/wordprocessingml/2006/main";
			int rowCount=document.getElementsByTagNameNS(ns,"tr").getLength();
			int cells=document.getElementsByTagNameNS(ns,"tc").getLength();
			long images=zip.stream().filter(e -> e.getName().endsWith(".png")).count();
			if (rowCount!=3 || cells!=12 || images!=6 || document.getDocumentElement().getTextContent().contains("R2")) fail("visible Word snapshot: rows="+rowCount+" cells="+cells+" images="+images);
		}
		System.out.println("Visible Word snapshot: 2 filtered rows x 4 displayed columns, 6 PNG structures; "+word);
		com.actelion.research.datawarrior.pyxis.DETaskImportCddVault cdd = new com.actelion.research.datawarrior.pyxis.DETaskImportCddVault(input);
		java.util.Properties cddConfig = new java.util.Properties(); cddConfig.setProperty("vault","23"); cddConfig.setProperty("molecules","1,2");
		SwingUtilities.invokeAndWait(() -> { JComponent content=cdd.createDialogContent(); cdd.setDialogConfiguration(cddConfig); renderPanel(input,content,"cdd"); if (cdd.getDialogConfiguration().size()!=2 || !"1,2".equals(cdd.getDialogConfiguration().getProperty("molecules"))) throw new AssertionError("CDD config or token persistence"); });
		DEFrame cddFrame=DataWarrior.getApplication().getEmptyFrame("CDD offline mapping fixture");
		com.actelion.research.datawarrior.pyxis.DETaskImportCddVault.populate(cddFrame.getTableModel(),java.util.List.of(new com.actelion.research.datawarrior.pyxis.CddVaultClient.Molecule("1","Fixture",a,"","{\"Ki\":2.5}")),"23");
		CompoundTableModel cddTable=cddFrame.getTableModel();
		if (cddTable.getTotalRowCount()!=1 || !cddTable.isColumnTypeStructure(0) || !"23".equals(cddTable.getTotalValueAt(0,cddTable.findColumn("CDD Vault ID")))) fail("CDD built-app mapping");
		System.out.println("CDD GUI config excludes token; offline mapping creates one molecule with vault provenance (live vault untested)");
		SwingUtilities.invokeAndWait(() -> {
			ScaffoldHierarchy hierarchy=new ScaffoldHierarchy();
			com.actelion.research.table.model.CompoundRecord[] records=new com.actelion.research.table.model.CompoundRecord[3];
			for (int r=0;r<3;r++) { records[r]=table.getTotalRecord(r); hierarchy.addRow(r,ScaffoldHierarchy.murckoScaffoldIDCode(table.getChemicalStructure(records[r],4,0,null))); }
			hierarchy.build(1);
			String list=table.getListHandler().createList("Only last row");
			records[2].setFlag(table.getListHandler().getListFlagNo(list));
			ScaffoldTreeDialog stats=new ScaffoldTreeDialog(input,hierarchy,records,"Structure",1,0);
			stats.setVisible(true);
			List<Component> controls=new ArrayList<>(); collect(stats.getContentPane(),controls);
			JComboBox<String> subset=null;
			for (Component control:controls) if (control instanceof JComboBox) {
				JComboBox<String> combo=(JComboBox<String>)control;
				for (int i=0;i<combo.getItemCount();i++) {
					if ("Ki".equals(combo.getItemAt(i))) combo.setSelectedItem("Ki");
					if ("All rows".equals(combo.getItemAt(i))) subset=combo;
				}
				}
			if (subset==null) throw new AssertionError("subset selector");
			checkStatsExport(stats,controls,2,4);
			int exclude=table.getUnusedRowFlag(true); records[2].setFlag(exclude); table.updateExternalExclusion(exclude,false,true);
			subset.setSelectedItem("Visible rows only"); checkStatsExport(stats,controls,1,2);
			subset.setSelectedItem("List: "+list); checkStatsExport(stats,controls,1,6);
			try { BufferedImage image=new BufferedImage(stats.getWidth(),stats.getHeight(),BufferedImage.TYPE_INT_RGB); Graphics2D g=image.createGraphics(); stats.paint(g); g.dispose(); ImageIO.write(image,"png",new File(picturePrefix+"-statistics.png")); } catch(Exception e) { throw new RuntimeException(e); }
			stats.dispose();
		});
		System.out.println("Statistics GUI snapshots: all rows count 2/mean 4/median 4; visible count 1/value 2; row-list count 1/value 6");
        CompoundTableModel pairOutput=output, rOutput=derived;
        final List<Component> linkedControls=new ArrayList<>();
        final ScaffoldTreeDialog[] linkedTreeRef={null};
        SwingUtilities.invokeAndWait(() -> {
            // Drive a real scaffold tree selection, rather than calling just the filter helper.
            ScaffoldHierarchy linkedHierarchy=new ScaffoldHierarchy();
            com.actelion.research.table.model.CompoundRecord[] linkedRecords=new com.actelion.research.table.model.CompoundRecord[3];
            for(int r=0;r<3;r++) { linkedRecords[r]=table.getTotalRecord(r); linkedHierarchy.addRow(r,table.getTotalValueAt(r,0)); }
            linkedHierarchy.build(1);
            ScaffoldTreeDialog linkedTree=new ScaffoldTreeDialog(input,linkedHierarchy,linkedRecords,"Structure",1,0);
            linkedTree.setVisible(true);
            collect(linkedTree.getContentPane(),linkedControls);
            boolean found=false;
            for(Component control:linkedControls) if(control instanceof JTree) {
                JTree tree=(JTree)control;
                for(int r=0;r<tree.getRowCount();r++) {
                    ScaffoldHierarchy.Node n=(ScaffoldHierarchy.Node)((DefaultMutableTreeNode)tree.getPathForRow(r).getLastPathComponent()).getUserObject();
                    if(b.equals(n.getIDCode())) { tree.setSelectionRow(r); found=true; }
                }
            }
            linkedTreeRef[0]=linkedTree;
            if(!found) throw new AssertionError("pyridine scaffold missing from the linked tree: "+treeSignature(linkedControls));
        });
        // Row-visibility events are fired deferred by CompoundTableModel.updateVisibleRecords();
        // flush the EDT queue deterministically before asserting, so the check does not depend
        // on event timing across platforms.
        if(!waitForVisibleRows(rOutput,0)) throw new AssertionError("tree selection did not filter R-group table: visible="+rOutput.getRowCount()+" total="+rOutput.getTotalRowCount());
        SwingUtilities.invokeAndWait(() -> { for(Component control:linkedControls) if(control instanceof JTree) ((JTree)control).clearSelection(); });
        if(!waitForVisibleRows(rOutput,2)) throw new AssertionError("tree clear did not restore rows: visible="+rOutput.getRowCount()+" total="+rOutput.getTotalRowCount());
        SwingUtilities.invokeAndWait(() -> linkedTreeRef[0].dispose());
        SwingUtilities.invokeAndWait(() -> {
            // Select after source/target sorts. Original records, including duplicate structures, stay distinct.
            com.actelion.research.table.model.CompoundRecord[] originals={table.getTotalRecord(0),table.getTotalRecord(1),table.getTotalRecord(2)};
            table.sort(3,true,false); rOutput.sort(rOutput.findColumn("Ki"),true,false);
            int chosenIndex="1".equals(rOutput.getTotalValueAt(0,rOutput.findColumn("Source Row ID"))) ? 0 : 1;
            com.actelion.research.table.model.CompoundRecord chosen=rOutput.getTotalRecord(chosenIndex);
            com.actelion.research.table.model.CompoundRecord expected=originals[0];
            chosen.setSelection(true); rOutput.invalidateSelectionModel();
            if (countSelected(table)!=1 || !expected.isSelected()) throw new AssertionError("R-group stable selection after sorting");
            chosen.setSelection(false); rOutput.invalidateSelectionModel();
            pairOutput.getTotalRecord(0).setSelection(true); pairOutput.invalidateSelectionModel();
            if (countSelected(table)!=2) throw new AssertionError("pair selection must select both source rows");
            java.util.Set<com.actelion.research.table.model.CompoundRecord> members=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            members.add(table.getTotalRecord(0));
            com.actelion.research.datawarrior.pyxis.SourceRowLink.filter(input,members);
            if (rOutput.getRowCount()!=1) throw new AssertionError("scaffold family filter");
            com.actelion.research.datawarrior.pyxis.SourceRowLink.filter(input,null);
            if (rOutput.getRowCount()!=2) throw new AssertionError("clear scaffold family filter");
            // Delete one selected source record. A later selection must not shift onto a neighbour.
            for (int r=0;r<table.getTotalRowCount();r++) table.getTotalRecord(r).setSelection(false);
            expected.setSelection(true); table.removeSelected();
            chosen.setSelection(true); rOutput.invalidateSelectionModel();
            if (countSelected(table)!=0) throw new AssertionError("deleted record redirected selection");
            try {
                for (DEFrame frame:DataWarrior.getApplication().getFrameList()) if (frame.getTableModel()==rOutput) {
                    BufferedImage image=new BufferedImage(frame.getWidth(),frame.getHeight(),BufferedImage.TYPE_INT_RGB); Graphics2D g=image.createGraphics(); frame.paint(g); g.dispose(); ImageIO.write(image,"png",new File(picturePrefix+"-linked-table.png"));
                }
            } catch(Exception e) { throw new RuntimeException(e); }
            input.dispose();
            pairOutput.getTotalRecord(0).setSelection(false); pairOutput.invalidateSelectionModel();
            if (rOutput.getRowCount()!=2) throw new AssertionError("source close left exclusion");
        });
        System.out.println("Source links GUI: sorted duplicate molecules remain distinct; pair selects both originals; family filter/clear; deleted source ignored; source close detaches");



		}

	private static void checkStatsExport(ScaffoldTreeDialog dialog,List<Component> controls,int count,double mean) {
		for (Component control:controls) if (control instanceof JButton && "Refresh".equals(((JButton)control).getText())) ((JButton)control).doClick();
		for (Component control:controls) if (control instanceof JButton && "Tree As New Table".equals(((JButton)control).getText())) ((JButton)control).doClick();
		CompoundTableModel result=null;
		for (DEFrame frame:DataWarrior.getApplication().getFrameList()) if (frame.getTitle().startsWith("Scaffold Tree of "+((DEFrame)dialog.getOwner()).getTitle())) result=frame.getTableModel();
		if (result==null || Double.parseDouble(result.getTotalValueAt(0,result.findColumn("Subset Count")))!=count || Double.parseDouble(result.getTotalValueAt(0,result.findColumn("Mean")))!=mean || Double.parseDouble(result.getTotalValueAt(0,result.findColumn("Median")))!=mean) throw new AssertionError("statistics export mismatch");
	}

	private static void renderPanel(DEFrame owner,JComponent content,String suffix) {
		try {
			JDialog window=new JDialog(owner,suffix,false); window.setContentPane(content); window.pack(); window.setVisible(true);
			BufferedImage image=new BufferedImage(window.getWidth(),window.getHeight(),BufferedImage.TYPE_INT_RGB);
			Graphics2D g=image.createGraphics(); window.paint(g); g.dispose();
			ImageIO.write(image,"png",new File(picturePrefix+"-"+suffix+".png")); window.dispose();
		} catch(Exception e) { throw new RuntimeException(e); }
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
