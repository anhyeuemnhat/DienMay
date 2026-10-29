package vn.dienmay.common.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Shared shape validation; the server always revalidates authorization and current data. */
public final class Validation {
  public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  private Validation() {}

  public static String required(String value, int max, String label) {
    String s = value == null ? "" : value.trim();
    if (s.isEmpty() || s.length() > max) {
      throw new IllegalArgumentException(label + " phai co 1 den " + max + " ky tu.");
    }
    return s;
  }

  public static String optional(String value, int max, String label) {
    String s = value == null ? "" : value.trim();
    if (s.length() > max) {
      throw new IllegalArgumentException(label + " qua dai (toi da " + max + ").");
    }
    return s;
  }

  public static String serial(String s) {
    return required(s, 128, "Serial/IMEI").toUpperCase(Locale.ROOT);
  }

  public static List<String> serials(String text) {
    return text == null || text.isBlank() ? List.of()
        : Arrays.stream(text.split("[,;\\s]+", -1))
            .filter(x -> !x.isBlank()).map(Validation::serial).toList();
  }

  public static Instant start(LocalDate date) {
    return date.atStartOfDay(ZONE).toInstant();
  }

  public static void dates(LocalDate from, LocalDate to) {
    if (from == null || to == null || from.isAfter(to)) {
      throw new IllegalArgumentException("Khoang ngay khong hop le.");
    }
  }
}
