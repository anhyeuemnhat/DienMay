package vn.dienmay.tests;

import static vn.dienmay.tests.TestKit.check;
import static vn.dienmay.tests.TestKit.equal;
import static vn.dienmay.tests.TestKit.throwsType;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import vn.dienmay.common.api.BusinessException;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.model.DeletionPreview;
import vn.dienmay.common.model.DeletionRequest;
import vn.dienmay.common.model.InventoryRequest;
import vn.dienmay.common.model.Item;
import vn.dienmay.common.model.ItemQuantity;
import vn.dienmay.common.model.Money;
import vn.dienmay.common.model.PriceConfirmation;
import vn.dienmay.common.model.ReversalRequest;
import vn.dienmay.common.model.SaleRequest;
import vn.dienmay.server.InventorySystem;
import vn.dienmay.server.db.Jdbc;
import vn.dienmay.server.db.SqlDatabase;
import vn.dienmay.server.db.StockDao;
import vn.dienmay.server.service.AuthService;
import vn.dienmay.server.service.InventoryReversalService;
import vn.dienmay.server.service.Passwords;
import vn.dienmay.server.service.RecordDeletionService;

/** Real SQL cases invoked only AFTER SqlServerIT verifies opt-in, exact *_Test DB, DEMO and schema v5. */
public final class ControlledSqlCases {
  private record Case(String name, TestKit.Checked action) {}
  private final SqlDatabase db;
  private final InventorySystem app;
  private final AuthService.Session manager;
  private final AuthService.Session cashier;
  private final AuthService.Session warehouse;
  private final long category;
  private final long brand;
  private final long vendor;
  private final Map<String, Object> reference;
  private final String prefix = "V5" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

