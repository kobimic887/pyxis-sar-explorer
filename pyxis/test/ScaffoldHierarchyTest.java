/*
 * Pyxis SAR Explorer self-test. Run with pyxis/run-tests.sh; exits non-zero on failure.
 * GNU General Public License v3 or later, WITHOUT ANY WARRANTY.
 */

import com.actelion.research.chem.SmilesParser;
import com.actelion.research.chem.StereoMolecule;
import com.actelion.research.chem.io.SDFileParser;
import com.actelion.research.datawarrior.pyxis.ScaffoldHierarchy;
import com.actelion.research.datawarrior.pyxis.ScaffoldHierarchy.Node;

import java.util.BitSet;
import java.util.List;

public class ScaffoldHierarchyTest {
	private static int sFailures;

	public static void main(String[] args) throws Exception {
		testScaffoldIdentity();
		testKnownTree();
		testCentralRingIsNotRemoved();
		for (String file:args)
			testCollection(file);

		if (sFailures != 0) {
			System.out.println("FAILED: "+sFailures+" check(s)");
			System.exit(1);
			}
		System.out.println("ScaffoldHierarchyTest passed");
		}

	private static String scaffold(String smiles) throws Exception {
		StereoMolecule mol = new StereoMolecule();
		new SmilesParser().parse(mol, smiles);
		return ScaffoldHierarchy.murckoScaffoldIDCode(mol);
		}

	private static void check(boolean condition, String message) {
		if (!condition) {
			sFailures++;
			System.out.println("  FAIL: "+message);
			}
		}

	private static Node find(List<Node> nodes, String idcode) {
		for (Node node:nodes)
			if (node.getIDCode().equals(idcode))
				return node;
		return null;
		}

	/** The same ring system must give the same scaffold whatever it is decorated with. */
	private static void testScaffoldIdentity() throws Exception {
		check(scaffold("CCO") == null, "ethanol has no scaffold");
		check(scaffold("c1ccccc1").equals(scaffold("Cc1ccccc1O")), "toluene/phenol reduce to benzene");
		check(scaffold("C1=CC=CC=C1").equals(scaffold("c1ccccc1")), "Kekule and aromatic benzene agree");
		check(scaffold("C[C@H](N)c1ccccc1").equals(scaffold("C[C@@H](N)c1ccccc1")), "stereo is ignored");
		check(!scaffold("c1ccccc1").equals(scaffold("c1ccncc1")), "benzene differs from pyridine");
		}

	/**
	 * benzene x2, naphthalene x2, indole x1, biphenyl x1:
	 * benzene must be the single root covering all six; both Kekule forms of the
	 * fused parents must give the same benzene.
	 */
	private static void testKnownTree() throws Exception {
		String[] smiles = { "Cc1ccccc1", "Oc1ccccc1", "Cc1cccc2ccccc12", "C1=CC2=CC=CC=C2C=C1",
				"Cc1c[nH]c2ccccc12", "Cc1ccc(cc1)-c1ccccc1", "CCCC" };
		ScaffoldHierarchy hierarchy = new ScaffoldHierarchy();
		for (int i=0; i<smiles.length; i++)
			hierarchy.addRow(i, scaffold(smiles[i]));
		hierarchy.build(1);

		String benzene = scaffold("c1ccccc1");
		Node root = find(hierarchy.getRoots(), benzene);
		check(root != null, "benzene is a root");
		if (root == null)
			return;
		check(root.getMemberCount() == 6, "benzene covers 6 molecules, found "+root.getMemberCount());
		check(root.getExactMemberCount() == 2, "benzene is the exact scaffold of 2, found "+root.getExactMemberCount());

		Node naphthalene = find(root.getChildren(), scaffold("c1ccc2ccccc2c1"));
		check(naphthalene != null && naphthalene.getMemberCount() == 2, "naphthalene is a child of benzene with 2 molecules");
		check(find(root.getChildren(), scaffold("c1ccc(cc1)-c1ccccc1")) != null, "biphenyl is a child of benzene");

		String indole = scaffold("c1ccc2[nH]ccc2c1");
		Node pyrrole = find(hierarchy.getRoots(), scaffold("c1cc[nH]c1"));
		check(pyrrole != null && pyrrole.getMemberCount() == 1, "pyrrole is a root covering the indole");
		// indole has two parents with unequal coverage and must hang below the bigger one
		check(find(root.getChildren(), indole) != null, "indole hangs below benzene, its most populous parent");
		check(pyrrole == null || find(pyrrole.getChildren(), indole) == null, "indole is not duplicated below pyrrole");

		check(hierarchy.getRowsWithoutScaffold().cardinality() == 1, "butane is reported as having no scaffold");
		check(hierarchy.getNodesInDisplayOrder().get(0) == root, "most populous scaffold is listed first");
		}

	/** Removing the middle ring of a three ring chain would split it and is not a parent. */
	private static void testCentralRingIsNotRemoved() throws Exception {
		ScaffoldHierarchy hierarchy = new ScaffoldHierarchy();
		hierarchy.addRow(0, scaffold("c1ccc(cc1)-c1ccc(cc1)-c1ccncc1"));
		hierarchy.build(1);
		List<Node> all = hierarchy.getNodesInDisplayOrder();
		check(find(all, scaffold("c1ccc(cc1)-c1ccccc1")) != null, "biphenyl is an ancestor of the terphenyl analog");
		check(find(all, scaffold("c1ccc(cc1)-c1ccncc1")) != null, "phenylpyridine is an ancestor");
		for (Node node:all)
			check(node.getMemberCount() == 1, "every ancestor covers the single molecule");
		check(all.size() == 5, "expected 5 scaffolds (1 three-ring, 2 two-ring, 2 one-ring), found "+all.size());
		}

	/** Invariants that must hold for any real collection. */
	private static void testCollection(String fileName) throws Exception {
		SDFileParser parser = new SDFileParser(fileName);
		ScaffoldHierarchy hierarchy = new ScaffoldHierarchy();
		int rows = 0;
		int withRings = 0;
		long start = System.currentTimeMillis();
		while (parser.next()) {
			StereoMolecule mol = parser.getMolecule();
			String idcode = ScaffoldHierarchy.murckoScaffoldIDCode(mol);
			if (idcode != null)
				withRings++;
			hierarchy.addRow(rows++, idcode);
			}
		hierarchy.build(1);
		long millis = System.currentTimeMillis() - start;

		BitSet covered = new BitSet();
		int exact = 0;
		for (Node node:hierarchy.getNodesInDisplayOrder()) {
			exact += node.getExactMemberCount();
			if (node.getParent() == null)
				covered.or(node.getMembers());
			else {
				BitSet outside = (BitSet)node.getMembers().clone();
				outside.andNot(node.getParent().getMembers());
				check(outside.isEmpty(), "a child scaffold has molecules its parent lacks");
				check(node.getRingCount() == node.getParent().getRingCount()+1, "a child has exactly one more ring than its parent");
				}
			}
		check(exact == withRings, "every ring molecule has exactly one own scaffold: "+exact+" vs "+withRings);
		check(covered.cardinality() == withRings, "roots cover all ring molecules: "+covered.cardinality()+" vs "+withRings);
		check(withRings + hierarchy.getRowsWithoutScaffold().cardinality() == rows, "all rows are accounted for");

		System.out.println(fileName+": "+rows+" rows, "+withRings+" with rings, "
				+hierarchy.getNodesInDisplayOrder().size()+" scaffolds, "+hierarchy.getRoots().size()+" roots, "+millis+" ms");
		}
	}
