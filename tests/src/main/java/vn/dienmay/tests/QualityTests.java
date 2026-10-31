package vn.dienmay.tests;

import static vn.dienmay.tests.TestKit.check;
import static vn.dienmay.tests.TestKit.equal;
import static vn.dienmay.tests.TestKit.throwsType;

import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import vn.dienmay.client.ui.GuidedInput;
import vn.dienmay.client.ui.RowsPanel;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.model.CatalogRules;
import vn.dienmay.common.model.InventoryRequest;
import vn.dienmay.common.model.Item;
import vn.dienmay.common.model.Money;
import vn.dienmay.common.model.Role;
import vn.dienmay.server.DatabaseInstaller;
import vn.dienmay.server.InventorySystem;
import vn.dienmay.server.db.SqlDatabase;
import vn.dienmay.server.service.AuthService;
import vn.dienmay.server.service.Passwords;
import vn.dienmay.server.service.QualityService;
import vn.dienmay.server.service.RecordGuard;

/** Deterministic rule/UI/JDBC-double tests. This suite DOES NOT execute or validate T-SQL. */
public final class QualityTests {
  private static final String SOURCE = "https://reference.example.invalid/specification";
  private QualityTests() {}

  private static void edt(TestKit.Checked action) throws Exception {
    FutureTask<Void> task = new FutureTask<>(() -> { action.run(); return null; });
    SwingUtilities.invokeAndWait(task);
    task.get();
  }

  private static JTextField input(Container parent) {
    for (Component c : parent.getComponents()) {
      if (c instanceof JTextField field) { return field; }
      if (c instanceof Container child) {
        JTextField field = input(child);
        if (field != null) { return field; }
      }
    }
    return null;
  }

  private static Item item(long id, int revision, String status, String barcode, String barcodeType,
      String barcodeStatus, boolean demo) {
    return new Item(id, "000-TEST-SKU", barcode, "TEST-ONLY catalog identity", 2, 3,
        "TEST-ONLY category", "TEST-ONLY brand", "TEST-MODEL", "TEST-ONLY specifications",
        Money.of("1"), Money.of("2"), 12, false, true, 1, 0, 0, revision,
        status, barcodeType, barcodeStatus, demo, SOURCE);
  }

  private record Call(String sql, Map<Integer, Object> parameters) {}

  /** Exhaustive expected query dispatch; unexpected SQL fails instead of returning plausible rows. */
  private static final class ServiceDouble {
    final char[] password = UUID.randomUUID().toString().toCharArray();
    final String hash = Passwords.hash(password);
    final JdbcDouble transaction = new JdbcDouble();
    final List<Call> calls = new ArrayList<>();
    Role role = Role.MANAGER;
    String mode = "DEMO";
    boolean lookup = true;
    int revision = 1;
    int managers = 1;
    Item product = item(7, 1, "PENDING", "", "NONE", "PENDING", true);
    Object[] reference = {product.brand(), product.category(), product.name(), product.model(),
        product.description(), SOURCE};

    private List<Object[]> query(String sql) throws Exception {
      Object[] row;
      if (sql.startsWith("SELECT Id,FullName,PasswordHash")) {
        row = new Object[] {1L, "TEST account", hash, role.name(), 1, false, true, 0, null,
            Timestamp.valueOf("2026-10-31 00:00:00")};
      } else if (sql.startsWith("SELECT Role,Enabled")) { row = new Object[] {role.name(), true, 1, false}; }
      else if (sql.startsWith("SELECT Mode")) { row = new Object[] {mode}; }
      else if (sql.startsWith("SELECT Id FROM dm.Brands") || sql.startsWith("SELECT Id FROM dm.Categories")) {
        return lookup ? Collections.singletonList(new Object[] {2L}) : List.of();
      } else if (sql.startsWith("SELECT p.Id")) {
        java.lang.reflect.RecordComponent[] fields = Item.class.getRecordComponents();
        row = new Object[fields.length];
        for (int i = 0; i < fields.length; i++) { row[i] = fields[i].getAccessor().invoke(product); }
      } else if (sql.startsWith("SELECT VerificationStatus,Revision") ||
          sql.startsWith("SELECT BarcodeStatus,Revision")) {
        row = new Object[] {"PENDING", revision};
      } else if (sql.startsWith("SELECT Enabled,Revision")) { row = new Object[] {true, revision}; }
      else if (sql.startsWith("SELECT BrandName,CategoryName,ProductName")) { row = reference; }
      else if (sql.startsWith("SELECT COUNT(*) FROM dm.Employees")) { row = new Object[] {managers}; }
      else if (sql.startsWith("DECLARE @r")) { row = new Object[] {0}; }
      else if (sql.startsWith("INSERT dm.Products")) { row = new Object[] {99L}; }
      else { throw new AssertionError("Unexpected quality SQL: " + sql); }
      return Collections.singletonList(row);
    }

