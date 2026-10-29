package vn.dienmay.client.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import vn.dienmay.common.api.ApiException;
import vn.dienmay.common.api.ApiLimits;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.api.Operation;

/** Explicit JSON transport. No automatic retries, redirects, certificate bypass or JDBC linkage. */
public final class HttpApiClient implements ApiGateway {
  private static final int MAX_RESPONSE = ApiLimits.RESPONSE_BYTES;
  private final Duration timeout;
  private final URI base;
  private final HttpClient http;

  public HttpApiClient(URI base) {
    this(base, Duration.ofSeconds(35));
  }

  /** Timeout injection enables deterministic tests; it covers the entire response body. */
  public HttpApiClient(URI base, Duration timeout) {
    if (timeout == null || timeout.isNegative() || timeout.isZero()) {
      throw new IllegalArgumentException("Timeout must be positive");
    }
    this.timeout = timeout;
    String host = base.getHost();
    boolean local = Set.of("127.0.0.1", "localhost", "[::1]", "::1").contains(host == null ? "" : host);
    if (!("https".equals(base.getScheme()) || ("http".equals(base.getScheme()) && local))
        || host == null || base.getUserInfo() != null || base.getQuery() != null
        || base.getFragment() != null || !(base.getPath().isEmpty() || base.getPath().equals("/"))) {
      throw new IllegalArgumentException("API URL must be HTTPS; HTTP is allowed only on loopback.");
    }
    this.base = base;
    http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).build();
  }

  @Override
  public Object call(Operation operation, String bearer, Map<String, Object> parameters)
      throws Exception {
    String payload = ApiLimits.encodeRequest(parameters);
    HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(operation.path()))
        .timeout(timeout).header("Content-Type", "application/json; charset=utf-8")
        .header("Accept", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
    if (operation != Operation.LOGIN) {
      if (bearer == null || bearer.isBlank()) {
        throw new ApiException(401, "UNAUTHENTICATED", "Can dang nhap.", "");
      }
      request.header("Authorization", "Bearer " + bearer);
    }
    LimitedBody subscriber = new LimitedBody();
    CompletableFuture<HttpResponse<byte[]>> future = http.sendAsync(request.build(), info -> subscriber);
    HttpResponse<byte[]> response;
    try {
      response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException ex) {
      subscriber.cancel();
      future.cancel(true);
      throw new java.net.http.HttpTimeoutException("API timeout; retain the request and reconcile.");
    } catch (InterruptedException ex) {
      subscriber.cancel();
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw ex;
    } catch (ExecutionException ex) {
      if (ex.getCause() instanceof Exception failure) {
        throw failure;
      }
      throw new IOException("API response failed", ex.getCause());
    }
    byte[] bytes = response.body();
    String contentType = response.headers().firstValue("Content-Type").orElse("");
    if (!contentType.toLowerCase(java.util.Locale.ROOT).startsWith("application/json")) {
      throw new ApiException(502, "INVALID_RESPONSE", "API khong tra ve JSON. Kiem tra dia chi.", "");
    }
    String body = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    Map<String, Object> decoded = Json.object(Json.parse(body));
    if (response.statusCode() != 200) {
      Map<String, Object> error = Json.object(decoded.get("error"));
      throw new ApiException(response.statusCode(), Data.text(error, "code"),
          Data.text(error, "message"), Data.text(error, "correlationId"));
    }
    if (!decoded.containsKey("data")) {
      throw new ApiException(502, "INVALID_RESPONSE", "API thieu du lieu phan hoi.", "");
    }
    return decoded.get("data");
  }

  /** Bounds memory before buffering each chunk and cancels stalled/cancelled subscriptions. */
  private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final CompletableFuture<byte[]> body = new CompletableFuture<>();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private Flow.Subscription subscription;
    private boolean cancelled;

    @Override
    public CompletionStage<byte[]> getBody() {
      return body;
    }

    @Override
    public synchronized void onSubscribe(Flow.Subscription next) {
      if (subscription != null || cancelled) {
        next.cancel();
        return;
      }
      subscription = next;
      next.request(1);
    }

    @Override
    public synchronized void onNext(List<ByteBuffer> buffers) {
      if (cancelled) {
        return;
      }
      for (ByteBuffer buffer : buffers) {
        if (buffer.remaining() > MAX_RESPONSE - bytes.size()) {
          cancel();
          body.completeExceptionally(new IOException("API response exceeds 8 MiB"));
          return;
        }
        byte[] chunk = new byte[buffer.remaining()];
        buffer.get(chunk);
        bytes.writeBytes(chunk);
      }
      subscription.request(1);
    }

    @Override
    public synchronized void onError(Throwable error) {
      body.completeExceptionally(error);
    }

    @Override
    public synchronized void onComplete() {
      if (!cancelled) {
        body.complete(bytes.toByteArray());
      }
    }

    synchronized void cancel() {
      cancelled = true;
      if (subscription != null) {
        subscription.cancel();
      }
    }
  }

  @Override
  public String profile() {
    return base.toString();
  }
}
