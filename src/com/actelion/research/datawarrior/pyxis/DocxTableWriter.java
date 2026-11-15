// Pyxis addition; GPLv3 or later. Minimal Office Open XML table, no external dependencies.
package com.actelion.research.datawarrior.pyxis;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class DocxTableWriter {
    public static final int MAX_ROWS=1000, MAX_COLUMNS=30, MAX_CELLS=10000;
    public record Cell(String text, byte[] png) { public Cell(String text) { this(text,null); } }
    public static void checkSize(int rows, int columns) {
        if (rows>MAX_ROWS || columns>MAX_COLUMNS || (long)rows*columns>MAX_CELLS)
            throw new IllegalArgumentException("Word export supports at most 1,000 rows, 30 columns and 10,000 cells. Filter rows or hide columns first.");
    }
    private static String xml(String text) {
        StringBuilder s=new StringBuilder();
        if (text!=null) text.codePoints().filter(c -> c==9 || c==10 || c==13 || c>=32 && c<=0xD7FF || c>=0xE000 && c<=0xFFFD || c>=0x10000 && c<=0x10FFFF).forEach(s::appendCodePoint);
        return s.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    private static String paragraph(String text) {
        return "<w:p><w:r><w:t xml:space=\"preserve\">"+xml(text)+"</w:t></w:r></w:p>";
    }
    private static void entry(ZipOutputStream zip,String name,byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); zip.write(bytes); zip.closeEntry();
    }
    private static void entry(ZipOutputStream zip,String name,String text) throws IOException { entry(zip,name,text.getBytes(StandardCharsets.UTF_8)); }
    /** Creates a new file only. The temporary zip is renamed after every part is complete. */
    public static void write(Path file,List<String> headers,List<List<Cell>> rows) throws IOException {
        checkSize(rows.size(),headers.size());
        if (headers.isEmpty()) throw new IllegalArgumentException("No displayed columns to export.");
        for (List<Cell> row:rows) if (row.size()!=headers.size()) throw new IllegalArgumentException("Inconsistent table width.");
        if (Files.exists(file)) throw new IOException("File already exists; choose a new filename.");
        Path temp=Files.createTempFile(file.toAbsolutePath().getParent(),"pyxis-word-",".tmp");
        try {
            try (ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(temp))) {
                StringBuilder document=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><w:body><w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders><w:top w:val=\"single\"/><w:left w:val=\"single\"/><w:bottom w:val=\"single\"/><w:right w:val=\"single\"/><w:insideH w:val=\"single\"/><w:insideV w:val=\"single\"/></w:tblBorders></w:tblPr><w:tblGrid>");
                int width=Math.max(300,14400/headers.size());
                for (String h:headers) document.append("<w:gridCol w:w=\"").append(width).append("\"/>");
                document.append("</w:tblGrid><w:tr><w:trPr><w:tblHeader/></w:trPr>");
                for (String h:headers) document.append("<w:tc>").append(paragraph(h)).append("</w:tc>");
                document.append("</w:tr>");
                StringBuilder rels=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
                int image=0;
                long extent=Math.min(1905000L, (long)width*635);
                for (List<Cell> row:rows) {
                    document.append("<w:tr>");
                    for (Cell cell:row) {
                        document.append("<w:tc>");
                        if (cell.png()!=null) {
                            int id=++image;
                            entry(zip,"word/media/image"+id+".png",cell.png());
                            rels.append("<Relationship Id=\"rId").append(id).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image").append(id).append(".png\"/>");
                            document.append("<w:p><w:r><w:drawing><wp:inline><wp:extent cx=\"").append(extent).append("\" cy=\"").append(extent/2).append("\"/><wp:docPr id=\"").append(id).append("\" name=\"Structure ").append(id).append("\"/><a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic><pic:nvPicPr><pic:cNvPr id=\"0\" name=\"Structure\"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed=\"rId").append(id).append("\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"").append(extent).append("\" cy=\"").append(extent/2).append("\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>");
                        } else document.append(paragraph(cell.text()));
                        document.append("</w:tc>");
                    }
                    document.append("</w:tr>");
                }
                document.append("</w:tbl><w:sectPr><w:pgSz w:w=\"16838\" w:h=\"11906\" w:orient=\"landscape\"/><w:pgMar w:top=\"720\" w:right=\"720\" w:bottom=\"720\" w:left=\"720\"/></w:sectPr></w:body></w:document>");
                entry(zip,"word/document.xml",document.toString());
                entry(zip,"word/_rels/document.xml.rels",rels.append("</Relationships>").toString());
                entry(zip,"_rels/.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
                entry(zip,"[Content_Types].xml","<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Default Extension=\"png\" ContentType=\"image/png\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
            }
            Files.move(temp,file);
        } finally { Files.deleteIfExists(temp); }
    }
}
