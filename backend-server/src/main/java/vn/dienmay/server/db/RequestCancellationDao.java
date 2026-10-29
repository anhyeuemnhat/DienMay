package vn.dienmay.server.db;

import static vn.dienmay.server.db.Jdbc.statement;
import static vn.dienmay.server.db.Jdbc.update;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** Append-only cancellation fence. Caller must hold the same transaction applock as execution. */
public final class RequestCancellationDao {
  private RequestCancellationDao() {}

  public static void requireActive(Connection connection, String kind, UUID request,
      String fingerprint, long employee) throws SQLException {
    if (cancelled(connection, kind, request, fingerprint, employee)) {
      throw new IllegalArgumentException("Yeu cau da huy vinh vien. Khong gui lai ma nay.");
    }
  }

  public static boolean cancelled(Connection connection, String kind, UUID request,
      String fingerprint, long employee) throws SQLException {
    try (PreparedStatement statement = statement(connection,
        "SELECT RequestHash,EmployeeId FROM dm.RequestCancellations WHERE Kind=? AND RequestId=?",
        kind, request); ResultSet result = statement.executeQuery()) {
      if (!result.next()) {
        return false;
      }
      if (!fingerprint.equals(result.getString(1)) || employee != result.getLong(2)) {
        throw new SecurityException("Ma yeu cau da thuoc nhan vien hoac noi dung khac.");
      }
      return true;
    }
  }

  public static void cancel(Connection connection, String kind, UUID request,
      String fingerprint, long employee) throws SQLException {
    if (!cancelled(connection, kind, request, fingerprint, employee)) {
      update(connection, "INSERT dm.RequestCancellations(Kind,RequestId,RequestHash,EmployeeId) "
          + "VALUES(?,?,?,?)", kind, request, fingerprint, employee);
    }
  }
}
