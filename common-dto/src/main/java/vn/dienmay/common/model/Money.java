package vn.dienmay.common.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** Exact DECIMAL(19,2) amounts. No binary floating point is used for money. */
public final class Money {
  public static final BigDecimal ZERO = new BigDecimal("0.00");
  private static final BigDecimal MAX = new BigDecimal("99999999999999999.99");
  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private Money() {}

  public static BigDecimal of(String value) {
    if (value == null) {
      throw new IllegalArgumentException("So tien khong duoc de trong.");
    }
    try {
      return exact(new BigDecimal(value.trim()));
    } catch (NumberFormatException | ArithmeticException e) {
      throw new IllegalArgumentException("Nhap so tien khong phan cach, toi da 2 so le.", e);
    }
  }

  public static BigDecimal exact(BigDecimal value) {
    if (value == null || value.signum() < 0 || value.compareTo(MAX) > 0) {
      throw new IllegalArgumentException("So tien phai trong gioi han DECIMAL(19,2) va khong am.");
    }
    try {
      return value.setScale(2, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException("So tien chi duoc co toi da 2 so le.", e);
    }
  }

  public static BigDecimal discount(BigDecimal subtotal, BigDecimal percent) {
    subtotal = exact(subtotal);
    if (percent == null || percent.signum() < 0 || percent.compareTo(HUNDRED) > 0) {
      throw new IllegalArgumentException("Giam gia phai tu 0 den 100%.");
    }
    return exact(subtotal.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP));
  }

  public static BigDecimal total(BigDecimal subtotal, BigDecimal discount) {
    subtotal = exact(subtotal);
    discount = exact(discount);
    if (discount.compareTo(subtotal) > 0) {
      throw new IllegalArgumentException("Giam gia khong duoc vuot tong tien.");
    }
    return subtotal.subtract(discount);
  }

  public static String format(BigDecimal value) {
    NumberFormat f = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
    f.setMaximumFractionDigits(2);
    f.setMinimumFractionDigits(value.stripTrailingZeros().scale() > 0 ? 2 : 0);
    return f.format(value) + " ₫";
  }
}
