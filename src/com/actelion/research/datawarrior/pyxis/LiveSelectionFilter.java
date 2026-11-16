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

import com.actelion.research.table.model.CompoundRecord;
import com.actelion.research.table.model.CompoundTableListHandler;
import com.actelion.research.table.model.CompoundTableModel;
import com.actelion.research.table.view.JVisualization;

import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Lets a graphical view show only the rows that are currently selected, and keeps doing so
 * while the selection changes. One view can this way be driven by a lasso in another.
 *
 * It reuses what the views already have: every graphical view can be restricted to the
 * members of one row list. This class owns a row list that mirrors the selection and
 * points the chosen views at it. No other view is affected, because the restriction is a
 * property of the individual view and not a table filter.
 *
 * A selection made with the mouse inside a filtered view is not followed. Otherwise
 * clicking one marker in the filtered view would immediately remove all others from it.
 * Only pressing and dragging in that view counts; a pointer that merely rests over it
 * while the selection is changed elsewhere does not.
 *
 * The setting is saved with the project: after reopening, the view shows the rows it
 * showed when saved and follows the selection again.
 */
public class LiveSelectionFilter implements ListSelectionListener {
	public static final String LIST_NAME = "Live Selection";

	private static final WeakHashMap<CompoundTableModel,LiveSelectionFilter> sFilterMap = new WeakHashMap<>();

	private final CompoundTableModel mTableModel;
	private final ListSelectionModel mSelectionModel;
	private final List<WeakReference<JVisualization>> mViews = new ArrayList<>();
	private final java.util.WeakHashMap<JVisualization,java.awt.event.MouseAdapter> mMouseTrackers = new java.util.WeakHashMap<>();
	private String mListName;
	private int mSelectingInFilteredView;	// number of filtered views with a mouse button currently down

	private LiveSelectionFilter(CompoundTableModel tableModel, ListSelectionModel selectionModel) {
		mTableModel = tableModel;
		mSelectionModel = selectionModel;
		}

	/** @return true if the view currently follows the selection */
	public static boolean isActive(JVisualization view) {
		LiveSelectionFilter filter = sFilterMap.get(view.getTableModel());
		return filter != null && filter.contains(view) && view.getLocalExclusionList() == filter.getListIndex()
				&& filter.getListIndex() != CompoundTableListHandler.LISTINDEX_NONE;
		}

	/**
	 * Switches following of the selection on or off for one view.
	 * @param selectionModel the selection model shared by all views of the document
	 */
	public static void setActive(JVisualization view, ListSelectionModel selectionModel, boolean active) {
		CompoundTableModel tableModel = view.getTableModel();
		LiveSelectionFilter filter = sFilterMap.get(tableModel);
		if (active) {
			if (filter == null) {
				filter = new LiveSelectionFilter(tableModel, selectionModel);
				sFilterMap.put(tableModel, filter);
				selectionModel.addListSelectionListener(filter);
				}
			filter.add(view);
			}
		else if (filter != null) {
			filter.remove(view);
			}
		}

	/**
	 * Takes up following the selection for a view of a reopened project. The view is already
	 * restricted to the saved list; that list is adopted as it is, so the view shows what it
	 * showed when the project was saved until the selection changes.
	 */
	public static void resume(JVisualization view, ListSelectionModel selectionModel) {
		CompoundTableModel tableModel = view.getTableModel();
		int list = view.getLocalExclusionList();
		if (list == CompoundTableListHandler.LISTINDEX_NONE)
			return;
		LiveSelectionFilter filter = sFilterMap.get(tableModel);
		if (filter == null) {
			filter = new LiveSelectionFilter(tableModel, selectionModel);
			filter.mListName = tableModel.getListHandler().getListName(list);
			sFilterMap.put(tableModel, filter);
			selectionModel.addListSelectionListener(filter);
			}
		else if (filter.getListIndex() != list) {
			return;	// restricted to some other list; leave it as the file says
			}
		filter.track(view);
		}

	private boolean contains(JVisualization view) {
		for (WeakReference<JVisualization> reference:mViews)
			if (reference.get() == view)
				return true;
		return false;
		}

	private int getListIndex() {
		return (mListName == null) ? CompoundTableListHandler.LISTINDEX_NONE
								   : mTableModel.getListHandler().getListIndex(mListName);
		}

	private void add(JVisualization view) {
		CompoundTableListHandler listHandler = mTableModel.getListHandler();
		if (getListIndex() == CompoundTableListHandler.LISTINDEX_NONE)
			mListName = listHandler.createList(LIST_NAME);
		track(view);
		copySelectionToList();
		view.setLocalExclusionList(getListIndex());
		}

	private void track(JVisualization view) {
		if (!contains(view)) {
			mViews.add(new WeakReference<>(view));
			java.awt.event.MouseAdapter tracker = new java.awt.event.MouseAdapter() {
				private boolean mDown;
				@Override public void mousePressed(java.awt.event.MouseEvent e) {
					if (!mDown) { mDown = true; mSelectingInFilteredView++; }
					}
				@Override public void mouseReleased(java.awt.event.MouseEvent e) {
					// the view applies its selection while handling this same release,
					// so the flag is cleared only after all handlers of the event ran
					if (mDown) { mDown = false; javax.swing.SwingUtilities.invokeLater(() -> mSelectingInFilteredView--); }
					}
				};
			view.addMouseListener(tracker);
			mMouseTrackers.put(view, tracker);
			}
		}

	private void remove(JVisualization view) {
		for (Iterator<WeakReference<JVisualization>> iterator=mViews.iterator(); iterator.hasNext();) {
			JVisualization v = iterator.next().get();
			if (v == null || v == view)
				iterator.remove();
			}
		if (view.getLocalExclusionList() == getListIndex())
			view.setLocalExclusionList(CompoundTableListHandler.LISTINDEX_NONE);
		java.awt.event.MouseAdapter tracker = mMouseTrackers.remove(view);
		if (tracker != null)
			view.removeMouseListener(tracker);

		if (mViews.isEmpty()) {
			mSelectionModel.removeListSelectionListener(this);
			sFilterMap.remove(mTableModel);
			if (mListName != null)
				mTableModel.getListHandler().deleteList(mListName);
			mListName = null;
			}
		}

	@Override
	public void valueChanged(ListSelectionEvent e) {
		if (e.getValueIsAdjusting())
			return;

		boolean hasView = false;
		for (Iterator<WeakReference<JVisualization>> iterator=mViews.iterator(); iterator.hasNext();) {
			JVisualization view = iterator.next().get();
			if (view == null || view.getLocalExclusionList() != getListIndex()
			 || getListIndex() == CompoundTableListHandler.LISTINDEX_NONE) {
				iterator.remove();	// view was closed, or the user chose another list for it
				continue;
				}
			hasView = true;
			}

		if (hasView && mSelectingInFilteredView <= 0)	// else: selection is being made inside a filtered view
			copySelectionToList();
		}

	/** Makes the list contain exactly the visible selected rows and tells the views. */
	private void copySelectionToList() {
		int list = getListIndex();
		if (list == CompoundTableListHandler.LISTINDEX_NONE)
			return;

		CompoundTableListHandler listHandler = mTableModel.getListHandler();
		int flagNo = listHandler.getListFlagNo(list);
		for (int row=0; row<mTableModel.getTotalRowCount(); row++) {
			CompoundRecord record = mTableModel.getTotalRecord(row);
			if (!mTableModel.isVisibleAndSelected(record))
				record.clearFlag(flagNo);
			}
		listHandler.addSelected(list);	// adds the selected rows and notifies all list listeners
		}
	}
