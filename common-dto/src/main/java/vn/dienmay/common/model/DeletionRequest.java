package vn.dienmay.common.model;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable deletion intent. Never accepts an SQL identifier or table name. */
public record DeletionRequest(UUID id, String type, long entityId, int revision, String reason) implements
    GuardedRequest {
  public static final Set<String> TYPES = Set.of("product", "customer", "vendor", "employee",
      "promotion", "category", "brand");
  public DeletionRequest {
    Objects.requireNonNull(id, "id");
    requireTarget(type, entityId, revision);
    reason = Validation.required(reason, 1000, "Lý do xóa");
  }
  public static void requireTarget(String type, long id, int revision) {
    if (type == null || !TYPES.contains(type) || id <= 0 || revision <= 0) {
      throw new IllegalArgumentException("Chọn đúng loại bản ghi và phiên bản hiện tại.");
    }
  }
  public String fingerprint() {
    StringBuilder data = new StringBuilder();
    for (String value : java.util.List.of(type, Long.toString(entityId), Integer.toString(revision), reason)) {
      SaleRequest.token(data, value);
    }
    return SaleRequest.digest(data.toString());
  }
}
