package vn.dienmay.tests;

import static vn.dienmay.tests.TestKit.check;
import static vn.dienmay.tests.TestKit.equal;
import static vn.dienmay.tests.TestKit.throwsType;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.dienmay.client.api.ApiGateway;
import vn.dienmay.client.api.HttpApiClient;
import vn.dienmay.client.controller.GuardedActionController;
import vn.dienmay.client.state.PendingStore;
import vn.dienmay.common.api.ApiException;
import vn.dienmay.common.api.BusinessException;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.api.Operation;
import vn.dienmay.common.model.DeletionPreview;
import vn.dienmay.common.model.DeletionRequest;
import vn.dienmay.common.model.InventoryRequest;
import vn.dienmay.common.model.Money;
import vn.dienmay.common.model.PriceConfirmation;
import vn.dienmay.common.model.PricePolicy;
import vn.dienmay.common.model.ReversalRequest;
import vn.dienmay.common.model.Role;
import vn.dienmay.server.api.ApiServer;
import vn.dienmay.server.db.SqlDatabase;
import vn.dienmay.server.db.StockDao;
import vn.dienmay.server.service.AuthService;
import vn.dienmay.server.service.InventoryReversalService;
import vn.dienmay.server.service.RecordDeletionService;

/** Offline contracts, actual disk/restart and local HTTP. JDBC doubles are NOT real SQL Server tests. */
public final class ControlledOperationsTests {
  private ControlledOperationsTests() {}
  private static final BigDecimal COST = Money.of("100");
  private static final BigDecimal SELL = Money.of("80");
  private static PriceConfirmation ack() { return new PriceConfirmation(1, 1, COST, SELL); }
  private static DeletionRequest deletion() {
    return new DeletionRequest(UUID.randomUUID(), "product", 7, 2, "Synthetic test - never customer data");
  }
  private static final class Gateway implements ApiGateway {
    Object result;
    Exception failure;
    final List<Operation> calls = new ArrayList<>();
    public Object call(Operation op, String bearer, Map<String, Object> p) throws Exception {
      calls.add(op);
      if (failure != null) { throw failure; }
      return result;
    }
    public String profile() { return "controlled-operations-test-double"; }
  }
  private static InventoryRequest stock() {
    return new InventoryRequest(UUID.randomUUID(), 1, 1, List.of("490154203237518"),
        "Synthetic test receipt", 3L, COST, null, "KHO", "IMEI").withConfirmation(ack());
  }
  private static String source(String path) throws IOException { return Files.readString(Path.of(path)); }

