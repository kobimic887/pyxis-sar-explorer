import com.actelion.research.datawarrior.help.DEAboutDialog;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.io.File;
public class RenderAbout {
 public static void main(String[] args) throws Exception {
  SwingUtilities.invokeAndWait(() -> {
   try {
    var panel = DEAboutDialog.createImagePanel();
    panel.setSize(panel.getPreferredSize()); panel.doLayout();
    var image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics(); panel.printAll(graphics); graphics.dispose();
    ImageIO.write(image, "png", new File(args[0]));
    System.out.println("PASS: rendered actual Pyxis About panel " + panel.getWidth() + "x" + panel.getHeight());
   } catch (Exception e) { throw new RuntimeException(e); }
  });
 }
}
