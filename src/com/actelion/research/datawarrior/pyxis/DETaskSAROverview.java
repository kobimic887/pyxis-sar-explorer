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
import com.actelion.research.datawarrior.task.ConfigurableTask;
import com.actelion.research.datawarrior.task.chem.DETaskDecomposeRGroups;
import com.actelion.research.gui.hidpi.HiDPIHelper;
import com.actelion.research.table.model.CompoundTableModel;
import info.clearthought.layout.TableLayout;

import javax.swing.*;
import java.util.Properties;

/**
 * Runs the usual first steps of an SAR analysis in one go on the open table:
 * R-group decomposition on the Murcko scaffold of every molecule (unless Scaffold and
 * R-group columns already exist), the scaffold tree with statistics of the chosen value,
 * and the substituent swap summary. Every step is one of the ordinary tasks, run with
 * the settings a user would choose first; nothing here computes anything of its own.
 */
public class DETaskSAROverview extends ConfigurableTask {
	public static final String TASK_NAME = "SAR Overview";

	private static final String PROPERTY_STRUCTURE_COLUMN = "structureColumn";
	private static final String PROPERTY_VALUE_COLUMN = "valueColumn";
	private static final String ITEM_NONE = "<none>";

	private final DEFrame mParentFrame;
	private final CompoundTableModel mTableModel;
	private JComboBox<String> mComboBoxStructure,mComboBoxValue;

	public DETaskSAROverview(DEFrame parent) {
		super(parent, true);
		mParentFrame = parent;
		mTableModel = parent.getTableModel();
		}

	@Override
	public String getTaskName() {
		return TASK_NAME;
		}

	private boolean isRGroupOrScaffold(int column) {
		String title = mTableModel.getColumnTitleNoAlias(column);
		return title.matches("R[0-9]+") || title.equals("Scaffold");
		}

	@Override
	public boolean isConfigurable() {
		for (int column=0; column<mTableModel.getTotalColumnCount(); column++)
			if (mTableModel.isColumnTypeStructure(column) && !isRGroupOrScaffold(column))
				return true;
		showErrorMessage("No column with chemical structures found.");
		return false;
		}

	@Override
	public JComponent createDialogContent() {
		int gap = HiDPIHelper.scale(8);
		double[][] size = { {gap, TableLayout.PREFERRED, gap, TableLayout.PREFERRED, gap},
							{gap, TableLayout.PREFERRED, gap, TableLayout.PREFERRED, 2*gap, TableLayout.PREFERRED, gap} };
		JPanel content = new JPanel();
		content.setLayout(new TableLayout(size));

		mComboBoxStructure = new JComboBox<>();
		mComboBoxValue = new JComboBox<>();
		mComboBoxValue.addItem(ITEM_NONE);
		for (int column=0; column<mTableModel.getTotalColumnCount(); column++) {
			if (mTableModel.isColumnTypeStructure(column)) {
				if (!isRGroupOrScaffold(column))
					mComboBoxStructure.addItem(mTableModel.getColumnTitle(column));
				}
			else if (mTableModel.getColumnSpecialType(column) == null && mTableModel.isColumnTypeDouble(column)) {
				mComboBoxValue.addItem(mTableModel.getColumnTitle(column));
				}
			}
		mComboBoxStructure.setEditable(!isInteractive());
		mComboBoxValue.setEditable(!isInteractive());

		content.add(new JLabel("Structure column:"), "1,1");
		content.add(mComboBoxStructure, "3,1");
		content.add(new JLabel("Activity or other value:"), "1,3");
		content.add(mComboBoxValue, "3,3");
		content.add(new JLabel("<html>Decomposes R-groups on the Murcko scaffolds (if not done yet), opens the<br>"
				+ "scaffold tree with statistics of the value, and summarises substituent swaps.</html>"), "1,5,3,5");
		return content;
		}

	@Override
	public Properties getDialogConfiguration() {
		Properties configuration = new Properties();
		put(configuration, PROPERTY_STRUCTURE_COLUMN, mComboBoxStructure);
		put(configuration, PROPERTY_VALUE_COLUMN, mComboBoxValue);
		return configuration;
		}

	private void put(Properties configuration, String key, JComboBox<String> comboBox) {
		String item = (String)comboBox.getSelectedItem();
		if (item != null && !item.isEmpty() && !item.equals(ITEM_NONE)) {
			int column = mTableModel.findColumn(item);
			configuration.setProperty(key, column == -1 ? item : mTableModel.getColumnTitleNoAlias(column));
			}
		}

	@Override
	public void setDialogConfiguration(Properties configuration) {
		setDialogConfigurationToDefault();
		choose(mComboBoxStructure, configuration.getProperty(PROPERTY_STRUCTURE_COLUMN));
		choose(mComboBoxValue, configuration.getProperty(PROPERTY_VALUE_COLUMN));
		}

	private void choose(JComboBox<String> comboBox, String columnName) {
		if (columnName == null)
			return;
		int column = mTableModel.findColumn(columnName);
		if (column != -1)
			comboBox.setSelectedItem(mTableModel.getColumnTitle(column));
		else if (!isInteractive())
			comboBox.setSelectedItem(columnName);
		}

