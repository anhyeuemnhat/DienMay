package vn.dienmay.common.model;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** Shape checks are NOT proof of product ownership, authenticity or physical possession. */
public final class CatalogRules {
  public static final Set<String> BARCODE_TYPES = Set.of("NONE", "GTIN8", "UPC12", "EAN13", "GTIN14", "OTHER");
  public static final Set<String> STATUSES = Set.of("PENDING", "VERIFIED", "INVALID", "DEMO");
  private CatalogRules() { }

  public static String barcode(String raw, String type) {
    String value = Validation.optional(raw, 128, "Mã vạch");
    if (!BARCODE_TYPES.contains(type)) { throw new
        IllegalArgumentException("Chọn loại mã vạch hợp lệ."); }
    if (value.isBlank()) { return value; }
    if (type.equals("NONE")) {
      throw new IllegalArgumentException("Đã nhập mã vạch: hãy chọn loại mã. Mã nội bộ nhập ở SKU.");
    }
    int length = switch (type) { case "GTIN8" -> 8; case "UPC12" -> 12; case "EAN13" -> 13;
      case "GTIN14" -> 14; default -> 0; };
    if (length != 0 && (!value.matches("[0-9]{" + length + "}") || !validGtin(value))) {
      throw new IllegalArgumentException("Mã vạch chưa đúng độ dài / chữ số kiểm tra. Kiểm tra lại trê" +
          "n bao bì; đây không phải k" +
          "ết luận mã giả.");
    }
    if (value.codePoints().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("Mã vạch không được chứa ký tự điều khiển.");
    }
    return value;
  }

  public static boolean validGtin(String value) {
    if (value == null || !value.matches("(?:[0-9]{8}|[0-9]{12}|[0-9]{13}|[0-9]{14})")) { return false; }
    int sum = 0;
    int weight = 3;
    for (int index = value.length() - 2; index >= 0; index--) {
      sum += (value.charAt(index) - '0') * weight;
      weight = 4 - weight;
    }
    return (10 - sum % 10) % 10 == value.charAt(value.length() - 1) - '0';
  }

  public static boolean validImei(String value) {
    if (value == null || !value.matches("[0-9]{15}")) { return false; }
    int sum = 0;
    for (int i = 0; i < value.length(); i++) {
      int digit = value.charAt(i) - '0';
      if (i % 2 == 1) { digit *= 2; }
      sum += digit / 10 + digit % 10;
    }
    return sum % 10 == 0;
  }

  public static String phone(String value) {
    String phone = Validation.optional(value, 30, "Điện thoại");
    if (!phone.isEmpty() && (!phone.matches("\\+?[0-9 ()-]{7,30}")
        || phone.replaceAll("[^0-9]", "").length() < 7
        || phone.replaceAll("[^0-9]", "").length() > 15)) {
      throw new IllegalArgumentException("Điện thoại cần 7-15 chữ số; có thể dùng +, khoảng trắng, () " +
          "và -. Không rõ thì để trống.");
    }
    return phone;
  }

  public static String source(String value) {
    String source = Validation.required(value, 1000, "Nguồn đối chiếu");
    try {
      URI uri = URI.create(source);
      if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
          || uri.getUserInfo() != null || uri.getFragment() != null) { throw new IllegalArgumentException(); }
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Nguồn cần là đường dẫn HTTPS đến tài liệu chính hãng / nguồn" +
          " có thẩm quyền; không kèm " +
          "mật khẩu.");
    }
    return source; // The server stores evidence; it NEVER fetches arbitrary URLs (no SSRF).
  }

  public static String normalize(String text) {
    return text == null ? "" : text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }
}
