// Pyxis addition, GPLv3 or later. Snapshot values, identity-linked selection.
package com.actelion.research.datawarrior.pyxis;
import com.actelion.research.datawarrior.*;
import com.actelion.research.table.model.*;
import com.actelion.research.gui.JStructureView;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
public class RGroupGridDialog extends JDialog {
    public final RGroupGrid grid;
    public final JTable table;
    private final DEFrame source;
    private final CompoundRecord[] records;
    private final String rowName,columnName,valueName;
    private final CompoundTableModel selection=new CompoundTableModel();
    private final Map<RGroupGrid.Cell,CompoundRecord> selectionRows=new IdentityHashMap<>();
    public RGroupGridDialog(DEFrame source,RGroupGrid grid,String rowName,String columnName,String valueName) {
        super(source,"R-Group Grid — "+valueName,false); this.source=source; this.grid=grid; this.rowName=rowName; this.columnName=columnName; this.valueName=valueName;
        records=new CompoundRecord[source.getTableModel().getTotalRowCount()]; for(int i=0;i<records.length;i++) records[i]=source.getTableModel().getTotalRecord(i);
        table=new JTable(grid.rows.size(),grid.columns.size()+1) { public boolean isCellEditable(int r,int c) { return false; } };
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); table.setRowHeight(90); table.setCellSelectionEnabled(true);
        double lo=Double.POSITIVE_INFINITY, hi=Double.NEGATIVE_INFINITY; int count=0;
        for(int r=0;r<grid.rows.size();r++) for(int c=0;c<grid.columns.size();c++) { var cell=grid.cell(r,c); if(cell!=null) { count++; if(Double.isFinite(cell.value())) { lo=Math.min(lo,cell.value()); hi=Math.max(hi,cell.value()); } } }
        selection.initializeTable(count,1); IdentityHashMap<CompoundRecord,CompoundRecord[]> links=new IdentityHashMap<>(); int index=0;
        for(int r=0;r<grid.rows.size();r++) for(int c=0;c<grid.columns.size();c++) { var cell=grid.cell(r,c); if(cell!=null) { var record=selection.getTotalRecord(index++); selectionRows.put(cell,record); links.put(record,cell.sourceRows().stream().map(i->records[i]).toArray(CompoundRecord[]::new)); } }
        selection.finalizeTable(CompoundTableEvent.cSpecifierNoRuntimeProperties,null); // getTotalRowCount() and the selection bridge need a finalized table
        // Reuse the same stable-record selection bridge as derived tables; closing either window detaches it.
        SourceRowLink.attachSelection(source,this,selection,links);
        final double low=lo, high=hi;
        for(int c=0;c<table.getColumnCount();c++) { var col=table.getColumnModel().getColumn(c); col.setPreferredWidth(130); col.setHeaderValue(c==0?rowName+" / "+columnName:grid.columns.get(c-1)); }
        table.getTableHeader().setPreferredSize(new Dimension(130*table.getColumnCount(),90));
        table.getTableHeader().setDefaultRenderer((t,v,s,f,r,c)->c==0?new JLabel(rowName+" / "+columnName):structure(grid.columns.get(c-1)));
        table.setDefaultRenderer(Object.class,(t,v,s,f,r,c)-> {
            if(c==0) return structure(grid.rows.get(r));
            var cell=grid.cell(r,c-1); JLabel label=new JLabel(cell==null?"Empty":(Double.isFinite(cell.value())?ScaffoldTreeDialog.shortNumber(cell.value()):"No finite values")+"  (n="+cell.count()+")",SwingConstants.CENTER);
            label.setOpaque(true); label.setForeground(Color.BLACK);
            // Values that are all positive and span more than a factor of 100, as potencies do, are
            // coloured on a logarithmic scale; otherwise one large value would leave all others pale.
            boolean logarithmic=low>0 && high/low>100;
            double fraction=cell==null||!Double.isFinite(cell.value())?Double.NaN: high==low?0.5
                : logarithmic?(Math.log(cell.value())-Math.log(low))/(Math.log(high)-Math.log(low)):(cell.value()-low)/(high-low);
            label.setBackground(Double.isNaN(fraction)?new Color(235,235,235):new Color((int)(230-80*fraction),(int)(240-150*fraction),(int)(255-130*fraction)));
            label.setBorder(BorderFactory.createLineBorder(s?Color.BLACK:Color.WHITE,s?3:1)); return label;
        });
        table.addMouseListener(new MouseAdapter(){ public void mouseClicked(MouseEvent e) { int r=table.rowAtPoint(e.getPoint()),c=table.columnAtPoint(e.getPoint()); if(r>=0&&c>0) selectCell(r,c-1); } });
        JPanel buttons=new JPanel(); JButton image=new JButton("Export As Image"), asTable=new JButton("Grid As New Table"); buttons.add(image); buttons.add(asTable);
        image.addActionListener(e->{ JFileChooser chooser=new JFileChooser(); chooser.setSelectedFile(new File("r-group-grid.png")); if(chooser.showSaveDialog(this)==JFileChooser.APPROVE_OPTION) try { exportImage(chooser.getSelectedFile()); } catch(IOException ex) { JOptionPane.showMessageDialog(this,ex.getMessage()); } });
        asTable.addActionListener(e->exportTable());
        JPanel top=new JPanel(new BorderLayout()); top.add(new JLabel(valueName+"; low (pale) → high (dark); n includes missing values. Snapshot; links end on close."));
        add(top,BorderLayout.NORTH); add(new JScrollPane(table)); add(buttons,BorderLayout.SOUTH); setSize(900,600); setLocationRelativeTo(source);
    }
    public String rowName() { return rowName; }
    public String columnName() { return columnName; }
    public String valueName() { return valueName; }
    private static JComponent structure(String code) { if(code.isEmpty()) return new JLabel("H (hydrogen)",SwingConstants.CENTER); JStructureView view=new JStructureView(); view.setIDCode(code); view.setBackground(Color.WHITE); view.setForeground(Color.BLACK); // explicit foreground: image export paints via a detached CellRendererPane, so no parent supplies one
        view.setOpaque(true); return view; }
    public void selectCell(int r,int c) { for(int i=0;i<selection.getTotalRowCount();i++) selection.getTotalRecord(i).setSelection(selection.getTotalRecord(i)==selectionRows.get(grid.cell(r,c))); selection.invalidateSelectionModel(); }
    public void exportImage(File file) throws IOException {
        if(file.exists()) throw new IOException("File already exists.");
        int w=table.getColumnCount()*130,h=(table.getRowCount()+1)*90;
        if((long)w*h>25000000) throw new IOException("Image exceeds 25 million pixels. Export Grid As New Table instead.");
        BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB); Graphics2D g=image.createGraphics(); g.setColor(Color.WHITE);g.fillRect(0,0,w,h);
        // Paint renderers directly so offscreen/scrolled rows and headers are included.
        CellRendererPane pane=new CellRendererPane();
        for(int c=0;c<table.getColumnCount();c++) { pane.paintComponent(g,table.getTableHeader().getDefaultRenderer().getTableCellRendererComponent(table,null,false,false,-1,c),table,c*130,0,130,90,true); for(int r=0;r<table.getRowCount();r++) pane.paintComponent(g,table.prepareRenderer(table.getCellRenderer(r,c),r,c),table,c*130,(r+1)*90,130,90,true); }
        g.dispose(); ImageIO.write(image,"png",file);
    }
    public DEFrame exportTable() {
        DEFrame frame=DataWarrior.getApplication().getEmptyFrame("R-Group Grid cells (derived copy)"); CompoundTableModel target=frame.getTableModel(); target.initializeTable(selectionRows.size(),4);
        String[] names={rowName,columnName,valueName,"Molecule Count"}; for(int c=0;c<4;c++) target.setColumnName(names[c],c);
        for(int c=0;c<2;c++) target.setColumnProperty(c,CompoundTableModel.cColumnPropertySpecialType,CompoundTableModel.cColumnTypeIDCode);
        IdentityHashMap<CompoundRecord,CompoundRecord[]> links=new IdentityHashMap<>(); int i=0;
        for(int r=0;r<grid.rows.size();r++) for(int c=0;c<grid.columns.size();c++) { var cell=grid.cell(r,c); if(cell==null) continue; target.setTotalValueAt(grid.rows.get(r),i,0); target.setTotalValueAt(grid.columns.get(c),i,1); target.setTotalValueAt(Double.isFinite(cell.value())?Double.toString(cell.value()):"",i,2); target.setTotalValueAt(Integer.toString(cell.count()),i,3); links.put(target.getTotalRecord(i++),cell.sourceRows().stream().map(n->records[n]).toArray(CompoundRecord[]::new)); }
        target.finalizeTable(CompoundTableEvent.cSpecifierDefaultFiltersAndViews,null); if(source.isDisplayable()) SourceRowLink.attach(source,frame,links,false); return frame;
    }
}
