/*
 * Pyxis SAR Explorer self-test. Run with pyxis/run-tests.sh; exits non-zero on failure.
 * GNU General Public License v3 or later, WITHOUT ANY WARRANTY.
 */

import com.actelion.research.datawarrior.pyxis.SubstituentPairs;
import com.actelion.research.datawarrior.pyxis.SwapSummary;
import com.actelion.research.datawarrior.pyxis.SwapSummary.Swap;

import java.util.List;

public class SwapSummaryTest {
	private static int sFailures;

	public static void main(String[] args) {
		// Two scaffolds, one R position. H -> Me: S1 (1 -> 4), S2 (2 -> 8), S2 again without value.
		// Me -> H written the other way round in S3 (10 -> 5) must be pooled as H -> Me with change +5.
		String[] scaffold = { "S1", "S1", "S2", "S2", "S2", "S3", "S3", "S1" };
		String[][] group = { {""}, {"Me"}, {null}, {"Me"}, {"Me"}, {"Me"}, {""}, {"Et"} };
		double[] value = { 1, 4, 2, 8, Double.NaN, 10, 5, 7 };

		List<SubstituentPairs.Pair> pairs = new SubstituentPairs(scaffold, group, 1000).getPairs();
		List<Swap> swaps = new SwapSummary(pairs, scaffold, group, value).getSwaps();

		Swap hToMe = find(swaps, "", "Me");
		check(hToMe != null, "H -> Me exists");
		if (hToMe != null) {
			check(hToMe.pairs == 4, "4 H/Me pairs (S1, S2 twice, S3), found "+hToMe.pairs);
			check(hToMe.valuedPairs == 3, "3 of them have both values, found "+hToMe.valuedPairs);
			check(hToMe.scaffolds == 3, "seen on 3 scaffolds, found "+hToMe.scaffolds);
			check(close(hToMe.meanChange, (3+6+5)/3.0), "mean change 4.667, found "+hToMe.meanChange);
			check(close(hToMe.medianChange, 5), "median change 5, found "+hToMe.medianChange);
			check(close(hToMe.minChange, 3) && close(hToMe.maxChange, 6), "range 3..6");
			check(close(hToMe.meanFoldChange, Math.cbrt(4.0*4.0*2.0)), "geometric mean fold change, found "+hToMe.meanFoldChange);
			check(hToMe.increases == 3 && hToMe.decreases == 0, "three increases, the reversed S3 pair included");
			check(hToMe.rows.size() == 7, "7 molecules take part, found "+hToMe.rows.size());
			check(swaps.get(0) == hToMe, "best supported exchange is listed first");
			}
		check(find(swaps, "Me", "") == null, "the reverse direction is not listed separately");

		Swap hToEt = find(swaps, "", "Et");
		check(hToEt != null && hToEt.pairs == 1 && close(hToEt.meanChange, 6), "H -> Et in S1: 1 -> 7");
		Swap etToMe = find(swaps, "Et", "Me");
		check(etToMe != null && close(etToMe.meanChange, -3) && etToMe.decreases == 1, "Et -> Me in S1: 7 -> 4");
		check(swaps.size() == 3, "3 distinct exchanges, found "+swaps.size());

		// an exchange whose pairs all lack values is listed with empty statistics
		double[] none = { Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN };
		Swap empty = find(new SwapSummary(pairs, scaffold, group, none).getSwaps(), "", "Me");
		check(empty != null && empty.pairs == 4 && empty.valuedPairs == 0 && Double.isNaN(empty.meanChange)
				&& Double.isNaN(empty.meanFoldChange), "no values: counted, statistics empty");

		// fold change ignores non-positive values, the difference does not
		String[] s2 = { "S", "S" };
		String[][] g2 = { {"A"}, {"B"} };
		Swap zero = new SwapSummary(new SubstituentPairs(s2, g2, 10).getPairs(), s2, g2, new double[] { 0, 5 }).getSwaps().get(0);
		check(close(zero.meanChange, 5) && Double.isNaN(zero.meanFoldChange), "zero value: change kept, fold change empty");

		if (sFailures != 0) {
			System.out.println("FAILED: "+sFailures+" check(s)");
			System.exit(1);
			}
		System.out.println("SwapSummaryTest passed: pooling across scaffolds, direction merge, missing and zero values, ordering");
		}

	private static Swap find(List<Swap> swaps, String from, String to) {
		for (Swap swap:swaps)
			if (swap.from.equals(from) && swap.to.equals(to))
				return swap;
		return null;
		}

	private static boolean close(double a, double b) {
		return Math.abs(a - b) < 1e-9;
		}

	private static void check(boolean condition, String message) {
		if (!condition) {
			sFailures++;
			System.out.println("  FAIL: "+message);
			}
		}
	}
