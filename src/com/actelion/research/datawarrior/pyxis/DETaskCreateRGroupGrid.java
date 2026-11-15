// Pyxis addition, GPLv3 or later.
package com.actelion.research.datawarrior.pyxis;
import com.actelion.research.datawarrior.*;
import com.actelion.research.datawarrior.task.ConfigurableTask;
import com.actelion.research.table.model.*;
import com.actelion.research.gui.JStructureView;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
public class DETaskCreateRGroupGrid extends ConfigurableTask {
    public static final String TASK_NAME="Create R-Group Grid";
    private final CompoundTableModel model;
    private final Map<String,JComboBox<String>> controls=new LinkedHashMap<>();
    public DETaskCreateRGroupGrid(DEFrame frame) { super(frame,true); model=frame.getTableModel(); }
    public String getTaskName() { return TASK_NAME; }
    public boolean isConfigurable() { if(model.findColumn("R1")<0||model.findColumn("R2")<0) { showErrorMessage("Run Decompose R-Groups first; two R-group positions are required."); return false; } return true; }
    public JComponent createDialogContent() {
        JPanel panel=new JPanel(new GridLayout(0,2,8,8));
        String[] keys={"scaffoldColumn","scaffold","rowColumn","columnColumn","valueColumn","aggregate","order"};
        String[] labels={"Scaffold column","Scaffold value","Row R-group","Column R-group","Numerical value","Combine molecules","Order axes"};
        for(int i=0;i<keys.length;i++) { JComboBox<String> box=new JComboBox<>(); controls.put(keys[i],box); panel.add(new JLabel(labels[i]));panel.add(box); box.setEditable(!isInteractive()); }
        for(int c=0;c<model.getTotalColumnCount();c++) { String name=model.getColumnTitleNoAlias(c); if(model.isColumnTypeStructure(c)) { controls.get("scaffoldColumn").addItem(name); if(name.matches("R[0-9]+")) { controls.get("rowColumn").addItem(name);controls.get("columnColumn").addItem(name); } } else if(model.isColumnTypeDouble(c)) controls.get("valueColumn").addItem(name); }
        for(var a:RGroupGrid.Aggregate.values()) controls.get("aggregate").addItem(a.name()); controls.get("order").addItem("frequency");controls.get("order").addItem("mean");
        controls.get("scaffoldColumn").addActionListener(e->fillScaffolds());
        controls.get("scaffold").setRenderer((list,value,index,selected,focus)->{ JStructureView v=new JStructureView();v.setIDCode(value);v.setPreferredSize(new Dimension(220,80));v.setOpaque(true);v.setBackground(Color.WHITE);return v; });
        return panel;
    }
    private void fillScaffolds() { var box=controls.get("scaffold");box.removeAllItems(); int c=model.findColumn((String)controls.get("scaffoldColumn").getSelectedItem()); if(c<0)return; Map<String,Integer> counts=new TreeMap<>();for(int r=0;r<model.getTotalRowCount();r++){String s=model.getTotalValueAt(r,c);if(!s.isEmpty())counts.merge(s,1,Integer::sum);} counts.keySet().stream().sorted(Comparator.<String>comparingInt(counts::get).reversed().thenComparing(Comparator.naturalOrder())).forEach(box::addItem); }
    public Properties getDialogConfiguration() { Properties p=new Properties();controls.forEach((k,v)->p.setProperty(k,Objects.toString(v.getSelectedItem(),"")));return p; }
    public void setDialogConfigurationToDefault() { controls.get("scaffoldColumn").setSelectedItem("Scaffold");fillScaffolds();controls.get("rowColumn").setSelectedItem("R1");controls.get("columnColumn").setSelectedItem("R2");controls.get("aggregate").setSelectedItem("mean");controls.get("order").setSelectedItem("frequency"); }
    public void setDialogConfiguration(Properties p) { setDialogConfigurationToDefault(); for(String k:controls.keySet()) if(p.containsKey(k)) controls.get(k).setSelectedItem(p.getProperty(k)); }
    public boolean isConfigurationValid(Properties p,boolean live) {
        try { RGroupGrid.Aggregate.valueOf(p.getProperty("aggregate","mean")); }catch(Exception e){showErrorMessage("Unknown aggregation.");return false;}
        if(!List.of("frequency","mean").contains(p.getProperty("order","frequency")) || p.getProperty("scaffold","").isEmpty()) {showErrorMessage("Choose a scaffold and a valid ordering.");return false;}
        if(p.getProperty("rowColumn","").equals(p.getProperty("columnColumn",""))) {showErrorMessage("Choose two different R-group positions.");return false;}
        if(live) for(String k:List.of("scaffoldColumn","rowColumn","columnColumn","valueColumn")) { int c=model.findColumn(p.getProperty(k,"")); if(c<0 || (k.equals("valueColumn")?!model.isColumnTypeDouble(c):!model.isColumnTypeStructure(c)) || ((k.equals("rowColumn")||k.equals("columnColumn"))&&!model.getColumnTitleNoAlias(c).matches("R[0-9]+"))) {showErrorMessage("Invalid column: "+k);return false;} }
        return true;
    }
    public void runTask(Properties p) {
        int scaffold=model.findColumn(p.getProperty("scaffoldColumn")), row=model.findColumn(p.getProperty("rowColumn")),column=model.findColumn(p.getProperty("columnColumn")),value=model.findColumn(p.getProperty("valueColumn"));
        List<RGroupGrid.Input> input=new ArrayList<>();for(int r=0;r<model.getTotalRowCount();r++)if(p.getProperty("scaffold").equals(model.getTotalValueAt(r,scaffold))) input.add(new RGroupGrid.Input(model.getTotalValueAt(r,row),model.getTotalValueAt(r,column),model.getTotalOriginalDoubleAt(r,value),r));
        try { if(input.isEmpty())throw new IllegalArgumentException("No molecules have the chosen scaffold."); RGroupGrid grid=new RGroupGrid(input,RGroupGrid.Aggregate.valueOf(p.getProperty("aggregate","mean")),"mean".equals(p.getProperty("order"))); SwingUtilities.invokeLater(()->new RGroupGridDialog((DEFrame)getParentFrame(),grid,p.getProperty("rowColumn"),p.getProperty("columnColumn"),p.getProperty("aggregate","mean")+" "+p.getProperty("valueColumn")).setVisible(true)); } catch(IllegalArgumentException e) {showErrorMessage(e.getMessage());}
    }
}
