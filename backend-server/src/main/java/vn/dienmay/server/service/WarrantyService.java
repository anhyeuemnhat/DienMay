package vn.dienmay.server.service;

import static vn.dienmay.server.db.Jdbc.insert;
import static vn.dienmay.server.db.Jdbc.now;
import static vn.dienmay.server.db.Jdbc.rows;
import static vn.dienmay.server.db.Jdbc.statement;
import static vn.dienmay.server.db.Jdbc.update;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import vn.dienmay.common.model.Role;
import vn.dienmay.common.model.Validation;
import vn.dienmay.common.model.WarrantyRules;
import vn.dienmay.server.db.ProductDao;
import vn.dienmay.server.db.SqlDatabase;

public final class WarrantyService {
  private final SqlDatabase db;
  private final AuthService auth;

  public WarrantyService(SqlDatabase db, AuthService auth) {
    this.db = db;
    this.auth = auth;
  }

  public List<LinkedHashMap<String, Object>> search(AuthService.Session session, String text)
      throws SQLException {
    String pattern = ProductDao.like(Validation.optional(text, 180, "Tu khoa"));
    return db.read(c -> {
      auth.require(c, session, Role.MANAGER, Role.CASHIER);
      LocalDate today = now(c).atZone(Validation.ZONE).toLocalDate();
      return rows(c, "SELECT TOP (1000) w.Id,w.Code,i.Code AS InvoiceCode,d.SKU,d.ProductName,"
          + "COALESCE(u.Serial,N'') AS Serial,i.CustomerName,i.CustomerPhone,w.StartDate,w.EndDate,"
          + "w.WarrantyMonths,w.CoveredQuantity,CASE WHEN w.StartDate<=? AND w.EndDate>=? "
          + "THEN N'ACTIVE' ELSE N'EXPIRED' END AS Validity FROM dm.Warranties w "
          + "JOIN dm.InvoiceDetails d ON d.Id=w.DetailId JOIN dm.Invoices i ON i.Id=d.InvoiceId "
          + "LEFT JOIN dm.ProductUnits u ON u.Id=w.UnitId WHERE w.Code LIKE ? ESCAPE '~' "
          + "OR i.Code LIKE ? ESCAPE '~' OR u.Serial LIKE ? ESCAPE '~' "
          + "OR i.CustomerPhone LIKE ? ESCAPE '~' ORDER BY w.Id DESC",
          today, today, pattern, pattern, pattern, pattern);
    });
  }

  public long receive(AuthService.Session session, long warranty, String fault) throws SQLException {
    String description = Validation.required(fault, 1000, "Mo ta loi");
    return db.transaction(c -> {
      auth.require(c, session, Role.MANAGER, Role.CASHIER);
      try (PreparedStatement s = statement(c, "SELECT Id FROM dm.Warranties WHERE Id=?", warranty);
          ResultSet r = s.executeQuery()) {
        if (!r.next()) {
          throw new IllegalArgumentException("Khong tim thay bao hanh.");
        }
      }
      // Preserve baseline policy: display expiry; do not silently auto-reject an expired warranty.
      long claim = insert(c, "INSERT dm.WarrantyClaims(WarrantyId,ReceivedBy,Fault) "
          + "OUTPUT INSERTED.Id VALUES(?,?,?)", warranty, session.employeeId(), description);
      update(c, "INSERT dm.ClaimHistory(ClaimId,ChangedBy,OldStatus,NewStatus,Notes) "
          + "VALUES(?,?,NULL,'RECEIVED',?)", claim, session.employeeId(), description);
      return claim;
    });
  }

  public List<LinkedHashMap<String, Object>> claims(AuthService.Session session, Long warranty)
      throws SQLException {
    return db.read(c -> {
      auth.require(c, session, Role.MANAGER, Role.CASHIER);
      return rows(c, "SELECT TOP (1000) cl.Id,w.Code AS WarrantyCode,cl.ReceivedAt,cl.Fault,"
          + "e.FullName AS EmployeeName,cl.Status,cl.Notes,cl.CompletedAt FROM dm.WarrantyClaims cl "
          + "JOIN dm.Warranties w ON w.Id=cl.WarrantyId JOIN dm.Employees e ON e.Id=cl.ReceivedBy "
          + "WHERE (? IS NULL OR cl.WarrantyId=?) ORDER BY cl.Id DESC", warranty, warranty);
    });
  }

  public void transition(AuthService.Session session, long claim, String expected, String next,
      String notes) throws SQLException {
    if (!WarrantyRules.canTransition(expected, next)) {
      throw new IllegalArgumentException("Chuyen trang thai khong hop le.");
    }
    String message = Validation.required(notes, 1000, "Ghi chu xu ly");
    db.transaction(c -> {
      auth.require(c, session, Role.MANAGER);
      try (PreparedStatement s = statement(c,
          "SELECT Status FROM dm.WarrantyClaims WITH (UPDLOCK,HOLDLOCK) WHERE Id=?", claim);
          ResultSet r = s.executeQuery()) {
        if (!r.next()) {
          throw new IllegalArgumentException("Khong tim thay phieu.");
        }
        if (!r.getString(1).equals(expected)) {
          throw new IllegalArgumentException("Phieu da doi trang thai o noi khac. Hay tai lai.");
        }
      }
      update(c, "UPDATE dm.WarrantyClaims SET Status=?,Notes=?,CompletedAt=? WHERE Id=?",
          next, message, Set.of("COMPLETED", "REJECTED").contains(next) ? now(c) : null, claim);
      update(c, "INSERT dm.ClaimHistory(ClaimId,ChangedBy,OldStatus,NewStatus,Notes) VALUES(?,?,?,?,?)",
          claim, session.employeeId(), expected, next, message);
      return null;
    });
  }

  public List<LinkedHashMap<String, Object>> history(AuthService.Session session, long claim)
      throws SQLException {
    return db.read(c -> {
      auth.require(c, session, Role.MANAGER, Role.CASHIER);
      return rows(c, "SELECT h.Id,h.ChangedAt,e.FullName AS EmployeeName,h.OldStatus,h.NewStatus,h.Notes "
          + "FROM dm.ClaimHistory h JOIN dm.Employees e ON e.Id=h.ChangedBy "
          + "WHERE h.ClaimId=? ORDER BY h.Id", claim);
    });
  }
}
