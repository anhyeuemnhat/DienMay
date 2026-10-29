package vn.dienmay.tests;

import static vn.dienmay.tests.Fixtures.line;
import static vn.dienmay.tests.Fixtures.request;
import static vn.dienmay.tests.TestKit.check;
import static vn.dienmay.tests.TestKit.equal;
import static vn.dienmay.tests.TestKit.throwsType;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import vn.dienmay.client.api.ApiGateway;
import vn.dienmay.client.api.HttpApiClient;
import vn.dienmay.client.controller.SaleController;
import vn.dienmay.client.controller.StockController;
import vn.dienmay.common.api.ApiException;
import vn.dienmay.common.api.ApiLimits;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.api.Operation;
import vn.dienmay.common.model.InventoryRequest;
import vn.dienmay.common.model.Item;
import vn.dienmay.common.model.Role;
import vn.dienmay.common.model.Sale;
import vn.dienmay.common.model.SaleRequest;
import vn.dienmay.server.api.ApiServer;

/** Real HTTP transport with explicit test handlers; controller durability with explicit test doubles. */
public final class ApiTests {
  private ApiTests() {}

  private static final class Gateway implements ApiGateway {
    Object result;
    Exception failure;
    Operation last;
    int calls;
    Map<String, Object> parameters;

    @Override
    public Object call(Operation operation, String bearer, Map<String, Object> parameters)
        throws Exception {
      last = operation;
      calls++;
      this.parameters = parameters;
      if (failure != null) {
        throw failure;
      }
      return result;
    }

    @Override
    public String profile() {
      return "test-fixture-only";
    }
  }

