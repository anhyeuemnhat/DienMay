package vn.dienmay.client.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import javax.swing.JPanel;

/** Translucent decoration only. Tables and editable controls keep their own opaque backgrounds. */
@SuppressWarnings("serial") // View state is never Java-serialized.
public class SurfacePanel extends JPanel {
  private final Color fill;
  private final int radius;

  public SurfacePanel(LayoutManager layout, Color fill, int radius) {
    super(layout);
    this.fill = fill;
    this.radius = radius;
    setOpaque(false);
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    Graphics2D canvas = (Graphics2D) graphics.create();
    try {
      canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      canvas.setColor(fill);
      canvas.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
    } finally {
      canvas.dispose();
    }
  }
}
