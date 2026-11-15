// GPLv3 or later. Reopens every XML/PNG part rather than checking strings in generated XML.
import com.actelion.research.datawarrior.pyxis.DocxTableWriter;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import javax.xml.parsers.DocumentBuilderFactory;

public class DocxTableWriterTest {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("pyxis-docx-test"); Path file=dir.resolve("table.docx");
        ByteArrayOutputStream png=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(10,10,BufferedImage.TYPE_INT_RGB),"png",png);
        try {
            DocxTableWriter.write(file,List.of("Structure","ID & value"),List.of(List.of(new DocxTableWriter.Cell("",png.toByteArray()),new DocxTableWriter.Cell("α < β & γ\u0000")),List.of(new DocxTableWriter.Cell(""),new DocxTableWriter.Cell("line\nsecond"))));
            verify(file,3,1);
            try { DocxTableWriter.write(file,List.of("x"),List.of()); throw new AssertionError("overwrote file"); } catch(IOException expected) {}
            for (int[] size:new int[][]{{1001,1},{1,31},{501,20}}) {
                try { DocxTableWriter.checkSize(size[0],size[1]); throw new AssertionError("cap missing"); } catch(IllegalArgumentException expected) {}
            }
            DocxTableWriter.checkSize(1000,10);
            if (Files.isExecutable(Path.of("/usr/bin/textutil"))) {
                Path text=dir.resolve("table.txt");
                Process process=new ProcessBuilder("/usr/bin/textutil","-convert","txt","-output",text.toString(),file.toString()).inheritIO().start();
                if (process.waitFor()!=0 || !Files.readString(text).contains("α < β & γ")) throw new AssertionError("textutil could not read DOCX");
                System.out.println("textutil opened DOCX and preserved Unicode/text");
            }
            System.out.println("DocxTableWriterTest passed: 3 XML table rows, 1 embedded PNG; all XML parts parsed, limits and overwrite refusal");
        } finally {
            try (var files=Files.list(dir)) { for (Path p:files.toList()) Files.deleteIfExists(p); } Files.delete(dir);
        }
    }
    public static void verify(Path file,int rows,int images) throws Exception {
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        int png=0,refs=0;
        try (ZipFile zip=new ZipFile(file.toFile())) {
            for (var entries=zip.entries();entries.hasMoreElements();) {
                ZipEntry entry=entries.nextElement();
                try (InputStream in=zip.getInputStream(entry)) {
                    if (entry.getName().endsWith(".xml") || entry.getName().endsWith(".rels")) {
                        var document=factory.newDocumentBuilder().parse(in);
                        if (entry.getName().equals("word/document.xml")) {
                            if (document.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main","tr").getLength()!=rows) throw new AssertionError("row count");
                            if (document.getElementsByTagNameNS("http://schemas.openxmlformats.org/drawingml/2006/main","blip").getLength()!=images) throw new AssertionError("image references");
                        }
                        if (entry.getName().equals("word/_rels/document.xml.rels")) refs=document.getDocumentElement().getElementsByTagNameNS("http://schemas.openxmlformats.org/package/2006/relationships","Relationship").getLength();
                    } else if (entry.getName().endsWith(".png")) { if (ImageIO.read(in)==null) throw new AssertionError("invalid PNG"); png++; }
                }
            }
        }
        if (png!=images || refs!=images) throw new AssertionError("media/relationship count");
    }
}
