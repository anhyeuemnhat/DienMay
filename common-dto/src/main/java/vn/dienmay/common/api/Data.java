package vn.dienmay.common.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed request-field extraction and null-friendly object construction. */
public final class Data {
  private Data() {}

  public static Map<String, Object> map(Object... entries) {
    if (entries.length % 2 != 0) {
      throw new IllegalArgumentException("Key/value pairs required");
    }
    Map<String, Object> result = new LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2) {
      result.put((String) entries[i], entries[i + 1]);
    }
    return result;
  }

  public static String text(Map<String, Object> data, String key) {
    Object value = data.get(key);
    if (!(value instanceof String text) || text.length() > 10000) {
      throw new IllegalArgumentException("Missing or invalid field: " + key);
    }
    return text;
  }

  public static long id(Map<String, Object> data, String key) {
    long value = Json.number(data.get(key)).longValueExact();
    if (value <= 0) {
      throw new IllegalArgumentException("ID must be positive: " + key);
    }
    return value;
  }

  public static Long optionalId(Map<String, Object> data, String key) {
    return data.get(key) == null ? null : id(data, key);
  }

  public static BigDecimal money(Map<String, Object> data, String key) {
    return Json.number(data.get(key));
  }

  public static boolean flag(Map<String, Object> data, String key) {
    if (!(data.get(key) instanceof Boolean flag)) {
      throw new IllegalArgumentException("Missing boolean field: " + key);
    }
    return flag;
  }

  public static LocalDate date(Map<String, Object> data, String key) {
    return LocalDate.parse(text(data, key));
  }
}
