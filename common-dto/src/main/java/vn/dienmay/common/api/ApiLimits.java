package vn.dienmay.common.api;

import java.nio.charset.StandardCharsets;

/** Shared transport limits; reject oversize drafts before creating durable pending transactions. */
public final class ApiLimits {
  public static final int REQUEST_BYTES = 1024 * 1024;
  public static final int RESPONSE_BYTES = 8 * 1024 * 1024;

  private ApiLimits() {}

  public static String encodeRequest(Object payload) {
    String json = Json.stringify(payload);
    if (json.getBytes(StandardCharsets.UTF_8).length > REQUEST_BYTES) {
      throw new IllegalArgumentException("Yeu cau vuot 1 MiB. Chia nho so luong/Serial truoc khi gui.");
    }
    return json;
  }
}
