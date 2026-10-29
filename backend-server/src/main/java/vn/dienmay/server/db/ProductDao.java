package vn.dienmay.server.db;

import static vn.dienmay.server.db.Jdbc.statement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import vn.dienmay.common.model.Item;

/** Product queries and row mapping; the service owns authorization and transactions. */
public final class ProductDao {
  private static final String SELECT = "SELECT p.Id,p.SKU,COALESCE(p.Barcode,N''),p.Name,"
      + "p.CategoryId,p.BrandId,c.Name,b.Name,p.Model,p.Description,p.CostPrice,p.SellingPrice,"
      + "p.WarrantyMonths,p.TrackSerial,p.Enabled,p.ReorderLevel,COALESCE(k.Quantity,0),"
      + "COALESCE(q.Quantity,0),p.Revision FROM dm.Products p %s "
      + "JOIN dm.Categories c ON c.Id=p.CategoryId JOIN dm.Brands b ON b.Id=p.BrandId "
      + "LEFT JOIN dm.Stock k ON k.ProductId=p.Id AND k.Location='KHO' "
      + "LEFT JOIN dm.Stock q ON q.ProductId=p.Id AND q.Location='QUAY' ";

  private Item map(ResultSet r) throws SQLException {
    return new Item(r.getLong(1), r.getString(2), r.getString(3), r.getString(4), r.getLong(5),
        r.getLong(6), r.getString(7), r.getString(8), r.getString(9), r.getString(10),
        r.getBigDecimal(11), r.getBigDecimal(12), r.getInt(13), r.getBoolean(14), r.getBoolean(15),
        r.getInt(16), r.getInt(17), r.getInt(18), r.getInt(19));
  }

  public Item get(Connection c, long id, boolean lock) throws SQLException {
    try (PreparedStatement s = statement(c,
        SELECT.formatted(lock ? "WITH (UPDLOCK,HOLDLOCK)" : "") + "WHERE p.Id=?", id);
        ResultSet r = s.executeQuery()) {
      if (!r.next()) {
        throw new IllegalArgumentException("Khong tim thay san pham.");
      }
      return map(r);
    }
  }

  public Item code(Connection c, String code) throws SQLException {
    try (PreparedStatement s = statement(c,
        SELECT.formatted("") + "JOIN dm.ProductCodes pc ON pc.ProductId=p.Id WHERE pc.Code=?", code);
        ResultSet r = s.executeQuery()) {
      if (!r.next()) {
        throw new IllegalArgumentException("Khong tim thay SKU/ma vach: " + code);
      }
      return map(r);
    }
  }

  public List<Item> search(Connection c, String text) throws SQLException {
    List<Item> items = new ArrayList<>();
    String sql = SELECT.formatted("") + "WHERE p.SKU LIKE ? ESCAPE '~' "
        + "OR p.Barcode LIKE ? ESCAPE '~' OR p.Name LIKE ? ESCAPE '~' "
        + "ORDER BY p.Id OFFSET 0 ROWS FETCH NEXT 500 ROWS ONLY";
    try (PreparedStatement s = statement(c, sql, like(text), like(text), like(text));
        ResultSet r = s.executeQuery()) {
      while (r.next()) {
        items.add(map(r));
      }
    }
    return items;
  }

  public static String like(String value) {
    return "%" + value.replace("~", "~~").replace("%", "~%")
        .replace("_", "~_").replace("[", "~[") + "%";
  }

  public List<String> availableSerials(Connection c, long id, String location) throws SQLException {
    List<String> values = new ArrayList<>();
    try (PreparedStatement s = statement(c, "SELECT Serial FROM dm.ProductUnits "
        + "WHERE ProductId=? AND Location=? AND Status='IN_STOCK' ORDER BY Serial", id, location);
        ResultSet r = s.executeQuery()) {
      while (r.next()) {
        values.add(r.getString(1));
      }
    }
    return values;
  }
}
