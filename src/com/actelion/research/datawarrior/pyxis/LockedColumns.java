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

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.JTableHeader;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.util.WeakHashMap;

/**
 * Keeps the first columns of a table in view while it is scrolled horizontally.
 *
 * The table itself is not split or changed. Two thin strips are placed at the left edge of
 * the visible area, one in the table and one in its header. Each strip draws the table's
 * (or header's) own leftmost columns, using the table's own painting, and hands every
 * mouse event to the table as if it had happened on those columns. Sorting, selection,
 * column resizing, row heights, structure rendering and popup menus therefore keep being
 * done by the one existing table. The strips are only shown while the table is scrolled
 * to the right; at the left end the real columns are visible anyway.
 *
 * Limits: the lock is not saved with the project. Editing a cell of a locked column
 * scrolls the table back to that column first, because the editor lives in the real cell.
 */
public class LockedColumns {
	/**
	 * A mouse event that happened on a strip. Popup menus opened for it must be shifted by
	 * getShiftX() to appear where the user clicked, see DETableView.
	 */
	public static class StripEvent extends MouseEvent {
		private final int mShiftX;

		private StripEvent(JComponent target, MouseEvent e, int shiftX) {
			super(target, e.getID(), e.getWhen(), e.getModifiersEx(), e.getX(), e.getY(),
					e.getXOnScreen(), e.getYOnScreen(), e.getClickCount(), e.isPopupTrigger(), e.getButton());
			mShiftX = shiftX;
			}

		/** @return how far right of the real column the click took place */
		public int getShiftX() { return mShiftX; }
		}

	/** @return horizontal offset to add when showing a popup for this event; 0 for ordinary events */
	public static int popupShift(MouseEvent e) {
		return (e instanceof StripEvent) ? ((StripEvent)e).getShiftX() : 0;
		}

	private static final WeakHashMap<JScrollPane,LockedColumns> sMap = new WeakHashMap<>();

	private final JScrollPane mScrollPane;
	private final JTable mTable;
	private final Strip mTableStrip, mHeaderStrip;
	private int mCount;

	/** @return number of locked columns of the table in this scroll pane */
	public static int getLockedCount(JScrollPane scrollPane) {
		LockedColumns locked = sMap.get(scrollPane);
		return (locked == null) ? 0 : locked.mCount;
		}

	/**
	 * Locks the first 'count' displayed columns; 0 unlocks.
	 * @param scrollPane the scroll pane whose view is the table
	 */
	public static void setLockedCount(JScrollPane scrollPane, JTable table, int count) {
		LockedColumns locked = sMap.get(scrollPane);
		if (locked == null) {
			if (count <= 0)
				return;
			locked = new LockedColumns(scrollPane, table);
			sMap.put(scrollPane, locked);
			}
		locked.mCount = Math.max(0, count);
		locked.update();
		}

