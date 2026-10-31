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
    Long vendorId, BigDecimal unitCost, String from, String to, String identifierType) {

  /** Compatibility constructor: legacy serials are NOT silently interpreted as IMEI. */
  public InventoryRequest(UUID id, long productId, int quantity, List<String> serials, String reference,
      Long vendorId, BigDecimal unitCost, String from, String to) {
    this(id, productId, quantity, serials, reference, vendorId, unitCost, from, to, "SERIAL");
  }

  public InventoryRequest {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(serials, "serials");
    if (!Set.of("SERIAL", "IMEI").contains(identifierType)) {
      throw new IllegalArgumentException("Chon loai dinh danh SERIAL hoac IMEI.");
    }
    if (productId <= 0 || quantity <= 0 || quantity > 100000) {
      throw new IllegalArgumentException("San pham/so luong (1-100000) khong hop le.");
    }
    serials = serials.stream().map(Validation::serial).sorted().toList();
    if (new HashSet<>(serials).size() != serials.size()) {
      throw new IllegalArgumentException("Serial bi trung.");
    }
    if (identifierType.equals("IMEI") && serials.stream().anyMatch(code -> !CatalogRules.validImei(code))) {
      throw new IllegalArgumentException("IMEI can dung 15 chu so va chu so kiem tra. Kiem tra lai nhan thiet bi.");
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
    if (!identifierType.equals("SERIAL")) { SaleRequest.token(b, "identifierType=" + identifierType); }
    return SaleRequest.digest(b.toString());
  }
}
