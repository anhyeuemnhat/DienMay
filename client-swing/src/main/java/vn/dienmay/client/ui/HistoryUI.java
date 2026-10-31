package vn.dienmay.client.ui;

import javax.swing.JLabel;
import javax.swing.JTextField;
import vn.dienmay.client.AppContext;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Operation;

/** Stock movement, individual serial history and low-stock read models. */
@SuppressWarnings("serial") // View state is never Java-serialized.
public final class HistoryUI extends RowsFrame {
  private final Operation operation;
  private final JTextField search = new JTextField(28);

  public HistoryUI(AppContext app, boolean serial) {
    this(app, serial ? Operation.SERIAL_HISTORY : Operation.STOCK_HISTORY);
  }

  public HistoryUI(AppContext app, Operation operation) {
    super(app, operation == Operation.LOW_STOCK ? "Cảnh báo tồn kho"
        : operation == Operation.SERIAL_HISTORY ? "Lịch sử Serial / IMEI" : "Lịch sử kho",
        "Dữ liệu chứng từ đã lưu; giờ hiển thị theo Việt Nam");
    this.operation = operation;
    if (operation == Operation.SERIAL_HISTORY) {
      rows.columns("Serial", "IdentifierType", "IdentifierSource", "SKU", "Status", "CreatedAt", "Kind", "Location",
          "Delta", "Reference");
    } else if (operation == Operation.LOW_STOCK) {
      rows.columns("Id", "SKU", "Name", "TotalStock", "ReorderLevel");
    } else {
      rows.columns("Id", "CreatedAt", "SKU", "Location", "Delta", "Kind", "Reference", "EmployeeName", "UnitCost",
          "RequestId");
    }
    GuidedInput.install(search, operation == Operation.SERIAL_HISTORY ? "Nhập chính xác mã trên thiết bị" :
        "Nhập SKU nội bộ");
    if (operation != Operation.LOW_STOCK) {
      actions.add(new JLabel(operation == Operation.SERIAL_HISTORY ? "Serial / IMEI chính xác:" : "SKU:"));
      actions.add(search);
      search.addActionListener(event -> reload());
    }
    actions.add(Ui.button("Tra cứu", this::reload));
    if (operation != Operation.SERIAL_HISTORY) {
      reload();
    }
  }

  private void reload() {
    load(operation, operation == Operation.LOW_STOCK ? Data.map()
        : Data.map(operation == Operation.SERIAL_HISTORY ? "serial" : "sku", search.getText()));
  }
}
