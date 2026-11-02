package vn.dienmay.client.ui;

import java.awt.Component;
import java.awt.Graphics;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import vn.dienmay.client.AppContext;
import vn.dienmay.client.controller.GuardedActionController;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.api.Operation;
import vn.dienmay.common.model.DeletionPreview;
import vn.dienmay.common.model.DeletionRequest;

/** Shared per-screen destructive action. Does not create a menu or remove lifecycle actions. */
public final class RecordDeleteActions {
  private RecordDeleteActions() {}

  public static JButton destructive(Runnable action) {
    JButton button = Ui.button("Xóa bản ghi", action);
    button.setName("recordDelete");
    button.setIcon(new TrashIcon());
    return button;
  }

  public static void install(AppContext app, RowsFrame owner, Supplier<String> type, Runnable reload) {
    if (!Operation.DELETE_RECORD.allows(app.session().role())) { return; }
    JButton remove = destructive(() -> begin(app, owner, type.get(), owner.rows.selected(), reload));
    remove.setEnabled(false);
    owner.rows.table().getSelectionModel().addListSelectionListener(event ->
        remove.setEnabled(owner.rows.table().getSelectedRow() >= 0));
    owner.actions.add(remove);
    JButton recover = Ui.button("Đối soát xóa", () -> recover(app, owner, reload));
    recover.setName("deleteRecovery");
    owner.actions.add(recover);
  }

  private static GuardedActionController<DeletionRequest> controller(AppContext app) {
    if (!Operation.DELETE_RECORD.allows(app.session().role())) { throw new SecurityException("Chỉ Quản lý."); }
    try {
      return new GuardedActionController<>(app.gateway(), app.pendingDirectory(), app.session().employeeId(),
          DeletionRequest.class);
    } catch (IOException ex) { throw new IllegalStateException("Không mở được yêu cầu xóa chờ; không xóa tệp.", ex); }
  }

  private static void begin(AppContext app, WorkFrame owner, String type, Map<String, Object> row, Runnable reload) {
    if (app.jobs() != 0) { return; }
    long id = Json.number(row.get("Id")).longValueExact();
    int revision = Json.number(row.get("Revision")).intValueExact();
    DeletionRequest.requireTarget(type, id, revision);
    GuardedActionController<DeletionRequest> controller = controller(app);
    if (controller.pending() != null) { recover(app, owner, reload, controller); return; }
    String bearer = app.token();
    app.run(owner, () -> Json.convert(app.gateway().call(Operation.DELETE_PREVIEW, bearer,
        Data.map("type", type, "id", id, "revision", revision)), DeletionPreview.class), preview -> {
      if (!preview.allowed()) {
        close(owner, controller);
        Ui.message(owner, "Không thể xóa:\n" + preview.blockReason()
            + "\nCó thể dùng Ngừng sử dụng nếu phù hợp.");
        return;
      }
      String reason = AdminConfirmations.reason(owner, "Xác nhận xóa bản ghi",
          "Loại: " + label(preview.type()) + "\nMã: " + preview.code() + " (#" + preview.entityId()
              + ")\nTên: " + preview.name() + "\nPhiên bản: " + preview.revision()
              + "\n\nXóa vĩnh viễn không thể khôi phục bằng chức năng Khôi phục thông thường. "
              + "Backend sẽ kiểm tra lại mọi điều kiện khi ghi.", "Xác nhận xóa vĩnh viễn");
      if (reason == null) { close(owner, controller); return; }
      DeletionRequest request = new DeletionRequest(UUID.randomUUID(), type, id, revision, reason);
      app.run(owner, () -> controller.submit(bearer, request), result -> {
        close(owner, controller);
        owner.message("Đã xóa bản ghi; nhật ký được giữ nguyên.");
        reload.run();
      }, error -> { close(owner, controller); Ui.error(owner, error);
        Ui.message(owner, "Yêu cầu " + request.id() + " được giữ để đối soát. "
            + "Không tự động gửi lại. Dùng nút Đối soát xóa."); });
    }, error -> { close(owner, controller); Ui.error(owner, error); });
  }

  private static void recover(AppContext app, WorkFrame owner, Runnable reload) {
    if (app.jobs() != 0) { return; }
    recover(app, owner, reload, controller(app));
  }

  private static void recover(AppContext app, WorkFrame owner, Runnable reload,
      GuardedActionController<DeletionRequest> controller) {
    DeletionRequest request = controller.pending();
    if (request == null) { close(owner, controller); Ui.message(owner, "Không có yêu cầu xóa chờ."); return; }
    String resolve = "Đối soát kết quả";
    String cancel = "Hủy yêu cầu chưa ghi";
    JOptionPane pane = new JOptionPane("Yêu cầu: " + request.id() + "\n" + label(request.type())
        + " #" + request.entityId() + "\nKhông tự động thử lại lệnh xóa.", JOptionPane.WARNING_MESSAGE,
        JOptionPane.DEFAULT_OPTION, null, new Object[] {resolve, cancel, "Đóng"}, resolve);
    javax.swing.JDialog dialog = pane.createDialog(owner, "Đối soát xóa bản ghi");
    Ui.decorateDialog(dialog, "Đối soát xóa bản ghi");
    Ui.fitDialog(dialog);
    dialog.setVisible(true);
    Object choice = pane.getValue();
    dialog.dispose();
    if (!resolve.equals(choice) && !cancel.equals(choice)) { close(owner, controller); return; }
    String bearer = app.token();
    boolean cancelling = cancel.equals(choice);
    app.run(owner, () -> cancelling ? controller.cancel(bearer) : controller.resolve(bearer), done -> {
      close(owner, controller);
      Ui.message(owner, done ? (cancelling ? "Đã hủy an toàn yêu cầu chưa ghi."
          : "Backend xác nhận bản ghi đã xóa.") : (cancelling
          ? "Yêu cầu đã ghi; dùng Đối soát kết quả."
          : "Chưa ghi. Yêu cầu vẫn được giữ; hủy an toàn trước khi thao tác lại."));
      reload.run();
    }, error -> { close(owner, controller); Ui.error(owner, error); });
  }

  private static void close(Component owner, AutoCloseable resource) {
    try { resource.close(); } catch (Exception ex) { Ui.error(owner, ex); }
  }

  public static String label(String type) {
    return switch (type) {
      case "product" -> "Sản phẩm"; case "customer" -> "Khách hàng";
      case "vendor" -> "Nhà cung cấp"; case "employee" -> "Nhân viên";
      case "promotion" -> "Khuyến mãi"; case "category" -> "Loại sản phẩm";
      case "brand" -> "Thương hiệu"; default -> type;
    };
  }

  private static final class TrashIcon implements Icon {
    @Override public int getIconWidth() { return 18; }
    @Override public int getIconHeight() { return 18; }
    @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
      Graphics g = graphics.create();
      g.setColor(component.isEnabled() ? java.awt.Color.WHITE : Ui.DISABLED_TEXT);
      g.drawRect(x + 5, y + 6, 9, 10);
      g.drawLine(x + 3, y + 4, x + 16, y + 4);
      g.drawRect(x + 7, y + 1, 5, 3);
      g.drawLine(x + 8, y + 8, x + 8, y + 14);
      g.drawLine(x + 11, y + 8, x + 11, y + 14);
      g.dispose();
    }
  }
}