  private static HttpResponse<String> raw(URI base, String path, String method,
      String contentType, String bearer, String body) throws Exception {
    HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path))
        .timeout(Duration.ofSeconds(8)).method(method, HttpRequest.BodyPublishers.ofString(body));
    if (contentType != null) {
      request.header("Content-Type", contentType);
    }
    if (bearer != null) {
      request.header("Authorization", bearer);
    }
    return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  public static void main(String[] args) throws Exception {
    TestKit t = new TestKit();
    t.test("JSON / item round trip", () -> equal(Fixtures.item(1, true, 4),
        Json.convert(Json.parse(Json.stringify(Fixtures.item(1, true, 4))), Item.class)));
    t.test("JSON / sale request exact decimal UUID serial round trip", () -> {
      SaleRequest value = request(UUID.randomUUID(), line(1, 1, "001"));
      equal(value, Json.convert(Json.parse(Json.stringify(value)), SaleRequest.class));
    });
    t.test("JSON / inventory request round trip", () -> {
      InventoryRequest value = Fixtures.stock();
      equal(value, Json.convert(Json.parse(Json.stringify(value)), InventoryRequest.class));
    });
    t.test("JSON / persisted invoice nested record round trip", () -> equal(Fixtures.sale(),
        Json.convert(Json.parse(Json.stringify(Fixtures.sale())), Sale.class)));
    t.test("JSON / Unicode and control escaping", () -> {
      String value = "\u0110i\u1ec7n m\u00e1y \uD83D\uDE00\n\t\"\\";
      equal(value, Json.parse(Json.stringify(value)));
    });
    t.test("JSON / nullable table cells", () -> {
      Map<String, Object> object = Json.object(Json.parse(Json.stringify(Data.map("a", null, "b", 7))));
      equal(null, object.get("a"));
      equal(new BigDecimal("7"), object.get("b"));
    });
    t.test("JSON / duplicate property rejected", () -> throwsType(IllegalArgumentException.class, () ->
        Json.parse("{\"a\":1,\"a\":2}")));
    t.test("JSON / trailing data rejected", () -> throwsType(IllegalArgumentException.class, () ->
        Json.parse("{} true")));
    t.test("JSON / trailing comma rejected", () -> throwsType(IllegalArgumentException.class, () ->
        Json.parse("[1,]")));
    t.test("JSON / leading zero number rejected", () -> throwsType(IllegalArgumentException.class, () ->
        Json.parse("01")));
    t.test("JSON / nonfinite input rejected", () -> throwsType(IllegalArgumentException.class, () ->
        Json.parse("NaN")));
    t.test("JSON / excessive exponent rejected", () -> throwsType(IllegalArgumentException.class, () ->
        Json.parse("1e999999")));
    t.test("JSON / excessive nesting rejected", () -> throwsType(IllegalArgumentException.class, () ->
        Json.parse("[".repeat(60) + "0" + "]".repeat(60))));
    t.test("JSON / raw control character rejected", () -> throwsType(IllegalArgumentException.class, () ->
        Json.parse("\"bad\ntext\"")));
    t.test("JSON / unpaired surrogate rejected", () -> throwsType(IllegalArgumentException.class, () ->
        Json.parse("\"\\ud800\"")));
    t.test("JSON / fractional ID rejected", () -> throwsType(ArithmeticException.class, () -> Json.convert(new
        BigDecimal("1.2"), Long.class)));
    t.test("JSON / missing record fields rejected", () -> throwsType(IllegalArgumentException.class, () ->
        Json.convert(Data.map("id", 1), Item.class)));
    t.test("JSON / extra record fields rejected", () -> {
      Map<String, Object> map = Json.object(Json.parse(Json.stringify(Fixtures.item(1, false, 2))));
      map.put("admin", true);
      throwsType(IllegalArgumentException.class, () -> Json.convert(map, Item.class));
    });
    t.test("JSON / arbitrary Java object cannot serialize", () -> throwsType(IllegalArgumentException.class, () ->
        Json.stringify(new Object())));
    t.test("API / unique explicit paths round trip", () -> {
      HashSet<String> paths = new HashSet<>();
      for (Operation operation : Operation.values()) {
        check(paths.add(operation.path()), "Duplicate endpoint");
        equal(operation, Operation.fromPath(operation.path()));
      }
    });
    t.test("API / role matrix critical denials", () -> {
      check(!Operation.PRODUCT_SAVE.allows(Role.CASHIER), "Cashier edits products");
      check(!Operation.REPORT.allows(Role.WAREHOUSE), "Warehouse sees revenue");
      check(!Operation.CHECKOUT.allows(Role.WAREHOUSE), "Warehouse sells");
      check(!Operation.RECEIVE.allows(Role.CASHIER), "Cashier receives stock");
      check(!Operation.EMPLOYEE_SAVE.allows(Role.WAREHOUSE), "Warehouse edits permissions");
      check(Operation.SALE_CANCEL.allows(Role.CASHIER), "Cashier cannot reconcile");
      check(Operation.STOCK_CANCEL.allows(Role.WAREHOUSE), "Warehouse cannot reconcile");
    });
    t.test("client / external cleartext URL refused", () -> throwsType(IllegalArgumentException.class, () -> new
        HttpApiClient(URI.create("http://192.0.2.1:8080"))));
    t.test("client / userinfo and path refused", () -> {
      throwsType(IllegalArgumentException.class, () -> new
          HttpApiClient(URI.create("https://user:secret@example.test")));
      throwsType(IllegalArgumentException.class, () -> new HttpApiClient(URI.create("https://example.test/not-root")));
    });
    t.test("server / non-loopback cleartext refused", () -> throwsType(IllegalArgumentException.class, () -> new
        ApiServer(new InetSocketAddress("0.0.0.0", 0), null, (op, token, p) -> null)));
    t.test("controller / unknown sale result persists through restart and resolve", () -> {
      Gateway gateway = new Gateway();
      gateway.failure = new IOException("response lost after potential commit");
      Path directory = Files.createTempDirectory("dm-api-pending-");
      SaleRequest value = request(UUID.randomUUID(), line(1, 1));
      try (SaleController controller = new SaleController(gateway, directory, 1)) {
        throwsType(IOException.class, () -> controller.submit("token", value));
        equal(value, controller.pending());
        equal(Operation.CHECKOUT, gateway.last);
      }
      gateway.failure = null;
      try (SaleController controller = new SaleController(gateway, directory, 1)) {
        equal(value, controller.pending());
        equal(null, controller.resolve("token"));
        equal(value, controller.pending());
        gateway.result = Json.parse(Json.stringify(Fixtures.sale()));
        equal(Fixtures.sale(), controller.resolve("token"));
        equal(null, controller.pending());
      }
    });
    t.test("controller / retry reuses exact UUID and content", () -> {
      Gateway gateway = new Gateway();
      gateway.failure = new IOException("lost");
      SaleRequest value = request(UUID.randomUUID(), line(1, 1));
      try (SaleController controller = new SaleController(gateway, Files.createTempDirectory("dm-retry-"), 1)) {
        throwsType(IOException.class, () -> controller.submit("t", value));
        gateway.failure = null;
        gateway.result = Json.parse(Json.stringify(Fixtures.sale()));
        controller.retry("t");
        equal(value, gateway.parameters.get("request"));
        equal(2, gateway.calls);
        equal(null, controller.pending());
      }
    });
    t.test("controller / malformed successful sale response retains pending", () -> {
      Gateway gateway = new Gateway();
      try (SaleController controller = new SaleController(gateway, Files.createTempDirectory("dm-null-"), 1)) {
        throwsType(IOException.class, () -> controller.submit("t", request(UUID.randomUUID(), line(1, 1))));
        check(controller.pending() != null, "Pending was lost");
      }
    });
    t.test("controller / cannot replace pending with new checkout", () -> {
      Gateway gateway = new Gateway();
      gateway.failure = new IOException("lost");
      try (SaleController controller = new SaleController(gateway, Files.createTempDirectory("dm-replace-"), 1)) {
        throwsType(IOException.class, () -> controller.submit("t", request(UUID.randomUUID(), line(1, 1))));
        throwsType(IllegalStateException.class, () -> controller.submit("t", request(UUID.randomUUID(), line(2, 1))));
        equal(1, gateway.calls);
      }
    });
    t.test("controller / cancellation false retains; true clears", () -> {
      Gateway gateway = new Gateway();
      gateway.failure = new IOException("lost");
      try (SaleController controller = new SaleController(gateway, Files.createTempDirectory("dm-cancel-"), 1)) {
        throwsType(IOException.class, () -> controller.submit("t", request(UUID.randomUUID(), line(1, 1))));
        gateway.failure = null;
        gateway.result = false;
        equal(false, controller.cancel("t"));
        check(controller.pending() != null, "False cancellation cleared request");
        gateway.result = true;
        equal(true, controller.cancel("t"));
        equal(Operation.SALE_CANCEL, gateway.last);
        equal(null, controller.pending());
      }
    });
    t.test("controller / stock lost or invalid reply retained until resolved", () -> {
      Gateway gateway = new Gateway();
      gateway.failure = new IOException("lost");
      try (StockController controller = new StockController(gateway, Files.createTempDirectory("dm-stock-api-"), 1,
          true)) {
        throwsType(IOException.class, () -> controller.submit("t", Fixtures.stock()));
        InventoryRequest saved = controller.pending();
        gateway.failure = null;
        gateway.result = "invalid success envelope data";
        throwsType(IOException.class, () -> controller.retry("t"));
        equal(saved, controller.pending());
        gateway.result = false;
        equal(false, controller.resolve("t"));
        check(controller.pending() != null, "Negative resolve discarded pending");
        gateway.result = true;
        equal(true, controller.resolve("t"));
        equal(null, controller.pending());
      }
    });
    t.test("controller / stock wrong operation rejected before network", () -> {
      Gateway gateway = new Gateway();
      try (StockController controller = new StockController(gateway, Files.createTempDirectory("dm-wrong-kind-"), 1,
          false)) {
        throwsType(IllegalArgumentException.class, () -> controller.submit("t", Fixtures.stock()));
        equal(0, gateway.calls);
        equal(null, controller.pending());
      }
    });
    t.test("API / oversized draft rejected before persistence", () -> {
      List<String> serials = java.util.stream.IntStream.range(0, 10000)
          .mapToObj(i -> "X".repeat(120) + i).toList();
      InventoryRequest value = new InventoryRequest(UUID.randomUUID(), 1, 10000, serials, "Test", 1L,
          vn.dienmay.common.model.Money.ZERO, null, "KHO");
      Gateway gateway = new Gateway();
      try (StockController controller = new StockController(gateway, Files.createTempDirectory("dm-size-"), 1, true)) {
        throwsType(IllegalArgumentException.class, () -> controller.submit("t", value));
        equal(null, controller.pending());
        equal(0, gateway.calls);
      }
    });
    AtomicInteger calls = new AtomicInteger();
    try (ApiServer server = new ApiServer(new InetSocketAddress("127.0.0.1", 0), null, (op, token, p) -> {
      calls.incrementAndGet();
      if (op == Operation.REPORT) {
        throw new SQLException("SENSITIVE SQL MUST NOT LEAK");
      }
      if (op == Operation.EMPLOYEES) {
        throw new SecurityException("Denied");
      }
      return op == Operation.PRODUCT_GET ? Fixtures.item(1, true, 2) : Data.map("echo", p, "token", token);
    })) {
      server.start();
      URI base = URI.create("http://127.0.0.1:" + server.port());
      HttpApiClient client = new HttpApiClient(base);
      t.test("HTTP / actual client-server DTO exchange", () -> equal(Fixtures.item(1, true, 2),
          Json.convert(client.call(Operation.PRODUCT_GET, "opaque-test-token", Data.map("id", 1)), Item.class)));
      t.test("HTTP / null input fields round trip", () -> {
        Map<String, Object> response = Json.object(client.call(Operation.CUSTOMER_SAVE, "opaque-test-token",
            Data.map("id", null, "name", "Unicode \u0110i\u1ec7n")));
        equal("opaque-test-token", response.get("token"));
        equal(null, Json.object(response.get("echo")).get("id"));
      });
      t.test("HTTP / missing bearer blocked before handler", () -> {
        int before = calls.get();
        equal(401, raw(base, Operation.PRODUCT_SEARCH.path(), "POST", "application/json", null, "{}").statusCode());
        equal(before, calls.get());
      });
      t.test("HTTP / GET not allowed for command", () -> equal(405, raw(base, Operation.PRODUCT_GET.path(), "GET",
          "application/json", "Bearer t", "").statusCode()));
      t.test("HTTP / unknown API path", () -> equal(404, raw(base, "/api/v1/reflection/invoke", "POST",
          "application/json", "Bearer t", "{}").statusCode()));
      t.test("HTTP / wrong content type", () -> equal(415, raw(base, Operation.LOGIN.path(), "POST", "text/plain", null,
          "{}").statusCode()));
      t.test("HTTP / duplicate JSON rejected", () -> equal(400, raw(base, Operation.LOGIN.path(), "POST",
          "application/json", null, "{\"x\":1,\"x\":2}").statusCode()));
      t.test("HTTP / malformed JSON rejected", () -> equal(400, raw(base, Operation.LOGIN.path(), "POST",
          "application/json", null, "{invalid}").statusCode()));
      t.test("HTTP / oversized body rejected", () -> equal(413, raw(base, Operation.PRODUCT_SEARCH.path(), "POST",
          "application/json", "Bearer t", "x".repeat(ApiLimits.REQUEST_BYTES + 1)).statusCode()));
      t.test("HTTP / SQL details sanitized and correlation retained", () -> {
        ApiException error = throwsType(ApiException.class, () -> client.call(Operation.REPORT, "t", Data.map()));
        equal(503, error.status());
        check(!error.getMessage().contains("SENSITIVE"), "SQL text leaked");
        check(!error.correlationId().isEmpty(), "No correlation ID");
      });
      t.test("HTTP / security exception is forbidden", () -> equal(403, throwsType(ApiException.class, () ->
          client.call(Operation.EMPLOYEES, "t", Data.map())).status()));
      t.test("HTTP / health is liveness not SQL readiness", () -> {
        HttpResponse<String> result = raw(base, "/health", "GET", null, null, "");
        equal(200, result.statusCode());
        equal(false, Json.object(Json.parse(result.body())).get("databaseChecked"));
      });
      t.test("HTTP / login rate limit enforced", () -> {
        int status = 0;
        for (int i = 0; i < 31; i++) {
          status = raw(base, Operation.LOGIN.path(), "POST", "application/json", null, "{}").statusCode();
        }
        equal(429, status);
      });
    }
    t.test("HTTP / full response-body timeout cancels stalled server", () -> {
      HttpServer slow = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      slow.createContext("/", exchange -> {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, 0);
        exchange.getResponseBody().write('{');
        exchange.getResponseBody().flush();
        try {
          Thread.sleep(800);
        } catch (InterruptedException error) {
          Thread.currentThread().interrupt();
        } finally {
          exchange.close();
        }
      });
      slow.start();
      try {
        HttpApiClient client = new HttpApiClient(URI.create("http://127.0.0.1:" + slow.getAddress().getPort()),
            Duration.ofMillis(200));
        long start = System.nanoTime();
        throwsType(java.net.http.HttpTimeoutException.class, () -> client.call(Operation.LOGIN, "", Data.map()));
        check(Duration.ofNanos(System.nanoTime() - start).toMillis() < 1500, "Body timeout failed");
      } finally {
        slow.stop(0);
      }
    });
    t.test("HTTP / oversized response cancelled while streaming", () -> {
      HttpServer huge = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      huge.createContext("/", exchange -> {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, 0);
        byte[] chunk = "x".repeat(65536).getBytes(StandardCharsets.UTF_8);
        try {
          for (int i = 0; i < 140; i++) {
            exchange.getResponseBody().write(chunk);
          }
        } catch (IOException expectedCancellation) {
          // The client deliberately aborts the oversized stream.
        } finally {
          exchange.close();
        }
      });
      huge.start();
      try {
        HttpApiClient client = new HttpApiClient(URI.create("http://127.0.0.1:" + huge.getAddress().getPort()));
        throwsType(IOException.class, () -> client.call(Operation.LOGIN, "", Data.map()));
      } finally {
        huge.stop(0);
      }
    });
    t.finish("api-json-durability");
    System.out.println("SCOPE | Real HTTP; explicit handler/JDBC test doubles. NOT a SQL Server integration result.");
  }
}
