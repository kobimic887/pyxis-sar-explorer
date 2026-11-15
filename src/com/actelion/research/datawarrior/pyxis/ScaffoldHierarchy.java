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

import com.actelion.research.chem.Canonizer;
import com.actelion.research.chem.IDCodeParser;
import com.actelion.research.chem.Molecule;
import com.actelion.research.chem.RingCollection;
import com.actelion.research.chem.ScaffoldHelper;
import com.actelion.research.chem.StereoMolecule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Builds a ring-removal scaffold hierarchy for a compound collection.
 *
 * Every molecule is reduced to its Murcko scaffold (stereo removed, aromatic bonds kept
 * delocalized, so that a scaffold is the same no matter which Kekule form it came from).
 * Parents of a scaffold are all scaffolds obtained by removing exactly one ring, provided
 * the remainder stays one connected ring-containing piece. A molecule belongs to its own
 * scaffold and to every ancestor of it. For display each scaffold hangs below the parent
 * that covers the most molecules, which puts small, populous scaffolds at the top and
 * larger, rarer ones below them.
 *
 * This is a documented, deterministic method. It is not a reproduction of any other
 * program's scaffold detection.
 */
public class ScaffoldHierarchy {
	/** Scaffolds with more rings than this are kept, but not decomposed further. */
	public static final int MAX_RINGS_TO_DECOMPOSE = 10;

	public static class Node {
		private final String idcode;
		private final int ringCount;
		private final int atomCount;
		private final BitSet members = new BitSet();	// rows of this scaffold or any descendant
		private final BitSet exactMembers = new BitSet();	// rows whose own scaffold this is
		private final List<Node> children = new ArrayList<>();
		private Node parent;

		private Node(String idcode, int ringCount, int atomCount) {
			this.idcode = idcode;
			this.ringCount = ringCount;
			this.atomCount = atomCount;
			}

		/** @return idcode of the scaffold as a substructure fragment */
		public String getIDCode() { return idcode; }
		public int getRingCount() { return ringCount; }
		public int getAtomCount() { return atomCount; }
		public Node getParent() { return parent; }
		public List<Node> getChildren() { return children; }
		/** @return number of molecules containing this scaffold within their own scaffold */
		public int getMemberCount() { return members.cardinality(); }
		/** @return number of molecules whose Murcko scaffold is exactly this one */
		public int getExactMemberCount() { return exactMembers.cardinality(); }
		public BitSet getMembers() { return members; }
		public BitSet getExactMembers() { return exactMembers; }

		public int getLevel() {
			int level = 0;
			for (Node n=parent; n!=null; n=n.parent)
				level++;
			return level;
			}
		}

	private static final Comparator<Node> DISPLAY_ORDER = (a, b) -> {
		int c = Integer.compare(b.getMemberCount(), a.getMemberCount());
		if (c == 0)
			c = Integer.compare(a.atomCount, b.atomCount);
		return (c != 0) ? c : a.idcode.compareTo(b.idcode);
		};

	private final Map<String,Node> mNodeMap = new LinkedHashMap<>();
	private final Map<String,String[]> mParentCache = new HashMap<>();
	private final List<Node> mRoots = new ArrayList<>();
	private final BitSet mNoScaffoldRows = new BitSet();
	private int mRowCount;

	/**
	 * Returns the stereo-free Murcko scaffold of a molecule as a fragment idcode.
	 * @param mol is not changed
	 * @return null if the molecule has no ring
	 */
	public static String murckoScaffoldIDCode(StereoMolecule mol) {
		if (mol == null || mol.getAllAtoms() == 0)
			return null;

		StereoMolecule work = mol.getCompactCopy();
		work.stripSmallFragments();
		work.stripStereoInformation();
		boolean[] isCoreAtom = ScaffoldHelper.findMurckoScaffold(work);
		return (isCoreAtom == null) ? null : fragmentIDCode(work, isCoreAtom);
		}

	private static String fragmentIDCode(StereoMolecule source, boolean[] includeAtom) {
		StereoMolecule fragment = new StereoMolecule();
		source.copyMoleculeByAtoms(fragment, includeAtom, true, null);
		if (fragment.getAllAtoms() == 0)
			return null;
		fragment.setFragment(true);
		return new Canonizer(fragment).getIDCode();
		}

