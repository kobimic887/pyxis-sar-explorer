// Pyxis addition; GPLv3 or later. Token is entered at execution and never included in task properties.
package com.actelion.research.datawarrior.pyxis;
import com.actelion.research.datawarrior.DEFrame;
import com.actelion.research.datawarrior.task.ConfigurableTask;
import com.actelion.research.table.model.*;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class DETaskImportCddVault extends ConfigurableTask {
    public static final String TASK_NAME="Import CDD Vault Molecules";
    private JTextField vault,ids;
    public DETaskImportCddVault(DEFrame parent) { super(parent,true); }
    @Override public String getTaskName() { return TASK_NAME; }
    @Override public boolean isConfigurable() { return true; }
    @Override public JComponent createDialogContent() {
        JPanel panel=new JPanel(new GridLayout(0,1,8,8)); vault=new JTextField(30); ids=new JTextField(40);
        panel.add(new JLabel("Not yet tested against a live vault. Imports up to 100 explicit molecule IDs."));
        panel.add(new JLabel("Vault ID:")); panel.add(vault); panel.add(new JLabel("CDD molecule IDs (commas/spaces):")); panel.add(ids);
        panel.add(new JLabel("The API token is requested separately each time and is never recorded in macros.")); return panel;
    }
    @Override public Properties getDialogConfiguration() { Properties p=new Properties(); p.setProperty("vault",vault.getText().trim()); p.setProperty("molecules",ids.getText().trim()); return p; }
    @Override public void setDialogConfiguration(Properties p) { vault.setText(p.getProperty("vault","")); ids.setText(p.getProperty("molecules","")); }
    @Override public void setDialogConfigurationToDefault() { vault.setText(""); ids.setText(""); }
    @Override public boolean isConfigurationValid(Properties p,boolean live) {
        try {
            if (!p.getProperty("vault","").matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Enter a positive numeric vault ID.");
            CddVaultClient.parseIDs(p.getProperty("molecules","")); return true;
        } catch(IllegalArgumentException e) { showErrorMessage(e.getMessage()); return false; }
    }
    @Override public void runTask(Properties p) {
        final char[][] secret={null};
        try {
            SwingUtilities.invokeAndWait(() -> { JPasswordField field=new JPasswordField(36); if (JOptionPane.showConfirmDialog(getParentFrame(),field,"CDD API token (not saved)",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION) secret[0]=field.getPassword(); field.setText(""); });
            if (secret[0]==null || secret[0].length==0) return;
            List<CddVaultClient.Molecule> molecules=new ArrayList<>(); CddVaultClient client=new CddVaultClient();
            List<String> requested=CddVaultClient.parseIDs(p.getProperty("molecules"));
            startProgress("Reading CDD molecules...",0,requested.size());
            for (String id:requested) { if (threadMustDie()) return; molecules.add(client.fetch(p.getProperty("vault"),id,new String(secret[0]))); updateProgress(molecules.size()); }
            CompoundTableModel table=getEmptyFrame("CDD Vault molecules (not yet tested against a live vault)").getTableModel();
            populate(table,molecules,p.getProperty("vault"));
        } catch(Exception e) { showErrorMessage("CDD import failed. "+e.getMessage()); }
        finally { if (secret[0]!=null) Arrays.fill(secret[0],'\0'); }
    }
    /** Mapping shared by the live task and offline built-app check. */
    public static void populate(CompoundTableModel table,List<CddVaultClient.Molecule> molecules,String vaultID) {
            table.initializeTable(molecules.size(),6);
            table.prepareStructureColumns(0,"Structure",false,true,false);
            table.setColumnName("CDD Molecule ID",2); table.setColumnName("Name",3); table.setColumnName("Molecule fields (JSON)",4); table.setColumnName("CDD Vault ID",5);
            for (int r=0;r<molecules.size();r++) {
                CddVaultClient.Molecule m=molecules.get(r);
                String[] data={m.idcode(),m.coordinates(),m.id(),m.name(),m.fields(),vaultID};
                for (int c=0;c<data.length;c++) table.setTotalValueAt(data[c],r,c);
            }
            table.finalizeTable(CompoundTableEvent.cSpecifierDefaultFiltersAndViews,null);
    }

}
