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

import com.actelion.research.chem.StereoMolecule;
import com.actelion.research.datawarrior.DEFrame;
import com.actelion.research.datawarrior.task.ConfigurableTask;
import com.actelion.research.gui.hidpi.HiDPIHelper;
import com.actelion.research.table.model.CompoundRecord;
import com.actelion.research.table.model.CompoundTableModel;
import info.clearthought.layout.TableLayout;

import javax.swing.*;
import java.util.Properties;

/**
 * Builds a hierarchical scaffold tree of all structures and opens it in a window, where
 * choosing a scaffold selects the molecules of that scaffold family in the table.
 */
public class DETaskBuildScaffoldTree extends ConfigurableTask {
	public static final String TASK_NAME = "Build Scaffold Tree";

	private static final String PROPERTY_STRUCTURE_COLUMN = "structureColumn";
	private static final String PROPERTY_MIN_MEMBERS = "minMolecules";
	private static final int DEFAULT_MIN_MEMBERS = 2;

	private final DEFrame mParentFrame;
	private final CompoundTableModel mTableModel;
	private JComboBox<String> mComboBoxStructureColumn;
	private JTextField mTextFieldMinMembers;

	public DETaskBuildScaffoldTree(DEFrame parent) {
		super(parent, true);
		mParentFrame = parent;
		mTableModel = parent.getTableModel();
		}

	@Override
	public Properties getRecentConfiguration() {
		Object saved = mTableModel.getExtensionData(ScaffoldSession.NAME);
		return saved instanceof Properties ? (Properties)((Properties)saved).clone() : super.getRecentConfiguration();
		}

	@Override
	public String getTaskName() {
		return TASK_NAME;
		}

	@Override
	public boolean isConfigurable() {
		if (mTableModel.getSpecialColumnList(CompoundTableModel.cColumnTypeIDCode) == null) {
			showErrorMessage("No column with chemical structures found.");
			return false;
			}
		return true;
		}

	@Override
	public JComponent createDialogContent() {
		int gap = HiDPIHelper.scale(8);
		double[][] size = { {gap, TableLayout.PREFERRED, gap, TableLayout.PREFERRED, gap},
							{gap, TableLayout.PREFERRED, gap, TableLayout.PREFERRED, 2*gap, TableLayout.PREFERRED, gap} };
		JPanel content = new JPanel();
		content.setLayout(new TableLayout(size));

		mComboBoxStructureColumn = new JComboBox<>();
		int[] idcodeColumn = mTableModel.getSpecialColumnList(CompoundTableModel.cColumnTypeIDCode);
		if (idcodeColumn != null)
			for (int column:idcodeColumn)
				mComboBoxStructureColumn.addItem(mTableModel.getColumnTitle(column));
		mComboBoxStructureColumn.setEditable(!isInteractive());
		content.add(new JLabel("Structure column:"), "1,1");
		content.add(mComboBoxStructureColumn, "3,1");

		mTextFieldMinMembers = new JTextField(4);
		content.add(new JLabel("Hide scaffolds with fewer molecules than:"), "1,3");
		content.add(mTextFieldMinMembers, "3,3");

		content.add(new JLabel("<html>Scaffolds are Murcko scaffolds without stereo information.<br>"
				+ "A scaffold's parents are obtained by removing one ring at a time.</html>"), "1,5,3,5");
		return content;
		}

	@Override
	public Properties getDialogConfiguration() {
		Properties configuration = new Properties();
		Object column = mComboBoxStructureColumn.getSelectedItem();
		configuration.setProperty(PROPERTY_STRUCTURE_COLUMN, column == null ? "" : (String)column);
		configuration.setProperty(PROPERTY_MIN_MEMBERS, mTextFieldMinMembers.getText().trim());
		return configuration;
		}

