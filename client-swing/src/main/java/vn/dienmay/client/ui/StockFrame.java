package vn.dienmay.client.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.util.List;
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
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import vn.dienmay.client.AppContext;
import vn.dienmay.client.controller.StockController;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.api.Operation;
import vn.dienmay.common.model.InventoryRequest;
import vn.dienmay.common.model.Item;
import vn.dienmay.common.model.Money;
import vn.dienmay.common.model.Validation;

/** Shared stock workspace retaining the upstream receive/transfer validation and recovery workflow. */
@SuppressWarnings("serial") // View state is never Java-serialized.
public class StockFrame extends WorkFrame {
  private final boolean receiving;
  private final StockController controller;
  private final JTextField sku = new JTextField(24);
  private final JLabel product = new JLabel("Chưa chọn sản phẩm");
  private final JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, 100000, 1));
  private final JTextField reference = new JTextField(24);
  private final JTextField cost = new JTextField("0", 24);
  private final JComboBox<Ui.Choice> vendor = new JComboBox<>();
  private final JComboBox<String> source = new JComboBox<>(new String[] {"KHO", "QUAY"});
  private final JTextArea serials = new JTextArea(12, 30);
  private final JPanel editor = new JPanel(new BorderLayout(12, 12));
  private final JButton submit;
  private final JButton retry;
  private final JButton resolve;
  private final JButton cancel;
  private Item selected;
  private boolean closed;

  protected StockFrame(AppContext app, boolean receiving) {
    super(app, receiving ? "Nhập hàng" : "Chuyển kho / quầy",
        "Ghi nguyên tử • Mã yêu cầu bền vững • Đối soát khi mất kết nối");
    workspaceMode("dense");
    this.receiving = receiving;
    try {
      controller = new StockController(app.gateway(), app.pendingDirectory(), app.session().employeeId(), receiving);
    } catch (IOException ex) {
      super.dispose();
      throw new IllegalStateException("Không mở được yêu cầu chờ; không xóa tệp. " + ex.getMessage(), ex);
    }
    JPanel productInput = Ui.toolbar(sku, Ui.button("Tìm sản phẩm", this::findProduct));
    JPanel fields = receiving
        ? Ui.fields("SKU / mã vạch *", productInput, "Sản phẩm", product,
            "Nhà cung cấp *", vendor, "Số lượng *", quantity,
            "Đơn giá nhập *", cost, "Chứng từ / lý do *", reference)
        : Ui.fields("SKU / mã vạch *", productInput, "Sản phẩm", product,
            "Chuyển từ", source, "Số lượng *", quantity,
            "Chứng từ / lý do *", reference);
    JPanel left = new JPanel(new BorderLayout());
    left.add(fields, BorderLayout.NORTH);
    JTextArea instructions = new JTextArea(receiving
        ? "Nhập hàng luôn vào KHO.\n\nNhập mỗi Serial/IMEI một dòng hoặc cách nhau bằng dấu phẩy.\n"
            + "Hàng theo Serial phải có đủ một mã cho mỗi thiết bị.\n\n"
            + "Đơn giá nhập cập nhật giá vốn; không thay đổi giá bán."
        : "Đích chuyển là vị trí còn lại: KHO ↔ QUẦY.\n\n"
            + "Chỉ chuyển Serial đang còn tại nguồn.\n\n"
            + "Backend kiểm tra tồn kho lại ngay trong giao dịch, không tin số tồn trên màn hình.");
    instructions.setEditable(false);
    instructions.setLineWrap(true);
    instructions.setWrapStyleWord(true);
    instructions.setBorder(BorderFactory.createEmptyBorder(22, 20, 20, 20));
    left.add(instructions, BorderLayout.CENTER);
    serials.setLineWrap(true);
    serials.setWrapStyleWord(true);
    serials.setName("serialInput");
    JPanel right = new JPanel(new BorderLayout(8, 8));
    right.setBorder(BorderFactory.createTitledBorder("Serial / IMEI • để trống với hàng thường"));
    right.add(Ui.scroll(serials), BorderLayout.CENTER);
    if (!receiving) {
      right.add(Ui.button("Chọn Serial còn tại nguồn", this::chooseSerials), BorderLayout.NORTH);
    }
    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, Ui.scroll(left), right);
    split.setResizeWeight(0.5);
    split.setDividerLocation(0.5);
    editor.add(split, BorderLayout.CENTER);
    body.add(editor, BorderLayout.CENTER);
    submit = Ui.button("Xác nhận ghi kho", this::submit);
    retry = Ui.button("Thử lại cùng mã", this::retry);
    resolve = Ui.button("Đối soát", this::resolve);
    cancel = Ui.button("Yêu cầu hủy an toàn", this::cancel);
    body.add(Ui.toolbar(submit, retry, resolve, cancel), BorderLayout.SOUTH);
    sku.addActionListener(event -> findProduct());
    if (receiving) {
      app.request(this, Operation.LOOKUPS, Data.map("type", "vendor"), result -> {
        for (Ui.Choice choice : Json.list(result, Ui.Choice.class)) {
          vendor.addItem(choice);
        }
        restorePending();
      });
    }
    restorePending();
  }

  private void restorePending() {
    InventoryRequest pending = controller.pending();
    if (pending != null) {
      product.setText("Sản phẩm ID " + pending.productId());
      quantity.setValue(pending.quantity());
      reference.setText(pending.reference());
      cost.setText(pending.unitCost().toPlainString());
      serials.setText(String.join("\n", pending.serials()));
      if (pending.from() != null) {
        source.setSelectedItem(pending.from());
      }
      for (int i = 0; i < vendor.getItemCount(); i++) {
        if (Long.valueOf(vendor.getItemAt(i).id()).equals(pending.vendorId())) {
          vendor.setSelectedIndex(i);
        }
      }
    }
    refreshState();
  }

  private void findProduct() {
    app.request(this, Operation.PRODUCT_FIND, Data.map("code", sku.getText()), result -> {
      selected = Json.convert(result, Item.class);
      product.setText("<html>" + escape(selected.sku()) + " / " + escape(selected.name())
          + "<br>KHO: " + selected.warehouseQuantity() + " | QUẦY: " + selected.counterQuantity() + "</html>");
      if (receiving) {
        cost.setText(selected.costPrice().toPlainString());
      }
    });
  }

  private static String escape(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private void chooseSerials() {
    if (selected == null) {
      throw new IllegalArgumentException("Tìm sản phẩm trước.");
    }
    app.request(this, Operation.SERIALS, Data.map("id", selected.id(), "location", source.getSelectedItem()), result ->
        {
      JList<String> list = new JList<>(Json.list(result, String.class).toArray(String[]::new));
      list.setVisibleRowCount(14);
      list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      if (Ui.option(this, Ui.scroll(list), "Chọn Serial nguồn",
          JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
        List<String> chosen = list.getSelectedValuesList();
        serials.setText(String.join("\n", chosen));
        if (!chosen.isEmpty()) {
          quantity.setValue(chosen.size());
        }
      }
    });
  }

  private InventoryRequest buildRequest() {
    if (selected == null || !sku.getText().trim().equalsIgnoreCase(selected.sku())
        && !sku.getText().trim().equalsIgnoreCase(selected.barcode())) {
      throw new IllegalArgumentException("Tìm và xác nhận sản phẩm trước khi ghi.");
    }
    int count = Ui.integer(quantity);
    List<String> values = Validation.serials(serials.getText());
    if (selected.trackSerial() ? values.size() != count : !values.isEmpty()) {
      throw new IllegalArgumentException("Hàng theo Serial cần đủ mã cho số lượng; hàng thường để trống.");
    }
    String from = receiving ? null : (String) source.getSelectedItem();
    Ui.Choice supplier = (Ui.Choice) vendor.getSelectedItem();
    if (receiving && supplier == null) {
      throw new IllegalArgumentException("Chọn nhà cung cấp.");
    }
    return new InventoryRequest(UUID.randomUUID(), selected.id(), count, values, reference.getText(),
        receiving ? supplier.id() : null, receiving ? Money.of(cost.getText()) : Money.ZERO,
        from, receiving ? "KHO" : "KHO".equals(from) ? "QUAY" : "KHO");
  }

  private void submit() {
    InventoryRequest request = buildRequest();
    if (!Ui.confirm(this, "Xác nhận ghi " + request.quantity() + " sản phẩm?")) {
      return;
    }
    String bearer = app.token();
    app.run(this, () -> {
      controller.submit(bearer, request);
      return true;
    }, result -> completed(), this::failed);
  }

  private void retry() {
    String bearer = app.token();
    app.run(this, () -> {
      controller.retry(bearer);
      return true;
    }, result -> completed(), this::failed);
  }

  private void resolve() {
    String bearer = app.token();
    app.run(this, () -> controller.resolve(bearer), committed -> {
      if (committed) {
        completed();
      } else {
        refreshState();
        Ui.message(this, "Chưa thấy chứng từ đã ghi. Thử lại cùng mã hoặc yêu cầu hủy an toàn.");
      }
    }, this::failed);
  }

  private void cancel() {
    if (!Ui.confirm(this, "Backend sẽ ghi dấu hủy vĩnh viễn nếu chưa có chứng từ. Tiếp tục?")) {
      return;
    }
    String bearer = app.token();
    app.run(this, () -> controller.cancel(bearer), cancelled -> {
      refreshState();
      Ui.message(this, cancelled ? "Đã hủy an toàn. Có thể tạo yêu cầu mới."
          : "Chứng từ đã ghi; không hủy. Chọn Đối soát.");
    }, this::failed);
  }

  private void completed() {
    serials.setText("");
    reference.setText("");
    quantity.setValue(1);
    refreshState();
    Ui.message(this, "Đã xác nhận chứng từ kho.");
    if (!sku.getText().isBlank()) {
      findProduct();
    }
  }

  private void failed(Throwable error) {
    refreshState();
    Ui.error(this, error);
  }

  private void refreshState() {
    boolean pending = controller.pending() != null;
    enabled(editor, !pending);
    submit.setEnabled(!pending);
    retry.setEnabled(pending);
    resolve.setEnabled(pending);
    cancel.setEnabled(pending);
    message(pending ? "Yêu cầu chờ: " + controller.pending().id() + " — Không tạo mã mới."
        : "Sẵn sàng • Chỉ xác nhận thành công sau khi backend phản hồi.");
  }

  private static void enabled(Component component, boolean enabled) {
    component.setEnabled(enabled);
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        enabled(child, enabled);
      }
    }
  }

  @Override
  public void dispose() {
    if (!closed) {
      closed = true;
      try {
        controller.close();
      } catch (IOException ex) {
        Ui.error(this, ex);
      }
    }
    super.dispose();
  }
}
