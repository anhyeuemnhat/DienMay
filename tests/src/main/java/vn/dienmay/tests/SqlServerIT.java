package vn.dienmay.tests;

import static vn.dienmay.server.db.Jdbc.insert;
import static vn.dienmay.server.db.Jdbc.statement;
import static vn.dienmay.tests.TestKit.check;
import static vn.dienmay.tests.TestKit.equal;
import static vn.dienmay.tests.TestKit.throwsType;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import vn.dienmay.client.api.HttpApiClient;
import vn.dienmay.common.api.ApiException;
import vn.dienmay.common.api.ApiSession;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.api.Operation;
import vn.dienmay.common.model.InventoryRequest;
import vn.dienmay.common.model.Item;
import vn.dienmay.common.model.ItemQuantity;
import vn.dienmay.common.model.Money;
import vn.dienmay.common.model.Role;
import vn.dienmay.common.model.Sale;
import vn.dienmay.common.model.SaleRequest;
import vn.dienmay.common.model.Validation;
import vn.dienmay.server.InventorySystem;
import vn.dienmay.server.api.ApiServer;
import vn.dienmay.server.api.ApplicationApi;
import vn.dienmay.server.db.Jdbc;
import vn.dienmay.server.db.SqlDatabase;
import vn.dienmay.server.service.AuthService;
import vn.dienmay.server.service.Passwords;
import vn.dienmay.server.service.SalesService;

/** Real SQL Server only. Explicit opt-in, exact database name and *_Test suffix are all required. */
public final class SqlServerIT {
  private static SqlDatabase db;
  private static InventorySystem app;
  private static AuthService.Session manager;
  private static AuthService.Session cashier;
  private static AuthService.Session warehouse;
  private static long category;
  private static long brand;
  private static long vendor;
  private static long customer;
  private static long product;
  private static long serialProduct;
  private static Sale firstSale;
  private static Sale serialSale;
  private static String prefix;
  private static char[] password;
  private static Map<String, Object> reference;
  private static long referenceId;

  private static int revision(String table, long id) throws SQLException {
    if (!List.of("Employees", "Brands", "Categories", "Promotions").contains(table)) {
      throw new IllegalArgumentException("Test table allowlist");
    }
    return Math.toIntExact(scalar("SELECT Revision FROM dm." + table + " WHERE Id=?", id));
  }

  private static long lookup(String type, String table, String name) throws SQLException {
    long id = scalar("SELECT COALESCE(MIN(Id),0) FROM dm." + table + " WHERE Name=?", name);
    if (id == 0) { id = app.catalog().saveLookup(manager, type, null, name); }
    app.quality().review(manager, type, id, revision(table, id), "identity", "VERIFIED",
        Data.text(reference, "SourceUrl"),
            "Isolated DEMO test: imports bundled official reference; no store fact is asserted.", null);
    return id;
  }

  private static void approve(long id) throws SQLException {
    Item current = app.catalog().get(manager, id);
    app.quality().review(manager, "product", id, current.revision(), "identity", "VERIFIED",
        Data.text(reference, "SourceUrl"),
            "Isolated DEMO test: exact tuple of curated official reference, simulated store values.", referenceId);
  }

  private SqlServerIT() {}

  private static long scalar(String sql, Object... parameters) throws SQLException {
    return db.read(c -> {
      try (PreparedStatement statement = statement(c, sql, parameters);
          ResultSet row = statement.executeQuery()) {
        check(row.next(), "Missing scalar result");
        return row.getLong(1);
      }
    });
  }

  private static Item draft(long id, int revision, String sku, boolean serial, int months,
      BigDecimal price) {
    return new Item(id, sku, "", Data.text(reference, "ProductName"), category, brand,
        Data.text(reference, "CategoryName"), Data.text(reference, "BrandName"), Data.text(reference, "Model"),
        Data.text(reference, "Specifications"), Money.of("60"), price,
        months, serial, true, 2, 0, 0, revision);
  }

