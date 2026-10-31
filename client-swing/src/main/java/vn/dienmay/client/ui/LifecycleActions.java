package vn.dienmay.client.ui;

import java.awt.Component;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JTextField;
import vn.dienmay.client.AppContext;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Operation;

/** Always names the target and consequence. Soft removal never discards business history. */
public final class LifecycleActions {
  private LifecycleActions() { }
  public static void edit(AppContext app, Component owner, String type, Map<String, Object> row,
      boolean enabled, Runnable saved) {
    if (!Operation.LIFECYCLE.allows(app.session().role())) { throw new SecurityException("Khong co quyen"); }
    String name = String.valueOf(row.getOrDefault("Name", row.getOrDefault("FullName", row.get("Id"))));
    JTextField reason = Ui.text("");
    JLabel target = new JLabel(name);
    target.setToolTipText(name);
    new EditDialog(app, owner, enabled ? "Khôi phục sử dụng" :
        "Ngừng sử dụng (xóa mềm)",
        Ui.fields("Bản ghi", target, "Hậu quả", new JLabel(enabled
            ? "Cho phép sử dụng lại; vẫn phải đạt kiểm tra dữ liệu."
            : "Chặn sử dụng mới; giữ nguyên chứng từ, tồn và lịch sử."),
            "Lý do *", reason), Operation.LIFECYCLE, () -> {
          if (reason.getText().trim().isEmpty()) { throw new
              IllegalArgumentException("Nhập lý do trước khi xác nhận."); }
          if (!Ui.confirm(owner, (enabled ? "Khôi phục " : "Ngừng sử dụng ") + name + "?")) {
            throw new IllegalArgumentException("Chưa xác nhận; không thay đổi dữ liệu.");
          }
          return Data.map("type", type, "id", row.get("Id"), "revision", row.get("Revision"),
              "enabled", enabled, "reason", reason.getText());
        }, result -> saved.run()).open();
  }
}
