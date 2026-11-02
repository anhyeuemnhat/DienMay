package vn.dienmay.common.model;

import java.util.Objects;
import java.util.UUID;

/** Reverses one posted receipt, never edits or deletes that original document. */
public record ReversalRequest(UUID id, long movementId, int productRevision, String reason) implements GuardedRequest {
  public ReversalRequest {
    Objects.requireNonNull(id, "id");
    if (movementId <= 0 || productRevision <= 0) { throw new IllegalArgumentException("Invalid receipt/revision."); }
    reason = Validation.required(reason, 1000, "Lý do hủy/đảo phiếu");
  }
  public String fingerprint() {
    StringBuilder data = new StringBuilder();
    for (String value : java.util.List.of(Long.toString(movementId), Integer.toString(productRevision), reason)) {
      SaleRequest.token(data, value);
    }
    return SaleRequest.digest(data.toString());
  }
}
