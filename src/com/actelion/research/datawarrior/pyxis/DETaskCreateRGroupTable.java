// Pyxis addition, GPLv3 or later. A derived snapshot of an existing R-group decomposition.
package com.actelion.research.datawarrior.pyxis;

import com.actelion.research.datawarrior.DEFrame;
import com.actelion.research.datawarrior.task.ConfigurableTask;
import com.actelion.research.table.model.*;
import com.actelion.research.gui.JStructureView;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class DETaskCreateRGroupTable extends ConfigurableTask {
    public static final String TASK_NAME = "Create R-Group Table";
    private final CompoundTableModel source;
    private JComboBox<String> scaffoldColumn, moleculeColumn, scaffold, id;
    private JList<String> values;
    public DETaskCreateRGroupTable(DEFrame frame) { super(frame, true); source = frame.getTableModel(); }
    @Override public String getTaskName() { return TASK_NAME; }
    @Override public boolean isConfigurable() {
        if (rColumns().isEmpty()) { showErrorMessage("Run Decompose R-Groups first (R1, R2, ... columns required)."); return false; }
        return true;
    }
    private List<Integer> rColumns() {
        List<Integer> result = new ArrayList<>();
        for (int c=0; c<source.getTotalColumnCount(); c++)
            if (source.isColumnTypeStructure(c) && source.getColumnTitleNoAlias(c).matches("R[0-9]+")) result.add(c);
        result.sort(Comparator.comparingInt(c -> Integer.parseInt(source.getColumnTitleNoAlias(c).substring(1))));
        return result;
    }
    private void fillScaffolds() {
        scaffold.removeAllItems();
        int c = source.findColumn((String)scaffoldColumn.getSelectedItem());
        if (c < 0) return;
        Set<String> codes = new TreeSet<>();
        for (int r=0; r<source.getTotalRowCount(); r++) { String code=source.getTotalValueAt(r,c); if (!code.isEmpty()) codes.add(code); }
        for (String code:codes) scaffold.addItem(code);
    }
    @Override public JComponent createDialogContent() {
        JPanel panel = new JPanel(new GridLayout(0,2,8,8));
        scaffoldColumn = new JComboBox<>(); moleculeColumn = new JComboBox<>(); scaffold = new JComboBox<>(); id = new JComboBox<>();
        id.addItem("<row number>");
        List<String> names = new ArrayList<>();
        for (int c=0; c<source.getTotalColumnCount(); c++) {
            String title = source.getColumnTitleNoAlias(c);
            if (source.isColumnTypeStructure(c)) { scaffoldColumn.addItem(title); moleculeColumn.addItem(title); }
            else if (source.getColumnSpecialType(c) == null) { id.addItem(title); names.add(title); }
        }
        scaffold.setRenderer((list,value,index,selected,focus) -> {
            JStructureView view = new JStructureView(); view.setIDCode(value); view.setPreferredSize(new Dimension(220,80)); return view;
        });
        scaffold.setEditable(!isInteractive()); scaffoldColumn.setEditable(!isInteractive()); moleculeColumn.setEditable(!isInteractive()); id.setEditable(!isInteractive());
        scaffoldColumn.addActionListener(e -> fillScaffolds());
        values = new JList<>(names.toArray(new String[0]));
        panel.add(new JLabel("Decomposed scaffold column:")); panel.add(scaffoldColumn);
        panel.add(new JLabel("Choose one scaffold:")); panel.add(scaffold);
        panel.add(new JLabel("Molecule column:")); panel.add(moleculeColumn);
        panel.add(new JLabel("ID column:")); panel.add(id);
        panel.add(new JLabel("Value columns (multiple selection):")); panel.add(new JScrollPane(values));
        panel.add(new JLabel("Derived copy; edits never update the source."));
        return panel;
    }
    @Override public Properties getDialogConfiguration() {
        Properties p = new Properties();
        p.setProperty("scaffoldColumn", Objects.toString(scaffoldColumn.getSelectedItem(),""));
        p.setProperty("moleculeColumn", Objects.toString(moleculeColumn.getSelectedItem(),""));
        p.setProperty("scaffold", Objects.toString(scaffold.getSelectedItem(),""));
        p.setProperty("idColumn", Objects.toString(id.getSelectedItem(),"<row number>"));
        p.setProperty("valueColumns", String.join("\t",values.getSelectedValuesList()));
        return p;
    }
    @Override public void setDialogConfigurationToDefault() {
        if (source.findColumn("Scaffold")>=0) scaffoldColumn.setSelectedItem("Scaffold");
        if (source.findColumn("Structure")>=0) moleculeColumn.setSelectedItem("Structure");
        fillScaffolds(); id.setSelectedIndex(0); values.clearSelection();
    }
    @Override public void setDialogConfiguration(Properties p) {
        setDialogConfigurationToDefault();
        scaffoldColumn.setSelectedItem(p.getProperty("scaffoldColumn","Scaffold"));
        moleculeColumn.setSelectedItem(p.getProperty("moleculeColumn","Structure"));
        scaffold.setSelectedItem(p.getProperty("scaffold","")); id.setSelectedItem(p.getProperty("idColumn","<row number>"));
        Set<String> chosen = new HashSet<>(Arrays.asList(p.getProperty("valueColumns", "").split("\t")));
        for (int i=0; i<values.getModel().getSize(); i++) if (chosen.contains(values.getModel().getElementAt(i))) values.addSelectionInterval(i,i);
    }
    @Override public boolean isConfigurationValid(Properties p, boolean live) {
        if (p.getProperty("scaffold", "").isEmpty()) { showErrorMessage("Choose a scaffold."); return false; }
        if (live) {
            for (String key:new String[]{"scaffoldColumn","moleculeColumn"}) {
                int c=source.findColumn(p.getProperty(key,""));
                if (c<0 || !source.isColumnTypeStructure(c)) { showErrorMessage("Missing structure column: "+key); return false; }
            }
            List<String> fields = new ArrayList<>(Arrays.asList(p.getProperty("valueColumns", "").split("\t")));
            if (!p.getProperty("idColumn", "<row number>").equals("<row number>")) fields.add(p.getProperty("idColumn"));
            for (String name:fields) if (!name.isEmpty() && source.findColumn(name)<0) { showErrorMessage("Missing column: "+name); return false; }
        }
        return true;
    }
    @Override public void runTask(Properties p) {
        int core=source.findColumn(p.getProperty("scaffoldColumn"));
        int molecule=source.findColumn(p.getProperty("moleculeColumn"));
        List<Integer> rows = new ArrayList<>();
        for (int r=0; r<source.getTotalRowCount(); r++) if (p.getProperty("scaffold").equals(source.getTotalValueAt(r,core))) rows.add(r);
        if (rows.isEmpty()) { showErrorMessage("No rows have that scaffold."); return; }
        List<Integer> columns = new ArrayList<>(); columns.add(core); columns.addAll(rColumns()); columns.add(molecule);
        int idColumn=source.findColumn(p.getProperty("idColumn","")); columns.add(idColumn);
        for (String name:p.getProperty("valueColumns", "").split("\t")) if (!name.isEmpty()) columns.add(source.findColumn(name));
        CompoundTableModel target=getEmptyFrame("R-Group Table (derived copy) of "+getParentFrame().getTitle()).getTableModel();
        target.initializeTable(rows.size(), columns.size());
        for (int c=0; c<columns.size(); c++) {
            int original=columns.get(c);
            target.setColumnName(original<0 ? "Source Row ID" : source.getColumnTitle(original),c);
            if (original>=0 && source.isColumnTypeStructure(original)) target.setColumnProperty(c,CompoundTableModel.cColumnPropertySpecialType,CompoundTableModel.cColumnTypeIDCode);
            for (int r=0; r<rows.size(); r++) target.setTotalValueAt(original<0 ? Integer.toString(rows.get(r)+1) : source.getTotalValueAt(rows.get(r),original),r,c);
        }
        target.finalizeTable(CompoundTableEvent.cSpecifierDefaultFiltersAndViews,getProgressController());
    }
}
