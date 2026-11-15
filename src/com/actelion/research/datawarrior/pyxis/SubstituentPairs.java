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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds scaffold-centric molecule pairs: two molecules that share the same decorated
 * scaffold and carry identical substituents at every R-group position but one.
 *
 * Input is the result of an R-group decomposition: one scaffold key and one substituent
 * per R-group position for every row. An empty substituent means that the position
 * carries no varying group in that row (typically hydrogen) and is compared as such.
 */
public class SubstituentPairs {
	public static class Pair {
		public final int rowA;
		public final int rowB;
		/** zero based index of the R-group position at which the two molecules differ */
		public final int position;

		private Pair(int rowA, int rowB, int position) {
			this.rowA = rowA;
			this.rowB = rowB;
			this.position = position;
			}
		}

	private final List<Pair> mPairs = new ArrayList<>();
	private boolean mTruncated;

	/**
	 * @param scaffold scaffold key per row; rows with null or empty scaffold are skipped
	 * @param substituent [row][position] substituent keys; null and empty mean no substituent
	 * @param maxPairs search stops once this number of pairs is reached
	 */
	public SubstituentPairs(String[] scaffold, String[][] substituent, int maxPairs) {
		// rows that agree on scaffold and all positions but one end up in the same bucket
		Map<String,List<Integer>> bucketMap = new HashMap<>();
		List<String> bucketOrder = new ArrayList<>();
		List<Integer> bucketPosition = new ArrayList<>();

		for (int row=0; row<scaffold.length; row++) {
			if (scaffold[row] == null || scaffold[row].isEmpty())
				continue;

			int positions = substituent[row].length;
			for (int position=0; position<positions; position++) {
				StringBuilder key = new StringBuilder();
				key.append(position).append('\t').append(scaffold[row]);
				for (int i=0; i<positions; i++)
					if (i != position)
						key.append('\t').append(normalize(substituent[row][i]));

				String k = key.toString();
				List<Integer> bucket = bucketMap.get(k);
				if (bucket == null) {
					bucket = new ArrayList<>();
					bucketMap.put(k, bucket);
					bucketOrder.add(k);
					bucketPosition.add(position);
					}
				bucket.add(row);
				}
			}

		for (int b=0; b<bucketOrder.size() && !mTruncated; b++) {
			List<Integer> bucket = bucketMap.get(bucketOrder.get(b));
			int position = bucketPosition.get(b);
			for (int i=0; i<bucket.size() && !mTruncated; i++) {
				int rowA = bucket.get(i);
				String groupA = normalize(substituent[rowA][position]);
				for (int j=i+1; j<bucket.size(); j++) {
					int rowB = bucket.get(j);
					if (groupA.equals(normalize(substituent[rowB][position])))
						continue;	// same molecule decoration, not a pair
					if (mPairs.size() >= maxPairs) {
						mTruncated = true;
						break;
						}
					mPairs.add(new Pair(rowA, rowB, position));
					}
				}
			}
		}

	private static String normalize(String s) {
		return (s == null) ? "" : s;
		}

	public List<Pair> getPairs() { return mPairs; }

	/** @return true if more pairs exist than the given maximum */
	public boolean isTruncated() { return mTruncated; }
	}
