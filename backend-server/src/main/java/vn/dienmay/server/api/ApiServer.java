package vn.dienmay.server.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import vn.dienmay.common.api.ApiException;
import vn.dienmay.common.api.ApiLimits;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.api.Operation;

/** Bounded HTTP transport; clear-text is accepted only on a loopback listening address. */
public final class ApiServer implements AutoCloseable {
  private static final int MAX_BODY = ApiLimits.REQUEST_BYTES;
  private final HttpServer server;
  private final ApiHandler handler;
  private final ThreadPoolExecutor workers;
  private final Map<String, long[]> loginWindows = new HashMap<>();

  public ApiServer(InetSocketAddress address, SSLContext tls, ApiHandler handler) throws IOException {
    if (address.isUnresolved()) {
      throw new IllegalArgumentException("Unresolved backend bind address");
    }
    if (tls == null && !address.getAddress().isLoopbackAddress()) {
      throw new IllegalArgumentException("TLS is required outside loopback");
    }
    this.handler = handler;
    if (tls == null) {
      server = HttpServer.create(address, 64);
    } else {
      HttpsServer https = HttpsServer.create(address, 64);
      https.setHttpsConfigurator(new HttpsConfigurator(tls));
      server = https;
    }
    workers = new ThreadPoolExecutor(4, 8, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64),
        new ThreadPoolExecutor.AbortPolicy());
    server.setExecutor(workers);
    server.createContext("/", this::handle);
  }

  public void start() {
    server.start();
  }

  public int port() {
    return server.getAddress().getPort();
  }

  private synchronized boolean allowLogin(String peer) {
    long now = System.currentTimeMillis();
    loginWindows.entrySet().removeIf(entry -> now - entry.getValue()[0] > 60_000);
    if (loginWindows.size() >= 1024 && !loginWindows.containsKey(peer)) {
      return false;
    }
    long[] window = loginWindows.computeIfAbsent(peer, key -> new long[] {now, 0});
    return ++window[1] <= 30;
  }

  private void handle(HttpExchange exchange) throws IOException {
    String correlation = UUID.randomUUID().toString();
    try {
      String path = exchange.getRequestURI().getRawPath();
      if ("/health".equals(path) && "GET".equals(exchange.getRequestMethod())) {
        respond(exchange, 200, Data.map("status", "UP", "databaseChecked", false), correlation);
        return;
      }
      Operation operation;
      try {
        operation = Operation.fromPath(path);
      } catch (IllegalArgumentException ex) {
        throw new ApiException(404, "NOT_FOUND", "Khong tim thay API.", correlation);
      }
      if (!"POST".equals(exchange.getRequestMethod())) {
        exchange.getResponseHeaders().set("Allow", "POST");
        throw new ApiException(405, "METHOD_NOT_ALLOWED", "API yeu cau POST.", correlation);
      }
      String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
      if (contentType == null
          || !contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim().equals("application/json")) {
        throw new ApiException(415, "CONTENT_TYPE", "Can Content-Type application/json.", correlation);
      }
      if (operation == Operation.LOGIN
          && !allowLogin(exchange.getRemoteAddress().getAddress().getHostAddress())) {
        exchange.getResponseHeaders().set("Retry-After", "60");
        throw new ApiException(429, "RATE_LIMIT", "Qua nhieu lan dang nhap. Thu lai sau.", correlation);
      }
      String bearer = exchange.getRequestHeaders().getFirst("Authorization");
      if (operation != Operation.LOGIN && (bearer == null || !bearer.startsWith("Bearer ")
          || bearer.length() > 256)) {
        throw new ApiException(401, "UNAUTHENTICATED", "Can dang nhap.", correlation);
      }
      byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
      if (bytes.length > MAX_BODY) {
        throw new ApiException(413, "TOO_LARGE", "Yeu cau vuot gioi han 1 MiB.", correlation);
      }
      String body = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
      Map<String, Object> input = Json.object(Json.parse(body));
      Object result = handler.execute(operation, bearer == null ? "" : bearer.substring(7), input);
      respond(exchange, 200, Data.map("data", result), correlation);
    } catch (ApiException ex) {
      failure(exchange, ex.status(), ex.code(), ex.getMessage(), correlation);
    } catch (vn.dienmay.common.api.BusinessException ex) {
      failure(exchange, ex.status(), ex.code(), safe(ex.getMessage()), correlation);
    } catch (CharacterCodingException | IllegalArgumentException | DateTimeException ex) {
      failure(exchange, 400, "VALIDATION", "Du lieu khong hop le: " + safe(ex.getMessage()), correlation);
    } catch (SecurityException ex) {
      failure(exchange, 403, "FORBIDDEN", safe(ex.getMessage()), correlation);
    } catch (SQLException ex) {
      boolean conflict = ex.getErrorCode() == 2601 || ex.getErrorCode() == 2627
          || (ex.getSQLState() != null && ex.getSQLState().startsWith("23"));
      log(correlation, ex);
      failure(exchange, conflict ? 409 : 503, conflict ? "CONFLICT" : "DATABASE_UNAVAILABLE",
          conflict ? "Du lieu trung hoac vi pham rang buoc. Tai lai va kiem tra."
              : "Khong xac nhan duoc ket qua co so du lieu. Giu yeu cau va doi soat truoc khi thu lai.",
          correlation);
    } catch (Exception ex) {
      log(correlation, ex);
      failure(exchange, 500, "INTERNAL", "Khong the xu ly. Lien he quan tri voi ma loi.", correlation);
    } finally {
      exchange.close();
    }
  }

  private static String safe(String message) {
    if (message == null) {
      return "Du lieu khong hop le.";
    }
    return message.length() <= 400 ? message : message.substring(0, 400);
  }

  private static void log(String correlation, Exception exception) {
    // No input values, SqlDatabase text, bearer tokens, passwords or JDBC configuration in logs.
    System.err.println("request=" + correlation + " failure=" + exception.getClass().getSimpleName());
  }

  private static void failure(HttpExchange exchange, int status, String code, String message,
      String correlation) throws IOException {
    respond(exchange, status, Data.map("error", Data.map("code", code, "message", message,
        "correlationId", correlation)), correlation);
  }

  private static void respond(HttpExchange exchange, int status, Object payload, String correlation)
      throws IOException {
    byte[] bytes = Json.stringify(payload).getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
    exchange.getResponseHeaders().set("X-Request-Id", correlation);
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
  }

  @Override
  public void close() {
    server.stop(1);
    workers.shutdown();
    try {
      if (!workers.awaitTermination(30, TimeUnit.SECONDS)) {
        workers.shutdownNow();
      }
    } catch (InterruptedException ex) {
      workers.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
