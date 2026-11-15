// Pyxis addition, GPLv3 or later. Document extension; never execute serialized content.
package com.actelion.research.datawarrior.pyxis;
import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public final class ScaffoldSession {
    public static final String NAME = "pyxisScaffoldTree";
    private static final String END = "</datawarrior "+NAME+">";
    private ScaffoldSession() {}
    public static void write(BufferedWriter out, Properties properties) throws IOException {
        out.write("<datawarrior "+NAME+">\n");
        // Base64 keeps newlines, Unicode and user column names out of the tag grammar.
        StringWriter text = new StringWriter(); properties.store(text, "Pyxis scaffold session v1");
        out.write(Base64.getEncoder().encodeToString(text.toString().getBytes(StandardCharsets.UTF_8)));
        out.write("\n"+END+"\n");
    }
    public static Properties read(BufferedReader in) {
        StringBuilder text = new StringBuilder();
        try {
            String line;
            while ((line=in.readLine())!=null && !line.equals(END)) {
                if (text.length()+line.length()>4000000) return null;
                text.append(line);
            }
            if (line==null) return null;
            Properties properties = new Properties();
            properties.load(new StringReader(new String(Base64.getDecoder().decode(text.toString()), StandardCharsets.UTF_8)));
            return properties;
        } catch (IOException | IllegalArgumentException e) { return null; }
    }
}
