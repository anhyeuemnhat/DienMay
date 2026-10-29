package vn.dienmay.common.model;

import java.time.LocalDate;
import java.util.Set;

/** Existing warranty dates are inclusive; only forward service-state transitions are legal. */
public final class WarrantyRules {
  private WarrantyRules() {}

  public static LocalDate end(LocalDate start, int months) {
    if (months < 1 || months > 120) {
      throw new IllegalArgumentException("Thoi han bao hanh phai tu 1 den 120 thang.");
    }
    return start.plusMonths(months);
  }

  public static boolean active(LocalDate start, LocalDate end, LocalDate today) {
    return !today.isBefore(start) && !today.isAfter(end);
  }

  public static boolean canTransition(String old, String next) {
    return (old.equals("RECEIVED") && Set.of("PROCESSING", "REJECTED").contains(next))
        || (old.equals("PROCESSING") && Set.of("COMPLETED", "REJECTED").contains(next));
  }

  public static String label(String status) {
    return switch (status) {
      case "RECEIVED" -> "Đã tiếp nhận";
      case "PROCESSING" -> "Đang xử lý";
      case "COMPLETED" -> "Hoàn tất";
      case "REJECTED" -> "Từ chối";
      default -> status;
    };
  }
}
