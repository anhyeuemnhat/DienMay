package vn.dienmay.server.service;

import static vn.dienmay.server.db.Jdbc.lock;
import static vn.dienmay.server.db.Jdbc.statement;
import static vn.dienmay.server.db.Jdbc.update;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import vn.dienmay.common.model.Role;
import vn.dienmay.common.model.Validation;
import vn.dienmay.server.db.SqlDatabase;

/** Soft removal only. Posted financial, stock and warranty history can never be deleted by this API. */
public final class LifecycleService {
  private final SqlDatabase db;
  private final AuthService auth;
  public LifecycleService(SqlDatabase db, AuthService auth) { this.db = db; this.auth = auth; }

  private static String table(String type) {
    return switch (type) {
      case "product" -> "dm.Products"; case "customer" -> "dm.Customers"; case "vendor" -> "dm.Vendors";
      case "employee" -> "dm.Employees"; case "promotion" -> "dm.Promotions";
      case "category" -> "dm.Categories"; case "brand" -> "dm.Brands";
      default -> throw new IllegalArgumentException("Không được xóa chứng từ, giao dịch hoặc lịch sử.");
    };
  }

  public void setActive(AuthService.Session session, String type, long id, int revision, boolean enabled,
      String reason) throws SQLException {
    String target = table(type);
    String note = Validation.required(reason, 1000, "Lý do");
    if (id <= 0 || revision <= 0) { throw new
        IllegalArgumentException("Chọn bản ghi và tải lại phiên bản."); }
    db.transaction(c -> {
      if (type.equals("employee")) { lock(c, "dm:employee-admin"); }
      auth.require(c, session, Role.MANAGER);
      boolean old;
      try (PreparedStatement s = statement(c, "SELECT Enabled,Revision FROM " + target
          + " WITH (UPDLOCK,HOLDLOCK) WHERE Id=?", id); ResultSet r = s.executeQuery()) {
        if (!r.next() || r.getInt(2) != revision) {
          throw new IllegalArgumentException("Bản ghi đã thay đổi. Tải lại trước khi thao tác.");
        }
        old = r.getBoolean(1);
      }
      if (old == enabled) { return null; }
      if (type.equals("employee") && !enabled) {
        if (id == session.employeeId()) { throw new
            IllegalArgumentException("Không tự khóa tài khoản đang dùng."); }
        try (PreparedStatement s = statement(c, "SELECT COUNT(*) FROM dm.Employees WITH (UPDLOCK,HOLDLOCK) "
            + "WHERE Role='MANAGER' AND Enabled=1 AND Id<>?", id); ResultSet r = s.executeQuery()) {
          if (!r.next() || r.getInt(1) == 0) { throw new
              IllegalArgumentException("Cần giữ ít nhất một quản lý hoạt động."); }
        }
      }
      String extra = type.equals("employee") ? ",AuthVersion=AuthVersion+1" : "";
      if (update(c, "UPDATE " + target + " SET Enabled=?,Revision=Revision+1" + extra
          + " WHERE Id=? AND Revision=?", enabled, id, revision) != 1) {
        throw new IllegalArgumentException("Xung đột cập nhật. Tải lại dữ liệu.");
      }
      QualityService.audit(c, type, id, enabled ? "RESTORE" : "ARCHIVE", Boolean.toString(old),
          Boolean.toString(enabled), note, session.employeeId());
      return null;
    });
  }
}