    Connection connection() {
      return JdbcDouble.proxy(Connection.class, (p, method, args) -> {
        if (!method.getName().equals("prepareStatement")) { return transaction.invoke(p, method, args); }
        String sql = (String) args[0];
        Map<Integer, Object> values = new LinkedHashMap<>();
        return JdbcDouble.proxy(PreparedStatement.class, (s, operation, params) -> {
          if (operation.getName().startsWith("set") && params != null && params.length == 2
              && params[0] instanceof Integer index) { values.put(index, params[1]); }
          if (operation.getName().equals("executeQuery")) {
            calls.add(new Call(sql, new LinkedHashMap<>(values)));
            return JdbcDouble.rows(query(sql));
          }
          if (operation.getName().equals("executeUpdate")) {
            calls.add(new Call(sql, new LinkedHashMap<>(values)));
            return 1;
          }
          return JdbcDouble.defaultValue(operation.getReturnType());
        });
      });
    }
  }

  public static void main(String[] args) throws Exception {
    TestKit t = new TestKit();
    for (String code : List.of("5012345670003", "042100005264", "00000000", "00000000000000")) {
      t.test("GTIN / checksum fixture " + code + " is structural, not a product claim",
          () -> check(CatalogRules.validGtin(code), "Checksum algorithm"));
    }
    t.test("GTIN / leading zero preserved", () -> equal("042100005264", CatalogRules.barcode("042100005264", "UPC12")));
    t.test("GTIN / incorrect check digit rejected", () -> throwsType(IllegalArgumentException.class,
        () -> CatalogRules.barcode("5012345670004", "EAN13")));
    t.test("GTIN / wrong declared type rejected", () -> throwsType(IllegalArgumentException.class,
        () -> CatalogRules.barcode("5012345670003", "UPC12")));
    t.test("GTIN / Unicode digits and letters rejected", () -> {
      check(!CatalogRules.validGtin("\uff10\uff10\uff10\uff10\uff10\uff10\uff10\uff10"), "ASCII required");
      check(!CatalogRules.validGtin("1234567A"), "Digits required");
    });
    t.test("Barcode / no arbitrary code with NONE", () -> throwsType(IllegalArgumentException.class,
        () -> CatalogRules.barcode("000-INT", "NONE")));
    t.test("Barcode / unknown is blank, not invented", () -> equal("", CatalogRules.barcode(null, "NONE")));
    t.test("Barcode / OTHER retains exact leading zeros", () -> equal("000-INT", CatalogRules.barcode("000-INT",
        "OTHER")));
    t.test("IMEI / mathematical fixture is not proof of a device", () ->
        check(CatalogRules.validImei("000000000000000"), "Luhn"));
    t.test("IMEI / wrong length and check digit", () -> {
      check(!CatalogRules.validImei("000000000000001"), "Check digit");
      check(!CatalogRules.validImei("00000000000000"), "Length");
      check(!CatalogRules.validImei(null), "Missing value");
    });
    t.test("Phone / optional and caller supplied", () -> {
      equal("", CatalogRules.phone(null)); equal("+84 (28) 1234-5678", CatalogRules.phone("+84 (28) 1234-5678"));
      throwsType(IllegalArgumentException.class, () -> CatalogRules.phone("abc"));
      throwsType(IllegalArgumentException.class, () -> CatalogRules.phone("1234567890123456"));
    });
    t.test("Evidence / only HTTPS without embedded credentials or fragment", () -> {
      equal(SOURCE, CatalogRules.source(SOURCE));
      for (String source : List.of("http://example.invalid", "file:///tmp/a", "https://u:p@example.invalid/a",
          "https://example.invalid/#secret", "https:///missing-host", "")) {
        throwsType(IllegalArgumentException.class, () -> CatalogRules.source(source));
      }
    });
    t.test("Legacy Item constructor never asserts verified status", () -> {
      equal("PENDING", Fixtures.item(7, true, 1).verificationStatus());
      equal("PENDING", Fixtures.item(7, true, 1).barcodeStatus());
    });
    InventoryRequest legacy = new InventoryRequest(UUID.randomUUID(), 7, 1, List.of("000000000000001"),
        "TEST-ONLY receipt", 1L, Money.of("1"), null, "KHO");
    t.test("Legacy numeric serial never guessed to be IMEI", () -> equal("SERIAL", legacy.identifierType()));
    t.test("Old persisted JSON migrates only the missing identifierType", () -> {
      Map<String, Object> map = Json.object(Json.parse(Json.stringify(legacy)));
      map.remove("identifierType");
      InventoryRequest restored = Json.convert(map, InventoryRequest.class);
      equal(legacy, restored); equal(legacy.fingerprint(), restored.fingerprint());
      map.remove("productId");
      throwsType(IllegalArgumentException.class, () -> Json.convert(map, InventoryRequest.class));
    });
    t.test("Explicit IMEI validates before a request can be sent", () -> throwsType(IllegalArgumentException.class,
        () -> new InventoryRequest(legacy.id(), 7, 1, legacy.serials(), legacy.reference(), 1L, Money.of("1"),
            null, "KHO", "IMEI")));
    t.test("Identifier type participates in non-legacy fingerprint", () -> {
      InventoryRequest serial = new InventoryRequest(legacy.id(), 7, 1, List.of("000000000000000"),
          legacy.reference(), 1L, Money.of("1"), null, "KHO");
      InventoryRequest imei = new InventoryRequest(legacy.id(), 7, 1, serial.serials(), serial.reference(),
          1L, Money.of("1"), null, "KHO", "IMEI");
      check(!serial.fingerprint().equals(imei.fingerprint()), "Type collision");
    });
    t.test("Installer / exact isolated targets accepted", () -> {
      DatabaseInstaller.validateTarget("Store_Demo", "Store_Demo", "init-demo", "--confirm-demo");
      DatabaseInstaller.validateTarget("Store_Test", "Store_Test", "init-test", "--confirm-test");
      DatabaseInstaller.validateTarget("Store", "Store", "migrate", "--backup-confirmed");
    });
    t.test("Installer / system databases, mismatch and injection rejected", () -> {
      for (String name : List.of("master", "MODEL", "msdb", "tempdb", "a];DROP DATABASE x;--")) {
        throwsType(SecurityException.class, () -> DatabaseInstaller.validateTarget(name, name, "migrate",
            "--backup-confirmed"));
      }
      throwsType(SecurityException.class, () -> DatabaseInstaller.validateTarget("Store", "Store_Demo", "init-demo",
          "--confirm-demo"));
      throwsType(SecurityException.class, () -> DatabaseInstaller.validateTarget("Store", "Store", "seed-demo",
          "--confirm-demo"));
    });
    t.test("Installer / explicit confirmation, no relabeling DEMO as production", () -> {
      throwsType(SecurityException.class, () -> DatabaseInstaller.validateTarget("Store_Demo", "Store_Demo",
          "init-demo", ""));
      throwsType(SecurityException.class, () -> DatabaseInstaller.validateTarget("Store", "Store", "migrate", ""));
      throwsType(SecurityException.class, () -> DatabaseInstaller.validateTarget("Store_Demo", "Store_Demo",
          "classify-production", "--acknowledge-unverified"));
    });
    t.test("Installer / supplied batch separator and forbidden SQLCMD", () -> {
      equal(List.of("SELECT 1;", "SELECT 2;"), DatabaseInstaller.batches("SELECT 1;\nGO -- batch\nSELECT 2;\ngo\n"));
      throwsType(IllegalArgumentException.class, () -> DatabaseInstaller.batches(":r other.sql\n"));
      for (String path : List.of("schema/001_schema.sql", "migration/007_api_transition.sql",
          "migration/010_quality_lifecycle.sql", "migration/011_identifiers.sql", "reference/012_reference_catalog.sql",
          "demo/002_demo.sql")) {
        check(!DatabaseInstaller.batches(Files.readString(Path.of("database", path))).isEmpty(), "Empty script: " +
            path);
      }
    });
    JdbcDouble guard = new JdbcDouble();
    guard.resultRows = Collections.singletonList(new Object[] {3, true});
    t.test("Revision / matching update and row lock", () -> {
      check(RecordGuard.expect(guard.connection(), "dm.Customers", 7, 3, true), "Active flag");
      check(guard.lastSql.contains("UPDLOCK,HOLDLOCK"), "Lock request");
      equal(7L, guard.bound.get(1));
    });
    t.test("Revision / stale write cannot overwrite", () -> throwsType(IllegalArgumentException.class,
        () -> RecordGuard.expect(guard.connection(), "dm.Customers", 7, 2, true)));
    t.test("Revision / ordinary update cannot bypass lifecycle reason", () -> throwsType(IllegalArgumentException.class,
        () -> RecordGuard.expect(guard.connection(), "dm.Customers", 7, 3, false)));
    t.test("Revision / table allowlist and positive version", () -> {
      throwsType(IllegalArgumentException.class, () -> RecordGuard.expect(guard.connection(), "dm.Invoices", 7, 3,
          true));
      throwsType(IllegalArgumentException.class, () -> RecordGuard.expect(guard.connection(), "dm.Customers;--", 7, 3,
          true));
      throwsType(IllegalArgumentException.class, () -> RecordGuard.expect(guard.connection(), "dm.Customers", 7, -1,
          true));
    });
    ServiceDouble database = new ServiceDouble();
    InventorySystem app = new InventorySystem(new SqlDatabase(database::connection));
    AuthService.Session manager = app.auth().login("TEST", database.password);
    t.test("Tradability / pending identity cannot be sold or moved", () -> throwsType(IllegalArgumentException.class,
        () -> QualityService.requireTradable(database.connection(), database.product)));
    t.test("Tradability / unclassified database fails closed", () -> {
      database.mode = "UNCLASSIFIED";
      try { throwsType(IllegalArgumentException.class, () -> QualityService.mode(database.connection())); }
      finally { database.mode = "DEMO"; }
    });
    t.test("Tradability / pending barcode despite valid checksum is blocked", () ->
        throwsType(IllegalArgumentException.class,
        () -> QualityService.requireTradable(database.connection(), item(7, 1, "VERIFIED", "5012345670003", "EAN13",
            "PENDING", true))));
    t.test("Tradability / DEMO and real inventory cannot mix", () -> throwsType(IllegalArgumentException.class,
        () -> QualityService.requireTradable(database.connection(), item(7, 1, "VERIFIED", "", "NONE", "PENDING",
            false))));
    t.test("Tradability / absent barcode is allowed with verified identity", () ->
        QualityService.requireTradable(database.connection(), item(7, 1, "VERIFIED", "", "NONE", "PENDING", true)));
    t.test("Tradability / disabled or pending reference fails closed", () -> {
      database.lookup = false;
      try { throwsType(IllegalArgumentException.class, () -> QualityService.requireTradable(database.connection(),
          item(7, 1, "VERIFIED", "", "NONE", "PENDING", true))); }
      finally { database.lookup = true; }
    });
    t.test("Catalog create / forged approved status ignored and environment owns IsDemo", () -> {
      database.calls.clear();
      equal(99L, app.catalog().save(manager, item(0, 0, "VERIFIED", "", "NONE", "VERIFIED", false)));
      Call insert = database.calls.stream().filter(c ->
          c.sql().startsWith("INSERT dm.Products")).findFirst().orElseThrow();
      check(!insert.sql().contains("VerificationStatus"), "Must use default PENDING, not client status");
      equal(true, insert.parameters().get(15));
      check(database.calls.stream().anyMatch(c -> c.sql().startsWith("INSERT dm.AuditLog")), "Create audit missing");
    });
    t.test("Catalog update / stale revision rolls back before write", () -> {
      database.calls.clear();
      int rollbacks = database.transaction.rollbacks;
      throwsType(IllegalArgumentException.class, () -> app.catalog().save(manager,
          item(7, 5, "VERIFIED", "", "NONE", "PENDING", true)));
      equal(rollbacks + 1, database.transaction.rollbacks);
      check(database.calls.stream().noneMatch(c -> c.sql().startsWith("UPDATE dm.Products")), "Stale write issued");
    });
    t.test("Review / stale snapshot cannot certify changed record", () -> throwsType(IllegalArgumentException.class,
        () -> app.quality().review(manager, "product", 7, 2, "identity", "VERIFIED", SOURCE,
            "TEST review evidence only", 1L)));
    for (int column = 0; column < 6; column++) {
      int index = column;
      t.test("Review / rejects cross-field reference mismatch at column " + column, () -> {
        Object original = database.reference[index];
        database.reference[index] = "MISMATCH";
        try { throwsType(IllegalArgumentException.class, () -> app.quality().review(manager, "product", 7, 1,
            "identity", "VERIFIED", SOURCE, "TEST review evidence only", 1L)); }
        finally { database.reference[index] = original; }
      });
    }
    t.test("Review / matching explicit review binds actor and creates audit", () -> {
      database.calls.clear();
      app.quality().review(manager, "product", 7, 1, "identity", "VERIFIED", SOURCE, "TEST review evidence only", 1L);
      Call update = database.calls.stream().filter(c ->
          c.sql().startsWith("UPDATE dm.Products")).findFirst().orElseThrow();
      equal("VERIFIED", update.parameters().get(1)); equal(1L, update.parameters().get(4));
      check(database.calls.stream().anyMatch(c -> c.sql().startsWith("INSERT dm.AuditLog")), "Review audit missing");
    });
    t.test("Review / barcode approval requires verified identity and nonblank code", () ->
        throwsType(IllegalArgumentException.class,
        () -> app.quality().review(manager, "product", 7, 1, "barcode", "VERIFIED", SOURCE, "TEST review evidence only",
            null)));
    t.test("Lifecycle / cannot delete posted invoices or unknown entity", () -> {
      for (String type : List.of("invoice", "receipt", "history", "product;DROP")) {
        throwsType(IllegalArgumentException.class, () -> app.lifecycle().setActive(manager, type, 7, 1, false,
            "TEST reason"));
      }
    });
    t.test("Lifecycle / reason and revision required", () -> {
      throwsType(IllegalArgumentException.class, () -> app.lifecycle().setActive(manager, "product", 7, 1, false, ""));
      throwsType(IllegalArgumentException.class, () -> app.lifecycle().setActive(manager, "product", 7, 2, false,
          "TEST reason"));
    });
    t.test("Lifecycle / manager cannot disable own account", () -> throwsType(IllegalArgumentException.class,
        () -> app.lifecycle().setActive(manager, "employee", manager.employeeId(), 1, false, "TEST reason")));
    t.test("Lifecycle / last manager protection", () -> {
      database.managers = 0;
      try { throwsType(IllegalArgumentException.class, () -> app.lifecycle().setActive(manager, "employee", 7, 1, false,
          "TEST reason")); }
      finally { database.managers = 1; }
    });
    t.test("Lifecycle / only soft update plus append-only audit", () -> {
      database.calls.clear();
      app.lifecycle().setActive(manager, "customer", 7, 1, false, "TEST reason");
      check(database.calls.stream().anyMatch(c -> c.sql().startsWith("UPDATE dm.Customers")), "Soft update missing");
      check(database.calls.stream().anyMatch(c -> c.sql().startsWith("INSERT dm.AuditLog")), "Audit missing");
      check(database.calls.stream().noneMatch(c -> c.sql().startsWith("DELETE")), "No history deletion");
    });
    database.role = Role.CASHIER;
    AuthService.Session cashier = app.auth().login("TEST", database.password);
    t.test("Service / lifecycle not accessible through direct cashier call", () -> throwsType(SecurityException.class,
        () -> app.lifecycle().setActive(cashier, "customer", 7, 1, false, "TEST reason")));
    t.test("Service / review not accessible through direct cashier call", () -> throwsType(SecurityException.class,
        () -> app.quality().review(cashier, "product", 7, 1, "identity", "VERIFIED", SOURCE,
            "TEST review evidence only", null)));
    Arrays.fill(database.password, '\0');
    t.test("Cell semantics / business names and internal codes never translated as enums or dates", () -> {
      equal("SALE", RowsPanel.display("Name", "SALE"));
      equal("2026-10-31", RowsPanel.display("SKU", "2026-10-31"));
      equal("000001", RowsPanel.display("Barcode", "000001"));
      equal("31/10/2026", RowsPanel.display("StartDate", "2026-10-31"));
      equal("B\u00e1n h\u00e0ng", RowsPanel.display("Kind", "SALE"));
      equal("Chuy\u1ec3n kho", RowsPanel.display("Kind", "TRANSFER"));
      equal("Chuy\u1ec3n kho\u1ea3n", RowsPanel.display("PaymentMethod", "TRANSFER"));
    });
    t.test("Table / numeric sorting, translated filter, stable selection and empty schema", () -> edt(() -> {
      RowsPanel panel = new RowsPanel();
      List<Map<String, Object>> rows = List.of(Data.map("Id", 1, "SellingPrice", new BigDecimal("100"), "Role",
          "CASHIER"),
          Data.map("Id", 2, "SellingPrice", new BigDecimal("20"), "Role", "MANAGER"));
      panel.setData(rows); panel.table().getRowSorter().toggleSortOrder(1);
      equal(2, panel.table().getValueAt(0, 0));
      panel.table().setRowSelectionInterval(0, 0); panel.setData(rows); equal(2, panel.selected().get("Id"));
      input(panel).setText("Thu ng\u00e2n"); equal(1, panel.table().getRowCount());
      input(panel).setText(""); panel.setData(List.of()); equal(3, panel.table().getColumnCount());
    }));
    t.test("Guided input / placeholder paint never alters document", () -> edt(() -> {
      JTextField field = new JTextField(); GuidedInput.install(field, "EXAMPLE - NEVER SAVE");
      field.setSize(300, 40);
      java.awt.Graphics g = new BufferedImage(300, 40, BufferedImage.TYPE_INT_RGB).getGraphics();
      try { field.paint(g); } finally { g.dispose(); }
      equal("", field.getText()); field.setText("000-actual"); equal("000-actual", field.getText());
      check(field.getAccessibleContext().getAccessibleDescription().contains("EXAMPLE"), "Accessible hint");
    }));
    t.test("Guided input / suggestions come only from supplied controlled choices", () -> edt(() -> {
      JComboBox<String> choices = new JComboBox<>(new String[] {"Sony", "Samsung", "LG"});
      GuidedInput.autocomplete(choices); check(!choices.isEditable(), "Uncontrolled free text");
      equal(0, choices.getKeySelectionManager().selectionForKey('S', choices.getModel()));
      equal(1, choices.getKeySelectionManager().selectionForKey('a', choices.getModel()));
      equal(1, choices.getKeySelectionManager().selectionForKey('m', choices.getModel()));
      equal(-1, choices.getKeySelectionManager().selectionForKey('z', choices.getModel()));
      equal(3, choices.getItemCount());
    }));
    t.finish("quality-lifecycle-navigation-rules");
    System.out.println("SCOPE | Pure rules, Swing components, explicit JDBC doubles. SQL Server semantics NOT tested.");
  }
}
