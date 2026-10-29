package vn.dienmay.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/** The only text in the brand strip; fit the entire exact title instead of using ellipses. */
@SuppressWarnings("serial") // View state is never Java-serialized.
public final class BrandHeading extends SurfacePanel {
  private final JLabel title = new JLabel(Ui.APP_TITLE, SwingConstants.CENTER);

  public BrandHeading() {
    super(new BorderLayout(), new Color(65, 5, 15, 238), 0);
    setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
    title.setName("applicationBrandTitle");
    title.setForeground(Color.WHITE);
    title.setFont(new Font(Font.DIALOG, Font.BOLD, 24));
    title.setToolTipText(Ui.APP_TITLE);
    title.getAccessibleContext().setAccessibleName(Ui.APP_TITLE);
    add(title, BorderLayout.CENTER);
  }

  @Override
  public void doLayout() {
    int available = Math.max(1, getWidth() - getInsets().left - getInsets().right);
    int size = 24;
    Font font = new Font(Font.DIALOG, Font.BOLD, size);
    while (size > 9 && title.getFontMetrics(font).stringWidth(Ui.APP_TITLE) > available) {
      font = font.deriveFont((float) --size);
    }
    if (!font.equals(title.getFont())) {
      title.setFont(font);
    }
    super.doLayout();
  }
}
