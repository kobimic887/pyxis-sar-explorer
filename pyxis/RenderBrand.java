import java.io.*;
import org.apache.batik.transcoder.*;
import org.apache.batik.transcoder.image.PNGTranscoder;
public class RenderBrand {
 public static void main(String[] args) throws Exception {
  PNGTranscoder t = new PNGTranscoder();
  try (var in = new FileInputStream(args[0]); var out = new FileOutputStream(args[1])) {
   t.transcode(new TranscoderInput(in), new TranscoderOutput(out));
  }
 }
}
