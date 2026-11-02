package vn.dienmay.server.service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import vn.dienmay.common.api.BusinessException;
import vn.dienmay.common.model.DeletionPreview;
import vn.dienmay.common.model.DeletionRequest;
import vn.dienmay.common.model.Role;
import vn.dienmay.server.db.Jdbc;
import vn.dienmay.server.db.RequestCancellationDao;
import vn.dienmay.server.db.SqlDatabase;

/** Manager-only physical deletion. SQL stored procedure rechecks dependencies, version and audit atomically. */
public final class RecordDeletionService {
  private final SqlDatabase db;
  private final AuthService auth;
  public RecordDeletionService(SqlDatabase db, AuthService auth) { this.db = db; this.auth = auth; }

  public DeletionPreview preview(AuthService.Session session, String type, long id, int revision) throws SQLException {
    DeletionRequest.requireTarget(type, id, revision);
    return guard(session, type, id, revision, null);
  }

  public DeletionPreview delete(AuthService.Session session, DeletionRequest request) throws SQLException {
    return guard(session, request.type(), request.entityId(), request.revision(), request);
  }

  private DeletionPreview guard(AuthService.Session session, String type, long id, int revision,
      DeletionRequest request) throws SQLException {
    try {
      return db.transaction(c -> {
        if (type.equals("employee")) { Jdbc.lock(c, "dm:employee-admin"); }
        auth.require(c, session, Role.MANAGER);
        try (PreparedStatement s = Jdbc.statement(c,
            "EXEC dm.ControlledDelete @Type=?,@EntityId=?,@Revision=?,@ActorId=?,@Execute=?,"
                + "@RequestId=?,@RequestHash=?,@Reason=?", type, id, revision, session.employeeId(),
            request != null, request == null ? null : request.id(),
            request == null ? null : request.fingerprint(), request == null ? "" : request.reason());
            ResultSet rs = s.executeQuery()) {
          if (!rs.next()) { throw new SQLException("Missing guarded delete result; reconcile request."); }
          return new DeletionPreview(rs.getString(1), rs.getLong(2), rs.getInt(3), rs.getString(4),
              rs.getString(5), rs.getBoolean(6), rs.getString(7));
        }
      });
    } catch (SQLException ex) { throw translate(ex); }
  }

  public boolean resolve(AuthService.Session session, DeletionRequest request) throws SQLException {
    return db.transaction(c -> {
      auth.require(c, session, Role.MANAGER);
      Jdbc.lock(c, "dm:delete:" + request.id());
      return committed(c, session, request);
    });
  }

  public boolean cancel(AuthService.Session session, DeletionRequest request) throws SQLException {
    return db.transaction(c -> {
      auth.require(c, session, Role.MANAGER);
      Jdbc.lock(c, "dm:delete:" + request.id());
      if (committed(c, session, request)) { return false; }
      RequestCancellationDao.cancel(c, "DELETE", request.id(), request.fingerprint(), session.employeeId());
      return true;
    });
  }

  private static boolean committed(java.sql.Connection c, AuthService.Session session,
      DeletionRequest request) throws SQLException {
    try (PreparedStatement s = Jdbc.statement(c,
        "SELECT RequestHash,EmployeeId FROM dm.DeletionRequests WHERE RequestId=?", request.id());
        ResultSet rs = s.executeQuery()) {
      if (!rs.next()) { return false; }
      if (!request.fingerprint().equals(rs.getString(1)) || session.employeeId() != rs.getLong(2)) {
        throw new SecurityException("Yêu cầu thuộc nội dung hoặc nhân viên khác.");
      }
      return true;
    }
  }

  private static SQLException translate(SQLException ex) {
    switch (ex.getErrorCode()) {
      case 51500 -> throw new BusinessException(403, "FORBIDDEN", "Chỉ Quản lý đang hoạt động được xóa.");
      case 51501 -> throw new BusinessException(404, "RECORD_NOT_FOUND", "Bản ghi không còn tồn tại.");
      case 51502 -> throw new BusinessException(409, "REVISION_CONFLICT",
          "Bản ghi đã thay đổi. Tải lại trước khi xóa.");
      case 51503 -> throw new BusinessException(409, "RECORD_LINKED", ex.getMessage());
      case 51504 -> throw new BusinessException(400, "DELETE_VALIDATION",
          "Loại bản ghi, lý do hoặc mã yêu cầu không hợp lệ.");
      case 51505 -> throw new BusinessException(409, "REQUEST_CONFLICT",
          "Yêu cầu đã hủy hoặc đã dùng cho nội dung khác.");
      case 547 -> throw new BusinessException(409, "RECORD_LINKED",
          "Có liên kết dữ liệu mới. Giao dịch xóa đã rollback.");
      default -> { return ex; }
    }
  }
}
