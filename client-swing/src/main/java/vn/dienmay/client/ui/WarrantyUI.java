package vn.dienmay.client.ui;

import javax.swing.JComboBox;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import vn.dienmay.client.AppContext;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.api.Operation;

/** Warranty lookup, intake, status transition and immutable status history are all wired to the API. */
@SuppressWarnings("serial") // View state is never Java-serialized.
public final class WarrantyUI extends RowsFrame {
  private final JTextField search = new JTextField(28);

  public WarrantyUI(AppContext app) {
    super(app, "Bảo hành", "Tìm mã bảo hành, hóa đơn, Serial hoặc điện thoại");
    rows.columns("Id", "Code", "InvoiceCode", "SKU", "ProductName", "Serial", "CustomerName", "CustomerPhone",
        "StartDate", "EndDate", "WarrantyMonths", "CoveredQuantity", "Validity");
    GuidedInput.install(search, "Mã bảo hành / hóa đơn / Serial / điện thoại");
    actions.add(search);
    actions.add(Ui.button("Tra cứu", this::reload));
    actions.add(Ui.button("Tiếp nhận sửa chữa", this::receive));
    actions.add(Ui.button("Phiếu của bảo hành", () -> claims(Json.number(rows.selected().get("Id")).longValueExact())));
    actions.add(Ui.button("Tất cả phiếu", () -> claims(null)));
    search.addActionListener(event -> reload());
    reload();
  }

  private void reload() {
    load(Operation.WARRANTIES, Data.map("text", search.getText()));
  }

  private void receive() {
    long warranty = Json.number(rows.selected().get("Id")).longValueExact();
    JTextArea fault = new JTextArea(5, 35);
    fault.setLineWrap(true);
    fault.setWrapStyleWord(true);
    new EditDialog(app, this, "Tiếp nhận bảo hành",
        Ui.fields("Mô tả lỗi *", Ui.scroll(fault)), Operation.CLAIM_RECEIVE,
        () -> Data.map("warranty", warranty, "fault", fault.getText()), result -> {
          Ui.message(this, "Đã tạo phiếu: " + result);
          claims(warranty);
        }).open();
  }

  private void claims(Long warranty) {
    RowsFrame frame = new RowsFrame(app, "Phiếu bảo hành", "Trạng thái và lịch sử xử lý");
    frame.rows.columns("Id", "WarrantyCode", "ReceivedAt", "Fault", "EmployeeName", "Status", "Notes", "CompletedAt");
    Runnable refresh = () -> frame.load(Operation.CLAIMS, Data.map("warranty", warranty));
    frame.actions.add(Ui.button("Tải lại", refresh));
    frame.actions.add(Ui.button("Lịch sử trạng thái", () -> {
      long claim = Json.number(frame.rows.selected().get("Id")).longValueExact();
      RowsFrame history = new RowsFrame(app, "Lịch sử phiếu " + claim, "Không chỉnh sửa lịch sử");
      history.rows.columns("Id", "ChangedAt", "EmployeeName", "OldStatus", "NewStatus", "Notes");
      history.open();
      history.load(Operation.CLAIM_HISTORY, Data.map("claim", claim));
    }));
    if (Operation.CLAIM_TRANSITION.allows(app.session().role())) {
      frame.actions.add(Ui.button("Chuyển trạng thái", () -> {
        java.util.Map<String, Object> selected = frame.rows.selected();
        long claim = Json.number(selected.get("Id")).longValueExact();
        String expected = selected.get("Status").toString();
        String[] allowed = expected.equals("RECEIVED") ? new String[] {"PROCESSING", "REJECTED"}
            : expected.equals("PROCESSING") ? new String[] {"COMPLETED", "REJECTED"} : new String[0];
        if (allowed.length == 0) {
          throw new IllegalArgumentException("Phiếu đã kết thúc, không mở lại.");
        }
        JComboBox<String> next = new JComboBox<>(allowed);
        JTextArea notes = new JTextArea(4, 30);
        new EditDialog(app, frame, "Chuyển trạng thái phiếu",
            Ui.fields("Trạng thái mới", next, "Ghi chú *", Ui.scroll(notes)), Operation.CLAIM_TRANSITION,
            () -> Data.map("claim", claim, "expected", expected, "next", next.getSelectedItem(),
                "notes", notes.getText()), result -> refresh.run()).open();
      }));
    }
    frame.open();
    refresh.run();
  }
}
