package vn.dienmay.common.model;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Extracted from InventoryService.Request without changing its fingerprint algorithm. */
public record InventoryRequest(
    UUID id, long productId, int quantity, List<String> serials, String reference,
    Long vendorId, BigDecimal unitCost, String from, String to) {

  public InventoryRequest {
    Objects.requireNonNull(id, "id");
    if (productId <= 0 || quantity <= 0 || quantity > 100000) {
      throw new IllegalArgumentException("San pham/so luong (1-100000) khong hop le.");
    }
    serials = serials.stream().map(Validation::serial).sorted().toList();
    if (new HashSet<>(serials).size() != serials.size()) {
      throw new IllegalArgumentException("Serial bi trung.");
    }
    reference = Validation.required(reference, 200, "So chung tu/ly do");
    unitCost = Money.exact(unitCost);
    if (!Set.of("KHO", "QUAY").contains(to)) {
      throw new IllegalArgumentException("Vi tri dich khong hop le.");
    }
    if (from != null && (!Set.of("KHO", "QUAY").contains(from) || from.equals(to))) {
      throw new IllegalArgumentException("Nguon va dich phai khac nhau.");
    }
  }

  public String fingerprint() {
    StringBuilder b = new StringBuilder();
    for (String value : List.of("" + productId, "" + quantity, reference, "" + vendorId,
        unitCost.toPlainString(), "" + from, to)) {
      SaleRequest.token(b, value);
    }
    serials.forEach(serial -> SaleRequest.token(b, serial));
    return SaleRequest.digest(b.toString());
  }
}
