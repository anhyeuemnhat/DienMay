package vn.dienmay.tests;

import static vn.dienmay.tests.Fixtures.item;
import static vn.dienmay.tests.Fixtures.line;
import static vn.dienmay.tests.Fixtures.request;
import static vn.dienmay.tests.TestKit.check;
import static vn.dienmay.tests.TestKit.equal;
import static vn.dienmay.tests.TestKit.throwsType;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.JTextField;
import vn.dienmay.client.state.PendingStore;
import vn.dienmay.client.ui.Ui;
import vn.dienmay.common.model.Cart;
import vn.dienmay.common.model.InventoryRequest;
import vn.dienmay.common.model.ItemQuantity;
import vn.dienmay.common.model.Money;
import vn.dienmay.common.model.Role;
import vn.dienmay.common.model.SaleRequest;
import vn.dienmay.common.model.Validation;
import vn.dienmay.common.model.WarrantyRules;
import vn.dienmay.server.db.Jdbc;
import vn.dienmay.server.db.ProductDao;
import vn.dienmay.server.db.SqlDatabase;
import vn.dienmay.server.db.SqlParameters;
import vn.dienmay.server.db.StockDao;
import vn.dienmay.server.service.AuthService;
import vn.dienmay.server.service.Passwords;

/** Ported upstream regression cases plus the movement binding regression. No real SQL Server here. */
public final class UnitTests {
  private UnitTests() {}