	@Override
	public void setDialogConfiguration(Properties configuration) {
		String name = configuration.getProperty(PROPERTY_STRUCTURE_COLUMN, "");
		int column = mTableModel.findColumn(name);
		if (column != -1 && mTableModel.isColumnTypeStructure(column))
			mComboBoxStructureColumn.setSelectedItem(mTableModel.getColumnTitle(column));
		else if (!isInteractive())
			mComboBoxStructureColumn.setSelectedItem(name);
		else if (mComboBoxStructureColumn.getItemCount() != 0)
			mComboBoxStructureColumn.setSelectedIndex(0);
		mTextFieldMinMembers.setText(configuration.getProperty(PROPERTY_MIN_MEMBERS, Integer.toString(DEFAULT_MIN_MEMBERS)));
		}

	@Override
	public void setDialogConfigurationToDefault() {
		if (mComboBoxStructureColumn.getItemCount() != 0)
			mComboBoxStructureColumn.setSelectedIndex(0);
		else if (!isInteractive())
			mComboBoxStructureColumn.setSelectedItem("Structure");
		mTextFieldMinMembers.setText(Integer.toString(DEFAULT_MIN_MEMBERS));
		}

	@Override
	public boolean isConfigurationValid(Properties configuration, boolean isLive) {
		String name = configuration.getProperty(PROPERTY_STRUCTURE_COLUMN, "");
		if (name.isEmpty()) {
			showErrorMessage("No structure column defined.");
			return false;
			}
		try {
			if (Integer.parseInt(configuration.getProperty(PROPERTY_MIN_MEMBERS, Integer.toString(DEFAULT_MIN_MEMBERS))) < 1) {
				showErrorMessage("The minimum number of molecules must be 1 or larger.");
				return false;
				}
			}
		catch (NumberFormatException nfe) {
			showErrorMessage("The minimum number of molecules is not a whole number.");
			return false;
			}
		if (isLive) {
			int column = mTableModel.findColumn(name);
			if (column == -1) {
				showErrorMessage("Structure column '"+name+"' not found.");
				return false;
				}
			if (!mTableModel.isColumnTypeStructure(column)) {
				showErrorMessage("Column '"+name+"' does not contain chemical structures.");
				return false;
				}
			}
		return true;
		}

	@Override
	public void runTask(Properties configuration) {
		final int structureColumn = mTableModel.findColumn(configuration.getProperty(PROPERTY_STRUCTURE_COLUMN, ""));
		final int minMembers = Integer.parseInt(configuration.getProperty(PROPERTY_MIN_MEMBERS, Integer.toString(DEFAULT_MIN_MEMBERS)));

		int rowCount = mTableModel.getTotalRowCount();
		final CompoundRecord[] record = new CompoundRecord[rowCount];
		final ScaffoldHierarchy hierarchy = new ScaffoldHierarchy();
		StereoMolecule container = new StereoMolecule();
		int emptyCount = 0;

		startProgress("Finding scaffolds...", 0, rowCount);
		for (int row=0; row<rowCount; row++) {
			if (threadMustDie())
				return;
			if ((row % 16) == 15)
				updateProgress(row);

			record[row] = mTableModel.getTotalRecord(row);
			StereoMolecule mol = mTableModel.getChemicalStructure(record[row], structureColumn, CompoundTableModel.ATOM_COLOR_MODE_NONE, container);
			if (mol == null || mol.getAllAtoms() == 0) {
				emptyCount++;	// rows without a structure are neither scaffold nor ring-free molecules
				continue;
				}
			hierarchy.addRow(row, ScaffoldHierarchy.murckoScaffoldIDCode(mol));
			}

		startProgress("Arranging scaffold tree...", 0, 0);
		hierarchy.build(minMembers);

		if (threadMustDie())
			return;

		final int emptyRows = emptyCount;
		final String structureColumnName = mTableModel.getColumnTitle(structureColumn);
		SwingUtilities.invokeLater(() -> {
			new ScaffoldTreeDialog(mParentFrame, hierarchy, record, structureColumnName, minMembers, emptyRows).setVisible(true);
			// Macro/packaging checks can distinguish a displayed tree from a skipped task.
			System.out.println("Pyxis scaffold tree displayed: " + hierarchy.getNodesInDisplayOrder().size() + " scaffolds");
			});
		}
	}
