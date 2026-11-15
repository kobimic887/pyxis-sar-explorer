// GPLv3 or later. All HTTP calls stay on a local mock; no live CDD account/token needed.
import com.actelion.research.datawarrior.pyxis.CddVaultClient;
import com.actelion.research.chem.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;

public class CddVaultClientTest {
    public static void main(String[] args) throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        int[] calls={0};
        server.createContext("/api/v1/vaults/23/molecules/",exchange -> {
            calls[0]++;
            if (!exchange.getRequestMethod().equals("GET") || !"mock-token".equals(exchange.getRequestHeaders().getFirst("X-CDD-Token"))) { exchange.sendResponseHeaders(401,-1); exchange.close(); return; }
            String id=exchange.getRequestURI().getPath().replaceAll(".*/", "");
            int status=id.equals("404")?404:id.equals("302")?302:200;
            String payload=switch(id) {
                case "1" -> "{\"id\":1,\"name\":\"Taurine\",\"smiles\":\"NCCS(O)(=O)=O\",\"molecule_fields\":{\"Ki\":2.5}}";
                case "2" -> "{\"id\":2,\"smiles\":\"N[C@@H](C)C(=O)O\"}";
                case "6" -> {
                    StereoMolecule mol=new StereoMolecule();
                    try { new SmilesParser().parse(mol,"CCO"); } catch(Exception e) { throw new IOException(e); }
                    yield new org.json.JSONObject().put("id",6).put("molfile",new MolfileCreator(mol).getMolfile()).toString();
                }
                case "3" -> "{\"id\":999,\"smiles\":\"C\"}";
                case "4" -> "{\"id\":4}";
                default -> "malformed";
            };
            byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            CddVaultClient client=new CddVaultClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1/vaults/"));
            var molecule=client.fetch("23","1","mock-token");
            if (!molecule.id().equals("1") || !molecule.name().equals("Taurine") || !molecule.fields().contains("2.5") || new IDCodeParser().getCompactMolecule(molecule.idcode()).getAllAtoms()!=7) throw new AssertionError("mapping");
            var stereo=client.fetch("23","2","mock-token");
            StereoMolecule expected=new StereoMolecule(); new SmilesParser().parse(expected,"N[C@@H](C)C(=O)O");
            if (!new Canonizer(expected).getIDCode().equals(stereo.idcode())) throw new AssertionError("stereo mapping");
            for (String id:List.of("3","4","5","404","302")) {
                try { client.fetch("23",id,"mock-token"); throw new AssertionError("accepted invalid response "+id); } catch(IOException expectedFailure) {}
            }
            try { client.fetch("23","1","wrong-token"); throw new AssertionError("accepted auth failure"); } catch(IOException expectedFailure) {}
            if (new IDCodeParser().getCompactMolecule(client.fetch("23","6","mock-token").idcode()).getAllAtoms()!=3) throw new AssertionError("molfile mapping");
            try { client.fetch("23","1","secret\nvalue"); throw new AssertionError("bad token accepted"); } catch(IllegalArgumentException expectedFailure) { if (expectedFailure.getMessage().contains("secret")) throw new AssertionError("token echoed"); }
            if (calls[0]!=9) throw new AssertionError("unexpected retry/redirect");
            if (!CddVaultClient.parseIDs("1, 2 1").equals(List.of("1","2"))) throw new AssertionError("ID normalization");
            for (String bad:List.of("", "../x", "0", "1?token=secret")) {
                try { CddVaultClient.parseIDs(bad); throw new AssertionError("bad ID accepted"); } catch(IllegalArgumentException expectedFailure) {}
            }
            try { new CddVaultClient(URI.create("https://example.com/")); throw new AssertionError("arbitrary host accepted"); } catch(IllegalArgumentException expectedFailure) {}
            System.out.println("CddVaultClientTest passed: 9 loopback requests; token header, mapping/stereo, missing/invalid structures, ID mismatch, 401/404, no redirect");
        } finally { server.stop(0); }
    }
}
