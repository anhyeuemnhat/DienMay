package vn.dienmay.client.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;

/** Cross-platform button states with readable disabled text and an explicit keyboard focus ring. */
@SuppressWarnings("serial") // View state is never Java-serialized.
public final class ThemeButton extends JButton {
  private final Color fill;

  public ThemeButton(String title, Color fill, Color text) {
    super(title);
    this.fill = fill;
    setForeground(text);
    setBackground(fill);
    setFont(Ui.TEXT_FONT.deriveFont(java.awt.Font.BOLD));
    setMargin(new Insets(9, 15, 9, 15));
    setContentAreaFilled(false);
    setBorderPainted(false);
    setOpaque(false);
    setFocusPainted(false);
    setRolloverEnabled(true);
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    setUI(new BasicButtonUI() {
      @Override
      protected void paintText(Graphics graphics, AbstractButton button, Rectangle rect, String text) {
        graphics.setColor(button.isEnabled() ? button.getForeground() : Ui.DISABLED_TEXT);
        BasicGraphicsUtils.drawStringUnderlineCharAt(graphics, text, button.getDisplayedMnemonicIndex(),
            rect.x, rect.y + graphics.getFontMetrics().getAscent());
      }
    });
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D canvas = (Graphics2D) graphics.create();
    try {
      canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      Color background = !isEnabled() ? Ui.DISABLED_BACKGROUND
          : getModel().isPressed() ? fill.darker() : getModel().isRollover()
          ? (fill.equals(Color.WHITE) ? Ui.ALTERNATE_ROW : fill.darker()) : fill;
      canvas.setColor(background);
      canvas.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 10, 10);
      canvas.setColor(isEnabled() ? (fill.equals(Color.WHITE) ? Ui.CONTROL_BORDER : fill)
          : Ui.CONTROL_BORDER);
      canvas.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 10, 10);
      if (hasFocus()) {
        canvas.setColor(Ui.FOCUS);
        canvas.setStroke(new BasicStroke(2f));
        canvas.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
      }
    } finally {
      canvas.dispose();
    }
    super.paintComponent(graphics);
  }
}
