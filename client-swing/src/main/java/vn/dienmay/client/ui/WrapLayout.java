package vn.dienmay.client.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/** Flow toolbar whose preferred height includes wrapped rows rather than clipping action buttons. */
@SuppressWarnings("serial") // View state is never Java-serialized.
public final class WrapLayout extends FlowLayout {
  public WrapLayout() {
    super(FlowLayout.LEADING, 8, 8);
  }

  @Override
  public Dimension preferredLayoutSize(Container target) {
    synchronized (target.getTreeLock()) {
      Insets insets = target.getInsets();
      int available = target.getWidth();
      if (available <= 0 && target.getParent() != null) {
        available = target.getParent().getWidth();
      }
      if (available <= 0) {
        available = 1000;
      }
      available = Math.max(1, available - insets.left - insets.right - 2 * getHgap());
      int width = 0;
      int height = 0;
      int rowWidth = 0;
      int rowHeight = 0;
      for (Component component : target.getComponents()) {
        if (!component.isVisible()) {
          continue;
        }
        Dimension size = component.getPreferredSize();
        if (rowWidth > 0 && rowWidth + getHgap() + size.width > available) {
          width = Math.max(width, rowWidth);
          height += rowHeight + getVgap();
          rowWidth = 0;
          rowHeight = 0;
        }
        if (rowWidth > 0) {
          rowWidth += getHgap();
        }
        rowWidth += size.width;
        rowHeight = Math.max(rowHeight, size.height);
      }
      return new Dimension(Math.max(width, rowWidth) + insets.left + insets.right + 2 * getHgap(),
          height + rowHeight + insets.top + insets.bottom + 2 * getVgap());
    }
  }
}