	/**
	 * Registers the scaffold of one table row. Rows must be added before build() is called.
	 * @param row any non-negative row identifier
	 * @param scaffoldIDCode as returned by murckoScaffoldIDCode(); null for ring-free molecules
	 */
	public void addRow(int row, String scaffoldIDCode) {
		mRowCount++;
		if (scaffoldIDCode == null) {
			mNoScaffoldRows.set(row);
			return;
			}
		getNode(scaffoldIDCode).exactMembers.set(row);
		}

	private Node getNode(String idcode) {
		Node node = mNodeMap.get(idcode);
		if (node == null) {
			StereoMolecule mol = new IDCodeParser(false).getCompactMolecule(idcode);
			mol.ensureHelperArrays(Molecule.cHelperRings);
			node = new Node(idcode, mol.getRingSet().getSize(), mol.getAtoms());
			mNodeMap.put(idcode, node);
			}
		return node;
		}

	/**
	 * @return idcodes of all scaffolds that have exactly one ring less than the given one
	 */
	String[] getParentIDCodes(String idcode) {
		String[] cached = mParentCache.get(idcode);
		if (cached != null)
			return cached;

		TreeSet<String> parents = new TreeSet<>();
		StereoMolecule mol = new IDCodeParser(false).getCompactMolecule(idcode);
		mol.ensureHelperArrays(Molecule.cHelperRings);
		RingCollection ringSet = mol.getRingSet();
		int ringCount = ringSet.getSize();

		if (ringCount > 1 && ringCount <= MAX_RINGS_TO_DECOMPOSE) {
			int[] ringMembership = new int[mol.getAtoms()];
			for (int ring=0; ring<ringCount; ring++)
				for (int atom:ringSet.getRingAtoms(ring))
					ringMembership[atom]++;

			for (int ring=0; ring<ringCount; ring++) {
				boolean[] keep = new boolean[mol.getAllAtoms()];
				Arrays.fill(keep, true);
				boolean hasExclusiveAtom = false;
				for (int atom:ringSet.getRingAtoms(ring)) {
					if (ringMembership[atom] == 1) {
						keep[atom] = false;
						hasExclusiveAtom = true;
						}
					}
				if (!hasExclusiveAtom)
					continue;	// ring is entirely part of other rings, e.g. in cages

				String parent = trimmedScaffold(mol, keep, ringCount);
				if (parent != null && !parent.equals(idcode))
					parents.add(parent);
				}
			}

		String[] result = parents.toArray(new String[0]);
		mParentCache.put(idcode, result);
		return result;
		}

	/**
	 * Copies the kept atoms, removes chains that no longer connect rings, and accepts the
	 * result only if it is one connected piece that lost exactly one ring.
	 */
	private static String trimmedScaffold(StereoMolecule mol, boolean[] keep, int originalRingCount) {
		StereoMolecule rest = new StereoMolecule();
		mol.copyMoleculeByAtoms(rest, keep, true, null);
		if (rest.getAllAtoms() == 0)
			return null;
		rest.setFragment(true);

		boolean[] isCoreAtom = ScaffoldHelper.findMurckoScaffold(rest);
		if (isCoreAtom == null)
			return null;

		StereoMolecule parent = new StereoMolecule();
		rest.copyMoleculeByAtoms(parent, isCoreAtom, true, null);
		if (parent.getAllAtoms() == 0)
			return null;
		parent.setFragment(true);
		localizeOrphanedDelocalizedBonds(parent);
		parent.ensureHelperArrays(Molecule.cHelperRings);

		if (parent.getFragmentNumbers(new int[parent.getAllAtoms()], false, false) != 1)
			return null;	// removing a central ring would split the scaffold
		if (parent.getRingSet().getSize() != originalRingCount - 1)
			return null;	// ring perception changed by more than the removed ring

		return new Canonizer(parent).getIDCode();
		}

