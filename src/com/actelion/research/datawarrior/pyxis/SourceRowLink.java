// Pyxis addition, GPLv3 or later. Session-local links use record identity, never row offsets.
package com.actelion.research.datawarrior.pyxis;

import com.actelion.research.datawarrior.DEFrame;
import com.actelion.research.table.model.*;
import java.awt.event.*;
import java.util.*;

public final class SourceRowLink implements CompoundTableListener {
    private static final List<SourceRowLink> LINKS = new ArrayList<>();
    private final DEFrame source, target;
    private final java.awt.Window selectionWindow;
    private final CompoundTableModel selectionModel;
    private final IdentityHashMap<CompoundRecord,CompoundRecord[]> rows;
    private final boolean scaffoldFilter;
    private int exclusion = -1;
    private boolean closed;
    private final WindowAdapter lifecycle = new WindowAdapter() {
        @Override public void windowClosed(WindowEvent e) { detach(); }
    };
    private SourceRowLink(DEFrame source, DEFrame target, IdentityHashMap<CompoundRecord,CompoundRecord[]> rows, boolean filter) {
        this(source,target,target.getTableModel(),target,rows,filter);
    }
    private SourceRowLink(DEFrame source, DEFrame target, CompoundTableModel model, java.awt.Window window, IdentityHashMap<CompoundRecord,CompoundRecord[]> rows, boolean filter) {
        this.source=source; this.target=target; this.rows=rows; scaffoldFilter=filter; selectionModel=model; selectionWindow=window;
        selectionModel.addCompoundTableListener(this);
        source.addWindowListener(lifecycle); selectionWindow.addWindowListener(lifecycle);
        LINKS.add(this);
    }
    public static void attach(DEFrame source, DEFrame target, IdentityHashMap<CompoundRecord,CompoundRecord[]> rows, boolean filter) {
        new SourceRowLink(source,target,rows,filter);
    }
    public static void attachSelection(DEFrame source, java.awt.Window window, CompoundTableModel model, IdentityHashMap<CompoundRecord,CompoundRecord[]> rows) {
        new SourceRowLink(source,null,model,window,rows,false);
    }
    @Override public void compoundTableChanged(CompoundTableEvent event) {
        if (closed || event.getType()!=CompoundTableEvent.cChangeSelection) return;
        if (!source.isDisplayable()) { detach(); return; }
        Set<CompoundRecord> selected = Collections.newSetFromMap(new IdentityHashMap<>());
        CompoundTableModel model=selectionModel;
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
        CompoundTableModel model=selectionModel;
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
        selectionModel.removeCompoundTableListener(this);
        source.removeWindowListener(lifecycle); selectionWindow.removeWindowListener(lifecycle);
        if (exclusion>=0) selectionModel.freeRowFlag(exclusion);
        rows.clear();
        if (target!=null && target.isDisplayable()) target.setTitle(target.getTitle()+" (source link closed)");
    }
}