  public static void main(String[] args) throws Exception {
    TestKit tests = new TestKit();
    tests.test("Selling above cost needs no acknowledgement", () -> {
      PricePolicy.require(1, 1, COST, Money.of("101"), null);
      check(!PricePolicy.belowCost(COST, Money.of("101")), "Wrong comparison");
    });
    tests.test("Selling equal to cost needs no acknowledgement", () -> PricePolicy.require(1, 1, COST, COST, null));
    tests.test("Zero cost never divides by zero", () -> {
      equal(new BigDecimal("0.00"), PricePolicy.percentage(Money.ZERO, Money.ZERO));
      PricePolicy.require(1, 1, Money.ZERO, SELL, null);
    });
    tests.test("Below cost without acknowledgement has explicit business code", () -> {
      BusinessException e = throwsType(BusinessException.class, () -> PricePolicy.require(1, 1, COST, SELL, null));
      equal("BELOW_COST_CONFIRMATION_REQUIRED", e.code()); equal(409, e.status());
    });
    tests.test("Exact acknowledgement authorizes below-cost prices", () -> PricePolicy.require(1, 1, COST, SELL,
        ack()));
    tests.test("Changed price invalidates acknowledgement", () -> {
      equal("PRICE_CONFIRMATION_STALE", throwsType(BusinessException.class,
          () -> PricePolicy.require(1, 1, Money.of("101"), SELL, ack())).code());
    });
    tests.test("Changed sale price invalidates acknowledgement", () -> throwsType(BusinessException.class,
        () -> PricePolicy.require(1, 1, COST, Money.of("79"), ack())));
    tests.test("Changed revision invalidates acknowledgement", () -> throwsType(BusinessException.class,
        () -> PricePolicy.require(1, 2, COST, SELL, ack())));
    tests.test("Acknowledgement cannot move to another record", () -> throwsType(BusinessException.class,
        () -> PricePolicy.require(2, 1, COST, SELL, ack())));
    tests.test("Negative price remains a hard validation error", () -> throwsType(IllegalArgumentException.class,
        () -> PricePolicy.require(1, 1, new BigDecimal("-1"), SELL, null)));
    tests.test("Fractional cents remain a hard validation error", () -> throwsType(IllegalArgumentException.class,
        () -> PricePolicy.require(1, 1, new BigDecimal("1.001"), SELL, null)));
    tests.test("Exact price differences do not use binary floating point", () -> {
      equal(new BigDecimal("-20.00"), PricePolicy.difference(COST, SELL));
      equal(new BigDecimal("-20.00"), PricePolicy.percentage(COST, SELL));
      equal(new BigDecimal("-33.33"), PricePolicy.percentage(Money.of("3"), Money.of("2")));
    });
    tests.test("Price fingerprint normalizes decimal representation", () -> equal(ack().fingerprint(),
        new PriceConfirmation(1, 1, new BigDecimal("100.0"), new BigDecimal("80.00")).fingerprint()));
    tests.test("Price acknowledgement JSON roundtrip", () -> equal(ack(),
        Json.convert(Json.parse(Json.stringify(ack())),
        PriceConfirmation.class)));
    tests.test("Legacy stock JSON defaults missing acknowledgement", () -> {
      String json = Json.stringify(Fixtures.stock());
      json = json.replace(",\"priceConfirmation\":null", "");
      check(Json.convert(Json.parse(json), InventoryRequest.class).priceConfirmation() == null, "Not legacy safe");
    });
    tests.test("Receive acknowledgement participates in fingerprint", () -> {
      InventoryRequest a = stock();
      InventoryRequest b = new InventoryRequest(a.id(), a.productId(), a.quantity(), a.serials(), a.reference(),
          a.vendorId(), a.unitCost(), a.from(), a.to(), a.identifierType());
      check(!a.fingerprint().equals(b.fingerprint()), "Acknowledgement not bound");
    });
    tests.test("Acknowledgement cannot be applied to another receipt cost", () ->
        throwsType(IllegalArgumentException.class,
        () -> Fixtures.stock().withConfirmation(ack())));
    tests.test("Transfer cannot carry price acknowledgement", () -> throwsType(IllegalArgumentException.class,
        () -> new InventoryRequest(UUID.randomUUID(), 1, 1, List.of(), "TEST", null, COST, "KHO", "QUAY")
            .withConfirmation(ack())));
    tests.test("IMEI and acknowledgement survive durable close and restart", () -> {
      Path dir = Files.createTempDirectory("dmd-receive-test-"); InventoryRequest request = stock();
      try (PendingStore store = new PendingStore(dir, 1, "receive")) { store.save(request); }
      try (PendingStore store = new PendingStore(dir, 1, "receive")) {
        equal(request, store.inventory()); equal(request.fingerprint(), store.inventory().fingerprint());
        equal("IMEI", store.inventory().identifierType()); store.clear();
      }
    });
    tests.test("Pending receipt cannot be replaced with a different confirmation", () -> {
      Path dir = Files.createTempDirectory("dmd-receive-test-"); InventoryRequest a = stock();
      try (PendingStore store = new PendingStore(dir, 1, "receive")) {
        store.save(a);
        InventoryRequest b = a.withConfirmation(new PriceConfirmation(1, 2, COST, SELL));
        throwsType(IOException.class, () -> store.save(b)); equal(a, store.inventory());
      }
    });
    for (String type : DeletionRequest.TYPES) {
      tests.test("Deletion target allowlist accepts " + type, () -> {
        DeletionRequest d = new DeletionRequest(UUID.randomUUID(), type, 1, 1, "TEST ONLY");
        equal(d, Json.convert(Json.parse(Json.stringify(d)), DeletionRequest.class));
      });
    }
    tests.test("SQL table names are not deletion entity types", () -> throwsType(IllegalArgumentException.class,
        () -> new DeletionRequest(UUID.randomUUID(), "dm.Products; DROP TABLE dm.Stock", 1, 1, "TEST")));
    tests.test("Deletion requires reason", () -> throwsType(IllegalArgumentException.class,
        () -> new DeletionRequest(UUID.randomUUID(), "product", 1, 1, " ")));
    tests.test("Deletion reason is bounded", () -> throwsType(IllegalArgumentException.class,
        () -> new DeletionRequest(UUID.randomUUID(), "product", 1, 1, "x".repeat(1001))));
    tests.test("Deletion requires selected row and revision", () -> {
      throwsType(IllegalArgumentException.class, () -> DeletionRequest.requireTarget("product", 0, 1));
      throwsType(IllegalArgumentException.class, () -> DeletionRequest.requireTarget("product", 1, 0));
    });
    tests.test("Deletion hash binds revision and reason", () -> {
      DeletionRequest d = deletion();
      check(!d.fingerprint().equals(new DeletionRequest(d.id(), d.type(), d.entityId(), 3, d.reason()).fingerprint()),
          "Revision not bound");
      check(!d.fingerprint().equals(new DeletionRequest(d.id(), d.type(), d.entityId(), 2, "OTHER").fingerprint()),
          "Reason not bound");
    });
    tests.test("Reversal requires reason and positive version", () -> {
      throwsType(IllegalArgumentException.class, () -> new ReversalRequest(UUID.randomUUID(), 1, 1, " "));
      throwsType(IllegalArgumentException.class, () -> new ReversalRequest(UUID.randomUUID(), 0, 1, "TEST"));
      throwsType(IllegalArgumentException.class, () -> new ReversalRequest(UUID.randomUUID(), 1, 0, "TEST"));
    });
    tests.test("Reversal fingerprint binds original movement", () -> {
      UUID id = UUID.randomUUID();
      check(!new ReversalRequest(id, 7, 2, "TEST").fingerprint().equals(
          new ReversalRequest(id, 8, 2, "TEST").fingerprint()), "Original movement not bound");
    });
    tests.test("Deletion unknown response is retained and reloaded after restart", () -> {
      Path dir = Files.createTempDirectory("dmd-delete-test-"); Gateway g = new Gateway(); DeletionRequest d =
          deletion();
      try (GuardedActionController<DeletionRequest> c = new GuardedActionController<>(g, dir, 1, DeletionRequest.class))
          {
        throwsType(IOException.class, () -> c.submit("test", d)); equal(d, c.pending());
      }
      try (GuardedActionController<DeletionRequest> c = new GuardedActionController<>(g, dir, 1, DeletionRequest.class))
          {
        equal(d, c.pending()); equal(1, g.calls.size());
        g.result = false; check(!c.resolve("test"), "Not committed was treated as success"); equal(d, c.pending());
        g.result = true; check(c.resolve("test"), "Committed not resolved"); equal(null, c.pending());
      }
    });
    tests.test("Lost deletion response cannot create another request", () -> {
      Path dir = Files.createTempDirectory("dmd-delete-test-"); Gateway g = new Gateway(); g.failure = new
          IOException("lost");
      DeletionRequest d = deletion();
      try (GuardedActionController<DeletionRequest> c = new GuardedActionController<>(g, dir, 1, DeletionRequest.class))
          {
        throwsType(IOException.class, () -> c.submit("test", d));
        throwsType(IllegalStateException.class, () -> c.submit("test", deletion())); equal(1, g.calls.size());
      }
    });
    tests.test("Deletion successful response must match selected record", () -> {
      Path dir = Files.createTempDirectory("dmd-delete-test-"); Gateway g = new Gateway(); DeletionRequest d =
          deletion();
      g.result = new DeletionPreview("product", 999, 2, "TEST", "TEST", true, "");
      try (GuardedActionController<DeletionRequest> c = new GuardedActionController<>(g, dir, 1, DeletionRequest.class))
          {
        throwsType(IOException.class, () -> c.submit("test", d)); equal(d, c.pending());
        g.result = new DeletionPreview(d.type(), d.entityId(), d.revision(), "TEST", "TEST", true, "");
        c.submit("test", d); equal(null, c.pending());
      }
    });
    tests.test("Pending administration has an exclusive OS lock", () -> {
      Path dir = Files.createTempDirectory("dmd-delete-test-"); Gateway g = new Gateway();
      try (GuardedActionController<DeletionRequest> c = new GuardedActionController<>(g, dir, 1, DeletionRequest.class))
          {
        equal(null, c.pending());
        throwsType(IOException.class, () -> new GuardedActionController<>(g, dir, 1, DeletionRequest.class));
      }
    });
    tests.test("Cancellation clears intent only after confirmed cancellation fence", () -> {
      Path dir = Files.createTempDirectory("dmd-delete-test-"); Gateway g = new Gateway(); DeletionRequest d =
          deletion();
      try (GuardedActionController<DeletionRequest> c = new GuardedActionController<>(g, dir, 1, DeletionRequest.class))
          {
        throwsType(IOException.class, () -> c.submit("test", d));
        g.result = "true"; throwsType(IOException.class, () -> c.cancel("test")); equal(d, c.pending());
        g.result = false; check(!c.cancel("test"), "Committed request cancelled locally"); equal(d, c.pending());
        g.result = true; check(c.cancel("test"), "Fence not acknowledged"); equal(null, c.pending());
      }
    });
    tests.test("Reversal lost response and explicit retry retain one request ID", () -> {
      Path dir = Files.createTempDirectory("dmd-reversal-test-"); Gateway g = new Gateway();
      ReversalRequest d = new ReversalRequest(UUID.randomUUID(), 7, 2, "TEST");
      g.failure = new IOException("Response lost after commit");
      try (GuardedActionController<ReversalRequest> c = new GuardedActionController<>(g, dir, 1, ReversalRequest.class))
          {
        throwsType(IOException.class, () -> c.submit("test", d)); equal(d.id(), c.pending().id());
      }
      try (GuardedActionController<ReversalRequest> c = new GuardedActionController<>(g, dir, 1, ReversalRequest.class))
          {
        equal(d, c.pending()); g.failure = null; g.result = 11L; c.submit("test", c.pending());
        equal(null, c.pending()); equal(List.of(Operation.REVERSE_RECEIPT, Operation.REVERSE_RECEIPT), g.calls);
      }
    });
    tests.test("Invalid reversal result does not lose request", () -> {
      Gateway g = new Gateway(); Path dir = Files.createTempDirectory("dmd-reversal-test-");
      ReversalRequest d = new ReversalRequest(UUID.randomUUID(), 7, 2, "TEST"); g.result = 0L;
      try (GuardedActionController<ReversalRequest> c = new GuardedActionController<>(g, dir, 1, ReversalRequest.class))
          {
        throwsType(IOException.class, () -> c.submit("test", d)); equal(d, c.pending());
      }
    });
    for (Operation op : List.of(Operation.DELETE_PREVIEW, Operation.DELETE_RECORD, Operation.DELETE_RESOLVE,
        Operation.DELETE_CANCEL, Operation.REVERSAL_PREVIEW, Operation.REVERSE_RECEIPT,
        Operation.REVERSAL_RESOLVE, Operation.REVERSAL_CANCEL)) {
      tests.test("Manager-only operation " + op, () -> {
        check(op.allows(Role.MANAGER), "Manager denied");
        check(!op.allows(Role.CASHIER) && !op.allows(Role.WAREHOUSE), "Role escalation");
        equal(op, Operation.fromPath(op.path()));
      });
    }
    tests.test("Warehouse acknowledgement does not grant product editing", () -> {
      check(Operation.RECEIVE.allows(Role.WAREHOUSE), "Receive was removed");
      check(!Operation.PRODUCT_SAVE.allows(Role.WAREHOUSE), "Product permission escalated");
      check(Operation.RECEIPTS.allows(Role.WAREHOUSE), "Receipt lookup denied");
    });
    tests.test("Delete service rejects unauthenticated caller and rolls back", () -> {
      JdbcDouble jdbc = new JdbcDouble(); SqlDatabase db = new SqlDatabase(jdbc::connection);
      RecordDeletionService s = new RecordDeletionService(db, new AuthService(db));
      throwsType(SecurityException.class, () -> s.delete(null, deletion())); equal(1, jdbc.rollbacks);
      check(jdbc.queries.stream().noneMatch(q -> q.startsWith("EXEC dm.ControlledDelete")), "Unauthenticated DML");
    });
    tests.test("Reversal service rejects unauthenticated caller and rolls back", () -> {
      JdbcDouble jdbc = new JdbcDouble(); SqlDatabase db = new SqlDatabase(jdbc::connection);
      InventoryReversalService s = new InventoryReversalService(db, new AuthService(db));
      throwsType(SecurityException.class, () -> s.reverse(null, new ReversalRequest(UUID.randomUUID(), 1, 1, "TEST")));
      equal(1, jdbc.rollbacks); check(jdbc.queries.isEmpty(), "Unauthenticated query");
    });
    tests.test("Receipt insert binds the previous cost and provenance (JDBC double only)", () -> {
      JdbcDouble jdbc = new JdbcDouble(); InventoryRequest d = stock();
      long result = new StockDao().receipt(jdbc.connection(), d.id(), d.productId(), d.quantity(), d.reference(),
          1, d.vendorId(), d.unitCost(), Money.of("60"));
      equal(7L, result); equal(9, jdbc.bound.size()); equal(Money.of("60"), jdbc.bound.get(8));
      check(jdbc.lastSql.contains("CostRevision+1"), "Missing provenance");
    });
    tests.test("Business errors survive real HTTP with exact code", () -> {
      try (ApiServer server = new ApiServer(new InetSocketAddress("127.0.0.1", 0), null,
          (op, bearer, p) -> { throw new BusinessException(409, "PRICE_CONFIRMATION_STALE", "Reload prices"); })) {
        server.start();
        HttpApiClient client = new HttpApiClient(URI.create("http://127.0.0.1:" + server.port()));
        ApiException e = throwsType(ApiException.class, () -> client.call(Operation.PRODUCT_SAVE, "test", Map.of()));
        equal(409, e.status()); equal("PRICE_CONFIRMATION_STALE", e.code());
      }
    });
    tests.test("Migration contains unique reversal relation and leaves legacy snapshot nullable (STATIC only)", () -> {
      String s = source("database/migration/014_controlled_operations.sql");
      check(s.contains("OriginalMovementId bigint NOT NULL UNIQUE"), "Missing uniqueness");
      check(s.contains("PreviousCostPrice decimal(19,2) NULL"), "Legacy snapshots fabricated");
      check(s.contains("RECEIVE_REVERSAL") && s.contains("'REVERSED'"), "Missing constraints");
      check(s.contains("Version=5") && s.contains("CREATE OR ALTER PROCEDURE dm.ControlledDelete"), "Missing v5");
    });
    tests.test("Delete SQL retains business history, checks non-FK promotions and revision (STATIC only)", () -> {
      String s = source("database/migration/014_controlled_operations.sql");
      check(s.contains("PromotionCode=@Code") && s.contains("sys.foreign_keys"), "Missing dependency protection");
      check(s.contains("@Current<>@Revision") && s.contains("HARD_DELETE"), "Missing version or audit");
      check(!s.contains("DELETE FROM dm.AuditLog") && !s.contains("DELETE FROM dm.StockMovements"), "History deletion");
      check(!s.toUpperCase().contains("DELETE CASCADE"), "Cascade is forbidden");
    });
    tests.test("Runtime grants only controlled deletion, not schema deletion (STATIC only)", () -> {
      String s = source("database/security/015_controlled_operations.sql");
      check(s.contains("GRANT EXECUTE ON OBJECT::dm.ControlledDelete"), "Procedure not granted");
      check(!s.toUpperCase().contains("GRANT DELETE ON SCHEMA"), "Broad deletion grant");
      check(s.contains("DENY INSERT, UPDATE, DELETE ON OBJECT::dm.DeletionRequests"), "Receipt mutation not denied");
    });
    tests.finish("Controlled operations / OFFLINE - SQL semantics not verified");
  }
}
