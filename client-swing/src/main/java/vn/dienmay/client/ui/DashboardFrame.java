package vn.dienmay.client.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import vn.dienmay.client.AppContext;

/** Shared role workspace; the original permission registry still drives every shortcut and menu. */
@SuppressWarnings("serial") // View state is never Java-serialized.
public class DashboardFrame extends WorkFrame {
  protected DashboardFrame(AppContext app, String title) {
    super(app, title, "Xin chào " + app.session().name() + "  |  " + app.session().role());
    workspaceMode("home");
    JPanel shortcuts = new ShortcutGrid();
    shortcuts.setOpaque(false);
    for (Navigation.Destination destination : Navigation.destinations(app)) {
      JPanel card = new SurfacePanel(new BorderLayout(4, 4), Ui.CARD, 14);
      card.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
      JLabel group = new JLabel(destination.group());
      group.setForeground(Ui.MUTED);
      card.add(group, BorderLayout.NORTH);
      card.add(Ui.button(destination.title(), () -> Navigation.open(app, destination)), BorderLayout.CENTER);
      shortcuts.add(card);
    }
    JPanel content = new ScrollablePanel(new BorderLayout(12, 12));
    content.setOpaque(false);
    content.add(shortcuts, BorderLayout.NORTH);
    JTextArea guidance = new JTextArea("QUY TRÌNH LÀM VIỆC\n"
        + "1. Danh mục: chuẩn bị sản phẩm, khách hàng và nhà cung cấp.\n"
        + "2. Cập nhật / Xử lý: nhập hàng, chuyển ra quầy, lập hóa đơn.\n"
        + "3. Tra cứu / Báo cáo: kiểm tra chứng từ, Serial, bảo hành và doanh thu.\n"
        + "Khi mất kết nối: không tạo yêu cầu mới. Giữ mã giao dịch và chọn Đối soát.\n"
        + "Các nút không thuộc vai trò hiện tại sẽ không xuất hiện.");
    guidance.setEditable(false);
    guidance.setLineWrap(true);
    guidance.setWrapStyleWord(true);
    guidance.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
    guidance.setRows(6);
    content.add(guidance, BorderLayout.CENTER);
    javax.swing.JScrollPane scroll = Ui.backgroundScroll(content);
    body.add(scroll, BorderLayout.CENTER);
    javax.swing.SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
    message("API: " + app.gateway().profile() + "  |  Phiên hết hạn: " + app.session().expiresAt());
  }

  @Override
  protected void closeRequested() {
    app.logout(this);
  }

  private static final class ShortcutGrid extends JPanel {
    private final GridLayout grid = new GridLayout(0, 4, 10, 10);

    private ShortcutGrid() {
      setLayout(grid);
    }

    private int columns() {
      int width = getParent() == null ? 980 : getParent().getWidth();
      return width > 0 ? Math.max(1, Math.min(4, (width + 10) / 235)) : 4;
    }

    @Override
    public Dimension getPreferredSize() {
      int columns = columns();
      int rows = (getComponentCount() + columns - 1) / columns;
      return new Dimension(220 * columns + 10 * (columns - 1), rows * 80 + 10 * Math.max(0, rows - 1));
    }

    @Override
    public void doLayout() {
      grid.setColumns(columns());
      super.doLayout();
    }
  }
}
