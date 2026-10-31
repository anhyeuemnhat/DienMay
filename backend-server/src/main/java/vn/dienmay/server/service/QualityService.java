package vn.dienmay.server.service;

import static vn.dienmay.server.db.Jdbc.rows;
import static vn.dienmay.server.db.Jdbc.statement;
import static vn.dienmay.server.db.Jdbc.update;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import vn.dienmay.common.model.CatalogRules;
import vn.dienmay.common.model.Item;
import vn.dienmay.common.model.Role;
import vn.dienmay.common.model.Validation;
import vn.dienmay.server.db.ProductDao;
import vn.dienmay.server.db.SqlDatabase;

/** Explicit, revision-bound human review with evidence. A valid code or URL never auto-verifies a product. */
public final class QualityService {
  private final SqlDatabase db;
  private final AuthService auth;

  public QualityService(SqlDatabase db, AuthService auth) { this.db = db; this.auth = auth; }

  public static String mode(Connection c) throws SQLException {
    try (PreparedStatement s = statement(c, "SELECT Mode FROM dm.DeploymentInfo WITH (HOLDLOCK) WHERE Id=1");
        ResultSet r = s.executeQuery()) {
      if (!r.next()) { throw new IllegalStateException("Missing deployment classification"); }
      String mode = r.getString(1);
      if (!List.of("PRODUCTION", "DEMO").contains(mode)) {
        throw new IllegalArgumentException("Database chưa phân loại. Quản trị cần xác nhận DEMO hoặc PRO" +
            "DUCTION trước khi ghi nghi" +
            "ệp vụ.");
      }
      return mode;
    }
  }

  public static void requireLookup(Connection c, String table, long id) throws SQLException {
    if (!List.of("dm.Brands", "dm.Categories").contains(table)) { throw new IllegalArgumentException("Lookup type"); }
    try (PreparedStatement s = statement(c, "SELECT Id FROM " + table
        + " WITH (HOLDLOCK) WHERE Id=? AND Enabled=1 AND VerificationSt" +
            "atus='VERIFIED'", id);
        ResultSet r = s.executeQuery()) {
      if (!r.next()) {
        throw new IllegalArgumentException("Thương hiệu / loại sản phẩm chưa được đối chiếu hoặc đã ngừn" +
            "g sử dụng.");
      }
    }
  }

  public static void requireTradable(Connection c, Item item) throws SQLException {
    String mode = mode(c);
    if (item.demo() != mode.equals("DEMO")) {
      throw new IllegalArgumentException("Không trộn sản phẩm / giá / tồn kho DEMO với môi trường thật.");
    }
    if (!item.enabled() || !"VERIFIED".equals(item.verificationStatus())) {
      throw new IllegalArgumentException("Sản phẩm chưa được duyệt danh tính hoặc đã ngừng kinh doanh: " + item.sku());
    }
    requireLookup(c, "dm.Brands", item.brandId());
    requireLookup(c, "dm.Categories", item.categoryId());
    if (!item.barcode().isBlank() && !"VERIFIED".equals(item.barcodeStatus())) {
      throw new IllegalArgumentException("Mã vạch chưa được đối chiếu với đúng sản phẩm: " + item.sku());
    }
    CatalogRules.barcode(item.barcode(), item.barcodeType());
  }

  private static String table(String type) {
    return switch (type) {
      case "product" -> "dm.Products";
      case "brand" -> "dm.Brands";
      case "category" -> "dm.Categories";
      default -> throw new IllegalArgumentException("Loại dữ liệu cần duyệt không hợp lệ.");
    };
  }

  public List<LinkedHashMap<String, Object>> list(AuthService.Session session, String type) throws SQLException {
    String target = table(type);
    return db.read(c -> {
      auth.require(c, session, Role.MANAGER);
      String extra = type.equals("product") ?
          ",SKU,Model,Description,Barcode,BarcodeType,BarcodeStatus,Bar" +
              "codeSource,BarcodeNote,IsD" +
              "emo,"
          + "(SELECT Name FROM dm.Brands WHERE Id=dm.Products.BrandId) AS" +
              " BrandName,"
          + "(SELECT Name FROM dm.Categories WHERE Id=dm.Products.Categor" +
              "yId) AS CategoryName" : "";
      return rows(c, "SELECT TOP (2000) Id,Name,Enabled,Revision,VerificationStatu" +
          "s,VerificationSource,"
          + "VerificationNote,VerifiedAt" + extra + " FROM " + target + " ORDER BY Id DESC");
    });
  }

  public List<LinkedHashMap<String, Object>> references(AuthService.Session session) throws SQLException {
    return db.read(c -> {
      auth.require(c, session, Role.MANAGER);
      return rows(c, "SELECT Id,BrandName,CategoryName,ProductName,Model,Specifica" +
          "tions,SourceUrl,CheckedOn "
          + "FROM dm.ReferenceProducts ORDER BY BrandName,Model");
    });
  }