  private static SaleRequest saleRequest(long id, int quantity, String... serials) throws SQLException {
    Item item = app.catalog().get(manager, id);
    return new SaleRequest(UUID.randomUUID(), customer, "CASH", Money.ZERO, "", Money.ZERO,
        List.of(new ItemQuantity(id, item.sku(), item.name(), item.sellingPrice(), quantity, List.of(serials))));
  }

  private static InventoryRequest receive(long id, int quantity, String... serials) {
    return new InventoryRequest(UUID.randomUUID(), id, quantity, List.of(serials), prefix + " receive",
        vendor, Money.of("60"), null, "KHO");
  }

  private static InventoryRequest transfer(long id, int quantity, String... serials) {
    return new InventoryRequest(UUID.randomUUID(), id, quantity, List.of(serials), prefix + " transfer",
        null, Money.ZERO, "KHO", "QUAY");
  }

  private static long quantity(long id, String location) throws SQLException {
    return scalar("SELECT Quantity FROM dm.Stock WHERE ProductId=? AND Location=?", id, location);
  }

  private static List<Object> parallel(Callable<Object> first, Callable<Object> second) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<Object> a = pool.submit(() -> {
        start.await();
        try { return first.call(); } catch (Exception error) { return error; }
      });
      Future<Object> b = pool.submit(() -> {
        start.await();
        try { return second.call(); } catch (Exception error) { return error; }
      });
      start.countDown();
      return List.of(a.get(45, TimeUnit.SECONDS), b.get(45, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }
  }

  public static void main(String[] args) throws Exception {
    if (!"YES".equals(System.getenv("DIENMAY_IT_ALLOW_WRITES"))) {
      System.out.println("SKIP | SQL Server integration NOT RUN: set explicit opt-in, expected "
          + "*_Test database and backend JDBC settings.");
      System.out.println("SKIP | Planned SQL scenarios=" + (38 + ControlledSqlCases.plannedCount())
          + " | baseline=38, controlled=" + ControlledSqlCases.plannedCount());
      return;
    }
    String expected = System.getenv("DIENMAY_IT_DATABASE");
    if (expected == null || !expected.endsWith("_Test")) {
      throw new SecurityException("DIENMAY_IT_DATABASE must name a dedicated *_Test database");
    }
    db = new SqlDatabase();
    String actual = db.read(c -> {
      try (PreparedStatement statement = statement(c, "SELECT DB_NAME()");
          ResultSet row = statement.executeQuery()) {
        row.next();
        return row.getString(1);
      }
    });
    if (!actual.equals(expected)) {
      throw new SecurityException("Actual database does not match the explicit test database");
    }
    db.checkConnection();
    if (!"DEMO".equals(db.read(vn.dienmay.server.service.QualityService::mode))) {
      throw new SecurityException("SQL integration tests require an isolated *_Test database classified DEMO");
    }
    app = new InventorySystem(db);
    prefix = "IT" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    password = UUID.randomUUID().toString().toCharArray();
    try {
      execute();
    } finally {
      Arrays.fill(password, '\0');
      System.out.println("FIXTURES RETAINED | prefix=" + prefix + " | database=" + actual);
      System.out.println("No DELETE/DROP cleanup runs. Review the retained audit trail before discarding the test DB.");
    }
  }

