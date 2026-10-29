package vn.dienmay.client.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.swing.JPanel;

/** Classpath-backed, left-anchored cover image. Never paints over Swing children. */
@SuppressWarnings("serial") // View state is never Java-serialized.
public final class BackgroundPanel extends JPanel {
  public static final String RESOURCE = "/images/red_mascot_background.png";
  private static final BufferedImage IMAGE = loadImage();
  private BufferedImage scaled;
  private int cachedWidth;
  private int cachedHeight;

  public BackgroundPanel(LayoutManager layout) {
    super(layout);
    setOpaque(true);
    setBackground(new Color(197, 0, 9));
    setName("redMascotBackground");
  }

  private static BufferedImage loadImage() {
    try (InputStream stream = BackgroundPanel.class.getResourceAsStream(RESOURCE)) {
      if (stream == null) {
        throw new IOException("Missing classpath resource: " + RESOURCE);
      }
      BufferedImage image = ImageIO.read(stream);
      if (image == null) {
        throw new IOException("Invalid PNG: " + RESOURCE);
      }
      return image;
    } catch (IOException ex) {
      throw new IllegalStateException("Không tải được ảnh nền. "
          + "Kiểm tra tài nguyên trong client-swing.jar.", ex);
    }
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    int width = getWidth();
    int height = getHeight();
    if (width <= 0 || height <= 0) {
      return;
    }
    if (scaled == null || width != cachedWidth || height != cachedHeight) {
      if (scaled != null) {
        scaled.flush();
      }
      scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      Graphics2D canvas = scaled.createGraphics();
      try {
        canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double ratio = Math.max((double) width / IMAGE.getWidth(), (double) height / IMAGE.getHeight());
        int imageWidth = (int) Math.ceil(IMAGE.getWidth() * ratio);
        int imageHeight = (int) Math.ceil(IMAGE.getHeight() * ratio);
        // Keep the mascot on the left; crop only excess right/top/bottom, never stretch.
        canvas.drawImage(IMAGE, 0, (height - imageHeight) / 2, imageWidth, imageHeight, null);
      } finally {
        canvas.dispose();
      }
      cachedWidth = width;
      cachedHeight = height;
    }
    graphics.drawImage(scaled, 0, 0, null);
  }

  @Override
  public void removeNotify() {
    if (scaled != null) {
      scaled.flush();
      scaled = null;
    }
    super.removeNotify();
  }
}