  public static void main(String[] args) throws Exception {
    TestKit t = new TestKit();
    t.test("money / exact decimal addition", () -> equal(Money.of("0.30"), Money.of("0.10").add(Money.of("0.20"))));
    t.test("money / normalized scale", () -> equal(new BigDecimal("100.00"), Money.of("100")));
    t.test("money / negative rejected", () -> throwsType(IllegalArgumentException.class, () -> Money.of("-1")));
    t.test("money / excessive precision rejected", () -> throwsType(IllegalArgumentException.class, () ->
        Money.of("1.001")));
    t.test("money / malformed separator rejected", () -> throwsType(IllegalArgumentException.class, () ->
        Money.of("1,000")));
    t.test("money / decimal19 boundary", () -> {
      equal(new BigDecimal("99999999999999999.99"), Money.of("99999999999999999.99"));
      throwsType(IllegalArgumentException.class, () -> Money.of("100000000000000000"));
    });
    t.test("money / HALF_UP discount", () -> equal(Money.of("0.01"), Money.discount(Money.of("0.05"), Money.of("10"))));
    t.test("money / discount cannot exceed subtotal", () -> throwsType(IllegalArgumentException.class, () ->
        Money.total(Money.of("10"), Money.of("10.01"))));
    t.test("money / percentage bounds", () -> {
      equal(Money.of("100"), Money.discount(Money.of("100"), Money.of("100")));
      throwsType(IllegalArgumentException.class, () -> Money.discount(Money.of("100"), Money.of("100.01")));
    });
    t.test("money / Vietnamese formatting preserves fractions", () ->
        check(Money.format(Money.of("1234.56")).contains("1.234,56"), "Locale formatting"));
    t.test("barcode / leading zero retained", () -> equal("000123", item(1, false, 10).barcode()));
    t.test("cart / repeated scans merge", () -> {
      Cart cart = new Cart();
      cart.add(item(1, false, 10), 1, List.of());
      cart.add(item(1, false, 10), 1, List.of());
      equal(1, cart.lines().size());
      equal(2, cart.lines().get(0).quantity());
      equal(Money.of("200.50"), cart.subtotal());
    });
    t.test("cart / overstock does not mutate", () -> {
      Cart cart = new Cart();
      cart.add(item(1, false, 1), 1, List.of());
      throwsType(IllegalArgumentException.class, () -> cart.add(item(1, false, 1), 1, List.of()));
      equal(1, cart.lines().get(0).quantity());
    });
    t.test("cart / zero quantity rejected", () -> throwsType(IllegalArgumentException.class, () -> new
        Cart().add(item(1, false, 1), 0, List.of())));
    t.test("cart / negative quantity rejected", () -> throwsType(IllegalArgumentException.class, () -> line(1, -1)));
    t.test("cart / serial count equals quantity", () -> throwsType(IllegalArgumentException.class, () -> new
        Cart().add(item(1, true, 2), 2, List.of("s1"))));
    t.test("cart / duplicate normalized serial rejected", () -> throwsType(IllegalArgumentException.class, () -> line(1,
        2, "abc", "ABC")));
    t.test("cart / serial lines merge", () -> {
      Cart cart = new Cart();
      cart.add(item(1, true, 2), 1, List.of("s1"));
      cart.add(item(1, true, 2), 1, List.of("s2"));
      equal(List.of("S1", "S2"), cart.lines().get(0).serials());
    });
    t.test("cart / ordinary product rejects serial", () -> throwsType(IllegalArgumentException.class, () -> new
        Cart().add(item(1, false, 2), 1, List.of("s1"))));
    t.test("cart / removal recomputes total", () -> {
      Cart cart = new Cart();
      cart.add(item(1, false, 2), 2, List.of());
      cart.remove(1);
      equal(Money.ZERO, cart.subtotal());
    });
    t.test("serial / delimiters and leading zeros", () -> equal(List.of("001", "002", "ABC", "DEF"),
        Validation.serials("001\n002,abc\tdef")));
    t.test("request / empty cart rejected", () -> throwsType(IllegalArgumentException.class, () ->
        request(UUID.randomUUID())));
    t.test("request / duplicate products rejected", () -> throwsType(IllegalArgumentException.class, () ->
        request(UUID.randomUUID(), line(1, 1), line(1, 1))));
    t.test("request / canonical line ordering", () -> equal(request(UUID.randomUUID(), line(1, 1), line(2,
        1)).fingerprint(), request(UUID.randomUUID(), line(2, 1), line(1, 1)).fingerprint()));
    t.test("request / changed quantity changes hash", () -> check(!request(UUID.randomUUID(), line(1,
        1)).fingerprint().equals(request(UUID.randomUUID(), line(1, 2)).fingerprint()), "Fingerprint collision"));
    t.test("request / promotion stacking rejected", () -> throwsType(IllegalArgumentException.class, () -> new
        SaleRequest(UUID.randomUUID(), null, "CASH", Money.of("1"), "TEST", Money.of("1"), List.of(line(1, 1)))));
    t.test("request / unknown payment rejected", () -> throwsType(IllegalArgumentException.class, () -> new
        SaleRequest(UUID.randomUUID(), null, "CARD", Money.ZERO, "", Money.ZERO, List.of(line(1, 1)))));
    t.test("request / immutable collection", () -> {
      List<ItemQuantity> lines = new ArrayList<>(List.of(line(1, 1)));
      SaleRequest value = new SaleRequest(UUID.randomUUID(), null, "CASH", Money.ZERO, "", Money.ZERO, lines);
      lines.clear();
      equal(1, value.lines().size());
      throwsType(UnsupportedOperationException.class, () -> value.lines().clear());
    });
    t.test("inventory / same source destination rejected", () -> throwsType(IllegalArgumentException.class, () -> new
        InventoryRequest(UUID.randomUUID(), 1, 1, List.of(), "Test", null, Money.ZERO, "KHO", "KHO")));
    t.test("inventory / reason required", () -> throwsType(IllegalArgumentException.class, () -> new
        InventoryRequest(UUID.randomUUID(), 1, 1, List.of(), "", 1L, Money.ZERO, null, "KHO")));
    t.test("inventory / duplicate serial rejected", () -> throwsType(IllegalArgumentException.class, () -> new
        InventoryRequest(UUID.randomUUID(), 1, 2, List.of("s1", "S1"), "Test", 1L, Money.ZERO, null, "KHO")));
    t.test("warranty / leap-year month arithmetic", () -> equal(LocalDate.of(2025, 2, 28),
        WarrantyRules.end(LocalDate.of(2024, 2, 29), 12)));
    t.test("warranty / end-of-month rule", () -> equal(LocalDate.of(2025, 2, 28), WarrantyRules.end(LocalDate.of(2025,
        1, 31), 1)));
    t.test("warranty / inclusive expiry", () -> {
      LocalDate start = LocalDate.of(2025, 1, 1);
      LocalDate end = LocalDate.of(2026, 1, 1);
      check(WarrantyRules.active(start, end, end), "End date not covered");
      check(!WarrantyRules.active(start, end, end.plusDays(1)), "Expired date covered");
    });
    t.test("warranty / month bounds", () -> {
      throwsType(IllegalArgumentException.class, () -> WarrantyRules.end(LocalDate.now(), 0));
      throwsType(IllegalArgumentException.class, () -> WarrantyRules.end(LocalDate.now(), 121));
    });
    t.test("claim / forward-only transitions", () -> {
      check(WarrantyRules.canTransition("RECEIVED", "PROCESSING"), "Receive to processing");
      check(WarrantyRules.canTransition("PROCESSING", "COMPLETED"), "Processing to completed");
      check(!WarrantyRules.canTransition("COMPLETED", "PROCESSING"), "Must not reopen");
      check(!WarrantyRules.canTransition("RECEIVED", "COMPLETED"), "Must not skip processing");
    });
    t.test("dates / local midnight to UTC", () -> equal(Instant.parse("2026-10-18T17:00:00Z"),
        Validation.start(LocalDate.of(2026, 10, 19))));
    t.test("dates / strict entry", () -> {
      equal(LocalDate.of(2026, 10, 19), Ui.date(new JTextField("19/10/2026")));
      throwsType(IllegalArgumentException.class, () -> Ui.date(new JTextField("31/02/2026")));
    });
    t.test("dates / reversed interval rejected", () -> throwsType(IllegalArgumentException.class, () ->
        Validation.dates(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1))));
    t.test("auth / salted hash and verification", () -> {
      char[] secret = UUID.randomUUID().toString().toCharArray();
      String a = Passwords.hash(secret);
      String b = Passwords.hash(secret);
      check(!a.equals(b), "Salt was reused");
      check(Passwords.verify(secret, a), "Valid password rejected");
      check(!Passwords.verify(UUID.randomUUID().toString().toCharArray(), a), "Invalid password accepted");
      java.util.Arrays.fill(secret, '\0');
    });
    t.test("auth / short password rejected", () -> throwsType(IllegalArgumentException.class, () -> Passwords.hash(new
        char[5])));
    t.test("auth / malformed hash rejected", () -> {
      check(!Passwords.verify(new char[20], "plain-text"), "Plain-text hash accepted");
      check(!Passwords.verify(new char[20], "pbkdf2_sha256$1$AAAA$AAAA"), "Unsafe iteration count accepted");
    });
    t.test("auth / role predicate matrix", () -> {
      for (Role actual : Role.values()) {
        for (Role allowed : Role.values()) {
          equal(actual == allowed, AuthService.roleAllowed(actual, allowed));
        }
      }
    });
    t.test("auth / null session denied before SQL", () -> throwsType(SecurityException.class, () -> new AuthService(new
        SqlDatabase(() -> null)).require(null, null, Role.MANAGER)));
    t.test("auth / no demo credentials shipped", () -> {
      String script = Files.readString(Path.of("database/demo/002_demo.sql"));
      check(!script.contains("pbkdf2_sha256$") && !script.contains("INSERT dm.Employees"),
          "Default credentials shipped");
    });
    t.test("pending / sale survives restart", () -> {
      Path dir = Files.createTempDirectory("dm-sale-");
      SaleRequest value = request(UUID.randomUUID(), line(1, 1, "000001"));
      try (PendingStore store = new PendingStore(dir, 1, "sale")) {
        store.save(value);
      }
      try (PendingStore store = new PendingStore(dir, 1, "sale")) {
        equal(value, store.sale());
        store.clear();
        equal(null, store.sale());
      }
    });
    t.test("pending / unresolved different request cannot overwrite", () -> {
      try (PendingStore store = new PendingStore(Files.createTempDirectory("dm-pending-"), 1, "sale")) {
        store.save(request(UUID.randomUUID(), line(1, 1)));
        throwsType(IOException.class, () -> store.save(request(UUID.randomUUID(), line(1, 1))));
      }
    });
    t.test("pending / OS lock excludes second writer", () -> {
      Path dir = Files.createTempDirectory("dm-lock-");
      try (PendingStore store = new PendingStore(dir, 1, "sale")) {
        check(store.path() != null, "Missing pending path");
        throwsType(IOException.class, () -> new PendingStore(dir, 1, "sale"));
      }
    });
    t.test("pending / inventory round trip", () -> {
      InventoryRequest value = Fixtures.stock();
      try (PendingStore store = new PendingStore(Files.createTempDirectory("dm-stock-"), 1, "receive")) {
        store.save(value);
        equal(value, store.inventory());
        equal(value.fingerprint(), store.inventory().fingerprint());
      }
    });
    t.test("pending / corrupt file preserved", () -> {
      try (PendingStore store = new PendingStore(Files.createTempDirectory("dm-corrupt-"), 1, "sale")) {
        Files.writeString(store.path(), "type=sale\nid=bad\n");
        throwsType(IOException.class, store::sale);
        check(Files.exists(store.path()), "Evidence removed");
      }
    });
    t.test("pending / same UUID different payload refused", () -> {
      UUID id = UUID.randomUUID();
      try (PendingStore store = new PendingStore(Files.createTempDirectory("dm-payload-"), 1, "sale")) {
        store.save(request(id, line(1, 1)));
        throwsType(IOException.class, () -> store.save(request(id, line(1, 2))));
      }
    });
    t.test("pending / tampered quantity detected", () -> {
      try (PendingStore store = new PendingStore(Files.createTempDirectory("dm-tamper-"), 1, "sale")) {
        store.save(request(UUID.randomUUID(), line(1, 1)));
        String content = Files.readString(store.path(), StandardCharsets.UTF_8);
        check(content.contains("line.0.qty=1"), "Fixture mismatch");
        Files.writeString(store.path(), content.replace("line.0.qty=1", "line.0.qty=2"));
        throwsType(IOException.class, store::sale);
        check(Files.exists(store.path()), "Evidence removed");
      }
    });
    t.test("pending / tampered location detected", () -> {
      try (PendingStore store = new PendingStore(Files.createTempDirectory("dm-tamper-"), 1, "receive")) {
        store.save(Fixtures.stock());
        Files.writeString(store.path(), Files.readString(store.path()).replace("to=KHO", "to=QUAY"));
        throwsType(IOException.class, store::inventory);
      }
    });
    t.test("receipt / persisted fields and timezone", () -> {
      String receipt = Fixtures.sale().generateReceipt("Store", "Address", "Phone");
      check(receipt.contains("HD-7") && receipt.contains("19/10/2026 08:00")
          && receipt.contains("0001, 0002") && receipt.contains("200 ₫"), "Receipt fields missing");
    });
    t.test("JDBC double / commit close", () -> {
      JdbcDouble f = new JdbcDouble();
      equal(7, new SqlDatabase(f::connection).transaction(c -> 7));
      equal(1, f.commits);
      equal(0, f.rollbacks);
      equal(1, f.closes);
    });
    t.test("JDBC double / SQL error rollback close", () -> {
      JdbcDouble f = new JdbcDouble();
      SQLException expected = new SQLException("injected");
      equal(expected, throwsType(SQLException.class, () -> new SqlDatabase(f::connection).transaction(c -> { throw
          expected; })));
      equal(0, f.commits);
      equal(1, f.rollbacks);
      equal(1, f.closes);
    });
    t.test("JDBC double / validation rollback", () -> {
      JdbcDouble f = new JdbcDouble();
      throwsType(IllegalArgumentException.class, () -> new SqlDatabase(f::connection).transaction(c -> { throw new
          IllegalArgumentException("injected"); }));
      equal(1, f.rollbacks);
    });
    t.test("JDBC double / uncertain commit is not success", () -> {
      JdbcDouble f = new JdbcDouble();
      f.failCommit = true;
      throwsType(SQLException.class, () -> new SqlDatabase(f::connection).transaction(c -> true));
      equal(1, f.commits);
      equal(1, f.rollbacks);
      equal(1, f.closes);
    });
    t.test("JDBC double / rollback exception suppressed", () -> {
      JdbcDouble f = new JdbcDouble();
      f.failRollback = true;
      SQLException error = throwsType(SQLException.class, () -> new SqlDatabase(f::connection).transaction(c -> { throw
          new SQLException("original"); }));
      equal(1, error.getSuppressed().length);
    });
    t.test("JDBC double / read closes", () -> {
      JdbcDouble f = new JdbcDouble();
      equal("ok", new SqlDatabase(f::connection).read(c -> "ok"));
      equal(1, f.closes);
    });
    t.test("SQL / LIKE metacharacter escape", () -> equal("%A~%~_~[~~%", ProductDao.like("A%_[~")));
    t.test("SQL / placeholder scanner ignores quoted and commented question marks", () -> equal(2,
        SqlParameters.count("SELECT ?, '?', [??], N'it''s?' /* ? /* ? */ */ -- ?\n WHERE x=?")));
    t.test("SQL / mismatch rejected before prepare", () -> {
      JdbcDouble f = new JdbcDouble();
      throwsType(SQLException.class, () -> Jdbc.statement(f.connection(), "SELECT ?,?", 1));
      equal(0, f.queries.size());
    });
    t.test("StockDao.movement / exact ten placeholders and ten bound arguments", () -> {
      JdbcDouble f = new JdbcDouble();
      equal(7L, new StockDao().movement(f.connection(), UUID.randomUUID(), 1, "KHO", 2,
          "RECEIVE", "TEST", 3, null, 4L, Money.of("60")));
      equal(10, SqlParameters.count(f.lastSql));
      equal(10, f.bound.size());
      equal(Money.of("60"), f.bound.get(10));
    });
    t.test("JDBC double / binding failure closes prepared statement", () -> {
      JdbcDouble f = new JdbcDouble();
      f.failBind = true;
      throwsType(SQLException.class, () -> Jdbc.statement(f.connection(), "SELECT ?", 1));
      equal(1, f.statementCloses);
    });
    t.test("architecture / client has no JDBC or backend linkage", () -> {
      try (var files = Files.walk(Path.of("client-swing/src/main/java"))) {
        for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
          String source = Files.readString(file);
          check(!source.contains("import java.sql") && !source.contains("vn.dienmay.server")
              && !source.contains("jdbc:sqlserver") && !source.contains("DIENMAY_DB_PASSWORD"), file.toString());
        }
      }
    });
    t.finish("offline-regression");
  }
}