  private static void execute() throws Exception {
    for (Role role : Role.values()) {
      String hash = Passwords.hash(password);
      db.transaction(c -> insert(c, "INSERT dm.Employees(UserName,FullName,PasswordHash,Role,MustChange) "
          + "OUTPUT INSERTED.Id VALUES(?,?,?,?,0)", prefix + role.name(), prefix + role.name(), hash, role.name()));
    }
    manager = app.auth().login(prefix + "MANAGER", password);
    cashier = app.auth().login(prefix + "CASHIER", password);
    warehouse = app.auth().login(prefix + "WAREHOUSE", password);
    reference = app.quality().references(manager).stream()
        .filter(r -> "Sony".equals(r.get("BrandName")) && "WH-CH520".equals(r.get("Model")))
        .findFirst().orElseThrow(() -> new IllegalStateException("Run init-test with bundled reference catalog first"));
    referenceId = ((Number) reference.get("Id")).longValue();
    category = lookup("category", "Categories", Data.text(reference, "CategoryName"));
    brand = lookup("brand", "Brands", Data.text(reference, "BrandName"));
    vendor = app.people().saveVendor(manager, null, prefix + " DEMO vendor", "", "DEMO / not a real address", true);
    customer = app.people().saveCustomer(cashier, null, prefix + " DEMO customer", "", "DEMO / not a real address");
    product = app.catalog().save(manager, draft(0, 0, "000" + prefix + "SKU", false, 12, Money.of("100.25")));
    TestKit t = new TestKit();
    t.test("SQL / role authentication", () -> {
      equal(Role.MANAGER, manager.role());
      equal(Role.CASHIER, cashier.role());
      equal(Role.WAREHOUSE, warehouse.role());
    });
    t.test("SQL / bad password denied", () -> throwsType(SecurityException.class,
        () -> app.auth().login(prefix + "CASHIER", UUID.randomUUID().toString().toCharArray())));
    t.test("SQL / cashier cannot create product", () -> throwsType(SecurityException.class,
        () -> app.catalog().save(cashier, draft(0, 0, prefix + "ILLEGAL", false, 0, Money.of("1")))));
    t.test("SQL / warehouse cannot manage employees", () -> throwsType(SecurityException.class,
        () -> app.people().saveEmployee(warehouse, null, prefix + "ILLEGAL", "Illegal", "", Role.MANAGER, true,
            password)));
    t.test("SQL / pending identity is blocked before receiving stock", () -> {
      equal("PENDING", app.catalog().get(manager, product).verificationStatus());
      throwsType(IllegalArgumentException.class, () -> app.inventory().receive(warehouse, receive(product, 1)));
      equal(0L, quantity(product, "KHO"));
    });
    approve(product);
    t.test("SQL / reference-reviewed product is explicitly DEMO with no invented barcode", () -> {
      Item checked = app.catalog().get(manager, product);
      equal("VERIFIED", checked.verificationStatus()); equal("", checked.barcode()); check(checked.demo(),
          "DEMO marker");
    });
    InventoryRequest receiving = receive(product, 100);
    t.test("SQL / receive persists movement and same UUID is idempotent", () -> {
      app.inventory().receive(warehouse, receiving);
      app.inventory().receive(warehouse, receiving);
      equal(100L, quantity(product, "KHO"));
      equal(1L, scalar("SELECT COUNT(*) FROM dm.StockMovements WHERE RequestId=?", receiving.id()));
    });
    t.test("SQL / transfer adds destination and retry does not repeat", () -> {
      InventoryRequest moving = transfer(product, 50);
      app.inventory().transfer(warehouse, moving);
      app.inventory().transfer(warehouse, moving);
      equal(50L, quantity(product, "KHO"));
      equal(50L, quantity(product, "QUAY"));
      equal(2L, scalar("SELECT COUNT(*) FROM dm.StockMovements WHERE RequestId=?", moving.id()));
    });
    t.test("SQL / over-transfer rolls back both locations", () -> {
      long before = quantity(product, "KHO");
      throwsType(IllegalArgumentException.class, () -> app.inventory().transfer(warehouse, transfer(product, 1000)));
      equal(before, quantity(product, "KHO"));
    });
    t.test("SQL / leading zero internal SKU lookup, no unverified barcode", () -> equal(product,
        app.catalog().find(cashier, "000" + prefix + "SKU").id()));
    SaleRequest first = saleRequest(product, 2);
    t.test("SQL / sale commits invoice stock movement and warranty", () -> {
      firstSale = app.sales().checkout(cashier, first);
      equal(Money.of("200.50"), firstSale.total());
      equal(48L, quantity(product, "QUAY"));
      equal(2L, scalar("SELECT SUM(w.CoveredQuantity) FROM dm.Warranties w "
          + "JOIN dm.InvoiceDetails d ON d.Id=w.DetailId WHERE d.InvoiceId=?", firstSale.id()));
    });
    t.test("SQL / duplicate sale returns same invoice", () -> {
      equal(firstSale, app.sales().checkout(cashier, first));
      equal(48L, quantity(product, "QUAY"));
    });
    t.test("SQL / reused UUID different payload refused", () -> {
      SaleRequest changed = new SaleRequest(first.requestId(), null, first.payment(), first.manualDiscount(),
          first.promotionCode(), first.expectedDiscount(), first.lines());
      throwsType(SecurityException.class, () -> app.sales().checkout(cashier, changed));
    });
    t.test("SQL / concurrent oversell permits exactly one sale", () -> {
      SaleRequest a = saleRequest(product, 40);
      SaleRequest b = saleRequest(product, 40);
      List<Object> result = parallel(() -> app.sales().checkout(cashier, a), () -> app.sales().checkout(cashier, b));
      equal(1L, result.stream().filter(Sale.class::isInstance).count());
      equal(8L, quantity(product, "QUAY"));
    });
    t.test("SQL / concurrent duplicate creates exactly one invoice", () -> {
      SaleRequest same = saleRequest(product, 1);
      List<Object> result = parallel(() -> app.sales().checkout(cashier, same), () -> app.sales().checkout(cashier,
          same));
      check(result.get(0) instanceof Sale && result.get(1) instanceof Sale, "Duplicate did not resolve");
      equal(((Sale) result.get(0)).id(), ((Sale) result.get(1)).id());
      equal(1L, scalar("SELECT COUNT(*) FROM dm.Invoices WHERE RequestId=?", same.requestId()));
      equal(7L, quantity(product, "QUAY"));
    });
    for (String phase : List.of("AFTER_INVOICE", "AFTER_STOCK", "BEFORE_COMMIT")) {
      t.test("SQL / rollback injected at " + phase, () -> {
        long before = quantity(product, "QUAY");
        long ledger = scalar("SELECT COUNT(*) FROM dm.StockMovements WHERE ProductId=?", product);
        SaleRequest value = saleRequest(product, 1);
        SalesService failing = new SalesService(db, app.auth(), step -> {
          if (step.equals(phase)) { throw new SQLException("Injected rollback phase"); }
        });
        throwsType(SQLException.class, () -> failing.checkout(cashier, value));
        equal(before, quantity(product, "QUAY"));
        equal(ledger, scalar("SELECT COUNT(*) FROM dm.StockMovements WHERE ProductId=?", product));
        equal(0L, scalar("SELECT COUNT(*) FROM dm.Invoices WHERE RequestId=?", value.requestId()));
        equal(null, app.sales().resolve(cashier, value));
      });
    }
    t.test("SQL / stale price rejected without stock change", () -> {
      SaleRequest stale = saleRequest(product, 1);
      Item item = app.catalog().get(manager, product);
      app.catalog().save(manager, draft(product, item.revision(), item.sku(), false, 12, Money.of("101")));
      try {
        long before = quantity(product, "QUAY");
        throwsType(IllegalArgumentException.class, () -> app.sales().checkout(cashier, stale));
        equal(before, quantity(product, "QUAY"));
      } finally {
        Item latest = app.catalog().get(manager, product);
        app.catalog().save(manager, draft(product, latest.revision(), latest.sku(), false, 12, Money.of("100.25")));
      }
    });
    t.test("SQL / changed promotion requires reconfirmation", () -> {
      LocalDate day = db.read(Jdbc::now).atZone(Validation.ZONE).toLocalDate();
      String code = prefix + "PROMO";
      long promotion = app.promotions().save(manager, null, code, "Test", day.minusDays(1), day.plusDays(1),
          Money.of("10"), true);
      SaleRequest draft = saleRequest(product, 1);
      SalesService.Quote quote = app.sales().quote(cashier, draft.lines(), code, Money.ZERO);
      SaleRequest stale = new SaleRequest(UUID.randomUUID(), customer, "CASH", Money.ZERO, code, quote.discount(),
          draft.lines());
      app.promotions().save(manager, promotion, code, "Changed", day.minusDays(1), day.plusDays(1), Money.of("20"),
          true, revision("Promotions", promotion));
      throwsType(IllegalArgumentException.class, () -> app.sales().checkout(cashier, stale));
    });
    t.test("SQL / serialized receive transfer sale and warranty", () -> {
      serialProduct = app.catalog().save(manager, draft(0, 0, prefix + "SER", true, 12, Money.of("1000")));
      approve(serialProduct);
      app.inventory().receive(warehouse, receive(serialProduct, 2, "DEMO-" + prefix + "S1", "DEMO-" + prefix + "S2"));
      app.inventory().transfer(warehouse, transfer(serialProduct, 2, "DEMO-" + prefix + "S1", "DEMO-" + prefix + "S2"));
      serialSale = app.sales().checkout(cashier, saleRequest(serialProduct, 1, "DEMO-" + prefix + "S1"));
      equal(1L, quantity(serialProduct, "QUAY"));
      equal(1L, scalar("SELECT COUNT(*) FROM dm.Warranties w JOIN dm.InvoiceDetails d ON d.Id=w.DetailId "
          + "JOIN dm.ProductUnits u ON u.Id=w.UnitId WHERE d.InvoiceId=? AND u.Serial=? "
          + "AND w.EndDate=DATEADD(month,12,w.StartDate)", serialSale.id(), "DEMO-" + prefix + "S1"));
    });
    t.test("SQL / duplicate serial receiving rolls back stock", () -> {
      long before = quantity(serialProduct, "KHO");
      throwsType(SQLException.class, () -> app.inventory().receive(warehouse, receive(serialProduct, 1, prefix +
          "S1")));
      equal(before, quantity(serialProduct, "KHO"));
    });
    t.test("SQL / sold serial cannot be resold", () -> {
      SaleRequest duplicate = saleRequest(serialProduct, 1, "DEMO-" + prefix + "S1");
      throwsType(IllegalArgumentException.class, () -> app.sales().checkout(cashier, duplicate));
      equal(1L, quantity(serialProduct, "QUAY"));
    });
    t.test("SQL / historical warranty independent of new product policy", () -> {
      Item item = app.catalog().get(manager, serialProduct);
      app.catalog().save(manager, draft(item.id(), item.revision(), item.sku(), true, 24, item.sellingPrice()));
      equal(12L, scalar("SELECT w.WarrantyMonths FROM dm.Warranties w JOIN dm.InvoiceDetails d "
          + "ON d.Id=w.DetailId WHERE d.InvoiceId=?", serialSale.id()));
    });
    t.test("SQL / warranty lookup and claim audit transitions", () -> {
      check(!app.warranties().search(cashier, serialSale.code()).isEmpty(), "Invoice warranty lookup");
      check(!app.warranties().search(cashier, "DEMO-" + prefix + "S1").isEmpty(), "Serial warranty lookup");
      long warranty = scalar("SELECT w.Id FROM dm.Warranties w JOIN dm.InvoiceDetails d ON "
          + "d.Id=w.DetailId WHERE d.InvoiceId=?", serialSale.id());
      long claim = app.warranties().receive(cashier, warranty, "Integration fault");
      throwsType(SecurityException.class, () -> app.warranties().transition(cashier, claim, "RECEIVED", "PROCESSING",
          "Denied"));
      app.warranties().transition(manager, claim, "RECEIVED", "PROCESSING", "Inspecting");
      app.warranties().transition(manager, claim, "PROCESSING", "COMPLETED", "Complete");
      equal(3L, scalar("SELECT COUNT(*) FROM dm.ClaimHistory WHERE ClaimId=?", claim));
      equal(1L, scalar("SELECT COUNT(*) FROM dm.WarrantyClaims WHERE Id=? AND CompletedAt IS NOT NULL", claim));
    });
    t.test("SQL / expired warranty remains receivable under baseline policy", () -> {
      long warranty = scalar("SELECT w.Id FROM dm.Warranties w JOIN dm.InvoiceDetails d ON "
          + "d.Id=w.DetailId WHERE d.InvoiceId=?", serialSale.id());
      db.transaction(c -> Jdbc.update(c, "UPDATE dm.Warranties SET StartDate=?,EndDate=? WHERE Id=?",
          LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1), warranty));
      check(app.warranties().search(cashier, serialSale.code()).stream().anyMatch(row ->
          "EXPIRED".equals(row.get("Validity"))), "Expiry status missing");
      check(app.warranties().receive(cashier, warranty, "Expired fixture intake") > 0, "Expired intake rejected");
    });
    t.test("SQL / cancelled sale fences delayed execution", () -> {
      SaleRequest value = saleRequest(product, 1);
      long before = quantity(product, "QUAY");
      check(app.sales().cancel(cashier, value), "Cancellation refused");
      check(app.sales().cancel(cashier, value), "Cancellation replay refused");
      throwsType(IllegalArgumentException.class, () -> app.sales().checkout(cashier, value));
      equal(before, quantity(product, "QUAY"));
    });
    t.test("SQL / committed sale cannot be cancelled", () -> equal(false, app.sales().cancel(cashier, first)));
    t.test("SQL / cancelled stock fences delayed execution", () -> {
      InventoryRequest value = receive(product, 1);
      long before = quantity(product, "KHO");
      check(app.inventory().cancel(warehouse, value), "Stock cancellation refused");
      throwsType(IllegalArgumentException.class, () -> app.inventory().receive(warehouse, value));
      equal(before, quantity(product, "KHO"));
    });
    t.test("SQL / concurrent cancel and sale yield consistent one outcome", () -> {
      SaleRequest value = saleRequest(product, 1);
      long before = quantity(product, "QUAY");
      List<Object> outcome = parallel(() -> app.sales().cancel(cashier, value), () -> app.sales().checkout(cashier,
          value));
      if (Boolean.TRUE.equals(outcome.get(0))) {
        check(outcome.get(1) instanceof IllegalArgumentException, "Cancelled request executed");
        equal(before, quantity(product, "QUAY"));
      } else {
        equal(false, outcome.get(0));
        check(outcome.get(1) instanceof Sale, "Neither execution nor cancellation succeeded");
        equal(before - 1, quantity(product, "QUAY"));
      }
    });
    t.test("SQL / disabled employee invalidates existing session", () -> {
      app.lifecycle().setActive(manager, "employee", warehouse.employeeId(),
          revision("Employees", warehouse.employeeId()), false, "DEMO integration deactivation");
      throwsType(SecurityException.class, () -> app.catalog().search(warehouse, ""));
      app.lifecycle().setActive(manager, "employee", warehouse.employeeId(),
          revision("Employees", warehouse.employeeId()), true, "DEMO integration restoration");
      warehouse = app.auth().login(prefix + "WAREHOUSE", password);
    });
    t.test("SQL / first password change required and old session revoked", () -> {
      String username = prefix + "NEW";
      app.people().saveEmployee(manager, null, username, "New cashier", "", Role.CASHIER, true, password);
      AuthService.Session limited = app.auth().login(username, password);
      check(limited.mustChange(), "MustChange missing");
      throwsType(SecurityException.class, () -> app.catalog().search(limited, ""));
      char[] next = UUID.randomUUID().toString().toCharArray();
      try {
        AuthService.Session changed = app.auth().changePassword(limited, password, next);
        check(!changed.mustChange(), "MustChange not cleared");
        throwsType(SecurityException.class, () -> app.catalog().search(limited, ""));
        check(!app.catalog().search(changed, prefix).isEmpty(), "New session cannot read");
      } finally {
        Arrays.fill(next, '\0');
      }
    });
    t.test("SQL / revenue counts invoice headers without serial join inflation", () -> {
      LocalDate day = db.read(Jdbc::now).atZone(Validation.ZONE).toLocalDate();
      Map<String, Object> totals = app.reports().report(manager, "summary", day, day).get(0);
      equal(scalar("SELECT COUNT_BIG(*) FROM dm.Invoices WHERE SoldAt>=? AND SoldAt<?",
          Validation.start(day), Validation.start(day.plusDays(1))), ((Number) totals.get("InvoiceCount")).longValue());
      BigDecimal actual = db.read(c -> {
        try (PreparedStatement statement = statement(c,
            "SELECT COALESCE(SUM(Total),0) FROM dm.Invoices WHERE SoldAt>=? AND SoldAt<?",
            Validation.start(day), Validation.start(day.plusDays(1))); ResultSet result = statement.executeQuery()) {
          result.next();
          return result.getBigDecimal(1);
        }
      });
      equal(0, actual.compareTo((BigDecimal) totals.get("NetRevenue")));
    });
    t.test("SQL / new service graph reloads persisted invoice", () -> {
      InventorySystem fresh = new InventorySystem(new SqlDatabase());
      AuthService.Session session = fresh.auth().login(prefix + "CASHIER", password);
      equal(firstSale, fresh.sales().load(session, firstSale.id()));
      check(!fresh.warranties().search(session, serialSale.code()).isEmpty(), "Warranty lost");
    });
    t.test("REAL THREE-TIER / HttpApiClient -> API -> SQL Server checkout", () -> {
      try (ApiServer server = new ApiServer(new InetSocketAddress("127.0.0.1", 0), null, new ApplicationApi(app))) {
        server.start();
        HttpApiClient client = new HttpApiClient(URI.create("http://127.0.0.1:" + server.port()));
        ApiSession session = Json.convert(client.call(Operation.LOGIN, "",
            Data.map("username", prefix + "CASHIER", "password", new String(password))), ApiSession.class);
        SaleRequest value = saleRequest(product, 1);
        Sale paid = Json.convert(client.call(Operation.CHECKOUT, session.token(), Data.map("request", value)),
            Sale.class);
        equal(1L, scalar("SELECT COUNT(*) FROM dm.Invoices WHERE Id=? AND RequestId=?", paid.id(), value.requestId()));
        equal(paid, Json.convert(client.call(Operation.SALE_RESOLVE, session.token(), Data.map("request", value)),
            Sale.class));
        equal(403, throwsType(ApiException.class, () -> client.call(Operation.EMPLOYEES, session.token(),
            Data.map())).status());
        client.call(Operation.LOGOUT, session.token(), Data.map());
      }
    });
    t.test("SQL / stock equals signed movement ledger", () -> equal(0L, scalar(
        "SELECT COUNT(*) FROM (SELECT s.ProductId,s.Location FROM dm.Stock s LEFT JOIN dm.StockMovements m "
            + "ON m.ProductId=s.ProductId AND m.Location=s.Location WHERE s.ProductId IN(?,?) "
            + "GROUP BY s.ProductId,s.Location,s.Quantity HAVING "
                + "s.Quantity<>COALESCE(SUM(CONVERT(bigint,m.Delta)),0)) x",
        product, serialProduct)));
    t.test("SQL / product archive preserves ledger and blocks further receiving", () -> {
      Item current = app.catalog().get(manager, product);
      long movements = scalar("SELECT COUNT(*) FROM dm.StockMovements WHERE ProductId=?", product);
      app.lifecycle().setActive(manager, "product", product, current.revision(), false, "DEMO archive acceptance");
      try {
        throwsType(IllegalArgumentException.class, () -> app.inventory().receive(warehouse, receive(product, 1)));
        equal(movements, scalar("SELECT COUNT(*) FROM dm.StockMovements WHERE ProductId=?", product));
      } finally {
        Item latest = app.catalog().get(manager, product);
        app.lifecycle().setActive(manager, "product", product, latest.revision(), true, "DEMO restore acceptance");
      }
    });
    t.test("SQL / customer stale update refused and archive preserves invoice snapshot", () -> {
      int version = Math.toIntExact(scalar("SELECT Revision FROM dm.Customers WHERE Id=?", customer));
      app.lifecycle().setActive(manager, "customer", customer, version, false, "DEMO archive customer");
      equal(firstSale, app.sales().load(cashier, firstSale.id()));
      throwsType(IllegalArgumentException.class, () -> app.people().saveCustomer(manager, customer,
          "DEMO stale write", "", "", version));
      app.lifecycle().setActive(manager, "customer", customer, version + 1, true, "DEMO restore customer");
    });
    new ControlledSqlCases(db, app, manager, cashier, warehouse, category, brand, vendor, reference).run(t);
    t.finish("sql-server-integration");
  }
}
