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
import com.actelion.research.gui.hidpi.HiDPIHelper;
import com.actelion.research.table.model.CompoundTableEvent;
import com.actelion.research.table.model.CompoundTableModel;
import info.clearthought.layout.TableLayout;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Lists pairs of molecules that share a scaffold and differ at exactly one R-group
 * position, together with the change of a chosen numerical value. Requires the scaffold
 * and R-group columns created by 'Decompose R-Groups'.
 */
public class DETaskAnalyseSubstituentPairs extends ConfigurableTask {
	public static final String TASK_NAME = "Analyse Substituent Pairs";

	private static final String PROPERTY_SCAFFOLD_COLUMN = "scaffoldColumn";
	private static final String PROPERTY_VALUE_COLUMN = "valueColumn";
	private static final String PROPERTY_ID_COLUMN = "idColumn";
	private static final String ITEM_NONE = "<none>";
	private static final int MAX_PAIRS = 200000;

	private final CompoundTableModel mTableModel;
	private JComboBox<String> mMode;
	private JComboBox<String> mComboBoxScaffold,mComboBoxValue,mComboBoxID;

	public DETaskAnalyseSubstituentPairs(DEFrame parent) {
		super(parent, true);
		mTableModel = parent.getTableModel();
		}

	@Override
	public String getTaskName() {
		return TASK_NAME;
		}

	/** @return structure columns named R1, R2, ... in the order of their number */
	private int[] getRGroupColumns() {
		List<int[]> list = new ArrayList<>();
		for (int column=0; column<mTableModel.getTotalColumnCount(); column++) {
			String title = mTableModel.getColumnTitleNoAlias(column);
			if (mTableModel.isColumnTypeStructure(column) && title.matches("R[0-9]+"))
				list.add(new int[] { Integer.parseInt(title.substring(1)), column });
			}
		list.sort((a, b) -> Integer.compare(a[0], b[0]));
		int[] columns = new int[list.size()];
		for (int i=0; i<columns.length; i++)
			columns[i] = list.get(i)[1];
		return columns;
		}

	private boolean isRGroupColumn(int column) {
		for (int c:getRGroupColumns())
			if (c == column)
				return true;
		return false;
		}

	@Override
	public boolean isConfigurable() {
		if (getRGroupColumns().length == 0) {
			showErrorMessage("No R-group columns (R1, R2, ...) found.\nRun 'Decompose R-Groups' from the Chemistry menu first.");
			return false;
			}
		return true;
		}

	@Override
	public JComponent createDialogContent() {
		int gap = HiDPIHelper.scale(8);
		double[][] size = { {gap, TableLayout.PREFERRED, gap, TableLayout.PREFERRED, gap},
							{gap, TableLayout.PREFERRED, gap, TableLayout.PREFERRED, gap, TableLayout.PREFERRED, gap, TableLayout.PREFERRED, gap, TableLayout.PREFERRED, gap} };
		JPanel content = new JPanel();
		content.setLayout(new TableLayout(size));

		mComboBoxScaffold = new JComboBox<>();
		mComboBoxValue = new JComboBox<>();
		mComboBoxID = new JComboBox<>();
		mComboBoxValue.addItem(ITEM_NONE);
		mComboBoxID.addItem(ITEM_NONE);
		for (int column=0; column<mTableModel.getTotalColumnCount(); column++) {
			String title = mTableModel.getColumnTitle(column);
			if (mTableModel.isColumnTypeStructure(column)) {
				if (!isRGroupColumn(column))
					mComboBoxScaffold.addItem(title);
				}
			else if (mTableModel.getColumnSpecialType(column) == null) {
				if (mTableModel.isColumnTypeDouble(column))
					mComboBoxValue.addItem(title);
				mComboBoxID.addItem(title);
				}
			}
		for (JComboBox<String> comboBox:List.of(mComboBoxScaffold, mComboBoxValue, mComboBoxID))
			comboBox.setEditable(!isInteractive());

		content.add(new JLabel("Scaffold column:"), "1,1");
		content.add(mComboBoxScaffold, "3,1");
		content.add(new JLabel("Value to compare:"), "1,3");
		content.add(mComboBoxValue, "3,3");
		content.add(new JLabel("Molecule name or ID column:"), "1,5");
		content.add(mComboBoxID, "3,5");
		mMode = new JComboBox<>(new String[] {"Same scaffold, one changed R group", "Same substituents, different scaffold"});
		content.add(new JLabel("Pair mode:"), "1,7");
		content.add(mMode, "3,7");
		content.add(new JLabel("<html>Creates a new table with all pairs of molecules that have the same<br>"
				+ "scaffold and differ at exactly one of the R-group columns R1, R2, ...</html>"), "1,9,3,9");
		return content;
		}

