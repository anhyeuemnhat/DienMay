package vn.dienmay.client.ui;

import java.util.List;
import java.util.function.Function;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import vn.dienmay.client.AppContext;
import vn.dienmay.common.api.Operation;

/** Functional grouping follows ptud p.159; access is derived from the shared operation policy. */
public final class Navigation {
  public record Destination(String group, String title, Operation permission,
      Function<AppContext, WorkFrame> create, boolean showOnDashboard) {
    public Destination(String group, String title, Operation permission, Function<AppContext, WorkFrame> create) {
      this(group, title, permission, create, true);
    }
  }

  private static final String SYSTEM = "Hệ thống";
  private static final String CATALOG = "Danh mục";
  private static final String UPDATE = "Cập nhật";
  private static final String PROCESS = "Xử lý";
  private static final String SEARCH = "Tra cứu";
  private static final String REPORT = "Báo cáo";
  private static final List<Destination> DESTINATIONS = List.of(
      new Destination(SYSTEM, "Kiểm chứng dữ liệu", Operation.QUALITY_LIST, QualityUI::new, false),
      new Destination(SYSTEM, "Nhân viên / tài khoản", Operation.EMPLOYEES,
          app -> new PeopleUI(app, PeopleUI.Kind.EMPLOYEES)),
      new Destination(CATALOG, "Sản phẩm / tồn kho", Operation.PRODUCT_SEARCH, ProductsUI::new),
      new Destination(CATALOG, "Khách hàng", Operation.CUSTOMERS,
          app -> new PeopleUI(app, PeopleUI.Kind.CUSTOMERS)),
      new Destination(CATALOG, "Nhà cung cấp", Operation.VENDORS,
          app -> new PeopleUI(app, PeopleUI.Kind.VENDORS)),
      new Destination(CATALOG, "Loại sản phẩm", Operation.LOOKUP_SAVE,
          app -> new PeopleUI(app, PeopleUI.Kind.CATEGORIES)),
      new Destination(CATALOG, "Thương hiệu", Operation.LOOKUP_SAVE,
          app -> new PeopleUI(app, PeopleUI.Kind.BRANDS)),
      new Destination(UPDATE, "Nhập hàng", Operation.RECEIVE, StockReceiveUI::new),
      new Destination(UPDATE, "Khuyến mãi", Operation.PROMOTIONS,
          app -> new PeopleUI(app, PeopleUI.Kind.PROMOTIONS)),
      new Destination(PROCESS, "Bán hàng", Operation.CHECKOUT, MakeSaleUI::new),
      new Destination(PROCESS, "Chuyển kho / quầy", Operation.TRANSFER, TransferFromWarehouse::new),
      new Destination(SEARCH, "Hóa đơn", Operation.INVOICES, ReceiptsUI::new),
      new Destination(SEARCH, "Lịch sử kho", Operation.STOCK_HISTORY,
          app -> new HistoryUI(app, false)),
      new Destination(SEARCH, "Lịch sử Serial / IMEI", Operation.SERIAL_HISTORY,
          app -> new HistoryUI(app, true)),
      new Destination(SEARCH, "Bảo hành", Operation.WARRANTIES, WarrantyUI::new),
      new Destination(REPORT, "Doanh thu / bán chạy", Operation.REPORT, ReportsUI::new),
      new Destination(REPORT, "Cảnh báo tồn kho", Operation.LOW_STOCK,
          app -> new HistoryUI(app, Operation.LOW_STOCK)));

  private Navigation() {}

  public static List<Destination> destinations(AppContext app) {
    return DESTINATIONS.stream().filter(d -> d.permission().allows(app.session().role())).toList();
  }

  public static void open(AppContext app, Destination destination) {
    if (!destination.permission().allows(app.session().role())) {
      throw new SecurityException("Không có quyền mở chức năng này.");
    }
    app.open(destination.title(), () -> destination.create().apply(app));
  }

  public static JMenuBar menu(AppContext app, java.awt.Component owner) {
    JMenuBar bar = new JMenuBar();
    bar.setBackground(Ui.HEADER);
    bar.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 12, 3, 12));
    for (String group : List.of(SYSTEM, CATALOG, UPDATE, PROCESS, SEARCH, REPORT)) {
      JMenu menu = new JMenu(group);
      menu.setForeground(java.awt.Color.WHITE);
      menu.setFont(Ui.TEXT_FONT.deriveFont(java.awt.Font.BOLD));
      menu.setMargin(new java.awt.Insets(6, 12, 6, 12));
      for (Destination destination : destinations(app)) {
        if (destination.group().equals(group)) {
          JMenuItem item = new JMenuItem(destination.title());
          item.addActionListener(event -> Ui.safe(owner, () -> open(app, destination)));
          menu.add(item);
        }
      }
      if (group.equals(SYSTEM)) {
        menu.addSeparator();
        JMenuItem password = new JMenuItem("Đổi mật khẩu");
        password.addActionListener(event -> app.changePassword(owner, false));
        menu.add(password);
        JMenuItem logout = new JMenuItem("Đăng xuất");
        logout.addActionListener(event -> app.logout(owner));
        menu.add(logout);
      }
      menu.setEnabled(menu.getItemCount() > 0);
      bar.add(menu);
    }
    return bar;
  }
}
