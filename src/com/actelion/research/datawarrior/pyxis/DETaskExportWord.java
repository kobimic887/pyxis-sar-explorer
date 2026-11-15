// Pyxis addition; GPLv3 or later. Exports a snapshot of the visible table.
package com.actelion.research.datawarrior.pyxis;
import com.actelion.research.datawarrior.*;
import com.actelion.research.datawarrior.task.ConfigurableTask;
import com.actelion.research.table.model.*;
import com.actelion.research.gui.JStructureView;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

public class DETaskExportWord extends ConfigurableTask {
    public static final String TASK_NAME="Export Visible Table to MS Word";
    private final DEFrame frame;
    private JTextField filename;
    public DETaskExportWord(DEFrame parent) { super(parent,true); frame=parent; }
    @Override public String getTaskName() { return TASK_NAME; }
    @Override public boolean isConfigurable() { return frame.getMainFrame().getMainPane().getTable()!=null; }
    @Override public JComponent createDialogContent() {
        JPanel panel=new JPanel(new BorderLayout(8,8)); filename=new JTextField(40);
        JButton browse=new JButton("Choose file..."); browse.addActionListener(e -> { JFileChooser chooser=new JFileChooser(); if (chooser.showSaveDialog(frame)==JFileChooser.APPROVE_OPTION) { String path=chooser.getSelectedFile().getAbsolutePath(); filename.setText(path.toLowerCase(Locale.ROOT).endsWith(".docx")?path:path+".docx"); } });
        panel.add(new JLabel("Visible rows and displayed columns only; structures become PNG images."),BorderLayout.NORTH);
        panel.add(filename,BorderLayout.CENTER); panel.add(browse,BorderLayout.EAST);
        panel.add(new JLabel("Maximum 1,000 rows / 30 columns / 10,000 cells. Choose a new filename."),BorderLayout.SOUTH); return panel;
    }
    @Override public Properties getDialogConfiguration() { Properties p=new Properties(); p.setProperty("fileName",filename.getText().trim()); return p; }
    @Override public void setDialogConfiguration(Properties p) { filename.setText(p.getProperty("fileName","")); }
    @Override public void setDialogConfigurationToDefault() { filename.setText(""); }
    @Override public boolean isConfigurationValid(Properties p,boolean live) {
        String name=p.getProperty("fileName","");
        if (!name.toLowerCase(Locale.ROOT).endsWith(".docx")) { showErrorMessage("Choose a .docx filename."); return false; }
        if (live && Files.exists(Path.of(name))) { showErrorMessage("File already exists; choose a new filename."); return false; }
        return true;
    }
    public static void export(DEFrame frame,Path file) throws Exception {
        List<String> headers=new ArrayList<>(); List<List<DocxTableWriter.Cell>> rows=new ArrayList<>();
        // Swing column order/visibility and visible records must be captured on the EDT.
        Runnable snapshot=() -> {
            DETable table=frame.getMainFrame().getMainPane().getTable();
            CompoundTableModel model=frame.getTableModel();
            DocxTableWriter.checkSize(table.getRowCount(),table.getColumnCount());
            for (int c=0;c<table.getColumnCount();c++) headers.add(table.getColumnName(c));
            for (int r=0;r<table.getRowCount();r++) {
                List<DocxTableWriter.Cell> row=new ArrayList<>();
                for (int c=0;c<table.getColumnCount();c++) {
                    int column=table.convertTotalColumnIndexFromView(c);
                    CompoundRecord record=model.getRecord(r);
                    if (model.isColumnTypeStructure(column) && record.getData(column)!=null) {
                        JStructureView view=new JStructureView(model.getChemicalStructure(record,column,0,null)); view.setSize(400,200); view.setBackground(Color.WHITE); view.setForeground(Color.BLACK); view.setOpaque(true); view.setDisableBorder(true);
                        BufferedImage image=new BufferedImage(400,200,BufferedImage.TYPE_INT_RGB); Graphics2D g=image.createGraphics(); view.paint(g); g.dispose();
                        try { ByteArrayOutputStream png=new ByteArrayOutputStream(); ImageIO.write(image,"png",png); row.add(new DocxTableWriter.Cell("",png.toByteArray())); }
                        catch (IOException e) { throw new UncheckedIOException(e); }
                    } else row.add(new DocxTableWriter.Cell(model.encodeData(record,column)));
                }
                rows.add(row);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) snapshot.run(); else SwingUtilities.invokeAndWait(snapshot);
        DocxTableWriter.write(file,headers,rows);
    }
    @Override public void runTask(Properties p) {
        try { export(frame,Path.of(resolvePathVariables(p.getProperty("fileName")))); }
        catch (Exception e) { Throwable cause=e.getCause()==null?e:e.getCause(); showErrorMessage("Word export failed: "+cause.getMessage()); }
    }
}
