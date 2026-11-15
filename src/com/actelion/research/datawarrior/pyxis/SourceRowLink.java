// Pyxis addition, GPLv3 or later. Session-local links use record identity, never row offsets.
package com.actelion.research.datawarrior.pyxis;

import com.actelion.research.datawarrior.DEFrame;
import com.actelion.research.table.model.*;
import java.awt.event.*;
import java.util.*;

public final class SourceRowLink implements CompoundTableListener {
    private static final List<SourceRowLink> LINKS = new ArrayList<>();
    private final DEFrame source, target;
    private final IdentityHashMap<CompoundRecord,CompoundRecord[]> rows;
    private final boolean scaffoldFilter;
    private int exclusion = -1;
    private boolean closed;
    private final WindowAdapter lifecycle = new WindowAdapter() {
        @Override public void windowClosed(WindowEvent e) { detach(); }
    };
    private SourceRowLink(DEFrame source, DEFrame target, IdentityHashMap<CompoundRecord,CompoundRecord[]> rows, boolean filter) {
        this.source=source; this.target=target; this.rows=rows; scaffoldFilter=filter;
        target.getTableModel().addCompoundTableListener(this);
        source.addWindowListener(lifecycle); target.addWindowListener(lifecycle);
        LINKS.add(this);
    }
    public static void attach(DEFrame source, DEFrame target, IdentityHashMap<CompoundRecord,CompoundRecord[]> rows, boolean filter) {
        new SourceRowLink(source,target,rows,filter);
    }
    @Override public void compoundTableChanged(CompoundTableEvent event) {
        if (closed || event.getType()!=CompoundTableEvent.cChangeSelection) return;
        if (!source.isDisplayable()) { detach(); return; }
        Set<CompoundRecord> selected = Collections.newSetFromMap(new IdentityHashMap<>());
        CompoundTableModel model=target.getTableModel();
        for (int r=0;r<model.getTotalRowCount();r++) {
            CompoundRecord row=model.getTotalRecord(r);
            if (row.isSelected() && rows.containsKey(row)) Collections.addAll(selected,rows.get(row));
        }
        model=source.getTableModel();
        for (int r=0;r<model.getTotalRowCount();r++) {
            CompoundRecord row=model.getTotalRecord(r); row.setSelection(selected.contains(row));
        }
        model.invalidateSelectionModel();
    }
    public static void filter(DEFrame source, Set<CompoundRecord> members) {
        for (SourceRowLink link:new ArrayList<>(LINKS)) if (link.source==source && link.scaffoldFilter) link.filter(members);
    }
    private void filter(Set<CompoundRecord> members) {
        CompoundTableModel model=target.getTableModel();
        if (exclusion<0 && members!=null) exclusion=model.getUnusedRowFlag(true);
        if (exclusion<0) return;
        for (int r=0;r<model.getTotalRowCount();r++) {
            CompoundRecord row=model.getTotalRecord(r);
            boolean include=members==null;
            CompoundRecord[] original=rows.get(row);
            if (original!=null && members!=null) for (CompoundRecord record:original) include |= members.contains(record);
            if (include) row.clearFlag(exclusion); else row.setFlag(exclusion);
        }
        model.updateExternalExclusion(exclusion,false,true);
    }
    private void detach() {
        if (closed) return;
        closed=true; LINKS.remove(this);
        target.getTableModel().removeCompoundTableListener(this);
        source.removeWindowListener(lifecycle); target.removeWindowListener(lifecycle);
        if (exclusion>=0) target.getTableModel().freeRowFlag(exclusion);
        rows.clear();
        if (target.isDisplayable()) target.setTitle(target.getTitle()+" (source link closed)");
    }
}
