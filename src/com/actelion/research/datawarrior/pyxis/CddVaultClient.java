// Pyxis addition; GPLv3 or later. Read-only, explicit-ID subset of the documented CDD API.
package com.actelion.research.datawarrior.pyxis;
import com.actelion.research.chem.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class CddVaultClient {
    public static final URI API=URI.create("https://app.collaborativedrug.com/api/v1/vaults/");
    public record Molecule(String id,String name,String idcode,String coordinates,String fields) {}
    private final URI api;
    public CddVaultClient() { this(API); }
    /** Alternate base is restricted to literal loopback HTTP for mock tests. */
    public CddVaultClient(URI api) {
        if (!api.equals(API) && !("http".equals(api.getScheme()) && "127.0.0.1".equals(api.getHost()) && api.getUserInfo()==null && api.getQuery()==null && api.getFragment()==null)) throw new IllegalArgumentException("Unsupported API server");
        this.api=api;
    }
    public static List<String> parseIDs(String text) {
        Set<String> ids=new LinkedHashSet<>();
        for (String id:text.trim().split("[\\s,]+")) {
            if (!id.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Enter positive numeric CDD molecule IDs, separated by commas or spaces.");
            ids.add(id);
        }
        if (ids.size()>100) throw new IllegalArgumentException("Import at most 100 explicit molecule IDs at a time.");
        return new ArrayList<>(ids);
    }
    public Molecule fetch(String vault,String id,String token) throws IOException {
        if (!vault.matches("[1-9][0-9]{0,18}") || !id.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Invalid vault or molecule ID");
        if (token==null || token.isBlank() || token.chars().anyMatch(c -> c<32 || c>126)) throw new IllegalArgumentException("Enter a nonempty printable API token.");
        HttpURLConnection connection=(HttpURLConnection)api.resolve(vault+"/molecules/"+id).toURL().openConnection();
        connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(15000); connection.setReadTimeout(30000);
        connection.setRequestProperty("X-CDD-Token",token); connection.setRequestProperty("Accept","application/json");
        try {
            int status=connection.getResponseCode();
            if (status!=200) throw new IOException("CDD returned HTTP "+status+" for molecule "+id+". No table was imported.");
            byte[] bytes;
            try (InputStream in=connection.getInputStream()) { bytes=in.readNBytes(8*1024*1024+1); }
            if (bytes.length>8*1024*1024) throw new IOException("CDD molecule response exceeds 8 MiB.");
            try {
                JSONObject object=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
                if (!id.equals(object.get("id").toString())) throw new IOException("CDD response molecule ID does not match the request.");
                StereoMolecule mol=new StereoMolecule();
                String molfile=object.optString("molfile","");
                if (!molfile.isEmpty()) {
                    if (!new MolfileParser().parse(mol,molfile)) throw new IOException("Cannot parse CDD molfile for molecule "+id);
                } else {
                    String smiles=object.optString("smiles","");
                    if (smiles.isEmpty()) throw new IOException("CDD molecule "+id+" has no supported chemical structure.");
                    new SmilesParser().parse(mol,smiles);
                    new com.actelion.research.chem.coords.CoordinateInventor().invent(mol);
                }
                if (mol.getAllAtoms()==0) throw new IOException("Empty CDD structure for molecule "+id);
                Canonizer canon=new Canonizer(mol);
                return new Molecule(id,object.optString("name",""),canon.getIDCode(),canon.getEncodedCoordinates(),object.optJSONObject("molecule_fields")==null ? "" : object.getJSONObject("molecule_fields").toString());
            } catch (IOException e) { throw e; }
              catch (Exception e) { throw new IOException("Unsupported CDD molecule response for ID "+id+". No table was imported."); }
        } finally { connection.disconnect(); }
    }
}