	@Override
	public Properties getDialogConfiguration() {
		Properties configuration = new Properties();
		configuration.setProperty("mode", mMode.getSelectedIndex() == 1 ? "hopping" : "substituent");
		setIfChosen(configuration, PROPERTY_SCAFFOLD_COLUMN, mComboBoxScaffold);
		setIfChosen(configuration, PROPERTY_VALUE_COLUMN, mComboBoxValue);
		setIfChosen(configuration, PROPERTY_ID_COLUMN, mComboBoxID);
		return configuration;
		}

	private void setIfChosen(Properties configuration, String key, JComboBox<String> comboBox) {
		String item = (String)comboBox.getSelectedItem();
		if (item != null && !item.isEmpty() && !item.equals(ITEM_NONE)) {
			int column = mTableModel.findColumn(item);
			configuration.setProperty(key, column == -1 ? item : mTableModel.getColumnTitleNoAlias(column));
			}
		}

	@Override
	public void setDialogConfiguration(Properties configuration) {
		setDialogConfigurationToDefault();
		mMode.setSelectedIndex("hopping".equals(configuration.getProperty("mode")) ? 1 : 0);
		choose(mComboBoxScaffold, configuration.getProperty(PROPERTY_SCAFFOLD_COLUMN));
		choose(mComboBoxValue, configuration.getProperty(PROPERTY_VALUE_COLUMN));
		choose(mComboBoxID, configuration.getProperty(PROPERTY_ID_COLUMN));
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
		mMode.setSelectedIndex(0);
		int scaffoldColumn = mTableModel.findColumn("Scaffold");
		if (scaffoldColumn != -1 && mTableModel.isColumnTypeStructure(scaffoldColumn))
			mComboBoxScaffold.setSelectedItem(mTableModel.getColumnTitle(scaffoldColumn));
		else if (mComboBoxScaffold.getItemCount() != 0)
			mComboBoxScaffold.setSelectedIndex(0);
		else if (!isInteractive())
			mComboBoxScaffold.setSelectedItem("Scaffold");
		mComboBoxValue.setSelectedItem(ITEM_NONE);
		mComboBoxID.setSelectedItem(ITEM_NONE);
		}

