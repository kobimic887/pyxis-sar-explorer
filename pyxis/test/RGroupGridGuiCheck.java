import com.actelion.research.datawarrior.*;
import com.actelion.research.datawarrior.pyxis.*;
import com.actelion.research.chem.Canonizer;
import com.actelion.research.chem.SmilesParser;
import com.actelion.research.chem.StereoMolecule;
import com.actelion.research.table.model.*;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
public class RGroupGridGuiCheck {
 public static void main(String[] args) {try {check(args);System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
 static void check(String[] args)throws Exception {
  Thread.setDefaultUncaughtExceptionHandler((t,e)->{e.printStackTrace();System.exit(1);}); DataWarriorLinux.main(new String[]{args[0]});
  DEFrame opened=null;
  for(int i=0;i<120&&opened==null;i++){Thread.sleep(500);DataWarrior app=DataWarrior.getApplication();if(app==null)continue;
   for(DEFrame f:app.getFrameList())opened=f;}
  if(opened==null)throw new AssertionError("input file did not open");
  // Numeric fixture inside the running app: one scaffold (benzene), duplicate F/Cl combinations
  // with a missing Ki, a hydrogen-only row and one combination that no molecule has.
  final DEFrame[] frameRef=new DEFrame[1];
  SwingUtilities.invokeAndWait(()->{
   try{frameRef[0]=DataWarrior.getApplication().getEmptyFrame("Grid fixture");
   CompoundTableModel t=frameRef[0].getTableModel();
   t.initializeTable(5,2);
   t.setColumnName("Structure",0);t.setColumnName("Ki",1);
   t.setColumnProperty(0,CompoundTableModel.cColumnPropertySpecialType,CompoundTableModel.cColumnTypeIDCode);
   StereoMolecule m=new StereoMolecule();
   String[] smiles={"c1ccccc1","Fc1ccccc1Cl","Fc1ccc(Cl)cc1","Fc1ccccc1Cl","Fc1ccccc1Cl"};
   for(int r=0;r<5;r++){new SmilesParser().parse(m,smiles[r]);t.setTotalValueAt(new Canonizer(m).getIDCode(),r,0);}
   t.setTotalValueAt("1",0,1);t.setTotalValueAt("2",1,1);t.setTotalValueAt("3",2,1);t.setTotalValueAt("4",3,1);
   t.finalizeTable(CompoundTableEvent.cSpecifierDefaultFiltersAndViews,null);
   }catch(Exception e){throw new RuntimeException(e);}
  });
  DEFrame source=frameRef[0]; CompoundTableModel model=source.getTableModel();
  // Decompose through the real task: Structure, scaffoldMode=murcko, new columns.
  Properties decomp=new Properties();
  decomp.setProperty("structureColumn","Structure");decomp.setProperty("scaffoldMode","murcko");decomp.setProperty("useExistingColumns","false");
  new com.actelion.research.datawarrior.task.chem.DETaskDecomposeRGroups(source).executeAsSubtask(decomp,null);
  if(model.findColumn("R1")<0||model.findColumn("R2")<0)throw new AssertionError("Decomposition did not create R1/R2");
  // R-group cells hold the decomposition's substituent idcodes; read them from the table.
  Set<String> rowCodes=new TreeSet<>(),columnCodes=new TreeSet<>();
  for(int r=0;r<model.getTotalRowCount();r++){String a=model.getTotalValueAt(r,model.findColumn("R1")),b=model.getTotalValueAt(r,model.findColumn("R2"));if(!a.isEmpty())rowCodes.add(a);if(!b.isEmpty())columnCodes.add(b);}
  if(rowCodes.size()!=1||columnCodes.size()!=1)throw new AssertionError("fixture ambiguity: "+rowCodes+" "+columnCodes);
  String idF=rowCodes.iterator().next(),idCl=columnCodes.iterator().next();
  // Run the real task configuration and independently count one cell from the table.
  Properties p=new Properties(); DETaskCreateRGroupGrid task=new DETaskCreateRGroupGrid(source);
  SwingUtilities.invokeAndWait(()->{task.createDialogContent();task.setDialogConfigurationToDefault();p.putAll(task.getDialogConfiguration());});
  if(!"Scaffold".equals(p.getProperty("scaffoldColumn"))||!"R1".equals(p.getProperty("rowColumn"))
   ||!"R2".equals(p.getProperty("columnColumn"))||!"Ki".equals(p.getProperty("valueColumn"))
   ||!"mean".equals(p.getProperty("aggregate"))||!"frequency".equals(p.getProperty("order")))
   throw new AssertionError("unexpected dialog defaults: "+p);
  if(!task.isConfigurationValid(p,true))throw new AssertionError("config invalid: "+p);
  task.executeAsSubtask(p,null);SwingUtilities.invokeAndWait(()->{});
  RGroupGridDialog dialog=null;
  for(int i=0;i<40&&dialog==null;i++){Thread.sleep(500);for(java.awt.Window w:java.awt.Window.getWindows())if(w instanceof RGroupGridDialog)dialog=(RGroupGridDialog)w;}
  if(dialog==null)throw new AssertionError("grid missing"); RGroupGridDialog d=dialog;
  int rc=model.findColumn(p.getProperty("rowColumn")),cc=model.findColumn(p.getProperty("columnColumn")),sc=model.findColumn(p.getProperty("scaffoldColumn")),vc=model.findColumn(p.getProperty("valueColumn"));
  // Frequency order: rows F(4), H(1); columns Cl(3), H(2).
  if(!d.grid.rows.equals(List.of(idF,""))||!d.grid.columns.equals(List.of(idCl,"")))throw new AssertionError("axis order: "+d.grid.rows+" "+d.grid.columns);
  RGroupGrid.Cell known=d.grid.cell(0,0); // (F,Cl): rows 1,3,4; Ki 2 and 4 finite -> mean 3, n includes the missing value
  if(known==null||known.count()!=3||Math.abs(known.value()-3.0)>1e-8)throw new AssertionError("known cell (F,Cl): "+known);
  RGroupGrid.Cell hydrogenCell=d.grid.cell(1,1); // (H,H): benzene row 0
  if(hydrogenCell==null||hydrogenCell.count()!=1||Math.abs(hydrogenCell.value()-1.0)>1e-8)throw new AssertionError("hydrogen cell: "+hydrogenCell);
  RGroupGrid.Cell swapped=d.grid.cell(0,1); // (F,H): meta row 2
  if(swapped==null||swapped.count()!=1||Math.abs(swapped.value()-3.0)>1e-8)throw new AssertionError("(F,H) cell: "+swapped);
  if(d.grid.cell(1,0)!=null)throw new AssertionError("(H,Cl) must be empty");
  // Independent count straight from the table, ignoring the grid internals.
  Set<CompoundRecord> expected=Collections.newSetFromMap(new IdentityHashMap<>()); double sum=0;int finite=0;
  for(int r=0;r<model.getTotalRowCount();r++)if(p.getProperty("scaffold").equals(model.getTotalValueAt(r,sc))&&idF.equals(model.getTotalValueAt(r,rc))&&idCl.equals(model.getTotalValueAt(r,cc))){expected.add(model.getTotalRecord(r));double v=model.getTotalOriginalDoubleAt(r,vc);if(Double.isFinite(v)){sum+=v;finite++;}}
  if(known.count()!=expected.size()||finite!=2||Math.abs(known.value()-sum/finite)>1e-8)throw new AssertionError("independent cell aggregation: n="+expected.size()+" mean="+(sum/finite));
  // Click the known cell and assert exactly those records are selected in the source table.
  final int rr=0,ccc=0;
  SwingUtilities.invokeAndWait(()->{ java.awt.Rectangle b=d.table.getCellRect(rr,ccc+1,true);d.table.dispatchEvent(new java.awt.event.MouseEvent(d.table,java.awt.event.MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,b.x+5,b.y+5,1,false)); });
  for(int r=0;r<model.getTotalRowCount();r++)if(model.getTotalRecord(r).isSelected()!=expected.contains(model.getTotalRecord(r)))throw new AssertionError("cell selection identity at row "+r);
  File png=new File(args[1].replaceFirst("\\.png$", "-grid.png"));png.delete();SwingUtilities.invokeAndWait(()->{try{d.exportImage(png);}catch(Exception e){throw new RuntimeException(e);}});if(ImageIO.read(png)==null)throw new AssertionError("PNG decode");
  DEFrame[] exported={null}; SwingUtilities.invokeAndWait(()->exported[0]=d.exportTable());
  int nonempty=0;for(int r=0;r<d.grid.rows.size();r++)for(int c=0;c<d.grid.columns.size();c++)if(d.grid.cell(r,c)!=null)nonempty++;
  if(exported[0].getTableModel().getTotalRowCount()!=nonempty)throw new AssertionError("export rows: "+exported[0].getTableModel().getTotalRowCount()+" != "+nonempty);
  File dwar=new File(args[1].replaceFirst("\\.png$","-grid.dwar"));CountDownLatch saved=new CountDownLatch(1);
  var saver=new com.actelion.research.table.CompoundTableSaver(exported[0],exported[0].getTableModel(),exported[0].getMainFrame().getMainPane().getTable()){public void finalStatus(File f){if(f==null)throw new AssertionError("save");saved.countDown();}};
  saver.saveNative(new DERuntimeProperties(exported[0].getMainFrame()),dwar,com.actelion.research.table.CompoundTableSaver.ROW_MASK_ALL,true);if(!saved.await(30,TimeUnit.SECONDS))throw new AssertionError("save timeout");
  DEFrame reopened=new com.actelion.research.datawarrior.task.file.DETaskOpenFile(DataWarrior.getApplication()).openFile(dwar,new Properties());
  var result=reopened.getTableModel();var em=exported[0].getTableModel();
  // finalizeTable auto-adds a FragFp descriptor child for the first structure column, so
  // exported vs reopened column order differs; address grid columns by name in both models.
  if(result.getTotalRowCount()!=nonempty)throw new AssertionError("reopened export rows: "+result.getTotalRowCount()+" (expected "+nonempty+")");
  for(String name:new String[]{d.rowName(),d.columnName(),d.valueName(),"Molecule Count"}) {
    int a=result.findColumn(name),b=em.findColumn(name);
    if(a<0||b<0)throw new AssertionError("reopened export missing column '"+name+"' (reopened@"+a+" exported@"+b+")");
    if(name.equals(d.rowName())||name.equals(d.columnName())) { if(!result.isColumnTypeStructure(a)||!em.isColumnTypeStructure(b))throw new AssertionError("reopened export column '"+name+"' lost its structure type"); }
    for(int r=0;r<nonempty;r++)if(!result.getTotalValueAt(r,a).equals(em.getTotalValueAt(r,b)))throw new AssertionError("reopened values row "+r+" '"+name+"': '"+result.getTotalValueAt(r,a)+"' vs '"+em.getTotalValueAt(r,b)+"'");
    }
  java.nio.file.Path word=java.nio.file.Path.of(args[1].replaceFirst("\\.png$","-grid.docx"));java.nio.file.Files.deleteIfExists(word);DETaskExportWord.export(reopened,word);try(var zip=new java.util.zip.ZipFile(word.toFile())){if(zip.getEntry("word/document.xml")==null)throw new AssertionError("Word export");}
  // Sparse grid with guaranteed empty cells: rows H(2)/Cl(1), columns Cl(2)/H(1);
  // inputs cover (H,Cl) n=2 mean=4 and (Cl,H) n=1 mean=10, so (H,H) and (Cl,Cl) are empty.
  String other=new Canonizer(new SmilesParser().parseMolecule("Cl")).getIDCode();
  RGroupGrid sparse=new RGroupGrid(List.of(new RGroupGrid.Input("",other,2,0),new RGroupGrid.Input("",other,6,1),new RGroupGrid.Input(other,"",10,2)),RGroupGrid.Aggregate.mean,false);
  if(sparse.cell(0,0).count()!=2||Math.abs(sparse.cell(0,0).value()-4.0)>1e-8||sparse.cell(0,1)!=null||sparse.cell(1,0)!=null||sparse.cell(1,1)==null||Math.abs(sparse.cell(1,1).value()-10.0)>1e-8)throw new AssertionError("sparse cells");
  final RGroupGridDialog[] sparseRef=new RGroupGridDialog[1];
  SwingUtilities.invokeAndWait(()->{try{sparseRef[0]=new RGroupGridDialog(source,sparse,"R1","R2","mean Ki");sparseRef[0].setVisible(true);
   var label=(JLabel)sparseRef[0].table.prepareRenderer(sparseRef[0].table.getCellRenderer(0,2),0,2);
   if(!label.getText().equals("Empty"))throw new AssertionError("empty renderer text: "+label.getText());
   sparseRef[0].selectCell(0,0);}catch(Exception e){throw new RuntimeException(e);}});
  RGroupGridDialog s=sparseRef[0];
  // Selection propagated to source records 0 and 1 while the source is open.
  for(int r=0;r<model.getTotalRowCount();r++)if(model.getTotalRecord(r).isSelected()!=(r<2))throw new AssertionError("sparse selection at row "+r);
  File emptyPng=new File(args[1].replaceFirst("\\.png$","-grid-empty.png"));emptyPng.delete();SwingUtilities.invokeAndWait(()->{try{s.exportImage(emptyPng);}catch(Exception e){throw new RuntimeException(e);}});if(ImageIO.read(emptyPng)==null)throw new AssertionError("empty PNG decode");
  // Closing the source detaches the links: further dialog selection must not touch the table.
  source.dispose();SwingUtilities.invokeAndWait(()->{try{s.selectCell(1,1);}catch(Exception e){throw new RuntimeException(e);}});
  for(int r=0;r<model.getTotalRowCount();r++)if(model.getTotalRecord(r).isSelected()!=(r<2))throw new AssertionError("source close must detach selection links at row "+r);
  s.dispose();d.dispose();
  System.out.println("RGroupGrid GUI PASS: fixture "+model.getTotalRowCount()+" rows decomposed; cell (R1,R2) independently n="+expected.size()+" mean="+(sum/finite)+" (n includes "+(expected.size()-finite)+" missing); hydrogen cell n=1 mean=1.0; (R1,H) n=1 mean=3.0; (H,R2) empty; exact source selection of "+expected.size()+" records; "+nonempty+" cells exported, saved, reopened and Word-exported; sparse n=2 mean=4.0 with 'Empty' cell; source close detached");
 }
}
