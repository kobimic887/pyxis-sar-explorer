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
import com.actelion.research.table.model.CompoundRecord;
import com.actelion.research.table.model.CompoundTableEvent;
import com.actelion.research.table.model.CompoundTableModel;
import info.clearthought.layout.TableLayout;

import javax.swing.*;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Properties;

/**
 * Creates a table with one row per substituent exchange (X replaced by Y), pooled over
 * all scaffolds and R-group positions, with the number of pairs and the change of a
 * chosen numerical value. Requires the Scaffold and R1, R2, ... columns created by
 * 'Decompose R-Groups'. Selecting a row selects the molecules behind it in the source.
 */
public class DETaskSummariseSubstituentSwaps extends ConfigurableTask {
	public static final String TASK_NAME = "Summarise Substituent Swaps";

	private static final String PROPERTY_SCAFFOLD_COLUMN = "scaffoldColumn";
	private static final String PROPERTY_VALUE_COLUMN = "valueColumn";
	private static final String PROPERTY_MIN_PAIRS = "minPairs";
	private static final int MAX_PAIRS = 200000;

	private final CompoundTableModel mTableModel;
	private JComboBox<String> mComboBoxScaffold,mComboBoxValue;
	private JTextField mTextFieldMinPairs;

	public DETaskSummariseSubstituentSwaps(DEFrame parent) {
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

	private boolean hasNumericalColumn() {
		for (int column=0; column<mTableModel.getTotalColumnCount(); column++)
			if (mTableModel.getColumnSpecialType(column) == null && mTableModel.isColumnTypeDouble(column))
				return true;
		return false;
		}

	@Override
	public boolean isConfigurable() {
		if (getRGroupColumns().length == 0) {
			showErrorMessage("No R-group columns (R1, R2, ...) found.\nRun 'Decompose R-Groups' from the Chemistry menu first.");
			return false;
			}
		if (!hasNumericalColumn()) {
			showErrorMessage("No numerical column found whose change could be summarised.");
			return false;
			}
		return true;
		}

	@Override
	public JComponent createDialogContent() {
		int gap = HiDPIHelper.scale(8);
		double[][] size = { {gap, TableLayout.PREFERRED, gap, TableLayout.PREFERRED, gap},
							{gap, TableLayout.PREFERRED, gap, TableLayout.PREFERRED, gap, TableLayout.PREFERRED, 2*gap, TableLayout.PREFERRED, gap} };
		JPanel content = new JPanel();
		content.setLayout(new TableLayout(size));

		int[] rGroupColumn = getRGroupColumns();
		mComboBoxScaffold = new JComboBox<>();
		mComboBoxValue = new JComboBox<>();
		for (int column=0; column<mTableModel.getTotalColumnCount(); column++) {
			String title = mTableModel.getColumnTitle(column);
			if (mTableModel.isColumnTypeStructure(column)) {
				boolean isRGroup = false;
				for (int c:rGroupColumn)
					isRGroup |= (c == column);
				if (!isRGroup)
					mComboBoxScaffold.addItem(title);
				}
			else if (mTableModel.getColumnSpecialType(column) == null && mTableModel.isColumnTypeDouble(column)) {
				mComboBoxValue.addItem(title);
				}
			}
		mComboBoxScaffold.setEditable(!isInteractive());
		mComboBoxValue.setEditable(!isInteractive());
		mTextFieldMinPairs = new JTextField(4);

		content.add(new JLabel("Scaffold column:"), "1,1");
		content.add(mComboBoxScaffold, "3,1");
		content.add(new JLabel("Value whose change is summarised:"), "1,3");
		content.add(mComboBoxValue, "3,3");
		content.add(new JLabel("Leave out exchanges seen in fewer pairs than:"), "1,5");
		content.add(mTextFieldMinPairs, "3,5");
		content.add(new JLabel("<html>One row per exchange of one substituent by another, pooled over all<br>"
				+ "scaffolds and R-group positions. Change is value(To) minus value(From).</html>"), "1,7,3,7");
		return content;
		}

	@Override
	public Properties getDialogConfiguration() {
		Properties configuration = new Properties();
		put(configuration, PROPERTY_SCAFFOLD_COLUMN, mComboBoxScaffold);
		put(configuration, PROPERTY_VALUE_COLUMN, mComboBoxValue);
		configuration.setProperty(PROPERTY_MIN_PAIRS, mTextFieldMinPairs.getText().trim());
		return configuration;
		}

	private void put(Properties configuration, String key, JComboBox<String> comboBox) {
		String item = (String)comboBox.getSelectedItem();
		if (item != null && !item.isEmpty()) {
			int column = mTableModel.findColumn(item);
			configuration.setProperty(key, column == -1 ? item : mTableModel.getColumnTitleNoAlias(column));
			}
		}

	@Override
	public void setDialogConfiguration(Properties configuration) {
		setDialogConfigurationToDefault();
		choose(mComboBoxScaffold, configuration.getProperty(PROPERTY_SCAFFOLD_COLUMN));
		choose(mComboBoxValue, configuration.getProperty(PROPERTY_VALUE_COLUMN));
		mTextFieldMinPairs.setText(configuration.getProperty(PROPERTY_MIN_PAIRS, "1"));
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
		int scaffoldColumn = mTableModel.findColumn("Scaffold");
		if (scaffoldColumn != -1 && mTableModel.isColumnTypeStructure(scaffoldColumn))
			mComboBoxScaffold.setSelectedItem(mTableModel.getColumnTitle(scaffoldColumn));
		else if (mComboBoxScaffold.getItemCount() != 0)
			mComboBoxScaffold.setSelectedIndex(0);
		if (mComboBoxValue.getItemCount() != 0)
			mComboBoxValue.setSelectedIndex(0);
		mTextFieldMinPairs.setText("1");
		}

	@Override
	public boolean isConfigurationValid(Properties configuration, boolean isLive) {
		String scaffold = configuration.getProperty(PROPERTY_SCAFFOLD_COLUMN, "");
		String value = configuration.getProperty(PROPERTY_VALUE_COLUMN, "");
		if (scaffold.isEmpty() || value.isEmpty()) {
			showErrorMessage("A scaffold column and a value column are needed.");
			return false;
			}
		try {
			if (Integer.parseInt(configuration.getProperty(PROPERTY_MIN_PAIRS, "1")) < 1) {
				showErrorMessage("The minimum number of pairs must be 1 or larger.");
				return false;
				}
			}
		catch (NumberFormatException nfe) {
			showErrorMessage("The minimum number of pairs is not a whole number.");
			return false;
			}
		if (isLive) {
			int column = mTableModel.findColumn(scaffold);
			if (column == -1 || !mTableModel.isColumnTypeStructure(column)) {
				showErrorMessage("Scaffold column '"+scaffold+"' not found or not a structure column.");
				return false;
				}
			int valueColumn = mTableModel.findColumn(value);
			if (valueColumn == -1 || !mTableModel.isColumnTypeDouble(valueColumn)) {
				showErrorMessage("Value column '"+value+"' not found or not numerical.");
				return false;
				}
			if (getRGroupColumns().length == 0) {
				showErrorMessage("No R-group columns (R1, R2, ...) found.");
				return false;
				}
			}
		return true;
		}

	@Override
	public void runTask(Properties configuration) {
		int scaffoldColumn = mTableModel.findColumn(configuration.getProperty(PROPERTY_SCAFFOLD_COLUMN));
		int valueColumn = mTableModel.findColumn(configuration.getProperty(PROPERTY_VALUE_COLUMN));
		int minPairs = Integer.parseInt(configuration.getProperty(PROPERTY_MIN_PAIRS, "1"));
		int[] rGroupColumn = getRGroupColumns();

		int rowCount = mTableModel.getTotalRowCount();
		startProgress("Reading R-groups...", 0, rowCount);
		String[] scaffold = new String[rowCount];
		String[][] substituent = new String[rowCount][rGroupColumn.length];
		double[] value = new double[rowCount];
		for (int row=0; row<rowCount; row++) {
			if (threadMustDie())
				return;
			scaffold[row] = mTableModel.getTotalValueAt(row, scaffoldColumn);
			for (int i=0; i<rGroupColumn.length; i++)
				substituent[row][i] = mTableModel.getTotalValueAt(row, rGroupColumn[i]);
			value[row] = mTableModel.getTotalOriginalDoubleAt(row, valueColumn);
			}

		startProgress("Finding pairs...", 0, 0);
		SubstituentPairs analysis = new SubstituentPairs(scaffold, substituent, MAX_PAIRS);
		if (threadMustDie())
			return;

		List<SwapSummary.Swap> swaps = new ArrayList<>();
		for (SwapSummary.Swap swap:new SwapSummary(analysis.getPairs(), scaffold, substituent, value).getSwaps())
			if (swap.pairs >= minPairs)
				swaps.add(swap);
		if (swaps.isEmpty()) {
			showErrorMessage(analysis.getPairs().isEmpty()
					? "No two molecules share a scaffold and differ at exactly one R-group."
					: "No substituent exchange is seen in at least "+minPairs+" pairs.");
			return;
			}

		String name = mTableModel.getColumnTitle(valueColumn);
		String[] title = { "From", "To", "Pairs", "Pairs With Values", "Scaffolds",
				name+" Mean Change", name+" Median Change", name+" Smallest Change", name+" Largest Change",
				name+" Mean Fold Change", "Increases", "Decreases" };

		DEFrame frame = getEmptyFrame("Substituent Swaps of "+getParentFrame().getTitle());
		CompoundTableModel target = frame.getTableModel();
		target.initializeTable(swaps.size(), title.length);
		for (int column=0; column<title.length; column++)
			target.setColumnName(title[column], column);
		target.setColumnProperty(0, CompoundTableModel.cColumnPropertySpecialType, CompoundTableModel.cColumnTypeIDCode);
		target.setColumnProperty(1, CompoundTableModel.cColumnPropertySpecialType, CompoundTableModel.cColumnTypeIDCode);

		IdentityHashMap<CompoundRecord,CompoundRecord[]> links = new IdentityHashMap<>();
		for (int i=0; i<swaps.size(); i++) {
			SwapSummary.Swap swap = swaps.get(i);
			target.setTotalValueAt(swap.from, i, 0);	// an empty structure cell stands for hydrogen
			target.setTotalValueAt(swap.to, i, 1);
			target.setTotalValueAt(Integer.toString(swap.pairs), i, 2);
			target.setTotalValueAt(Integer.toString(swap.valuedPairs), i, 3);
			target.setTotalValueAt(Integer.toString(swap.scaffolds), i, 4);
			target.setTotalValueAt(format(swap.meanChange), i, 5);
			target.setTotalValueAt(format(swap.medianChange), i, 6);
			target.setTotalValueAt(format(swap.minChange), i, 7);
			target.setTotalValueAt(format(swap.maxChange), i, 8);
			target.setTotalValueAt(format(swap.meanFoldChange), i, 9);
			target.setTotalValueAt(Integer.toString(swap.increases), i, 10);
			target.setTotalValueAt(Integer.toString(swap.decreases), i, 11);

			CompoundRecord[] source = new CompoundRecord[swap.rows.size()];
			int index = 0;
			for (int row:swap.rows)
				source[index++] = mTableModel.getTotalRecord(row);
			links.put(target.getTotalRecord(i), source);
			}

		target.finalizeTable(CompoundTableEvent.cSpecifierDefaultFiltersAndViews, getProgressController());
		SwingUtilities.invokeLater(() -> SourceRowLink.attach((DEFrame)getParentFrame(), frame, links, false));

		if (analysis.isTruncated())
			showInteractiveTaskMessage("More than "+MAX_PAIRS+" pairs exist. The summary covers only the first "+MAX_PAIRS+".",
					JOptionPane.WARNING_MESSAGE);
		}

	/** Missing values stay empty instead of becoming 'NaN' text. */
	private static String format(double value) {
		if (Double.isNaN(value) || Double.isInfinite(value))
			return "";
		return Float.toString((float)value);
		}
	}
