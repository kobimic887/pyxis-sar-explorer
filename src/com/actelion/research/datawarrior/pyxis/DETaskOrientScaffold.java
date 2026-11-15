// Pyxis addition; GPLv3 or later. Uses DataWarrior's template coordinate task unchanged.
package com.actelion.research.datawarrior.pyxis;
import com.actelion.research.datawarrior.DEFrame;
import com.actelion.research.datawarrior.task.chem.DETaskAdd2DCoordinates;
import com.actelion.research.chem.StereoMolecule;
import java.util.*;

public class DETaskOrientScaffold extends DETaskAdd2DCoordinates {
    public static final String TASK_NAME = "Re-orient Scaffold Family";
    private Set<String> members;
    public DETaskOrientScaffold(DEFrame parent) { super(parent); }
    @Override public String getTaskName() { return TASK_NAME; }
    @Override protected boolean preprocessRows(Properties configuration) {
        members = new HashSet<>(Arrays.asList(configuration.getProperty("members", "").split("\\t")));
        return super.preprocessRows(configuration);
    }
    @Override public void processRow(int row, int firstNewColumn, StereoMolecule mol, int thread) {
        // Record canonical molecule keys rather than row indices so sorting cannot change the family.
        if (members.contains(getTableModel().getTotalValueAt(row, getChemistryColumn())))
            super.processRow(row, firstNewColumn, mol, thread);
    }
}
