package vn.dienmay.server.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import vn.dienmay.server.db.Jdbc;

/** Locks a versioned master record; lifecycle changes must use the reason-bearing lifecycle API. */
public final class RecordGuard {
  private RecordGuard() {}

  public static boolean expect(Connection c, String table, long id, int revision, Boolean enabled)
      throws SQLException {
    if (!Set.of("dm.Customers", "dm.Vendors", "dm.Employees", "dm.Promotions", "dm.Categories", "dm.Brands")
        .contains(table) || id <= 0 || revision <= 0) {
      throw new IllegalArgumentException("Can phien ban ban ghi hien tai. Tai lai du lieu truoc khi sua.");
    }
    try (PreparedStatement s = Jdbc.statement(c, "SELECT Revision,Enabled FROM " + table
        + " WITH (UPDLOCK,HOLDLOCK) WHERE Id=?", id); ResultSet r = s.executeQuery()) {
      if (!r.next() || r.getInt(1) != revision) {
        throw new IllegalArgumentException("Ban ghi da thay doi. Tai lai de tranh ghi de thay doi cua nguoi khac.");
      }
      boolean active = r.getBoolean(2);
      if (enabled != null && enabled != active) {
        throw new IllegalArgumentException("Dung nut Ngung su dung / Khoi phuc va nhap ly do de doi trang thai.");
      }
      return active;
    }
  }
}