	/**
	 * A bond shared by a benzene-like ring and a five-membered aromatic ring is stored as
	 * delocalized. When the benzene-like ring is removed, that bond is left over in a ring
	 * that is not delocalized as a whole (indole gives a pyrrole with one such bond). It is
	 * turned back into a double bond here, so that the remaining ring is the same scaffold
	 * as the free heterocycle. Without this, pyrrole from indole would not equal pyrrole.
	 */
	private static void localizeOrphanedDelocalizedBonds(StereoMolecule mol) {
		mol.ensureHelperArrays(Molecule.cHelperRings);
		boolean[] isInDelocalizedRing = new boolean[mol.getAllBonds()];
		RingCollection ringSet = mol.getRingSet();
		for (int ring=0; ring<ringSet.getSize(); ring++) {
			int[] ringBond = ringSet.getRingBonds(ring);
			boolean allDelocalized = true;
			for (int bond:ringBond)
				if (mol.getBondType(bond) != Molecule.cBondTypeDelocalized)
					allDelocalized = false;
			if (allDelocalized)
				for (int bond:ringBond)
					isInDelocalizedRing[bond] = true;
			}

		boolean[] hasDoubleBond = new boolean[mol.getAllAtoms()];
		for (int bond=0; bond<mol.getAllBonds(); bond++) {
			if (mol.getBondType(bond) == Molecule.cBondTypeDouble) {
				hasDoubleBond[mol.getBondAtom(0, bond)] = true;
				hasDoubleBond[mol.getBondAtom(1, bond)] = true;
				}
			}

		for (int bond=0; bond<mol.getAllBonds(); bond++) {
			if (mol.getBondType(bond) == Molecule.cBondTypeDelocalized && !isInDelocalizedRing[bond]) {
				int atom1 = mol.getBondAtom(0, bond);
				int atom2 = mol.getBondAtom(1, bond);
				boolean makeDouble = !hasDoubleBond[atom1] && !hasDoubleBond[atom2];
				mol.setBondType(bond, makeDouble ? Molecule.cBondTypeDouble : Molecule.cBondTypeSingle);
				if (makeDouble) {
					hasDoubleBond[atom1] = true;
					hasDoubleBond[atom2] = true;
					}
				}
			}
		}

	/**
	 * Creates all ancestor scaffolds, assigns members and arranges the display tree.
	 * @param minMembers scaffolds covering fewer molecules are left out of the tree;
	 *                   their molecules still count for the ancestors
	 */
	public void build(int minMembers) {
		// Propagate the members of every observed scaffold to all of its ancestors.
		List<Node> observed = new ArrayList<>(mNodeMap.values());
		for (Node leaf:observed) {
			TreeSet<String> ancestors = new TreeSet<>();
			collectAncestors(leaf.idcode, ancestors);
			leaf.members.or(leaf.exactMembers);
			for (String ancestor:ancestors)
				getNode(ancestor).members.or(leaf.exactMembers);
			}

		// Every scaffold hangs below its most populous parent.
		for (Node node:mNodeMap.values()) {
			Node best = null;
			for (String parentCode:getParentIDCodes(node.idcode)) {
				Node candidate = mNodeMap.get(parentCode);
				if (candidate != null && (best == null || DISPLAY_ORDER.compare(candidate, best) < 0))
					best = candidate;
				}
			node.parent = best;
			}

		mRoots.clear();
		for (Node node:mNodeMap.values()) {
			node.children.clear();
			}
		for (Node node:mNodeMap.values()) {
			if (node.getMemberCount() < Math.max(1, minMembers))
				continue;
			if (node.parent == null)
				mRoots.add(node);
			else
				node.parent.children.add(node);
			}

		mRoots.sort(DISPLAY_ORDER);
		for (Node node:mNodeMap.values())
			node.children.sort(DISPLAY_ORDER);
		}

	private void collectAncestors(String idcode, TreeSet<String> ancestors) {
		for (String parent:getParentIDCodes(idcode))
			if (ancestors.add(parent))
				collectAncestors(parent, ancestors);
		}

	/** @return top level scaffolds, most populous first */
	public List<Node> getRoots() { return mRoots; }

	/** @return rows whose molecule has no ring and therefore no scaffold */
	public BitSet getRowsWithoutScaffold() { return mNoScaffoldRows; }

	public int getRowCount() { return mRowCount; }

	/** @return all scaffolds shown in the tree in display order (parents before children) */
	public List<Node> getNodesInDisplayOrder() {
		List<Node> list = new ArrayList<>();
		for (Node root:mRoots)
			appendSubtree(root, list);
		return list;
		}

	private void appendSubtree(Node node, List<Node> list) {
		list.add(node);
		for (Node child:node.children)
			appendSubtree(child, list);
		}
	}
