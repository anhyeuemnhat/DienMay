package vn.dienmay.client.ui;

import java.awt.BorderLayout;
import java.io.IOException;
import java.util.UUID;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import vn.dienmay.client.AppContext;
import vn.dienmay.client.controller.GuardedActionController;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.api.Operation;
import vn.dienmay.common.model.Money;
import vn.dienmay.common.model.ReversalPreview;
import vn.dienmay.common.model.ReversalRequest;

/** Receipt management lives inside the Receive screen; original documents are never physically deleted. */
@SuppressWarnings("serial")
public final class PostedReceiptsPanel extends JPanel implements AutoCloseable {
  private final AppContext app;
  private final WorkFrame owner;
  private final RowsPanel rows = new RowsPanel();
  private final JTextField search = new JTextField(18);
  private final JLabel pendingNotice = new JLabel();
  private final GuardedActionController<ReversalRequest> controller;
  private final JButton remove;
  private final JButton resolve;
  private final JButton retry;
  private final JButton cancel;
  private boolean loaded;

  public PostedReceiptsPanel(AppContext app, WorkFrame owner) {
    super(new BorderLayout(8, 8));
    this.app = app;
    this.owner = owner;
    setOpaque(false);
    setName("postedReceiptsPanel");
    rows.dataContext(app::environmentMode);
    rows.columns("Id", "RequestId", "CreatedAt", "SKU", "Name", "Quantity", "UnitCost", "Reference", "VendorName",
        "EmployeeName", "Status", "ReversalMovementId", "ReversalReason", "Revision", "PreviousCostPrice");
    add(rows, BorderLayout.CENTER);
    JPanel toolbar = Ui.toolbar(new JLabel("SKU / chứng từ:"), search, Ui.button("Tải lại phiếu", this::reload));
    search.addActionListener(event -> reload());
    if (Operation.REVERSE_RECEIPT.allows(app.session().role())) {
      try {
        controller = new GuardedActionController<>(app.gateway(), app.pendingDirectory(), app.session().employeeId(),
            ReversalRequest.class);
      } catch (IOException ex) { throw new IllegalStateException("Khong mo duoc yeu cau dao cho; khong xoa tep.", ex); }
      remove = RecordDeleteActions.destructive(this::preview);
      resolve = Ui.button("Đối soát đảo", this::resolve);
      retry = Ui.button("Thử lại cùng mã đảo", this::retry);
      cancel = Ui.button("Hủy yêu cầu đảo chưa ghi", this::cancel);
      toolbar.add(remove); toolbar.add(resolve); toolbar.add(retry); toolbar.add(cancel);
      rows.table().getSelectionModel().addListSelectionListener(event -> refresh());
    } else {
      controller = null; remove = null; resolve = null; retry = null; cancel = null;
    }
    JPanel header = new JPanel(new BorderLayout());
    header.setOpaque(false);
    header.add(toolbar, BorderLayout.NORTH);
    pendingNotice.setForeground(Ui.HEADER);
    pendingNotice.setOpaque(true);
    pendingNotice.setBackground(java.awt.Color.WHITE);
    pendingNotice.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 12, 8, 12));
    header.add(pendingNotice, BorderLayout.SOUTH);
    add(header, BorderLayout.NORTH);
    refresh();
  }

  public void activate() { if (!loaded) { reload(); } }
  public boolean hasPending() { return controller != null && controller.pending() != null; }

  private void reload() {
    app.request(owner, Operation.RECEIPTS, Data.map("text", search.getText()), result -> {
      rows.setRows(result); loaded = true; refresh();
    });
  }

  private void preview() {
    if (app.jobs() != 0 || hasPending()) { return; }
    long id = Json.number(rows.selected().get("Id")).longValueExact();
    app.request(owner, Operation.REVERSAL_PREVIEW, Data.map("id", id), result -> {
      ReversalPreview value = Json.convert(result, ReversalPreview.class);
      if (!value.allowed()) { Ui.message(owner, "Không thể đảo phiếu:\n" + value.blockReason()); return; }
      String reason = AdminConfirmations.reason(owner, "Hủy/đảo phiếu nhập đã ghi sổ",
          "Phiếu #" + value.movementId() + "\n" + value.sku() + " / " + value.name()
              + "\nSố lượng: " + value.quantity() + " | Tồn KHO: " + value.availableStock()
              + "\nĐơn giá nhập: " + Money.format(value.receiptCost())
              + "\nGiá vốn khôi phục: " + Money.format(value.restoredCost())
              + "\n\nPhiếu đã ghi sổ không được xóa vĩnh viễn. Hệ thống sẽ tạo chứng từ đảo "
              + "để điều chỉnh tồn kho và giữ nguyên lịch sử.", "Xác nhận hủy/đảo phiếu");
      if (reason == null) { return; }
      submit(new ReversalRequest(UUID.randomUUID(), value.movementId(), value.productRevision(), reason));
    });
  }

  private void submit(ReversalRequest request) {
    String bearer = app.token();
    app.run(owner, () -> controller.submit(bearer, request), result -> {
      refresh(); reload(); Ui.message(owner, "Đã tạo chứng từ đảo #" + result + "; phiếu gốc được giữ nguyên.");
    }, this::failed);
  }

  private void retry() {
    if (app.jobs() != 0 || !hasPending()) { return; }
    if (Ui.confirm(owner, "Thử lại đúng yêu cầu đảo " + controller.pending().id() + "?")) {
      submit(controller.pending());
    }
  }

  private void resolve() {
    if (app.jobs() != 0 || !hasPending()) { return; }
    String bearer = app.token();
    app.run(owner, () -> controller.resolve(bearer), done -> {
      refresh(); reload(); Ui.message(owner, done ? "Backend xác nhận phiếu đã đảo."
          : "Chưa ghi. Giữ yêu cầu; thử lại cùng mã hoặc hủy an toàn.");
    }, this::failed);
  }

  private void cancel() {
    if (app.jobs() != 0 || !hasPending()
        || !Ui.confirm(owner, "Chỉ hủy yêu cầu đảo chưa ghi; không hoàn tác một lần đảo đã ghi. Tiếp tục?")) { return; }
    String bearer = app.token();
    app.run(owner, () -> controller.cancel(bearer), done -> {
      refresh(); Ui.message(owner, done ? "Đã hủy yêu cầu chưa ghi."
          : "Đã ghi sổ; hãy đối soát để xác nhận kết quả.");
    }, this::failed);
  }

  private void failed(Throwable error) { refresh(); Ui.error(owner, error); }

  private void refresh() {
    boolean pending = hasPending();
    pendingNotice.setVisible(pending);
    pendingNotice.setText(pending ? "Yêu cầu đảo chờ: " + controller.pending().id()
        + " - Không tạo mã mới. Đối soát trước khi thay đổi." : "");
    if (remove != null) {
      remove.setEnabled(!pending && rows.table().getSelectedRow() >= 0);
      resolve.setEnabled(pending); retry.setEnabled(pending); cancel.setEnabled(pending);
    }
    revalidate();
  }

  @Override public void close() throws IOException { if (controller != null) { controller.close(); } }
}
