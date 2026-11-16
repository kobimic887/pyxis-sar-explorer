/*
 * Pyxis SAR Explorer: builds the example project that ships inside the program
 * (src/html/pyxis/cox2-example.dwar) from pyxis/demo/cox2-chembl.tsv.
 *
 * The table holds 288 COX-2 inhibitors with their IC50 values as published in four
 * J. Med. Chem. papers, taken from ChEMBL (https://www.ebi.ac.uk/chembl, target CHEMBL230,
 * documents CHEMBL1130002, CHEMBL1130039, CHEMBL1133457 and CHEMBL1133620). ChEMBL data
 * is licensed CC BY-SA 3.0; the example file names this source in its explanation.
 *
 * Usage: pyxis/demo/make-example.sh   (from the repository root, after ./buildDataWarrior)
 * GNU General Public License v3 or later, WITHOUT ANY WARRANTY.
 */

import com.actelion.research.chem.Canonizer;
import com.actelion.research.chem.SmilesParser;
import com.actelion.research.chem.StereoMolecule;
import com.actelion.research.datawarrior.DEFrame;
import com.actelion.research.datawarrior.DERuntimeProperties;
import com.actelion.research.datawarrior.DataWarrior;
import com.actelion.research.datawarrior.DataWarriorLinux;
import com.actelion.research.table.CompoundTableSaver;
import com.actelion.research.table.model.CompoundTableEvent;
import com.actelion.research.table.model.CompoundTableModel;

import javax.swing.SwingUtilities;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MakeExample {
	public static final String EXPLANATION = "<html><body>"
		+"<h2>COX-2 inhibitors (example data)</h2>"
		+"<p>288 cyclooxygenase-2 inhibitors with the IC50 values published in four Journal of Medicinal Chemistry "
		+"papers (1997 and 2000), among them celecoxib. Use it to try the <b>SAR</b> menu: start with "
		+"<i>SAR &rarr; SAR Overview...</i> and the value column <i>COX-2 IC50 (nM)</i>.</p>"
		+"<p>Source: ChEMBL (https://www.ebi.ac.uk/chembl), target CHEMBL230, documents CHEMBL1130002, CHEMBL1130039, "
		+"CHEMBL1133457 and CHEMBL1133620. ChEMBL data is made available under the Creative Commons "
		+"Attribution-ShareAlike 3.0 Unported licence (https://creativecommons.org/licenses/by-sa/3.0/). "
		+"One row per molecule and document; values are as recorded in ChEMBL and were not curated further.</p>"
		+"</body></html>";

	private static final String[] COLUMN = { "Structure", "ChEMBL ID", "Name", "COX-2 IC50 (nM)", "pIC50", "Assay", "Document", "Source" };

	public static void main(String[] args) throws Exception {
		final List<String> lines = Files.readAllLines(Paths.get(args[0]));
		final File out = new File(args[1]);
		DataWarriorLinux.main(new String[0]);
		for (int i=0; i<120 && (DataWarrior.getApplication() == null || DataWarrior.getApplication().getFrameList().isEmpty()); i++)
			Thread.sleep(500);
		Thread.sleep(1500);

		final DEFrame[] frame = new DEFrame[1];
		SwingUtilities.invokeAndWait(() -> {
			try {
				frame[0] = DataWarrior.getApplication().getEmptyFrame("COX-2 inhibitors (example)");
				CompoundTableModel t = frame[0].getTableModel();
				t.initializeTable(lines.size(), COLUMN.length);
				for (int c=0; c<COLUMN.length; c++)
					t.setColumnName(COLUMN[c], c);
				t.setColumnProperty(0, CompoundTableModel.cColumnPropertySpecialType, CompoundTableModel.cColumnTypeIDCode);
				StereoMolecule mol = new StereoMolecule();
				for (int row=0; row<lines.size(); row++) {
					String[] field = lines.get(row).split("\t", -1);
					if (field.length != COLUMN.length)
						throw new IllegalStateException("line "+(row+1)+" has "+field.length+" fields");
					new SmilesParser().parse(mol, field[0]);
					t.setTotalValueAt(new Canonizer(mol).getIDCode(), row, 0);
					for (int c=1; c<COLUMN.length; c++)
						t.setTotalValueAt(field[c], row, c);
					}
				t.setExtensionData(CompoundTableModel.cExtensionNameFileExplanation, EXPLANATION);
				t.finalizeTable(CompoundTableEvent.cSpecifierDefaultFiltersAndViews, null);
				}
			catch (Exception e) { throw new RuntimeException(e); }
			});
		Thread.sleep(3000);

		final CountDownLatch saved = new CountDownLatch(1);
		final boolean[] ok = new boolean[1];
		CompoundTableSaver saver = new CompoundTableSaver(frame[0], frame[0].getTableModel(), frame[0].getMainFrame().getMainPane().getTable()) {
			public void finalStatus(File result) { ok[0] = (result != null); saved.countDown(); }
			};
		saver.saveNative(new DERuntimeProperties(frame[0].getMainFrame()), out, CompoundTableSaver.ROW_MASK_ALL, true);
		if (!saved.await(60, TimeUnit.SECONDS) || !ok[0]) {
			System.out.println("FAILED: example project was not saved");
			System.exit(1);
			}
		System.out.println("example project with "+lines.size()+" rows written to "+out);
		System.exit(0);
		}
	}