  public ControlledSqlCases(SqlDatabase db, InventorySystem app, AuthService.Session manager,
      AuthService.Session cashier, AuthService.Session warehouse, long category, long brand, long vendor,
      Map<String, Object> reference) {
    this.db = db; this.app = app; this.manager = manager; this.cashier = cashier; this.warehouse = warehouse;
    this.category = category; this.brand = brand; this.vendor = vendor; this.reference = reference;
  }
  public static int plannedCount() {
    return new ControlledSqlCases(null, null, null, null, null, 0, 0, 0, Map.of()).cases().size();
  }
  public void run(TestKit tests) {
    for (Case c : cases()) { tests.test("SQL v5 / " + c.name(), c.action()); }
    System.out.println("V5 FIXTURES RETAINED | " + prefix + " | No cleanup removes transaction history.");
  }
  private long scalar(String sql, Object... values) throws SQLException {
    return db.read(c -> { try (PreparedStatement q = Jdbc.statement(c, sql, values); ResultSet rs = q.executeQuery()) {
      check(rs.next(), "Missing scalar"); return rs.getLong(1);
    }});
  }
  private static String table(String type) {
    return switch (type) {
      case "product" -> "Products"; case "customer" -> "Customers"; case "vendor" -> "Vendors";
      case "employee" -> "Employees"; case "promotion" -> "Promotions"; case "category" -> "Categories";
      case "brand" -> "Brands"; default -> throw new IllegalArgumentException("Test allowlist");
    };
  }
  private int revision(String type, long id) throws SQLException {
    return Math.toIntExact(scalar("SELECT Revision FROM dm." + table(type) + " WHERE Id=?", id));
  }
  private Item draft(boolean serial, BigDecimal cost, BigDecimal selling) {
    return new Item(0, prefix + UUID.randomUUID().toString().substring(0, 8), "",
        Data.text(reference, "ProductName"), category, brand, Data.text(reference, "CategoryName"),
        Data.text(reference, "BrandName"), Data.text(reference, "Model"), Data.text(reference, "Specifications"),
        cost, selling, 12, serial, true, 1, 0, 0, 0);
  }
  private long product(boolean serial, boolean approve) throws SQLException {
    long id = app.catalog().save(manager, draft(serial, Money.of("60"), Money.of("100")));
    if (approve) {
      Item item = app.catalog().get(manager, id);
      app.quality().review(manager, "product", id, item.revision(), "identity", "VERIFIED",
          Data.text(reference, "SourceUrl"), "Isolated DEMO v5 test; not a real store assertion",
          ((Number) reference.get("Id")).longValue());
    }
    return id;
  }
  private Item change(Item item, String key, Object value) {
    Map<String, Object> data = Json.object(Json.parse(Json.stringify(item)));
    data.put(key, value); return Json.convert(data, Item.class);
  }
  private long isolated(String type) throws SQLException {
    String name = prefix + UUID.randomUUID().toString().substring(0, 8);
    return switch (type) {
      case "product" -> product(false, false);
      case "customer" -> app.people().saveCustomer(manager, null, name, "", "Synthetic test");
      case "vendor" -> app.people().saveVendor(manager, null, name, "", "Synthetic test", true);
      case "category", "brand" -> app.catalog().saveLookup(manager, type, null, name);
      case "promotion" -> app.promotions().save(manager, null, name, name, LocalDate.of(2020, 1, 1),
          LocalDate.of(2099, 12, 31), Money.of("5"), true);
      case "employee" -> db.transaction(c -> Jdbc.insert(c, "INSERT dm.Employees(UserName,FullName,PasswordHash,"
          + "Role,MustChange) OUTPUT INSERTED.Id VALUES(?,?,?,'WAREHOUSE',0)", name, "Synthetic unlinked import",
          Passwords.hash(UUID.randomUUID().toString().toCharArray())));
      default -> throw new IllegalArgumentException("Unsupported fixture");
    };
  }
  private DeletionRequest deletion(String type, long id) throws SQLException {
    return new DeletionRequest(UUID.randomUUID(), type, id, revision(type, id), prefix + " controlled test deletion");
  }
  private InventoryRequest receiving(long id, int quantity, BigDecimal cost, List<String> serials) {
    return new InventoryRequest(UUID.randomUUID(), id, quantity, serials, prefix + " test receipt", vendor, cost,
        null, "KHO");
  }
  private long receipt(long id, int quantity, String... serials) throws SQLException {
    InventoryRequest request = receiving(id, quantity, Money.of("70"), List.of(serials));
    app.inventory().receive(warehouse, request);
    return scalar("SELECT Id FROM dm.StockMovements WHERE RequestId=? AND Kind='RECEIVE'", request.id());
  }
  private ReversalRequest reversal(long movement) throws SQLException {
    return new ReversalRequest(UUID.randomUUID(), movement, app.reversals().preview(manager,
        movement).productRevision(),
        prefix + " controlled test reversal");
  }
  private long quantity(long id) throws SQLException {
    return scalar("SELECT Quantity FROM dm.Stock WHERE ProductId=? AND Location='KHO'", id);
  }
  private static List<Object> parallel(Callable<Object> a, Callable<Object> b) throws Exception {
    var pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
    try {
      var one = pool.submit(() -> { start.await(); try { return a.call(); } catch (Exception e) { return e; } });
      var two = pool.submit(() -> { start.await(); try { return b.call(); } catch (Exception e) { return e; } });
      start.countDown(); return List.of(one.get(45, TimeUnit.SECONDS), two.get(45, TimeUnit.SECONDS));
    } finally { pool.shutdownNow(); }
  }
  /** Injects an application/JDBC failure around REAL statements; no SQL result is mocked. */
  private SqlDatabase fault(String point) {
    return new SqlDatabase(() -> {
      Connection real = db.open();
      return JdbcDouble.proxy(Connection.class, (proxy, method, args) -> {
        try {
          Object result = method.invoke(real, args);
          if (point.equals("COMMIT_LOST") && method.getName().equals("commit")) {
            throw new SQLException("TEST ONLY: commit completed but acknowledgement was lost");
          }
          if (method.getName().equals("prepareStatement") && result instanceof PreparedStatement prepared) {
            String sql = (String) args[0];
            return JdbcDouble.proxy(PreparedStatement.class, (p, m, a) -> {
              try {
                if (point.equals("AUDIT") && sql.startsWith("INSERT dm.AuditLog") &&
                    m.getName().equals("executeUpdate")) {
                  throw new SQLException("TEST ONLY: audit statement failed");
                }
                Object value = m.invoke(prepared, a);
                if (point.equals("AFTER_DELETE") && sql.startsWith("EXEC dm.ControlledDelete")
                    && m.getName().equals("executeQuery")) {
                  if (value instanceof ResultSet rs) { rs.close(); }
                  throw new SQLException("TEST ONLY: failure after stored procedure before commit");
                }
                return value;
              } catch (InvocationTargetException ex) { throw ex.getCause(); }
            });
          }
          return result;
        } catch (InvocationTargetException ex) { throw ex.getCause(); }
      });
    });
  }

