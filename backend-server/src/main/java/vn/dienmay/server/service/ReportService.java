package vn.dienmay.server.service;

import static vn.dienmay.server.db.Jdbc.rows;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import vn.dienmay.common.model.Role;
import vn.dienmay.common.model.Validation;
import vn.dienmay.server.db.SqlDatabase;

/** Reporting retains the baseline date semantics (local days converted to UTC boundaries). */
public final class ReportService {
  private final SqlDatabase db;
  private final AuthService auth;

  public ReportService(SqlDatabase db, AuthService auth) {
    this.db = db;
    this.auth = auth;
  }

  public List<LinkedHashMap<String, Object>> report(AuthService.Session session, String type,
      LocalDate from, LocalDate to) throws SQLException {
    Validation.dates(from, to);
    return db.read(c -> {
      auth.require(c, session, Role.MANAGER);
      String filter = " WHERE i.SoldAt>=? AND i.SoldAt<? ";
      String sql = switch (type) {
        case "summary" -> "SELECT COUNT_BIG(*) AS InvoiceCount,COALESCE(SUM(i.Subtotal),0) AS Subtotal,"
            + "COALESCE(SUM(i.Discount),0) AS Discount,COALESCE(SUM(i.Total),0) AS NetRevenue "
            + "FROM dm.Invoices i" + filter;
        case "employee" -> "SELECT e.UserName,e.FullName,COUNT_BIG(*) AS InvoiceCount,"
            + "SUM(i.Total) AS NetRevenue FROM dm.Invoices i "
            + "JOIN dm.Employees e ON e.Id=i.EmployeeId" + filter
            + "GROUP BY e.UserName,e.FullName ORDER BY SUM(i.Total) DESC";
        case "best" -> "SELECT TOP (30) d.SKU,MAX(d.ProductName) AS ProductName,"
            + "SUM(CONVERT(bigint,d.Quantity)) AS Quantity,SUM(d.UnitPrice*d.Quantity) AS GrossSales "
            + "FROM dm.Invoices i JOIN dm.InvoiceDetails d ON d.InvoiceId=i.Id" + filter
            + "GROUP BY d.ProductId,d.SKU ORDER BY SUM(CONVERT(bigint,d.Quantity)) DESC";
        default -> throw new IllegalArgumentException("Loai bao cao khong hop le.");
      };
      return rows(c, sql, Validation.start(from), Validation.start(to.plusDays(1)));
    });
  }

  public List<LinkedHashMap<String, Object>> lowStock(AuthService.Session session) throws SQLException {
    return db.read(c -> {
      auth.require(c, session, Role.MANAGER, Role.WAREHOUSE);
      return rows(c, "SELECT p.Id,p.SKU,p.Name,SUM(CONVERT(bigint,s.Quantity)) AS TotalStock,"
          + "p.ReorderLevel FROM dm.Products p JOIN dm.Stock s ON s.ProductId=p.Id "
          + "WHERE p.Enabled=1 GROUP BY p.Id,p.SKU,p.Name,p.ReorderLevel "
          + "HAVING SUM(CONVERT(bigint,s.Quantity))<=p.ReorderLevel "
          + "ORDER BY SUM(CONVERT(bigint,s.Quantity))");
    });
  }
}
