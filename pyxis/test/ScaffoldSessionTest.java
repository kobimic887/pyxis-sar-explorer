import com.actelion.research.datawarrior.pyxis.ScaffoldSession;
import java.io.*;
import java.util.*;
public class ScaffoldSessionTest {
 public static void main(String[] args) throws Exception {
  Properties p=new Properties(); p.setProperty("structureColumn","Molecules Ω\n<tag>"); p.setProperty("minMolecules","3"); p.setProperty("userScaffolds","a b\tc d"); p.setProperty("statisticsColumn","Ki"); p.setProperty("subset","List: active");
  StringWriter text=new StringWriter(); BufferedWriter writer=new BufferedWriter(text); ScaffoldSession.write(writer,p); writer.flush();
  BufferedReader reader=new BufferedReader(new StringReader(text.toString())); reader.readLine();
  if (!p.equals(ScaffoldSession.read(reader))) throw new AssertionError("roundtrip");
  if (ScaffoldSession.read(new BufferedReader(new StringReader("invalid!\n</datawarrior pyxisScaffoldTree>\n")))!=null) throw new AssertionError("malformed");
  if (ScaffoldSession.read(new BufferedReader(new StringReader("YWJj")))!=null) throw new AssertionError("truncated");
  System.out.println("ScaffoldSessionTest passed: Unicode, multiline, properties roundtrip; malformed/truncated refused");
 }
}
