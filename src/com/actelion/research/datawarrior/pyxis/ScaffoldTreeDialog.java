/*
 * Pyxis SAR Explorer addition to DataWarrior.
 * Copyright (C) 2026 Pyxis Discovery.
 *
 * This file is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version. It is distributed WITHOUT ANY WARRANTY.
 * See the GNU General Public License for more details: http://www.gnu.org/licenses/.
 */

package com.actelion.research.datawarrior.pyxis;

import com.actelion.research.datawarrior.DEFrame;
import com.actelion.research.datawarrior.DataWarrior;
import com.actelion.research.datawarrior.pyxis.ScaffoldHierarchy.Node;
import com.actelion.research.gui.JStructureView;
import com.actelion.research.gui.hidpi.HiDPIHelper;
import com.actelion.research.table.model.CompoundRecord;
import com.actelion.research.table.model.CompoundTableEvent;
import com.actelion.research.table.model.CompoundTableModel;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.BitSet;
import java.util.List;

/**
 * Non-modal window showing the scaffold tree of one table. Double clicking a scaffold
 * selects all molecules of that scaffold family in the table the tree was built from.
 */
public class ScaffoldTreeDialog extends JDialog {
	private final DEFrame mParentFrame;
	private final int mMinMembers;
	private boolean mRestoring;
	private final String mStructureColumnName;
	private final java.util.Map<Node,String> mTemplates = new java.util.IdentityHashMap<>();
	private final ScaffoldHierarchy mHierarchy;
	private CompoundRecord[] mRecord;
	private final JTree mTree;
	private final JCheckBox mCheckBoxExactOnly;
	private final JLabel mLabelStatus;
	private final JComboBox<String> mValue = new JComboBox<>();
	private final JComboBox<String> mSubset = new JComboBox<>();
	private final java.util.Map<Node,double[]> mStatistics = new java.util.IdentityHashMap<>();