	private LockedColumns(JScrollPane scrollPane, JTable table) {
		mScrollPane = scrollPane;
		mTable = table;
		mTableStrip = new Strip(table);
		mHeaderStrip = new Strip(table.getTableHeader());
		table.add(mTableStrip);
		table.getTableHeader().add(mHeaderStrip);

		scrollPane.getViewport().addChangeListener((ChangeEvent e) -> update());
		table.addComponentListener(new ComponentAdapter() {
			@Override public void componentResized(ComponentEvent e) { update(); }
			});
		table.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
			@Override public void columnAdded(TableColumnModelEvent e) { update(); }
			@Override public void columnRemoved(TableColumnModelEvent e) { update(); }
			@Override public void columnMoved(TableColumnModelEvent e) { update(); }
			@Override public void columnMarginChanged(ChangeEvent e) { update(); }
			@Override public void columnSelectionChanged(ListSelectionEvent e) { repaintStrips(); }
			});
		table.getSelectionModel().addListSelectionListener(e -> repaintStrips());
		table.getModel().addTableModelListener(e -> SwingUtilities.invokeLater(this::update));
		}

	/** @return width of the locked columns in pixels */
	private int lockedWidth() {
		int count = Math.min(mCount, mTable.getColumnCount());
		int width = 0;
		for (int i=0; i<count; i++)
			width += mTable.getColumnModel().getColumn(i).getWidth();
		return width;
		}

	private void update() {
		int viewX = mScrollPane.getViewport().getViewPosition().x;
		int width = lockedWidth();
		int visibleWidth = mScrollPane.getViewport().getExtentSize().width;

		// only while scrolled, and never so wide that nothing else would remain visible
		boolean show = mCount > 0 && viewX > 0 && width > 0 && width < visibleWidth * 3 / 4;
		JTableHeader header = mTable.getTableHeader();
		mTableStrip.place(show, viewX, width, mTable.getHeight());
		mHeaderStrip.place(show && header != null, viewX, width, header == null ? 0 : header.getHeight());
		}

	private void repaintStrips() {
		mTableStrip.repaint();
		mHeaderStrip.repaint();
		}

	/** @return true while the locked columns are drawn at the left edge, i.e. the table is scrolled */
	public static boolean isShowing(JScrollPane scrollPane) {
		LockedColumns locked = sMap.get(scrollPane);
		return locked != null && locked.mTableStrip.isVisible();
		}

	/** @return the strip that covers the table body (for automated checks); null if nothing was ever locked */
	public static JComponent getTableStrip(JScrollPane scrollPane) {
		LockedColumns locked = sMap.get(scrollPane);
		return locked == null ? null : locked.mTableStrip;
		}

	/** @return the strip that covers the table header (for automated checks); null if nothing was ever locked */
	public static JComponent getHeaderStrip(JScrollPane scrollPane) {
		LockedColumns locked = sMap.get(scrollPane);
		return locked == null ? null : locked.mHeaderStrip;
		}

	/**
	 * Draws the left part of its target at its own position and forwards its mouse events.
	 * Strip coordinates equal target coordinates of the locked columns, because the strip
	 * shows the target's area starting at x=0.
	 */
	private class Strip extends JComponent implements MouseListener, MouseMotionListener {
		private final JComponent mTarget;
		private boolean mPainting;

		private Strip(JComponent target) {
			mTarget = target;
			setOpaque(true);
			setVisible(false);
			addMouseListener(this);
			addMouseMotionListener(this);
			addMouseWheelListener((MouseWheelEvent e) ->
					mScrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, mScrollPane)));
			setToolTipText("");	// registers with the tooltip manager; text comes from the target
			}

		private void place(boolean show, int x, int width, int height) {
			if (show)
				setBounds(x, 0, width, height);
			if (show != isVisible())
				setVisible(show);
			if (show)
				repaint();
			}

		@Override
		protected void paintComponent(Graphics g) {
			if (mPainting)
				return;	// the target paints its children, which includes this strip
			// Column widths can change without a column model event reaching this class in
			// time (programmatic resize, look and feel changes). Whenever the strip is drawn
			// with a stale width, it is measured again right after this paint.
			if (getWidth() != lockedWidth())
				SwingUtilities.invokeLater(LockedColumns.this::update);
			mPainting = true;
			try {
				Rectangle clip = g.getClipBounds();
				g.setColor(mTarget.getBackground());
				g.fillRect(0, 0, getWidth(), getHeight());

				Graphics targetGraphics = g.create();
				targetGraphics.clipRect(0, 0, getWidth(), getHeight());
				mTarget.paint(targetGraphics);
				targetGraphics.dispose();

				// a line marks where the locked columns end
				Color line = mTarget.getForeground();
				g.setColor(new Color(line.getRed(), line.getGreen(), line.getBlue(), 160));
				g.fillRect(getWidth()-2, clip == null ? 0 : clip.y, 2, clip == null ? getHeight() : clip.height);
				}
			finally {
				mPainting = false;
				}
			}

		@Override
		public String getToolTipText(MouseEvent e) {
			return mTarget.getToolTipText(new StripEvent(mTarget, e, getX()));
			}

		private void forward(MouseEvent e) {
			// The editor of a cell sits in the real cell. Bring that cell into view before a
			// double click starts editing, otherwise the editor would open out of sight.
			if (mTarget == mTable && e.getID() == MouseEvent.MOUSE_PRESSED && e.getClickCount() == 2) {
				Rectangle visible = mTable.getVisibleRect();
				mTable.scrollRectToVisible(new Rectangle(0, visible.y, 1, visible.height));
				}
			// Selecting a cell makes a table scroll that cell into view. For a locked cell that
			// would throw the table back to its left end although the cell is on screen here,
			// so the horizontal position is put back. Starting to edit is the exception above.
			boolean keepPosition = !(mTarget == mTable && e.getClickCount() == 2);
			java.awt.Point before = mScrollPane.getViewport().getViewPosition();
			mTarget.dispatchEvent(new StripEvent(mTarget, e, getX()));
			if (keepPosition) {
				java.awt.Point after = mScrollPane.getViewport().getViewPosition();
				if (after.x != before.x)
					mScrollPane.getViewport().setViewPosition(new java.awt.Point(before.x, after.y));
				}
			setCursor(mTarget.getCursor());
			e.consume();
			}

		@Override public void mouseClicked(MouseEvent e) { forward(e); }
		@Override public void mousePressed(MouseEvent e) { forward(e); }
		@Override public void mouseReleased(MouseEvent e) { forward(e); }
		@Override public void mouseEntered(MouseEvent e) { forward(e); }
		@Override public void mouseExited(MouseEvent e) { forward(e); }
		@Override public void mouseDragged(MouseEvent e) { forward(e); }
		@Override public void mouseMoved(MouseEvent e) { forward(e); }
		}
	}
