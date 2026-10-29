package vn.dienmay.client.ui;

import java.awt.BorderLayout;
import java.text.MessageFormat;
import java.time.LocalDate;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import vn.dienmay.client.AppContext;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.api.Operation;
import vn.dienmay.common.model.Sale;

/** Searches persisted invoices and reprints the persisted snapshot, never the current cart. */
@SuppressWarnings("serial") // View state is never Java-serialized.
public final class ReceiptsUI extends RowsFrame {
  private final JTextField search = new JTextField(20);
  private final JTextField from = new JTextField(Ui.DATE.format(LocalDate.now().withDayOfMonth(1)), 10);
  private final JTextField to = new JTextField(Ui.DATE.format(LocalDate.now()), 10);

  public ReceiptsUI(AppContext app) {
    super(app, "Tra cứu hóa đơn", "Tìm theo số hóa đơn, khách hàng, điện thoại hoặc nhân viên");
    actions.add(search);
    actions.add(new JLabel("Từ ngày:"));
    actions.add(from);
    actions.add(new JLabel("Đến:"));
    actions.add(to);
    actions.add(Ui.button("Tìm", this::reload));
    actions.add(Ui.button("Xem / in lại", this::showSelected));
    reload();
  }

  private void reload() {
    load(Operation.INVOICES, Data.map("text", search.getText(), "from", Ui.date(from), "to", Ui.date(to)));
  }

  private void showSelected() {
    long id = Json.number(rows.selected().get("Id")).longValueExact();
    app.request(this, Operation.INVOICE, Data.map("id", id), result -> show(app, this, Json.convert(result,
        Sale.class)));
  }

  public static void show(AppContext app, java.awt.Component owner, Sale sale) {
    app.request(owner, Operation.SETTINGS, Data.map(), settings -> {
      java.util.Map<String, Object> store = Json.object(settings);
      WorkFrame receipt = new WorkFrame(app, "Hóa đơn " + sale.code(),
          "Bản ghi đã lưu • Chứng từ nội bộ") {};
      JTextArea text = new JTextArea(sale.generateReceipt(Data.text(store, "name"),
          Data.text(store, "address"), Data.text(store, "phone")));
      text.setEditable(false);
      text.setLineWrap(true);
      text.setWrapStyleWord(true);
      text.setMargin(new java.awt.Insets(22, 26, 22, 26));
      text.setFont(new java.awt.Font(java.awt.Font.DIALOG, java.awt.Font.PLAIN, 16));
      receipt.body.add(Ui.scroll(text), BorderLayout.CENTER);
      receipt.body.add(Ui.toolbar(Ui.button("In / in lại hóa đơn", () -> {
        try {
          text.print(null, new MessageFormat("Trang {0}"));
        } catch (java.awt.print.PrinterException ex) {
          Ui.error(receipt, ex);
        }
      })), BorderLayout.SOUTH);
      receipt.setVisible(true);
    });
  }
}