	public ScaffoldTreeDialog(DEFrame parent, ScaffoldHierarchy hierarchy, CompoundRecord[] record,
							  String structureColumnName, int minMembers, int emptyRows) {
		super(parent, "Scaffold Tree of '"+structureColumnName+"' - "+parent.getTitle(), false);
		mParentFrame = parent;
		mMinMembers = minMembers;
		mStructureColumnName = structureColumnName;
		mHierarchy = hierarchy;
		mRecord = record;

		DefaultMutableTreeNode root = new DefaultMutableTreeNode("Scaffolds");
		for (Node node:hierarchy.getRoots())
			root.add(createTreeNode(node));

		mTree = new JTree(new DefaultTreeModel(root));
		mTree.setRootVisible(false);
        mTree.addTreeSelectionListener(e -> {
            Node node=chosenNode();
            java.util.Set<CompoundRecord> members=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            if (node!=null) for (int r=node.getMembers().nextSetBit(0);r>=0;r=node.getMembers().nextSetBit(r+1)) members.add(mRecord[r]);
            SourceRowLink.filter(mParentFrame,node==null ? null : members);
        });
		mTree.setShowsRootHandles(true);
		mTree.setRowHeight(HiDPIHelper.scale(84));
		mTree.setCellRenderer(new ScaffoldRenderer());
		mTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		mTree.setToggleClickCount(0);	// double click selects molecules instead of expanding
		mTree.addMouseListener(new MouseAdapter() {
			private void popup(MouseEvent e) {
				if (!e.isPopupTrigger()) return;
				TreePath path = mTree.getPathForLocation(e.getX(), e.getY());
				if (path == null) return;
				mTree.setSelectionPath(path);
				JPopupMenu menu = new JPopupMenu();
				JMenuItem edit = new JMenuItem("Edit Scaffold...");
				edit.addActionListener(a -> editScaffold()); menu.add(edit);
				JMenuItem orient = new JMenuItem("Re-orient Family");
				orient.addActionListener(a -> orientScaffold()); menu.add(orient);
				menu.show(mTree, e.getX(), e.getY());
				}
			@Override public void mousePressed(MouseEvent e) { popup(e); }
			@Override public void mouseReleased(MouseEvent e) { popup(e); }
			@Override public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && mTree.getPathForLocation(e.getX(), e.getY()) != null)
					selectMoleculesOfChosenScaffold();
				}
			});

		mCheckBoxExactOnly = new JCheckBox("Only molecules with exactly this scaffold");

		JButton buttonSelect = new JButton("Select Molecules");
		buttonSelect.addActionListener(e -> selectMoleculesOfChosenScaffold());
		JButton buttonNoScaffold = new JButton("Select Ring-Free");
		buttonNoScaffold.setEnabled(!hierarchy.getRowsWithoutScaffold().isEmpty());
		buttonNoScaffold.addActionListener(e -> select(hierarchy.getRowsWithoutScaffold(), "ring-free"));
		JButton buttonExpand = new JButton("Expand All");
		buttonExpand.addActionListener(e -> { for (int i=0; i<mTree.getRowCount(); i++) mTree.expandRow(i); });
		JButton buttonTable = new JButton("Tree As New Table");
		buttonTable.addActionListener(e -> createTable());

		mValue.addItem("<count only>");
		CompoundTableModel source = parent.getTableModel();
		for (int c=0; c<source.getTotalColumnCount(); c++)
			if (source.isColumnTypeDouble(c)) mValue.addItem(source.getColumnTitle(c));
		mSubset.addItem("All rows");
		mSubset.addItem("Visible rows only");
		for (int i=0; i<source.getListHandler().getListCount(); i++)
			mSubset.addItem("List: "+source.getListHandler().getListName(i));
		JPanel statistics = new JPanel(new FlowLayout(FlowLayout.LEFT));
		statistics.add(new JLabel("Statistics:"));
		statistics.add(mValue);
		statistics.add(mSubset);
		JButton refresh = new JButton("Refresh");
		refresh.addActionListener(e -> refreshStatistics());
		statistics.add(refresh);

		int shown = hierarchy.getNodesInDisplayOrder().size();
		int withScaffold = hierarchy.getRowCount() - hierarchy.getRowsWithoutScaffold().cardinality();
		StringBuilder summary = new StringBuilder();
		summary.append(shown).append(" scaffolds from ").append(withScaffold).append(" molecules with rings");
		if (minMembers > 1)
			summary.append(" (scaffolds with fewer than ").append(minMembers).append(" molecules hidden)");
		summary.append(". ").append(hierarchy.getRowsWithoutScaffold().cardinality()).append(" ring-free molecules");
		if (emptyRows != 0)
			summary.append(", ").append(emptyRows).append(" rows without structure");
		summary.append('.');
		mLabelStatus = new JLabel("Double click a scaffold to select its molecules in the table.");

		int gap = HiDPIHelper.scale(8);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, gap, 0));
		buttons.add(buttonSelect);
		buttons.add(buttonNoScaffold);
		buttons.add(buttonExpand);
		buttons.add(buttonTable);

		JPanel south = new JPanel();
		south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
		south.setBorder(BorderFactory.createEmptyBorder(gap, gap, gap, gap));
		for (JComponent c:new JComponent[] { statistics, mCheckBoxExactOnly, buttons, mLabelStatus }) {
			c.setAlignmentX(Component.LEFT_ALIGNMENT);
			south.add(c);
			south.add(Box.createVerticalStrut(gap/2));
			}

		JLabel north = new JLabel("<html>"+summary+"</html>");
		north.setBorder(BorderFactory.createEmptyBorder(gap, gap, gap, gap));

		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(north, BorderLayout.NORTH);
		getContentPane().add(new JScrollPane(mTree), BorderLayout.CENTER);
		getContentPane().add(south, BorderLayout.SOUTH);

		// Restore only the matching structure column; memberships always use current records.
		Object saved = source.getExtensionData(ScaffoldSession.NAME);
		mRestoring = true;
		if (saved instanceof java.util.Properties && structureColumnName.equals(((java.util.Properties)saved).getProperty("structureColumn"))) {
			java.util.Properties p = (java.util.Properties)saved;
			for (String template:p.getProperty("userScaffolds", "").split("\\t"))
				if (!template.isEmpty()) addEditedScaffold(new com.actelion.research.chem.IDCodeParser().getCompactMolecule(template));
			selectAvailable(mValue, p.getProperty("statisticsColumn", "<count only>"));
			selectAvailable(mSubset, p.getProperty("subset", "All rows"));
			}
		mRestoring = false;
		refreshStatistics();
		mValue.addActionListener(e -> saveSession());
		mSubset.addActionListener(e -> saveSession());
		addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) { SourceRowLink.filter(mParentFrame, null); }
        });
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setSize(HiDPIHelper.scale(640), HiDPIHelper.scale(720));
		setLocationRelativeTo(parent);
		}

	private static void selectAvailable(JComboBox<String> box, String value) {
		for (int i=0; i<box.getItemCount(); i++) if (value.equals(box.getItemAt(i))) box.setSelectedIndex(i);
		}

	private void saveSession() {
		if (mRestoring) return;
		java.util.Properties p = new java.util.Properties();
		p.setProperty("structureColumn", mStructureColumnName);
		p.setProperty("minMolecules", Integer.toString(mMinMembers));
		p.setProperty("statisticsColumn", (String)mValue.getSelectedItem());
		p.setProperty("subset", (String)mSubset.getSelectedItem());
		java.util.List<String> templates = new java.util.ArrayList<>();
		for (Node n:mHierarchy.getNodesInDisplayOrder()) if (n.isUserDefined()) templates.add(mTemplates.get(n));
		p.setProperty("userScaffolds", String.join("\t", templates));
		CompoundTableModel model = mParentFrame.getTableModel();
		if (!p.equals(model.getExtensionData(ScaffoldSession.NAME))) model.setExtensionData(ScaffoldSession.NAME, p);
		}

	private Node chosenNode() {
		TreePath path = mTree.getSelectionPath();
		return path == null ? null : (Node)((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
		}

	private void editScaffold() {
		Node node = chosenNode();
		if (node == null) return;
		com.actelion.research.chem.StereoMolecule mol = new com.actelion.research.chem.IDCodeParser().getCompactMolecule(mTemplates.getOrDefault(node, node.getIDCode()));
		com.actelion.research.gui.editor.SwingEditorDialog editor = new com.actelion.research.gui.editor.SwingEditorDialog(mParentFrame, mol);
		editor.setTitle("Edit Scaffold (adds a user-defined node)");
		editor.setVisible(true);
		if (!editor.isCancelled() && editor.getStructure().getAllAtoms() != 0) addEditedScaffold(editor.getStructure());
		}

	/** Shared by the editor and the built-app check. New rows require rebuilding the tree. */
	public void addEditedScaffold(com.actelion.research.chem.StereoMolecule fragment) {
		CompoundTableModel model = mParentFrame.getTableModel();
		int column = model.findColumn(mStructureColumnName);
		java.util.Set<CompoundRecord> known = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		java.util.List<CompoundRecord> records = new java.util.ArrayList<>(java.util.Arrays.asList(mRecord));
		known.addAll(records);
		for (int r=0; r<model.getTotalRowCount(); r++) if (known.add(model.getTotalRecord(r))) records.add(model.getTotalRecord(r));
		mRecord = records.toArray(new CompoundRecord[0]);
		com.actelion.research.chem.StereoMolecule[] molecules = new com.actelion.research.chem.StereoMolecule[mRecord.length];
		java.util.Set<CompoundRecord> present = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (int r=0; r<model.getTotalRowCount(); r++) present.add(model.getTotalRecord(r));
		for (int r=0; r<mRecord.length; r++)
			if (present.contains(mRecord[r])) molecules[r] = model.getChemicalStructure(mRecord[r], column, 0, null);
		Node node = mHierarchy.addUserScaffold(fragment, molecules);
		fragment.setFragment(true);
		com.actelion.research.chem.Canonizer canon = new com.actelion.research.chem.Canonizer(fragment);
		mTemplates.put(node, canon.getIDCode()+" "+canon.getEncodedCoordinates());
		DefaultTreeModel tree = (DefaultTreeModel)mTree.getModel();
		tree.insertNodeInto(createTreeNode(node), (DefaultMutableTreeNode)tree.getRoot(), ((DefaultMutableTreeNode)tree.getRoot()).getChildCount());
		refreshStatistics();
		}

	private void orientScaffold() {
		Node node = chosenNode();
		if (node == null) return;
		CompoundTableModel model = mParentFrame.getTableModel();
		int column = model.findColumn(mStructureColumnName);
		StringBuilder members = new StringBuilder();
		for (int r=node.getMembers().nextSetBit(0); r>=0; r=node.getMembers().nextSetBit(r+1)) {
			Object code = mRecord[r].getData(column);
			if (code instanceof byte[]) members.append(new String((byte[])code)).append('\t');
			}
		java.util.Properties config = new java.util.Properties();
		config.setProperty("structureColumn", mStructureColumnName);
		config.setProperty("scaffolds", mTemplates.getOrDefault(node, node.getIDCode()));
		config.setProperty("automatic", "false");
		config.setProperty("members", members.toString());
		DETaskOrientScaffold task = new DETaskOrientScaffold(mParentFrame);
		task.setPredefinedConfiguration(config);
		task.defineAndRun();
		}

	private void refreshStatistics() {
		CompoundTableModel model = mParentFrame.getTableModel();
		java.util.IdentityHashMap<CompoundRecord,Integer> present = new java.util.IdentityHashMap<>();
		for (int r=0; r<model.getTotalRowCount(); r++) present.put(model.getTotalRecord(r), r);
		BitSet subset = new BitSet();
		double[] values = new double[mRecord.length];
		java.util.Arrays.fill(values, Double.NaN);
		int column = mValue.getSelectedIndex() == 0 ? -1 : model.findColumn((String)mValue.getSelectedItem());
		String choice = (String)mSubset.getSelectedItem();
		int list = choice.startsWith("List: ") ? model.getListHandler().getListIndex(choice.substring(6)) : -1;
		for (int r=0; r<mRecord.length; r++) {
			Integer current = present.get(mRecord[r]);
			if (current == null) continue;
			boolean include = choice.equals("All rows") || choice.equals("Visible rows only") && model.isVisible(mRecord[r])
				|| list >= 0 && mRecord[r].isFlagSet(model.getListHandler().getListFlagNo(list));
			if (include) subset.set(r);
			if (column >= 0) values[r] = model.getTotalOriginalDoubleAt(current, column);
			}
		for (Node node:mHierarchy.getNodesInDisplayOrder())
			mStatistics.put(node, ScaffoldHierarchy.statistics(node.getMembers(), subset, values));
		saveSession();
		mTree.repaint();
		mLabelStatus.setText("Snapshot: "+choice+"; "+mValue.getSelectedItem()+". Refresh after changing filters or data. Finite values only.");
		}

	private DefaultMutableTreeNode createTreeNode(Node node) {
		DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(node);
		for (Node child:node.getChildren())
			treeNode.add(createTreeNode(child));
		return treeNode;
		}

	private void selectMoleculesOfChosenScaffold() {
		TreePath path = mTree.getSelectionPath();
		if (path == null) {
			mLabelStatus.setText("Choose a scaffold first.");
			return;
			}
		Node node = (Node)((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
		boolean exact = mCheckBoxExactOnly.isSelected();
		select(exact ? node.getExactMembers() : node.getMembers(), exact ? "exact scaffold" : "scaffold family");
		}

	/**
	 * Replaces the table's row selection. Rows deleted since the tree was built are
	 * simply no longer part of the table and therefore not counted.
	 */
	private void select(BitSet rows, String what) {
		CompoundTableModel tableModel = mParentFrame.getTableModel();
		for (int row=0; row<tableModel.getTotalRowCount(); row++)
			tableModel.getTotalRecord(row).setSelection(false);

		BitSet stillInTable = new BitSet();
		java.util.IdentityHashMap<CompoundRecord,Boolean> present = new java.util.IdentityHashMap<>();
		for (int row=0; row<tableModel.getTotalRowCount(); row++)
			present.put(tableModel.getTotalRecord(row), Boolean.TRUE);

		for (int row=rows.nextSetBit(0); row>=0; row=rows.nextSetBit(row+1)) {
			if (present.containsKey(mRecord[row])) {
				mRecord[row].setSelection(true);
				stillInTable.set(row);
				}
			}
		tableModel.invalidateSelectionModel();

		int selected = stillInTable.cardinality();
		String text = "Selected "+selected+" molecule"+(selected == 1 ? "" : "s")+" ("+what+").";
		if (selected != rows.cardinality())
			text += " "+(rows.cardinality()-selected)+" were deleted from the table meanwhile.";
		mLabelStatus.setText(text);
		}

	/** Writes the displayed tree into a new document, so that it can be saved and exported. */
	private void createTable() {
		List<Node> nodes = mHierarchy.getNodesInDisplayOrder();
		String[] title = { "Scaffold", "Parent Scaffold", "Level", "Rings", "Molecules", "Molecules With Exactly This Scaffold", "Subset Count", "Finite Values", "Mean", "Median", "User Defined" };

		DEFrame frame = DataWarrior.getApplication().getEmptyFrame("Scaffold Tree of "+mParentFrame.getTitle());
		CompoundTableModel tableModel = frame.getTableModel();
		tableModel.initializeTable(nodes.size(), title.length);
		for (int column=0; column<title.length; column++)
			tableModel.setColumnName(title[column], column);
		tableModel.setColumnProperty(0, CompoundTableModel.cColumnPropertySpecialType, CompoundTableModel.cColumnTypeIDCode);
		tableModel.setColumnProperty(1, CompoundTableModel.cColumnPropertySpecialType, CompoundTableModel.cColumnTypeIDCode);

		for (int row=0; row<nodes.size(); row++) {
			Node node = nodes.get(row);
			tableModel.setTotalValueAt(node.getIDCode(), row, 0);
			tableModel.setTotalValueAt(node.getParent() == null ? "" : node.getParent().getIDCode(), row, 1);
			tableModel.setTotalValueAt(Integer.toString(node.getLevel()+1), row, 2);
			tableModel.setTotalValueAt(Integer.toString(node.getRingCount()), row, 3);
			tableModel.setTotalValueAt(Integer.toString(node.getMemberCount()), row, 4);
			tableModel.setTotalValueAt(Integer.toString(node.getExactMemberCount()), row, 5);
			tableModel.setTotalValueAt(node.isUserDefined() ? "yes" : "no", row, 10);
			double[] stats = mStatistics.get(node);
			if (stats != null)
				for (int i=0; i<4; i++) tableModel.setTotalValueAt(Double.isFinite(stats[i]) ? Double.toString(stats[i]) : "", row, 6+i);
			}
		tableModel.finalizeTable(CompoundTableEvent.cSpecifierDefaultFiltersAndViews, null);
		}

	/** Draws the scaffold structure with its molecule counts below. */
	private class ScaffoldRenderer extends JPanel implements TreeCellRenderer {
		private final JStructureView mStructureView = new JStructureView();
		private final JLabel mLabel = new JLabel();

		private ScaffoldRenderer() {
			setLayout(new BorderLayout());
			setOpaque(true);
			mStructureView.setOpaque(false);
			mStructureView.setEnabled(false);	// plain depiction: no drag, no popup
			mStructureView.setDisableBorder(true);
			mLabel.setHorizontalAlignment(SwingConstants.CENTER);
			mLabel.setFont(mLabel.getFont().deriveFont(mLabel.getFont().getSize2D()*0.9f));
			add(mStructureView, BorderLayout.CENTER);
			add(mLabel, BorderLayout.SOUTH);
			setPreferredSize(new Dimension(HiDPIHelper.scale(440), HiDPIHelper.scale(82)));
			}

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
													  boolean leaf, int row, boolean hasFocus) {
			Object object = ((DefaultMutableTreeNode)value).getUserObject();
			if (object instanceof Node) {
				Node node = (Node)object;
				mStructureView.setIDCode(node.getIDCode());
				String text = node.getMemberCount()+(node.getMemberCount() == 1 ? " molecule" : " molecules");
				if (node.getExactMemberCount() != node.getMemberCount())
					text += " ("+node.getExactMemberCount()+" exact)";
				double[] stats = mStatistics.get(node);
				if (stats != null) text += " | subset "+(int)stats[0]+(stats[1] == 0 ? "" : "; mean "+stats[2]+"; median "+stats[3]);
				if (node.isUserDefined()) text = "User-defined: "+text;
				mLabel.setText(text);
				}
			// The structure view paints its own background, so the chosen scaffold is framed
			// instead of filled to stay readable in light and dark look and feels.
			Color highlight = UIManager.getColor("Tree.selectionBackground");
			int width = HiDPIHelper.scale(2);
			setBackground(tree.getBackground());
			mLabel.setForeground(tree.getForeground());
			setBorder(selected ? BorderFactory.createLineBorder(highlight != null ? highlight : tree.getForeground(), width)
							   : BorderFactory.createEmptyBorder(width, width, width, width));
			return this;
			}
		}
	}