	@Override
	public void setDialogConfigurationToDefault() {
		if (mComboBoxStructure.getItemCount() != 0)
			mComboBoxStructure.setSelectedIndex(0);
		else if (!isInteractive())
			mComboBoxStructure.setSelectedItem("Structure");
		mComboBoxValue.setSelectedItem(ITEM_NONE);
		}

	@Override
	public boolean isConfigurationValid(Properties configuration, boolean isLive) {
		String structure = configuration.getProperty(PROPERTY_STRUCTURE_COLUMN, "");
		if (structure.isEmpty()) {
			showErrorMessage("No structure column defined.");
			return false;
			}
		if (isLive) {
			int column = mTableModel.findColumn(structure);
			if (column == -1 || !mTableModel.isColumnTypeStructure(column)) {
				showErrorMessage("Structure column '"+structure+"' not found.");
				return false;
				}
			String value = configuration.getProperty(PROPERTY_VALUE_COLUMN);
			if (value != null) {
				int valueColumn = mTableModel.findColumn(value);
				if (valueColumn == -1 || !mTableModel.isColumnTypeDouble(valueColumn)) {
					showErrorMessage("Value column '"+value+"' not found or not numerical.");
					return false;
					}
				}
			}
		return true;
		}

	@Override
	public void runTask(Properties configuration) {
		String structure = configuration.getProperty(PROPERTY_STRUCTURE_COLUMN);
		String value = configuration.getProperty(PROPERTY_VALUE_COLUMN);

		// 1. R-groups, unless the table already carries a decomposition
		boolean hasRGroups = false;
		for (int column=0; column<mTableModel.getTotalColumnCount(); column++)
			hasRGroups |= mTableModel.isColumnTypeStructure(column) && mTableModel.getColumnTitleNoAlias(column).matches("R[0-9]+");
		int scaffoldColumn = mTableModel.findColumn("Scaffold");
		if (!hasRGroups || scaffoldColumn == -1 || !mTableModel.isColumnTypeStructure(scaffoldColumn)) {
			Properties decompose = new Properties();
			decompose.setProperty("structureColumn", structure);
			decompose.setProperty("scaffoldMode", "murcko");
			decompose.setProperty("useExistingColumns", "false");
			new DETaskDecomposeRGroups(mParentFrame).executeAsSubtask(decompose, getProgressController());
			}
		if (threadMustDie())
			return;

		// 2. scaffold tree; the tree window takes its statistics column from the project's
		//    saved tree settings, which are only written here when the project has none yet
		if (value != null && mTableModel.getExtensionData(ScaffoldSession.NAME) == null) {
			Properties session = new Properties();
			session.setProperty("structureColumn", structure);
			session.setProperty("minMolecules", "2");
			session.setProperty("statisticsColumn", mTableModel.getColumnTitle(mTableModel.findColumn(value)));
			session.setProperty("subset", "All rows");
			session.setProperty("userScaffolds", "");
			mTableModel.setExtensionData(ScaffoldSession.NAME, session);
			}
		Properties tree = new Properties();
		tree.setProperty("structureColumn", structure);
		tree.setProperty("minMolecules", "2");
		new DETaskBuildScaffoldTree(mParentFrame).executeAsSubtask(tree, getProgressController());
		if (threadMustDie())
			return;

		// 3. swap summary; quietly left out when there is nothing to summarise
		boolean nowHasRGroups = false;
		for (int column=0; column<mTableModel.getTotalColumnCount(); column++)
			nowHasRGroups |= mTableModel.isColumnTypeStructure(column) && mTableModel.getColumnTitleNoAlias(column).matches("R[0-9]+");
		if (value != null && nowHasRGroups && mTableModel.findColumn("Scaffold") != -1 && hasSingleSwap()) {
			Properties swaps = new Properties();
			swaps.setProperty("scaffoldColumn", "Scaffold");
			swaps.setProperty("valueColumn", value);
			swaps.setProperty("minPairs", "1");
			new DETaskSummariseSubstituentSwaps(mParentFrame).executeAsSubtask(swaps, getProgressController());
			SwingUtilities.invokeLater(() -> mParentFrame.toFront());	// keep the analysed table in front
			}
		}

	/** @return true if at least one pair of molecules differs at exactly one R-group */
	private boolean hasSingleSwap() {
		java.util.List<Integer> rColumns = new java.util.ArrayList<>();
		for (int column=0; column<mTableModel.getTotalColumnCount(); column++)
			if (mTableModel.isColumnTypeStructure(column) && mTableModel.getColumnTitleNoAlias(column).matches("R[0-9]+"))
				rColumns.add(column);
		int scaffoldColumn = mTableModel.findColumn("Scaffold");
		int rows = mTableModel.getTotalRowCount();
		String[] scaffold = new String[rows];
		String[][] substituent = new String[rows][rColumns.size()];
		for (int row=0; row<rows; row++) {
			scaffold[row] = mTableModel.getTotalValueAt(row, scaffoldColumn);
			for (int i=0; i<rColumns.size(); i++)
				substituent[row][i] = mTableModel.getTotalValueAt(row, rColumns.get(i));
			}
		return !new SubstituentPairs(scaffold, substituent, 1).getPairs().isEmpty();
		}
	}
