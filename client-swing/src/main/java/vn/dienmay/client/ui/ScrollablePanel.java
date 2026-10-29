package vn.dienmay.client.ui;

import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/** Width-tracking content for responsive dashboards; tall content remains vertically scrollable. */
@SuppressWarnings("serial") // View state is never Java-serialized.
public final class ScrollablePanel extends JPanel implements Scrollable {
  public ScrollablePanel(LayoutManager layout) {
    super(layout);
    setOpaque(false);
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
    return 24;
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
    return Math.max(24, (orientation == SwingConstants.VERTICAL ? visible.height : visible.width) - 24);
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return getParent() instanceof JViewport viewport && viewport.getHeight() > getPreferredSize().height;
  }
}