  private List<Case> cases() {
    List<Case> cases = new ArrayList<>();
    for (String type : List.of("product", "customer", "vendor", "category", "brand", "promotion", "employee")) {
      cases.add(new Case("Unlinked " + type + " hard-deletes, preserves audit and idempotent receipt", () -> {
        long id = isolated(type); DeletionRequest request = deletion(type, id);
        long before = scalar("SELECT COUNT(*) FROM dm.AuditLog WHERE EntityType=? AND EntityId=?", type, id);
        check(app.deletion().preview(manager, type, id, request.revision()).allowed(), "Unlinked record blocked");
        DeletionPreview result = app.deletion().delete(manager, request);
        equal(result, app.deletion().delete(manager, request));
        equal(0L, scalar("SELECT COUNT(*) FROM dm." + table(type) + " WHERE Id=?", id));
        equal(before + 1, scalar("SELECT COUNT(*) FROM dm.AuditLog WHERE EntityType=? AND EntityId=?", type, id));
        equal(1L, scalar("SELECT COUNT(*) FROM dm.DeletionRequests WHERE RequestId=?", request.id()));
        check(app.deletion().resolve(manager, request), "Committed deletion not resolved");
        check(!app.deletion().cancel(manager, request), "Committed deletion cancelled");
        if (type.equals("product")) {
          equal(0L, scalar("SELECT COUNT(*) FROM dm.Stock WHERE ProductId=?", id));
          equal(0L, scalar("SELECT COUNT(*) FROM dm.ProductCodes WHERE ProductId=?", id));
        }
      }));
    }
    cases.add(new Case("Delete checks role in service and procedure", () -> {
      long id = isolated("vendor"); DeletionRequest d = deletion("vendor", id);
      throwsType(SecurityException.class, () -> app.deletion().delete(cashier, d));
      throwsType(SecurityException.class, () -> app.deletion().delete(warehouse, d));
      SQLException denied = throwsType(SQLException.class, () -> db.transaction(c -> {
        try (PreparedStatement q = Jdbc.statement(c,
            "EXEC dm.ControlledDelete @Type='vendor',@EntityId=?,@Revision=1,@ActorId=?,@Execute=0",
            id, cashier.employeeId()); ResultSet ignored = q.executeQuery()) { return ignored.next(); }
      }));
      equal(51500, denied.getErrorCode()); equal(1L, scalar("SELECT COUNT(*) FROM dm.Vendors WHERE Id=?", id));
    }));
    cases.add(new Case("Current manager cannot delete their own account", () ->
        check(!app.deletion().preview(manager, "employee", manager.employeeId(),
            revision("employee", manager.employeeId())).allowed(), "Self deletion allowed")));
    cases.add(new Case("Employee with actor history is not deletable", () ->
        check(!app.deletion().preview(manager, "employee", warehouse.employeeId(),
            revision("employee", warehouse.employeeId())).allowed(), "Historical actor deletable")));
    cases.add(new Case("Stale revision and deleted target return distinct codes", () -> {
      long id = isolated("vendor"); DeletionRequest d = deletion("vendor", id);
      app.people().saveVendor(manager, id, prefix + " updated", "", "TEST", true, d.revision());
      equal("REVISION_CONFLICT", throwsType(BusinessException.class, () -> app.deletion().delete(manager, d)).code());
      app.deletion().delete(manager, deletion("vendor", id));
      equal("RECORD_NOT_FOUND", throwsType(BusinessException.class,
          () -> app.deletion().preview(manager, "vendor", id, d.revision() + 1)).code());
    }));
    cases.add(new Case("Cancelled deletion fences a delayed request", () -> {
      long id = isolated("customer"); DeletionRequest d = deletion("customer", id);
      check(app.deletion().cancel(manager, d), "Cancel failed");
      equal("REQUEST_CONFLICT", throwsType(BusinessException.class, () -> app.deletion().delete(manager, d)).code());
      equal(1L, scalar("SELECT COUNT(*) FROM dm.Customers WHERE Id=?", id));
    }));
    cases.add(new Case("Concurrent deletes create exactly one audit and receipt", () -> {
      long id = isolated("vendor"); DeletionRequest a = deletion("vendor", id), b = deletion("vendor", id);
      List<Object> values = parallel(() -> app.deletion().delete(manager, a), () -> app.deletion().delete(manager, b));
      equal(1L, values.stream().filter(v -> v instanceof DeletionPreview).count());
      equal(1L, scalar("SELECT COUNT(*) FROM dm.AuditLog"
          + " WHERE EntityType='vendor' AND EntityId=? AND Action='HARD_DELETE'", id));
    }));
    cases.add(new Case("Failure after delete procedure rolls back record audit and receipt together", () -> {
      long id = isolated("product"); DeletionRequest d = deletion("product", id);
      RecordDeletionService failing = new RecordDeletionService(fault("AFTER_DELETE"), app.auth());
      throwsType(SQLException.class, () -> failing.delete(manager, d));
      equal(1L, scalar("SELECT COUNT(*) FROM dm.Products WHERE Id=?", id));
      equal(2L, scalar("SELECT COUNT(*) FROM dm.Stock WHERE ProductId=?", id));
      equal(0L, scalar("SELECT COUNT(*) FROM dm.DeletionRequests WHERE RequestId=?", d.id()));
      equal(0L, scalar("SELECT COUNT(*) FROM dm.AuditLog"
          + " WHERE EntityType='product' AND EntityId=? AND Action='HARD_DELETE'", id));
    }));
    cases.add(new Case("Lost delete commit acknowledgement resolves without repeating delete", () -> {
      long id = isolated("vendor"); DeletionRequest d = deletion("vendor", id);
      throwsType(SQLException.class, () -> new RecordDeletionService(fault("COMMIT_LOST"), app.auth()).delete(manager,
          d));
      check(app.deletion().resolve(manager, d), "Lost commit not found");
      equal(0L, scalar("SELECT COUNT(*) FROM dm.Vendors WHERE Id=?", id));
    }));
    cases.add(new Case("Product below cost requires exact acknowledgement and same-transaction audit", () -> {
      Item value = draft(false, Money.of("120"), Money.of("100"));
      equal("BELOW_COST_CONFIRMATION_REQUIRED", throwsType(BusinessException.class,
          () -> app.catalog().save(manager, value)).code());
      equal(0L, scalar("SELECT COUNT(*) FROM dm.Products WHERE SKU=?", value.sku()));
      long id = app.catalog().save(manager, value, new PriceConfirmation(0, 0, value.costPrice(),
          value.sellingPrice()));
      equal(1L, scalar("SELECT COUNT(*) FROM dm.AuditLog"
          + " WHERE EntityType='product' AND EntityId=? AND Action='BELOW_COST_CONFIRMED'", id));
    }));
    cases.add(new Case("Receive warning checks latest price; acknowledgement never changes selling price", () -> {
      long id = product(false, true); Item item = app.catalog().get(manager, id);
      InventoryRequest missing = receiving(id, 2, Money.of("120"), List.of());
      throwsType(BusinessException.class, () -> app.inventory().receive(warehouse, missing)); equal(0L, quantity(id));
      check(app.inventory().cancel(warehouse, missing), "Rejected intent not safely fenced");
      InventoryRequest confirmed = receiving(id, 2, Money.of("120"), List.of())
          .withConfirmation(new PriceConfirmation(id, item.revision(), Money.of("120"), item.sellingPrice()));
      app.inventory().receive(warehouse, confirmed); app.inventory().receive(warehouse, confirmed);
      equal(2L, quantity(id)); equal(item.sellingPrice(), app.catalog().get(manager, id).sellingPrice());
      equal(1L, scalar("SELECT COUNT(*) FROM dm.AuditLog"
          + " WHERE EntityType='product' AND EntityId=? AND Action='RECEIVE_BELOW_COST'", id));
    }));
    cases.add(new Case("Receipt acknowledgement with stale product revision cannot post", () -> {
      long id = product(false, true); Item old = app.catalog().get(manager, id);
      InventoryRequest request = receiving(id, 1, Money.of("120"), List.of())
          .withConfirmation(new PriceConfirmation(id, old.revision(), Money.of("120"), old.sellingPrice()));
      app.catalog().save(manager, change(old, "sellingPrice", Money.of("110")));
      equal("PRICE_CONFIRMATION_STALE", throwsType(BusinessException.class,
          () -> app.inventory().receive(warehouse, request)).code()); equal(0L, quantity(id));
    }));
    cases.add(new Case("Received product and supplier are blocked from hard deletion", () -> {
      long id = product(false, true); receipt(id, 2);
      check(!app.deletion().preview(manager, "product", id, revision("product", id)).allowed(),
          "Stock product deleted");
      check(!app.deletion().preview(manager, "vendor", vendor, revision("vendor", vendor)).allowed(),
          "Used vendor deleted");
      check(!app.deletion().preview(manager, "category", category, revision("category", category)).allowed(),
          "Linked category");
      check(!app.deletion().preview(manager, "brand", brand, revision("brand", brand)).allowed(), "Linked brand");
    }));
    cases.add(new Case("Safe receipt reversal preserves original, compensates stock, restores exact cost", () -> {
      long id = product(false, true); long movement = receipt(id, 2); ReversalRequest request = reversal(movement);
      long reversed = app.reversals().reverse(manager, request);
      equal(0L, quantity(id)); equal(Money.of("60"), app.catalog().get(manager, id).costPrice());
      equal(1L, scalar("SELECT COUNT(*) FROM dm.StockMovements WHERE Id=? AND Kind='RECEIVE' AND Delta=2", movement));
      equal(-2L, scalar("SELECT Delta FROM dm.StockMovements WHERE Id=? AND Kind='RECEIVE_REVERSAL'", reversed));
      equal(reversed, scalar("SELECT ReversalMovementId FROM dm.InventoryReversals WHERE OriginalMovementId=?",
          movement));
      equal(1L, scalar("SELECT COUNT(*) FROM dm.AuditLog"
          + " WHERE EntityType='receipt' AND EntityId=? AND Action='RECEIPT_REVERSED'", movement));
      equal(reversed, app.reversals().reverse(manager, request)); check(app.reversals().resolve(manager, request),
          "Resolve");
      check(!app.reversals().cancel(manager, request), "Committed reversal cancelled");
      throwsType(BusinessException.class, () -> app.reversals().reverse(manager, reversal(movement)));
    }));
    cases.add(new Case("Serialized reversal preserves units and both movement links", () -> {
      long id = product(true, true); String serial = prefix + UUID.randomUUID(); long movement = receipt(id, 1, serial);
      long reversed = app.reversals().reverse(manager, reversal(movement));
      equal(1L, scalar("SELECT COUNT(*) FROM dm.ProductUnits WHERE ProductId=? AND Serial=? AND Status='REVERSED'", id,
          serial));
      equal(1L, scalar("SELECT COUNT(*) FROM dm.StockMovementUnits WHERE MovementId=?", movement));
      equal(1L, scalar("SELECT COUNT(*) FROM dm.StockMovementUnits WHERE MovementId=?", reversed)); equal(0L,
          quantity(id));
    }));
    cases.add(new Case("Transferred units and insufficient stock block reversal", () -> {
      long id = product(true, true); String serial = prefix + UUID.randomUUID(); long movement = receipt(id, 1, serial);
      app.inventory().transfer(warehouse, new InventoryRequest(UUID.randomUUID(), id, 1, List.of(serial), prefix,
          null, Money.ZERO, "KHO", "QUAY"));
      check(!app.reversals().preview(manager, movement).allowed(), "Transferred receipt reversible");
      throwsType(BusinessException.class, () -> app.reversals().reverse(manager, reversal(movement)));
      equal(0L, quantity(id));
    }));
    cases.add(new Case("Sold serial with invoice and warranty cannot be reversed", () -> {
      long id = product(true, true); String serial = prefix + UUID.randomUUID(); long movement = receipt(id, 1, serial);
      app.inventory().transfer(warehouse, new InventoryRequest(UUID.randomUUID(), id, 1, List.of(serial), prefix,
          null, Money.ZERO, "KHO", "QUAY"));
      Item item = app.catalog().get(manager, id);
      app.sales().checkout(cashier, new SaleRequest(UUID.randomUUID(), null, "CASH", Money.ZERO, "", Money.ZERO,
          List.of(new ItemQuantity(id, item.sku(), item.name(), item.sellingPrice(), 1, List.of(serial)))));
      check(!app.reversals().preview(manager, movement).allowed(), "Sold serial reversible");
      equal(1L, scalar("SELECT COUNT(*) FROM dm.Warranties w JOIN dm.ProductUnits u ON u.Id=w.UnitId"
          + " WHERE u.ProductId=?", id));
      throwsType(BusinessException.class, () -> app.reversals().reverse(manager, reversal(movement)));
    }));
    cases.add(new Case("Later receipt blocks earlier reversal even when stock is sufficient", () -> {
      long id = product(false, true); long first = receipt(id, 2); receipt(id, 1);
      check(!app.reversals().preview(manager, first).allowed(), "Later cost history ignored"); equal(3L, quantity(id));
    }));
    cases.add(new Case("Manual cost change and change-back invalidate receipt cost provenance", () -> {
      long id = product(false, true); long movement = receipt(id, 1);
      app.catalog().save(manager, change(app.catalog().get(manager, id), "costPrice", Money.of("80")));
      app.catalog().save(manager, change(app.catalog().get(manager, id), "costPrice", Money.of("70")));
      check(!app.reversals().preview(manager, movement).allowed(), "Price equality bypassed provenance");
    }));
    cases.add(new Case("Synthetic legacy receipt without snapshot is blocked, never guessed", () -> {
      long id = product(false, true); UUID request = UUID.randomUUID();
      long movement = db.transaction(c -> {
        StockDao stock = new StockDao(); stock.add(c, id, "KHO", 1);
        return stock.movement(c, request, id, "KHO", 1, "RECEIVE", prefix + " synthetic legacy", manager.employeeId(),
            null, vendor, Money.of("60"));
      });
      check(!app.reversals().preview(manager, movement).allowed(), "Missing snapshot was guessed");
      equal(1L, quantity(id));
    }));
    cases.add(new Case("Reversal rechecks manager permission", () -> {
      long id = product(false, true); long movement = receipt(id, 1); ReversalRequest request = reversal(movement);
      throwsType(SecurityException.class, () -> app.reversals().reverse(warehouse, request));
      throwsType(SecurityException.class, () -> app.reversals().reverse(cashier, request)); equal(1L, quantity(id));
    }));
    cases.add(new Case("Stale preview revision blocks reversal", () -> {
      long id = product(false, true); long movement = receipt(id, 1); ReversalRequest request = reversal(movement);
      app.catalog().save(manager, change(app.catalog().get(manager, id), "reorderLevel", 4));
      equal("REVISION_CONFLICT", throwsType(BusinessException.class,
          () -> app.reversals().reverse(manager, request)).code()); equal(1L, quantity(id));
    }));
    cases.add(new Case("Cancel reversal fences delayed execution without undoing original receipt", () -> {
      long id = product(false, true); long movement = receipt(id, 1); ReversalRequest request = reversal(movement);
      check(app.reversals().cancel(manager, request), "Cancel failed");
      throwsType(IllegalArgumentException.class, () -> app.reversals().reverse(manager, request)); equal(1L,
          quantity(id));
    }));
    cases.add(new Case("Two concurrent reversal intents compensate only once", () -> {
      long id = product(false, true); long movement = receipt(id, 1); ReversalRequest a = reversal(movement), b =
          reversal(movement);
      List<Object> values = parallel(() -> app.reversals().reverse(manager, a), () -> app.reversals().reverse(manager,
          b));
      equal(1L, values.stream().filter(v -> v instanceof Long).count()); equal(0L, quantity(id));
      equal(1L, scalar("SELECT COUNT(*) FROM dm.InventoryReversals WHERE OriginalMovementId=?", movement));
    }));
    cases.add(new Case("Concurrent retry with identical reversal ID returns same compensation", () -> {
      long id = product(false, true); long movement = receipt(id, 1); ReversalRequest request = reversal(movement);
      List<Object> values = parallel(() -> app.reversals().reverse(manager, request), () ->
          app.reversals().reverse(manager, request));
      check(values.get(0) instanceof Long && values.get(1) instanceof Long, "Idempotent retry failed");
      equal(values.get(0), values.get(1)); equal(0L, quantity(id));
    }));
    cases.add(new Case("Audit failure rolls back compensation, units, cost and quantity", () -> {
      long id = product(true, true); String serial = prefix + UUID.randomUUID(); long movement = receipt(id, 1, serial);
      ReversalRequest request = reversal(movement);
      throwsType(SQLException.class, () -> new InventoryReversalService(fault("AUDIT"), app.auth()).reverse(manager,
          request));
      equal(1L, quantity(id)); equal(Money.of("70"), app.catalog().get(manager, id).costPrice());
      equal(1L, scalar("SELECT COUNT(*) FROM dm.ProductUnits WHERE ProductId=? AND Status='IN_STOCK'", id));
      equal(0L, scalar("SELECT COUNT(*) FROM dm.InventoryReversals WHERE OriginalMovementId=?", movement));
      equal(0L, scalar("SELECT COUNT(*) FROM dm.StockMovements WHERE RequestId=?", request.id()));
    }));
    cases.add(new Case("Lost reversal commit acknowledgement resolves and retries exactly once", () -> {
      long id = product(false, true); long movement = receipt(id, 1); ReversalRequest request = reversal(movement);
      throwsType(SQLException.class, () -> new InventoryReversalService(fault("COMMIT_LOST"),
          app.auth()).reverse(manager, request));
      check(app.reversals().resolve(manager, request), "Lost commit not found");
      long result = app.reversals().reverse(manager, request); equal(0L, quantity(id));
      equal(result, scalar("SELECT ReversalMovementId FROM dm.InventoryReversals WHERE RequestId=?", request.id()));
      equal(1L, scalar("SELECT COUNT(*) FROM dm.StockMovements WHERE RequestId=?", request.id()));
    }));
    cases.add(new Case("Used promotion string reference blocks physical deletion and code rename", () -> {
      long promotion = isolated("promotion");
      String code = db.read(c -> { try (PreparedStatement q = Jdbc.statement(c,
          "SELECT Code FROM dm.Promotions WHERE Id=?", promotion); ResultSet rs = q.executeQuery()) {
        rs.next(); return rs.getString(1);
      }});
      long id = product(false, true); receipt(id, 1);
      app.inventory().transfer(warehouse, new InventoryRequest(UUID.randomUUID(), id, 1, List.of(), prefix,
          null, Money.ZERO, "KHO", "QUAY")); Item item = app.catalog().get(manager, id);
      app.sales().checkout(cashier, new SaleRequest(UUID.randomUUID(), null, "CASH", Money.ZERO, code, Money.of("5"),
          List.of(new ItemQuantity(id, item.sku(), item.name(), item.sellingPrice(), 1, List.of()))));
      check(!app.deletion().preview(manager, "promotion", promotion, 1).allowed(), "Non-FK invoice reference ignored");
      throwsType(IllegalArgumentException.class, () -> app.promotions().save(manager, promotion, code + "X", "TEST",
          LocalDate.of(2020, 1, 1), LocalDate.of(2099, 12, 31), Money.of("5"), true, 1));
    }));
    return cases;
  }
}
