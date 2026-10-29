package vn.dienmay.common.model;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Stable request identity is retained when the network result is uncertain. */
public record SaleRequest(
    UUID requestId, Long customerId, String payment, BigDecimal manualDiscount,
    String promotionCode, BigDecimal expectedDiscount, List<ItemQuantity> lines) {

  public SaleRequest {
    Objects.requireNonNull(requestId, "requestId");
    lines = List.copyOf(lines);
    if (!Set.of("CASH", "TRANSFER").contains(payment)) {
      throw new IllegalArgumentException("Phuong thuc thanh toan khong hop le.");
    }
    if (customerId != null && customerId <= 0) {
      throw new IllegalArgumentException("Ma khach hang khong hop le.");
    }
    manualDiscount = Money.exact(manualDiscount);
    expectedDiscount = Money.exact(expectedDiscount);
    promotionCode = Validation.optional(promotionCode, 50, "Ma khuyen mai")
        .toUpperCase(Locale.ROOT);
    if (!promotionCode.isEmpty() && manualDiscount.signum() != 0) {
      throw new IllegalArgumentException("Khong cong don giam gia truc tiep va khuyen mai.");
    }
    if (lines.isEmpty() || lines.size() > 100) {
      throw new IllegalArgumentException("Gio phai co tu 1 den 100 dong.");
    }
    if (lines.stream().map(ItemQuantity::productId).distinct().count() != lines.size()) {
      throw new IllegalArgumentException("Trung dong san pham.");
    }
    Money.total(subtotal(lines), expectedDiscount);
  }

  public static BigDecimal subtotal(List<ItemQuantity> lines) {
    return Money.exact(lines.stream().map(ItemQuantity::gross).reduce(Money.ZERO, BigDecimal::add));
  }

  /** Length-prefix encoding retains baseline request fingerprints across releases. */
  public String fingerprint() {
    StringBuilder b = new StringBuilder();
    token(b, String.valueOf(customerId));
    token(b, payment);
    token(b, manualDiscount.toPlainString());
    token(b, promotionCode);
    token(b, expectedDiscount.toPlainString());
    lines.stream().sorted(Comparator.comparingLong(ItemQuantity::productId)).forEach(line -> {
      token(b, "" + line.productId());
      token(b, "" + line.quantity());
      token(b, line.expectedPrice().toPlainString());
      line.serials().stream().sorted().forEach(serial -> token(b, serial));
      token(b, "END");
    });
    return digest(b.toString());
  }

  public static void token(StringBuilder builder, String value) {
    builder.append(value.length()).append(':').append(value);
  }

  public static String digest(String text) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by Java", e);
    }
  }
}