	@Override
	public boolean isConfigurationValid(Properties configuration, boolean isLive) {
		String scaffold = configuration.getProperty(PROPERTY_SCAFFOLD_COLUMN, "");
		if (scaffold.isEmpty()) {
			showErrorMessage("No scaffold column defined.");
			return false;
			}
		if (isLive) {
			int column = mTableModel.findColumn(scaffold);
			if (column == -1 || !mTableModel.isColumnTypeStructure(column)) {
				showErrorMessage("Scaffold column '"+scaffold+"' not found or not a structure column.");
				return false;
				}
			if (getRGroupColumns().length == 0) {
				showErrorMessage("No R-group columns (R1, R2, ...) found.");
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
			String id = configuration.getProperty(PROPERTY_ID_COLUMN);
			if (id != null && mTableModel.findColumn(id) == -1) {
				showErrorMessage("Column '"+id+"' not found.");
				return false;
				}
			}
		return true;
		}

	@Override
	public void runTask(Properties configuration) {
		boolean hopping = "hopping".equals(configuration.getProperty("mode"));
		int scaffoldColumn = mTableModel.findColumn(configuration.getProperty(PROPERTY_SCAFFOLD_COLUMN));
		String valueName = configuration.getProperty(PROPERTY_VALUE_COLUMN);
		String idName = configuration.getProperty(PROPERTY_ID_COLUMN);
		int valueColumn = (valueName == null) ? -1 : mTableModel.findColumn(valueName);
		int idColumn = (idName == null) ? -1 : mTableModel.findColumn(idName);
		int[] rGroupColumn = getRGroupColumns();

		// the structure the scaffold was derived from, if known; otherwise the first other structure column
		int structureColumn = mTableModel.getParentColumn(scaffoldColumn);
		if (structureColumn == -1 || !mTableModel.isColumnTypeStructure(structureColumn)) {
			structureColumn = -1;
			for (int column=0; column<mTableModel.getTotalColumnCount() && structureColumn == -1; column++)
				if (mTableModel.isColumnTypeStructure(column) && column != scaffoldColumn && !isRGroupColumn(column))
					structureColumn = column;
			}

		int rowCount = mTableModel.getTotalRowCount();
		startProgress("Reading R-groups...", 0, rowCount);
		String[] scaffold = new String[rowCount];
		String[][] substituent = new String[rowCount][rGroupColumn.length];
		for (int row=0; row<rowCount; row++) {
			if (threadMustDie())
				return;
			scaffold[row] = mTableModel.getTotalValueAt(row, scaffoldColumn);
			for (int i=0; i<rGroupColumn.length; i++)
				substituent[row][i] = mTableModel.getTotalValueAt(row, rGroupColumn[i]);
			}

		startProgress("Finding pairs...", 0, 0);
		SubstituentPairs analysis = new SubstituentPairs(scaffold, substituent, MAX_PAIRS, hopping);
		List<SubstituentPairs.Pair> pairs = analysis.getPairs();
		if (threadMustDie())
			return;
		if (pairs.isEmpty()) {
			showErrorMessage("No pairs found for the selected mode.");
			return;
			}

		List<String> title = new ArrayList<>();
		int colStructureA = -1, colStructureB = -1, colIDA = -1, colIDB = -1, colValueA = -1;
		if (structureColumn != -1) {
			colStructureA = title.size();	title.add("Molecule A");
			colStructureB = title.size();	title.add("Molecule B");
			}
		int colScaffold = title.size();	title.add(hopping ? "Scaffold A" : "Scaffold");
		int colPosition = title.size();	title.add("Position");
		int colGroupA = title.size();	title.add(hopping ? "Scaffold B" : "Substituent A");
		int colGroupB = title.size();	title.add(hopping ? "Compared R positions" : "Substituent B");
		if (idColumn != -1) {
			colIDA = title.size();	title.add(mTableModel.getColumnTitle(idColumn)+" A");
			colIDB = title.size();	title.add(mTableModel.getColumnTitle(idColumn)+" B");
			}
		if (valueColumn != -1) {
			String name = mTableModel.getColumnTitle(valueColumn);
			colValueA = title.size();
			title.add(name+" A");
			title.add(name+" B");
			title.add(name+" Difference (B - A)");
			title.add(name+" Ratio (B / A)");
			}

		DEFrame frame = getEmptyFrame((hopping ? "Scaffold Hopping Pairs of " : "Substituent Pairs of ")+getParentFrame().getTitle());
		CompoundTableModel target = frame.getTableModel();
		target.initializeTable(pairs.size(), title.size());
		for (int column=0; column<title.size(); column++)
			target.setColumnName(title.get(column), column);
		for (int column:new int[] { colStructureA, colStructureB, colScaffold, colGroupA, colGroupB })
			if (column != -1 && !(hopping && column == colGroupB))
				target.setColumnProperty(column, CompoundTableModel.cColumnPropertySpecialType, CompoundTableModel.cColumnTypeIDCode);

		startProgress("Creating pair table...", 0, pairs.size());
		for (int i=0; i<pairs.size(); i++) {
			if ((i % 256) == 255)
				updateProgress(i);
			SubstituentPairs.Pair pair = pairs.get(i);
			if (structureColumn != -1) {
				target.setTotalValueAt(mTableModel.getTotalValueAt(pair.rowA, structureColumn), i, colStructureA);
				target.setTotalValueAt(mTableModel.getTotalValueAt(pair.rowB, structureColumn), i, colStructureB);
				}
			target.setTotalValueAt(scaffold[pair.rowA], i, colScaffold);
			target.setTotalValueAt(hopping ? "Scaffold" : mTableModel.getColumnTitleNoAlias(rGroupColumn[pair.position]), i, colPosition);
			target.setTotalValueAt((hopping ? scaffold[pair.rowB] : substituent[pair.rowA][pair.position]), i, colGroupA);
			target.setTotalValueAt((hopping ? Integer.toString(rGroupColumn.length) : substituent[pair.rowB][pair.position]), i, colGroupB);
			if (idColumn != -1) {
				target.setTotalValueAt(mTableModel.getTotalValueAt(pair.rowA, idColumn), i, colIDA);
				target.setTotalValueAt(mTableModel.getTotalValueAt(pair.rowB, idColumn), i, colIDB);
				}
			if (valueColumn != -1) {
				double a = mTableModel.getTotalOriginalDoubleAt(pair.rowA, valueColumn);
				double b = mTableModel.getTotalOriginalDoubleAt(pair.rowB, valueColumn);
				target.setTotalValueAt(format(a), i, colValueA);
				target.setTotalValueAt(format(b), i, colValueA+1);
				target.setTotalValueAt(format(b - a), i, colValueA+2);
				target.setTotalValueAt(a == 0.0 ? "" : format(b / a), i, colValueA+3);
				}
			}

		target.finalizeTable(CompoundTableEvent.cSpecifierDefaultFiltersAndViews, getProgressController());

		if (analysis.isTruncated())
			showInteractiveTaskMessage("More than "+MAX_PAIRS+" pairs exist. Only the first "+MAX_PAIRS+" are listed.\n"
					+ "Filter the molecules or decompose with a more specific scaffold to see all.", JOptionPane.WARNING_MESSAGE);
		}

	/** Missing values stay empty instead of becoming 'NaN' text. */
	private static String format(double value) {
		if (Double.isNaN(value) || Double.isInfinite(value))
			return "";
		return Float.toString((float)value);
		}
	}
