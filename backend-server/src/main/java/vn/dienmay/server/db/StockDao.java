package vn.dienmay.server.db;

import static vn.dienmay.server.db.Jdbc.insert;
import static vn.dienmay.server.db.Jdbc.statement;
import static vn.dienmay.server.db.Jdbc.update;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import vn.dienmay.common.model.Item;

/** Atomic quantity and serialized-unit operations, executed only inside a service transaction. */
public final class StockDao {
  public void add(Connection c, long product, String location, int quantity) throws SQLException {
    if (quantity <= 0) {
      throw new IllegalArgumentException("So luong cong kho phai lon hon 0.");
    }
    int changed = update(c, "UPDATE dm.Stock SET Quantity=Quantity+? "
        + "WHERE ProductId=? AND Location=? AND Quantity<=2147483647-?",
        quantity, product, location, quantity);
    if (changed != 1) {
      throw new IllegalArgumentException("Khong the tang ton kho: thieu dong hoac tran so luong.");
    }
  }

  public void subtract(Connection c, long product, String location, int quantity) throws SQLException {
    if (quantity <= 0) {
      throw new IllegalArgumentException("So luong tru kho phai lon hon 0.");
    }
    int changed = update(c, "UPDATE dm.Stock SET Quantity=Quantity-? "
        + "WHERE ProductId=? AND Location=? AND Quantity>=?",
        quantity, product, location, quantity);
    if (changed != 1) {
      throw new IllegalArgumentException("Khong du ton tai " + location + ". Hay tai lai du lieu.");
    }
  }

  /** Exactly ten columns, ten bind markers, and ten bound values (baseline defect fixed). */
  public long movement(Connection c, UUID request, long product, String location, int delta,
      String kind, String reference, long employee, Long invoice, Long vendor, BigDecimal cost)
      throws SQLException {
    return insert(c, "INSERT dm.StockMovements(RequestId,ProductId,Location,Delta,Kind,Reference,"
        + "EmployeeId,InvoiceId,VendorId,UnitCost) OUTPUT INSERTED.Id VALUES(?,?,?,?,?,?,?,?,?,?)",
        request, product, location, delta, kind, reference, employee, invoice, vendor, cost);
  }

  public long sellUnit(Connection c, long product, String serial) throws SQLException {
    try (PreparedStatement s = statement(c,
        "UPDATE dm.ProductUnits SET Status='SOLD' OUTPUT INSERTED.Id "
            + "WHERE ProductId=? AND Serial=? AND Status='IN_STOCK' AND Location='QUAY'",
        product, serial); ResultSet r = s.executeQuery()) {
      if (!r.next()) {
        throw new IllegalArgumentException("Serial/IMEI khong con o quay hoac da ban: " + serial);
      }
      return r.getLong(1);
    }
  }

  public long transferUnit(Connection c, long product, String serial, String from, String to)
      throws SQLException {
    try (PreparedStatement s = statement(c,
        "UPDATE dm.ProductUnits SET Location=? OUTPUT INSERTED.Id "
            + "WHERE ProductId=? AND Serial=? AND Status='IN_STOCK' AND Location=?",
        to, product, serial, from); ResultSet r = s.executeQuery()) {
      if (!r.next()) {
        throw new IllegalArgumentException("Serial/IMEI khong nam tai nguon chuyen: " + serial);
      }
      return r.getLong(1);
    }
  }

  public void attachUnit(Connection c, long movement, long unit) throws SQLException {
    update(c, "INSERT dm.StockMovementUnits(MovementId,UnitId) VALUES(?,?)", movement, unit);
  }

  public static void checkSerials(Item item, int quantity, List<String> serials) {
    if (item.trackSerial() ? serials.size() != quantity : !serials.isEmpty()) {
      throw new IllegalArgumentException("Thiet bi theo Serial can du mot ma cho moi chiec.");
    }
    if (new HashSet<>(serials).size() != serials.size()) {
      throw new IllegalArgumentException("Serial/IMEI bi trung trong yeu cau.");
    }
  }
}
