import com.actelion.research.datawarrior.pyxis.RGroupGrid;
import java.util.*;
public class RGroupGridTest {
 public static void main(String[] args) {
  var input=List.of(new RGroupGrid.Input(null,"a",2,0),new RGroupGrid.Input("","a",6,1),new RGroupGrid.Input("","a",Double.NaN,2),new RGroupGrid.Input("b","c",10,3));
  double[] expected={4,4,2,6,3};int i=0;
  for(var a:RGroupGrid.Aggregate.values()) {var g=new RGroupGrid(input,a,false);if(g.cell(0,0).value()!=expected[i++]||g.cell(0,0).count()!=3||g.cell(0,1)!=null)throw new AssertionError(a);}
  var g=new RGroupGrid(input,RGroupGrid.Aggregate.mean,true);if(!g.rows.equals(List.of("","b")))throw new AssertionError("mean order");
  var missing=new RGroupGrid(List.of(new RGroupGrid.Input("","",Double.POSITIVE_INFINITY,0)),RGroupGrid.Aggregate.mean,false);if(!Double.isNaN(missing.cell(0,0).value()))throw new AssertionError("nonfinite");
  var cap=new ArrayList<RGroupGrid.Input>();for(int n=0;n<101;n++)cap.add(new RGroupGrid.Input("r"+n,"c",0,n));
  try {new RGroupGrid(cap,RGroupGrid.Aggregate.mean,false);throw new AssertionError("axis cap");}catch(IllegalArgumentException ok){}
  cap.clear();for(int n=0;n<51;n++)cap.add(new RGroupGrid.Input("r"+n,"c"+n,0,n));
  try {new RGroupGrid(cap,RGroupGrid.Aggregate.mean,false);throw new AssertionError("cell cap");}catch(IllegalArgumentException ok){}
  var order=new RGroupGrid(List.of(new RGroupGrid.Input("b","b",1,0),new RGroupGrid.Input("a","a",10,1)),RGroupGrid.Aggregate.mean,true);if(!order.rows.equals(List.of("b","a")))throw new AssertionError("ascending mean");
  System.out.println("RGroupGridTest passed: duplicate count 3, finite mean/median 4, min 2/max 6; hydrogen, missing/nonfinite, empty cells, order and both caps");
 }
}
