package vn.dienmay.server.service;

import static vn.dienmay.server.db.Jdbc.insert;
import static vn.dienmay.server.db.Jdbc.statement;
import static vn.dienmay.server.db.Jdbc.update;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import vn.dienmay.common.model.Item;
import vn.dienmay.common.model.Money;
import vn.dienmay.common.model.Role;
import vn.dienmay.common.model.Validation;
import vn.dienmay.server.db.ProductDao;
import vn.dienmay.server.db.SqlDatabase;

/** Product/catalog use cases, preserving revision checks and SKU/barcode uniqueness. */
public final class CatalogService {
  public record Lookup(long id, String name) {
    @Override
    public String toString() {
      return name;
    }
  }

  private final SqlDatabase db;
  private final AuthService auth;
  private final ProductDao dao;

  public CatalogService(SqlDatabase db, AuthService auth) {
    this(db, auth, new ProductDao());
  }

  public CatalogService(SqlDatabase db, AuthService auth, ProductDao dao) {
    this.db = db;
    this.auth = auth;
    this.dao = dao;
  }

  public List<Item> search(AuthService.Session session, String text) throws SQLException {
    return db.read(c -> {
      auth.require(c, session, Role.values());
      return dao.search(c, Validation.optional(text, 180, "Tu khoa"));
    });
  }

  public Item find(AuthService.Session session, String code) throws SQLException {
    return db.read(c -> {
      auth.require(c, session, Role.values());
      return dao.code(c, Validation.required(code, 128, "SKU/ma vach"));
    });
  }

  public Item get(AuthService.Session session, long id) throws SQLException {
    return db.read(c -> {
      auth.require(c, session, Role.values());
      return dao.get(c, id, false);
    });
  }

  public List<String> serials(AuthService.Session session, long id, String location)
      throws SQLException {
    if (!Set.of("KHO", "QUAY").contains(location)) {
      throw new IllegalArgumentException("Vi tri kho khong hop le.");
    }
    return db.read(c -> {
      auth.require(c, session, Role.values());
      return dao.availableSerials(c, id, location);
    });
  }

  public long save(AuthService.Session session, Item value) throws SQLException {
    String sku = Validation.required(value.sku(), 64, "SKU");
    String barcode = Validation.optional(value.barcode(), 128, "Ma vach");
    String name = Validation.required(value.name(), 180, "Ten san pham");
    String model = Validation.optional(value.model(), 100, "Model");
    String description = Validation.optional(value.description(), 1000, "Mo ta");
    if (value.id() < 0 || value.categoryId() <= 0 || value.brandId() <= 0
        || value.warrantyMonths() < 0 || value.warrantyMonths() > 120 || value.reorderLevel() < 0) {
      throw new IllegalArgumentException("Ma danh muc, thang bao hanh hoac muc ton khong hop le.");
    }
    Money.exact(value.costPrice());
    Money.exact(value.sellingPrice());
    return db.transaction(c -> {
      auth.require(c, session, Role.MANAGER);
      long id = value.id();
      if (id == 0) {
        id = insert(c, "INSERT dm.Products(SKU,Barcode,Name,CategoryId,BrandId,Model,Description,"
            + "CostPrice,SellingPrice,WarrantyMonths,TrackSerial,Enabled,ReorderLevel) "
            + "OUTPUT INSERTED.Id VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", sku,
            barcode.isBlank() ? null : barcode, name, value.categoryId(), value.brandId(), model,
            description, value.costPrice(), value.sellingPrice(), value.warrantyMonths(),
            value.trackSerial(), value.enabled(), value.reorderLevel());
        update(c, "INSERT dm.Stock(ProductId,Location,Quantity) VALUES(?,'KHO',0),(?,'QUAY',0)",
            id, id);
      } else {
        Item old = dao.get(c, id, true);
        if (old.revision() != value.revision()) {
          throw new IllegalArgumentException("San pham vua duoc sua. Hay tai lai truoc khi luu.");
        }
        if (old.trackSerial() != value.trackSerial()) {
          try (PreparedStatement s = statement(c,
              "SELECT TOP (1) 1 FROM dm.StockMovements WHERE ProductId=?", id);
              ResultSet r = s.executeQuery()) {
            if (r.next()) {
              throw new IllegalArgumentException("Khong doi che do Serial sau phat sinh kho.");
            }
          }
        }
        update(c, "UPDATE dm.Products SET SKU=?,Barcode=?,Name=?,CategoryId=?,BrandId=?,Model=?,"
            + "Description=?,CostPrice=?,SellingPrice=?,WarrantyMonths=?,TrackSerial=?,Enabled=?,"
            + "ReorderLevel=?,Revision=Revision+1 WHERE Id=?", sku,
            barcode.isBlank() ? null : barcode, name, value.categoryId(), value.brandId(), model,
            description, value.costPrice(), value.sellingPrice(), value.warrantyMonths(),
            value.trackSerial(), value.enabled(), value.reorderLevel(), id);
        // Search index entries only. No invoice, unit or movement history is removed.
        update(c, "DELETE FROM dm.ProductCodes WHERE ProductId=?", id);
      }
      update(c, "INSERT dm.ProductCodes(Code,ProductId) VALUES(?,?)", sku, id);
      if (!barcode.isBlank() && !barcode.equalsIgnoreCase(sku)) {
        update(c, "INSERT dm.ProductCodes(Code,ProductId) VALUES(?,?)", barcode, id);
      }
      return id;
    });
  }

  private static String table(String type) {
    return switch (type) {
      case "category" -> "dm.Categories";
      case "brand" -> "dm.Brands";
      case "vendor" -> "dm.Vendors";
      default -> throw new IllegalArgumentException("Loai danh muc khong hop le.");
    };
  }

  public List<Lookup> lookups(AuthService.Session session, String type) throws SQLException {
    String tableName = table(type); // Resolve allowlist before composing SQL.
    return db.read(c -> {
      auth.require(c, session, type.equals("vendor")
          ? new Role[] {Role.MANAGER, Role.WAREHOUSE} : Role.values());
      List<Lookup> values = new ArrayList<>();
      try (PreparedStatement s = statement(c, "SELECT Id,Name FROM " + tableName + " ORDER BY Name");
          ResultSet r = s.executeQuery()) {
        while (r.next()) {
          values.add(new Lookup(r.getLong(1), r.getString(2)));
        }
      }
      return values;
    });
  }

  public long saveLookup(AuthService.Session session, String type, Long id, String name)
      throws SQLException {
    String valid = Validation.required(name, 100, "Ten");
    String tableName = table(type);
    if (type.equals("vendor")) {
      throw new IllegalArgumentException("Dung chuc nang nha cung cap de luu.");
    }
    return db.transaction(c -> {
      auth.require(c, session, Role.MANAGER);
      if (id == null) {
        return insert(c, "INSERT " + tableName + "(Name) OUTPUT INSERTED.Id VALUES(?)", valid);
      }
      if (update(c, "UPDATE " + tableName + " SET Name=? WHERE Id=?", valid, id) != 1) {
        throw new IllegalArgumentException("Khong tim thay danh muc.");
      }
      return id;
    });
  }
}
