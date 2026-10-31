package vn.dienmay.client.ui;

import java.time.LocalDate;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import vn.dienmay.client.AppContext;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Operation;

/** Revenue, employee totals and best sellers use server-side aggregates without serial-join inflation. */
@SuppressWarnings("serial") // View state is never Java-serialized.
public final class ReportsUI extends RowsFrame {
  private final JComboBox<String> type = new JComboBox<>(new String[] {
      "Tổng hợp doanh thu", "Theo nhân viên", "Sản phẩm bán chạy"});
  private final JTextField from = new JTextField(Ui.DATE.format(LocalDate.now().withDayOfMonth(1)), 10);
  private final JTextField to = new JTextField(Ui.DATE.format(LocalDate.now()), 10);

  public ReportsUI(AppContext app) {
    super(app, "Báo cáo bán hàng", "Chọn khoảng ngày • Số liệu được tính tại backend");
    rows.columns("InvoiceCount", "Subtotal", "Discount", "NetRevenue");
    GuidedInput.install(from, "dd/MM/yyyy"); GuidedInput.install(to, "dd/MM/yyyy");
    actions.add(type);
    actions.add(new JLabel("Từ (dd/MM/yyyy):"));
    actions.add(from);
    actions.add(new JLabel("Đến:"));
    actions.add(to);
    actions.add(Ui.button("Lập báo cáo", this::reload));
    reload();
  }

  private void reload() {
    String report = switch (type.getSelectedIndex()) {
      case 1 -> "employee";
      case 2 -> "best";
      default -> "summary";
    };
    rows.setData(java.util.List.of());
    switch (report) {
      case "employee" -> rows.columns("UserName", "FullName", "InvoiceCount", "NetRevenue");
      case "best" -> rows.columns("SKU", "ProductName", "Quantity", "GrossSales");
      default -> rows.columns("InvoiceCount", "Subtotal", "Discount", "NetRevenue");
    }
    load(Operation.REPORT, Data.map("type", report, "from", Ui.date(from), "to", Ui.date(to)));
  }
}
