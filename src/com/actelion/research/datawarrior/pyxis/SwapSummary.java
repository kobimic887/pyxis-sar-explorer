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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Summarises single-position substituent pairs by the exchange they represent.
 *
 * All pairs that replace substituent X by substituent Y are pooled, whatever the scaffold
 * and whichever R-group position they occur at, and the change of a numerical value is
 * described over that pool. X to Y and Y to X are the same exchange seen from two sides;
 * they are stored once, in the direction whose 'from' key sorts first, and the value
 * change is always value(to) minus value(from).
 *
 * Pairs in which either molecule has no value are counted but do not enter the statistics.
 * This describes the supplied collection only; it is not a prediction for other molecules.
 */
public class SwapSummary {
	public static class Swap {
		public final String from, to;
		/** all pairs with this exchange, including those without both values */
		public int pairs;
		/** pairs with both values; all statistics below refer to these */
		public int valuedPairs;
		public int increases, decreases, scaffolds;
		public double meanChange = Double.NaN, medianChange = Double.NaN, minChange = Double.NaN, maxChange = Double.NaN;
		/** geometric mean of value(to)/value(from) over pairs where both values are positive; NaN if none */
		public double meanFoldChange = Double.NaN;
		/** source rows of all molecules taking part in this exchange */
		public final TreeSet<Integer> rows = new TreeSet<>();

		private final List<Double> changes = new ArrayList<>();
		private final TreeSet<String> scaffoldSet = new TreeSet<>();
		private double logRatioSum;
		private int logRatioCount;

		private Swap(String from, String to) {
			this.from = from;
			this.to = to;
			}
		}

	private final List<Swap> mSwaps = new ArrayList<>();

	/**
	 * @param pairs single-position pairs as found by SubstituentPairs
	 * @param scaffold scaffold key per row
	 * @param substituent [row][position] substituent keys; null and empty mean hydrogen
	 * @param value numerical value per row; NaN if missing
	 */
	public SwapSummary(List<SubstituentPairs.Pair> pairs, String[] scaffold, String[][] substituent, double[] value) {
		Map<String,Swap> map = new LinkedHashMap<>();
		for (SubstituentPairs.Pair pair:pairs) {
			if (pair.position < 0)
				continue;	// scaffold hopping pairs have no exchanged substituent

			int rowFrom = pair.rowA;
			int rowTo = pair.rowB;
			String from = normalize(substituent[rowFrom][pair.position]);
			String to = normalize(substituent[rowTo][pair.position]);
			if (from.compareTo(to) > 0) {
				String s = from; from = to; to = s;
				int r = rowFrom; rowFrom = rowTo; rowTo = r;
				}

			final String fromKey = from, toKey = to;
			Swap swap = map.computeIfAbsent(from+"\t"+to, k -> new Swap(fromKey, toKey));
			swap.pairs++;
			swap.rows.add(rowFrom);
			swap.rows.add(rowTo);
			swap.scaffoldSet.add(scaffold[rowFrom]);

			double a = value[rowFrom];
			double b = value[rowTo];
			if (Double.isNaN(a) || Double.isNaN(b) || Double.isInfinite(a) || Double.isInfinite(b))
				continue;
			swap.changes.add(b - a);
			if (b > a) swap.increases++;
			if (b < a) swap.decreases++;
			if (a > 0 && b > 0) {
				swap.logRatioSum += Math.log(b / a);
				swap.logRatioCount++;
				}
			}

		for (Swap swap:map.values()) {
			swap.scaffolds = swap.scaffoldSet.size();
			swap.valuedPairs = swap.changes.size();
			if (swap.valuedPairs != 0) {
				double[] sorted = new double[swap.valuedPairs];
				double sum = 0;
				for (int i=0; i<sorted.length; i++) {
					sorted[i] = swap.changes.get(i);
					sum += sorted[i];
					}
				Arrays.sort(sorted);
				swap.meanChange = sum / sorted.length;
				swap.minChange = sorted[0];
				swap.maxChange = sorted[sorted.length-1];
				swap.medianChange = (sorted.length % 2 == 1) ? sorted[sorted.length/2]
						: (sorted[sorted.length/2-1] + sorted[sorted.length/2]) / 2;
				}
			if (swap.logRatioCount != 0)
				swap.meanFoldChange = Math.exp(swap.logRatioSum / swap.logRatioCount);
			mSwaps.add(swap);
			}

		// best supported exchanges first; ties in a fixed order so that results are repeatable
		mSwaps.sort((x, y) -> {
			int c = Integer.compare(y.valuedPairs, x.valuedPairs);
			if (c == 0) c = Integer.compare(y.pairs, x.pairs);
			if (c == 0) c = x.from.compareTo(y.from);
			return (c != 0) ? c : x.to.compareTo(y.to);
			});
		}

	private static String normalize(String s) {
		return (s == null) ? "" : s;
		}

	/** @return exchanges, the ones backed by most pairs first */
	public List<Swap> getSwaps() { return mSwaps; }
	}
