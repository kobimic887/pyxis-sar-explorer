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
        // One row per setting at its natural height. A plain grid would make every row as
        // tall as the scaffold picture, which is the only tall control here.
        JPanel panel=new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        String[] keys={"scaffoldColumn","scaffold","rowColumn","columnColumn","valueColumn","aggregate","order"};
        String[] labels={"Scaffold column","Scaffold","R-group for rows","R-group for columns","Value shown in the cells","Combine several molecules by","Order rows and columns by"};
        for(int i=0;i<keys.length;i++) { JComboBox<String> box=new JComboBox<>(); controls.put(keys[i],box);
            GridBagConstraints left=new GridBagConstraints(); left.gridx=0; left.gridy=i; left.anchor=GridBagConstraints.WEST; left.insets=new Insets(4,0,4,12);
            GridBagConstraints right=new GridBagConstraints(); right.gridx=1; right.gridy=i; right.fill=GridBagConstraints.HORIZONTAL; right.weightx=1; right.insets=new Insets(4,0,4,0);
            panel.add(new JLabel(labels[i]+":"),left);panel.add(box,right); box.setEditable(!isInteractive()); }
        for(int c=0;c<model.getTotalColumnCount();c++) { String name=model.getColumnTitleNoAlias(c); if(model.isColumnTypeStructure(c)) { controls.get("scaffoldColumn").addItem(name); if(name.matches("R[0-9]+")) { controls.get("rowColumn").addItem(name);controls.get("columnColumn").addItem(name); } } else if(model.isColumnTypeDouble(c)) controls.get("valueColumn").addItem(name); }
        for(var a:RGroupGrid.Aggregate.values()) controls.get("aggregate").addItem(a.name()); controls.get("order").addItem("frequency");controls.get("order").addItem("mean");
        controls.get("scaffoldColumn").addActionListener(e->fillScaffolds());
        controls.get("scaffold").setRenderer((list,value,index,selected,focus)->{ JStructureView v=new JStructureView();v.setIDCode(value);v.setPreferredSize(new Dimension(220,80));v.setOpaque(true);v.setBackground(Color.WHITE);v.setForeground(Color.BLACK);/* dark looks would draw pale lines on the white field */return v; });
        return panel;
    }
    private void fillScaffolds() { var box=controls.get("scaffold");box.removeAllItems(); int c=model.findColumn((String)controls.get("scaffoldColumn").getSelectedItem()); if(c<0)return; Map<String,Integer> counts=new TreeMap<>();for(int r=0;r<model.getTotalRowCount();r++){String s=model.getTotalValueAt(r,c);if(!s.isEmpty())counts.merge(s,1,Integer::sum);} counts.keySet().stream().sorted(Comparator.<String>comparingInt(counts::get).reversed().thenComparing(Comparator.naturalOrder())).forEach(box::addItem); }
    public Properties getDialogConfiguration() { Properties p=new Properties();controls.forEach((k,v)->p.setProperty(k,Objects.toString(v.getSelectedItem(),"")));return p; }
    public void setDialogConfigurationToDefault() { controls.get("scaffoldColumn").setSelectedItem("Scaffold");fillScaffolds();controls.get("rowColumn").setSelectedItem("R1");controls.get("columnColumn").setSelectedItem("R2");controls.get("aggregate").setSelectedItem("mean");controls.get("order").setSelectedItem("frequency"); }
    public void setDialogConfiguration(Properties p) { setDialogConfigurationToDefault(); for(String k:controls.keySet()) if(p.containsKey(k)) controls.get(k).setSelectedItem(p.getProperty(k)); }
    public boolean isConfigurationValid(Properties p,boolean live) {
        try { RGroupGrid.Aggregate.valueOf(p.getProperty("aggregate","mean")); }catch(Exception e){showErrorMessage("Unknown aggregation.");return false;}
        if(!List.of("frequency","mean").contains(p.getProperty("order","frequency"))) {showErrorMessage("Unknown ordering of the grid axes.");return false;}
        if(p.getProperty("rowColumn","").equals(p.getProperty("columnColumn",""))) {showErrorMessage("Choose two different R-group positions.");return false;}
        if(live) for(String k:List.of("scaffoldColumn","rowColumn","columnColumn","valueColumn")) { int c=model.findColumn(p.getProperty(k,"")); if(c<0 || (k.equals("valueColumn")?!model.isColumnTypeDouble(c):!model.isColumnTypeStructure(c)) || ((k.equals("rowColumn")||k.equals("columnColumn"))&&!model.getColumnTitleNoAlias(c).matches("R[0-9]+"))) {showErrorMessage("Invalid column: "+k);return false;} }
        return true;
    }
    public void runTask(Properties p) {
        int scaffold=model.findColumn(p.getProperty("scaffoldColumn")), row=model.findColumn(p.getProperty("rowColumn")),column=model.findColumn(p.getProperty("columnColumn")),value=model.findColumn(p.getProperty("valueColumn"));
        // Without a chosen scaffold (macros, first use) the most frequent one is taken;
        // ties are broken by the scaffold key so that the choice is repeatable.
        String chosen=p.getProperty("scaffold","");
        if(chosen.isEmpty()) {
            Map<String,Integer> counts=new TreeMap<>();
            for(int r=0;r<model.getTotalRowCount();r++){String s=model.getTotalValueAt(r,scaffold);if(!s.isEmpty())counts.merge(s,1,Integer::sum);}
            int best=0;
            for(Map.Entry<String,Integer> e:counts.entrySet()) if(e.getValue()>best){best=e.getValue();chosen=e.getKey();}
        }
        final String scaffoldKey=chosen;
        List<RGroupGrid.Input> input=new ArrayList<>();for(int r=0;r<model.getTotalRowCount();r++)if(scaffoldKey.equals(model.getTotalValueAt(r,scaffold))) input.add(new RGroupGrid.Input(model.getTotalValueAt(r,row),model.getTotalValueAt(r,column),model.getTotalOriginalDoubleAt(r,value),r));
        try { if(input.isEmpty())throw new IllegalArgumentException("No molecules have the chosen scaffold."); RGroupGrid grid=new RGroupGrid(input,RGroupGrid.Aggregate.valueOf(p.getProperty("aggregate","mean")),"mean".equals(p.getProperty("order"))); SwingUtilities.invokeLater(()->{ new RGroupGridDialog((DEFrame)getParentFrame(),grid,p.getProperty("rowColumn"),p.getProperty("columnColumn"),p.getProperty("aggregate","mean")+" "+p.getProperty("valueColumn")).setVisible(true);
                // Macro/packaging checks can tell a displayed grid from a skipped task.
                System.out.println("Pyxis R-group grid displayed: "+grid.rows.size()+" x "+grid.columns.size()); }); } catch(IllegalArgumentException e) {showErrorMessage(e.getMessage());}
    }
}