  public void review(AuthService.Session session, String type, long id, int revision, String field,
      String status, String source, String note, Long referenceId) throws SQLException {
    String target = table(type);
    if (id <= 0 || revision <= 0 || !CatalogRules.STATUSES.contains(status)
        || !List.of("identity", "barcode").contains(field) || !type.equals("product") && field.equals("barcode")) {
      throw new IllegalArgumentException("Nội dung duyệt không hợp lệ.");
    }
    String evidence = status.equals("VERIFIED") ? CatalogRules.source(source)
        : Validation.optional(source, 1000, "Nguon");
    String reason = Validation.required(note, 1000, "Căn cứ / kết quả đối chiếu");
    if (reason.length() < 10) { throw new
        IllegalArgumentException("Mô tả căn cứ cần ít nhất 10 ký tự."); }
    db.transaction(c -> {
      auth.require(c, session, Role.MANAGER);
      if (status.equals("DEMO") && !mode(c).equals("DEMO")) {
        throw new IllegalArgumentException("Chỉ đánh dấu DEMO trong database DEMO riêng.");
      }
      String prefix = field.equals("identity") ? "Verification" : "Barcode";
      String old;
      try (PreparedStatement s = statement(c, "SELECT " + prefix + "Status,Revision FROM " + target
          + " WITH (UPDLOCK,HOLDLOCK) WHERE Id=?", id); ResultSet r = s.executeQuery()) {
        if (!r.next() || r.getInt(2) != revision) {
          throw new IllegalArgumentException("Bản ghi đã thay đổi. Tải lại trước khi duyệt.");
        }
        old = r.getString(1);
      }
      if (type.equals("product") && status.equals("VERIFIED")) {
        Item item = new ProductDao().get(c, id, true);
        if (field.equals("barcode")) {
          if (!"VERIFIED".equals(item.verificationStatus()) || item.barcode().isBlank()) {
            throw new IllegalArgumentException("Duyệt danh tính và nhập mã trước khi đối chiếu mã vạch.");
          }
          CatalogRules.barcode(item.barcode(), item.barcodeType());
        } else {
          requireLookup(c, "dm.Brands", item.brandId());
          requireLookup(c, "dm.Categories", item.categoryId());
          Validation.required(item.model(), 100, "Model chính hãng");
          if (referenceId != null) { matchReference(c, item, referenceId, evidence); }
        }
      }
      String by = field.equals("identity") ? "VerifiedBy" : "BarcodeVerifiedBy";
      String at = field.equals("identity") ? "VerifiedAt" : "BarcodeVerifiedAt";
      update(c, "UPDATE " + target + " SET " + prefix + "Status=?," + prefix + "Source=?,"
          + prefix + "Note=?," + by + "=?," + at + "=SYSUTCDATETIME(),Revision=Revision+1 WHERE Id=?",
          status, evidence, reason, session.employeeId(), id);
      audit(c, type, id, "REVIEW_" + field.toUpperCase(java.util.Locale.ROOT), old, status,
          reason, session.employeeId());
      return null;
    });
  }

  private static void matchReference(Connection c, Item item, long referenceId, String source) throws SQLException {
    try (PreparedStatement s = statement(c, "SELECT BrandName,CategoryName,ProductName,Model,Specificatio" +
        "ns,SourceUrl "
        + "FROM dm.ReferenceProducts WITH (HOLDLOCK) WHERE Id=?", referenceId); ResultSet r = s.executeQuery()) {
      if (!r.next() || !CatalogRules.normalize(item.brand()).equals(CatalogRules.normalize(r.getString(1)))
          || !CatalogRules.normalize(item.category()).equals(CatalogRules.normalize(r.getString(2)))
          || !CatalogRules.normalize(item.name()).equals(CatalogRules.normalize(r.getString(3)))
          || !CatalogRules.normalize(item.model()).equals(CatalogRules.normalize(r.getString(4)))
          || !CatalogRules.normalize(item.description()).equals(CatalogRules.normalize(r.getString(5)))
          || !source.equals(r.getString(6))) {
        throw new IllegalArgumentException("Thương hiệu, tên, model, loại, mô tả hoặc nguồn không khớp d" +
            "anh mục tham chiếu đã chọn.");
      }
    }
  }

  public List<LinkedHashMap<String, Object>> auditLog(AuthService.Session session) throws SQLException {
    return db.read(c -> {
      auth.require(c, session, Role.MANAGER);
      return rows(c, "SELECT TOP (1000) a.Id,a.EntityType,a.EntityId,a.Action,a.Ol" +
          "dValue,a.NewValue,a.Reason,"
          + "e.UserName,a.CreatedAt FROM dm.AuditLog a JOIN dm.Employees " +
              "e ON e.Id=a.EmployeeId ORD" +
              "ER BY a.Id DESC");
    });
  }

  public static void audit(Connection c, String type, long id, String action, String old, String next,
      String reason, long employee) throws SQLException {
    update(c, "INSERT dm.AuditLog(EntityType,EntityId,Action,OldValue,NewVa" +
        "lue,Reason,EmployeeId) "
        + "VALUES(?,?,?,?,?,?,?)", type, id, action, old, next, reason, employee);
  }
}
