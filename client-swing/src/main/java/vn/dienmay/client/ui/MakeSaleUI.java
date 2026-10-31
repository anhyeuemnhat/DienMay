package vn.dienmay.client.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import vn.dienmay.client.AppContext;
import vn.dienmay.client.controller.SaleController;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.api.Operation;
import vn.dienmay.common.model.Cart;
import vn.dienmay.common.model.Item;
import vn.dienmay.common.model.ItemQuantity;
import vn.dienmay.common.model.Money;
import vn.dienmay.common.model.Sale;
import vn.dienmay.common.model.SaleRequest;
import vn.dienmay.common.model.Validation;

/** Real POS workflow: scan, choose serials, quote, confirm, persist, commit and reconcile. */
@SuppressWarnings("serial") // View state is never Java-serialized.
public final class MakeSaleUI extends WorkFrame {
  private final Cart cart = new Cart();
  private final SaleController controller;
  private final RowsPanel lines = new RowsPanel();
  private final JTextField code = new JTextField(22);
  private final JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, 100000, 1));
  private final JTextField serial = new JTextField(22);
  private final JTextField customer = new JTextField(20);
  private final JLabel customerName = new JLabel("Khách lẻ");
  private final JTextField manual = new JTextField("0", 20);
  private final JTextField promotion = new JTextField(20);
  private final JComboBox<String> payment = new JComboBox<>(new String[] {"Tiền mặt", "Chuyển khoản"});
  private final JLabel subtotal = new JLabel("0 ₫");
  private final JLabel discount = new JLabel("0 ₫");
  private final JLabel total = new JLabel("0 ₫");
  private final JPanel inputs;
  private final JPanel checkoutFields;
  private final JButton remove;
  private final JButton checkout;
  private final JButton retry;
  private final JButton resolve;
  private final JButton cancel;
  private boolean closed;

  public MakeSaleUI(AppContext app) {
    super(app, "Bán hàng", "Giỏ hàng là bản nháp • Chỉ xác nhận thanh toán khi backend ghi thành công");
    try {
      controller = new SaleController(app.gateway(), app.pendingDirectory(), app.session().employeeId());
    } catch (IOException ex) {
      throw new IllegalStateException("Không mở được yêu cầu chờ; không xóa tệp. " + ex.getMessage(), ex);
    }
    workspaceMode("dense");
    lines.dataContext(app::environmentMode);
    lines.columns("Id", "SKU", "Name", "Quantity", "UnitPrice", "Total", "Serial");
    GuidedInput.install(code, "Quet / nhap SKU hoac ma vach da doi chieu");
    GuidedInput.install(serial, "Ma tren thiet bi; de trong de chon tu ton quay");
    code.setName("productCode");
    code.addActionListener(event -> Ui.safe(this, this::add));
    inputs = Ui.toolbar(new JLabel("SKU / mã vạch:"), code,
        new JLabel("SL:"), quantity, new JLabel("Serial (nếu có):"), serial,
        Ui.button("Thêm vào giỏ", this::add));
    body.add(inputs, BorderLayout.NORTH);
    JPanel right = new JPanel(new BorderLayout(10, 12));
    right.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(),
        BorderFactory.createEmptyBorder(8, 8, 8, 8)));
    checkoutFields = Ui.fields("Mã khách (trống = lẻ)", customer,
        "Khách hàng", customerName,
        "Chọn khách", Ui.button("Tra cứu khách hàng", this::chooseCustomer),
        "Thanh toán", payment, "Giảm trực tiếp", manual, "Mã khuyến mãi", promotion,
        "Tạm tính", subtotal, "Giảm giá", discount, "TỔNG TIỀN", total);
    total.setFont(total.getFont().deriveFont(java.awt.Font.BOLD, 24f));
    Ui.compactFields(checkoutFields);
    right.add(checkoutFields, BorderLayout.NORTH);
    javax.swing.JTextArea hint = new javax.swing.JTextArea("Chỉ chọn giảm trực tiếp HOẶC mã khuyến mãi.\n\n"
        + "Backend kiểm tra lại giá, quyền, khuyến mãi, tồn và Serial ngay khi thanh toán.\n\n"
        + "Mất mạng không có nghĩa là thất bại. Giữ yêu cầu và đối soát.");
    hint.setEditable(false);
    hint.setLineWrap(true);
    hint.setWrapStyleWord(true);
    hint.setBorder(BorderFactory.createEmptyBorder(15, 12, 12, 12));
    right.add(hint, BorderLayout.CENTER);
    javax.swing.JScrollPane checkoutScroll = Ui.scroll(right);
    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, lines, checkoutScroll);
    split.setResizeWeight(0.55);
    body.add(split, BorderLayout.CENTER);
    remove = Ui.button("Xóa dòng giỏ", () -> {
      cart.remove(Json.number(lines.selected().get("Id")).longValueExact());
      refresh();
    });
    checkout = Ui.button("Kiểm tra / thanh toán", this::checkout);
    retry = Ui.button("Thử lại cùng mã", this::retry);
    resolve = Ui.button("Đối soát", this::resolve);
    cancel = Ui.button("Yêu cầu hủy an toàn", this::cancel);
    body.add(Ui.toolbar(remove, checkout, retry, resolve, cancel), BorderLayout.SOUTH);
    javax.swing.SwingUtilities.invokeLater(() -> {
      split.setDividerLocation(0.55);
      checkoutScroll.getVerticalScrollBar().setValue(0);
    });
    refresh();
  }

  private void add() {
    String inputCode = code.getText().trim();
    int count = Ui.integer(quantity);
    List<String> enteredSerials = Validation.serials(serial.getText());
    String bearer = app.token();
    app.run(this, () -> {
      Item item = Json.convert(app.gateway().call(Operation.PRODUCT_FIND, bearer,
          Data.map("code", inputCode)), Item.class);
      List<String> available = item.trackSerial() && enteredSerials.isEmpty()
          ? Json.list(app.gateway().call(Operation.SERIALS, bearer,
              Data.map("id", item.id(), "location", "QUAY")), String.class) : List.of();
      return new ProductSelection(item, available);
    }, selection -> {
      List<String> chosen = enteredSerials;
      if (selection.item().trackSerial() && chosen.isEmpty()) {
        JList<String> list = new JList<>(selection.available().toArray(String[]::new));
        list.setVisibleRowCount(12);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        if (Ui.option(this, Ui.scroll(list), "Chọn " + count + " Serial / IMEI",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
          return;
        }
        chosen = list.getSelectedValuesList();
      }
      cart.add(selection.item(), count, chosen);
      code.setText("");
      serial.setText("");
      quantity.setValue(1);
      refresh();
      code.requestFocusInWindow();
    });
  }

  private record ProductSelection(Item item, List<String> available) {}

  private void chooseCustomer() {
    RowsFrame frame = new RowsFrame(app, "Chọn khách hàng", "Tìm theo tên hoặc điện thoại");
    frame.rows.columns("Id", "FullName", "Phone", "Address", "Enabled", "Revision");
    JTextField query = new JTextField(24);
    GuidedInput.install(query, "Tim khach hang da duoc cung cap hop le");
    Runnable search = () -> frame.load(Operation.CUSTOMERS, Data.map("text", query.getText()));
    frame.actions.add(query);
    frame.actions.add(Ui.button("Tìm", search));
    frame.actions.add(Ui.button("Chọn khách này", () -> {
      if (controller.pending() != null || app.jobs() > 0) {
        throw new IllegalStateException("Không đổi khách của yêu cầu đang chờ.");
      }
      Map<String, Object> selected = frame.rows.selected();
      customer.setText(Json.number(selected.get("Id")).toPlainString());
      customerName.setText(String.valueOf(selected.get("FullName")));
      frame.dispose();
    }));
    frame.open();
    search.run();
  }

  private void checkout() {
    if (cart.lines().isEmpty()) {
      throw new IllegalArgumentException("Giỏ hàng rỗng.");
    }
    Long customerId = customer.getText().isBlank() ? null : Long.valueOf(customer.getText().trim());
    if (customerId != null && customerId <= 0) {
      throw new IllegalArgumentException("Mã khách hàng phải là số dương.");
    }
    BigDecimal manualValue = Money.of(manual.getText());
    String promotionCode = promotion.getText().trim();
    String method = payment.getSelectedIndex() == 0 ? "CASH" : "TRANSFER";
    List<ItemQuantity> snapshot = cart.lines();
    String bearer = app.token();
    app.run(this, () -> {
      if (customerId != null) {
        app.gateway().call(Operation.CUSTOMER, bearer, Data.map("id", customerId));
      }
      return Json.object(app.gateway().call(Operation.QUOTE, bearer,
          Data.map("lines", snapshot, "code", promotionCode, "manual", manualValue)));
    }, quote -> {
      BigDecimal reduction = Json.number(quote.get("discount"));
      BigDecimal due = Json.number(quote.get("total"));
      discount.setText(Money.format(reduction));
      total.setText(Money.format(due));
      if (!Ui.confirm(this, "Xác nhận thanh toán " + Money.format(due) + "?")) {
        return;
      }
      SaleRequest request = new SaleRequest(UUID.randomUUID(), customerId, method, manualValue,
          promotionCode, reduction, snapshot);
      app.run(this, () -> controller.submit(bearer, request), this::completed, this::failed);
    });
  }

  private void retry() {
    String bearer = app.token();
    app.run(this, () -> controller.retry(bearer), this::completed, this::failed);
  }

  private void resolve() {
    String bearer = app.token();
    app.run(this, () -> controller.resolve(bearer), sale -> {
      if (sale == null) {
        refresh();
        Ui.message(this, "Chưa thấy hóa đơn đã ghi. Giữ nguyên mã; thử lại hoặc yêu cầu hủy an toàn.");
      } else {
        completed(sale);
      }
    }, this::failed);
  }

  private void cancel() {
    if (!Ui.confirm(this, "Chỉ hủy nếu backend xác nhận chưa ghi hóa đơn. Tiếp tục?")) {
      return;
    }
    String bearer = app.token();
    app.run(this, () -> controller.cancel(bearer), cancelled -> {
      if (cancelled) {
        cart.clear();
      }
      refresh();
      Ui.message(this, cancelled ? "Đã hủy an toàn. Mã cũ không thể thanh toán nữa."
          : "Hóa đơn đã ghi; chọn Đối soát để lấy kết quả.");
    }, this::failed);
  }

  private void completed(Sale sale) {
    cart.clear();
    manual.setText("0");
    promotion.setText("");
    customer.setText("");
    customerName.setText("Khách lẻ");
    refresh();
    message("Đã lưu hóa đơn " + sale.code() + " — " + Money.format(sale.total()));
    ReceiptsUI.show(app, this, sale);
  }

  private void failed(Throwable error) {
    refresh();
    Ui.error(this, error);
  }

  private void refresh() {
    SaleRequest pending = controller.pending();
    if (pending != null) {
      customer.setText(pending.customerId() == null ? "" : pending.customerId().toString());
      customerName.setText(pending.customerId() == null ? "Khach le" : "ID " + pending.customerId());
      payment.setSelectedIndex(pending.payment().equals("CASH") ? 0 : 1);
      manual.setText(pending.manualDiscount().toPlainString());
      promotion.setText(pending.promotionCode());
    }
    List<ItemQuantity> displayed = pending == null ? cart.lines() : pending.lines();
    List<Map<String, Object>> data = new ArrayList<>();
    for (ItemQuantity line : displayed) {
      data.add(Data.map("Id", line.productId(), "SKU", line.sku(), "Name", line.name(),
          "Quantity", line.quantity(), "UnitPrice", line.expectedPrice(), "Total", line.gross(),
          "Serial", String.join(", ", line.serials())));
    }
    lines.setData(data);
    subtotal.setText(Money.format(SaleRequest.subtotal(displayed)));
    discount.setText(Money.format(pending == null ? Money.ZERO : pending.expectedDiscount()));
    total.setText(Money.format(Money.total(SaleRequest.subtotal(displayed),
        pending == null ? Money.ZERO : pending.expectedDiscount())));
    enabled(inputs, pending == null);
    enabled(checkoutFields, pending == null);
    remove.setEnabled(pending == null);
    checkout.setEnabled(pending == null);
    retry.setEnabled(pending != null);
    resolve.setEnabled(pending != null);
    cancel.setEnabled(pending != null);
    message(pending == null ? "Sẵn sàng • Mã vạch giữ nguyên số 0 ở đầu."
        : "Yêu cầu chờ: " + pending.requestId() + " — Giữ nguyên nội dung để đối soát.");
  }

  private static void enabled(Component component, boolean value) {
    component.setEnabled(value);
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        enabled(child, value);
      }
    }
  }

  @Override
  public boolean hasUnsavedChanges() { return controller != null && controller.pending() != null ||
      !cart.lines().isEmpty() || !code.getText().isBlank()
      || !serial.getText().isBlank() || !customer.getText().isBlank() || !promotion.getText().isBlank()
      || !manual.getText().strip().equals("0") || payment.getSelectedIndex() != 0
      || Ui.integer(quantity) != 1; }

  @Override
  public boolean canLeave() {
    return !hasUnsavedChanges() || Ui.confirm(this, "Co du lieu nhap / giao dich cho. Giu nguyen de quay lai sau?");
  }

  @Override
  public void onClosed() {
    if (!closed) {
      closed = true;
      try {
        controller.close();
      } catch (IOException ex) {
        Ui.error(this, ex);
      }
    }
  }
}
