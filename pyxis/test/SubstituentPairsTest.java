/*
 * Pyxis SAR Explorer self-test. Run with pyxis/run-tests.sh; exits non-zero on failure.
 * GNU General Public License v3 or later, WITHOUT ANY WARRANTY.
 */

import com.actelion.research.datawarrior.pyxis.SubstituentPairs;
import com.actelion.research.datawarrior.pyxis.SubstituentPairs.Pair;

import java.util.List;

public class SubstituentPairsTest {
	private static int sFailures;

	public static void main(String[] args) {
		String[] scaffold = { "S1", "S1", "S1", "S1", "S2", null, "S1" };
		String[][] groups = {
				{ "Me", "H" },		// 0
				{ "Et", "H" },		// 1: differs from 0 at R1 only
				{ "Et", "Cl" },		// 2: differs from 1 at R2 only, from 0 at both
				{ "Me", null },		// 3: null equals "" but not "H"; differs from 0 at R2 only
				{ "Pr", "H" },		// 4: other scaffold, never paired with S1 rows
				{ "Pr", "H" },		// 5: no scaffold, skipped
				{ "Me", "H" },		// 6: identical to 0, not a pair with it
				};

		SubstituentPairs pairs = new SubstituentPairs(scaffold, groups, 1000);
		List<Pair> list = pairs.getPairs();

		check(has(list, 0, 1, 0), "0/1 differ at R1");
		check(has(list, 1, 2, 1), "1/2 differ at R2");
		check(has(list, 0, 3, 1), "0/3 differ at R2 (H versus none)");
		check(has(list, 1, 6, 0), "1/6 differ at R1");
		check(has(list, 3, 6, 1), "3/6 differ at R2");
		check(!has(list, 0, 2, 0) && !has(list, 0, 2, 1), "0/2 differ at two positions");
		check(!has(list, 0, 6, 0) && !has(list, 0, 6, 1), "identical decorations are not a pair");
		for (Pair p:list)
			check(p.rowA != 4 && p.rowB != 4 && p.rowA != 5 && p.rowB != 5, "other-scaffold and no-scaffold rows are never paired");
		check(list.size() == 5, "expected 5 pairs, found "+list.size());
		check(!pairs.isTruncated(), "not truncated");

		SubstituentPairs capped = new SubstituentPairs(scaffold, groups, 2);
		check(capped.getPairs().size() == 2 && capped.isTruncated(), "pair limit is honoured and reported");

		if (sFailures != 0) {
			System.out.println("FAILED: "+sFailures+" check(s)");
			System.exit(1);
			}
		System.out.println("SubstituentPairsTest passed");
		}

	private static boolean has(List<Pair> list, int a, int b, int position) {
		for (Pair p:list)
			if (p.rowA == a && p.rowB == b && p.position == position)
				return true;
		return false;
		}

	private static void check(boolean condition, String message) {
		if (!condition) {
			sFailures++;
			System.out.println("  FAIL: "+message);
			}
		}
	}
