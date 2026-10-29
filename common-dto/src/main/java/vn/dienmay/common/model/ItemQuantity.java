package vn.dienmay.common.model;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Immutable requested line; expected price is compared against SQL Server at checkout. */
public record ItemQuantity(
    long productId, String sku, String name, BigDecimal expectedPrice,
    int quantity, List<String> serials) {

  public ItemQuantity {
    if (productId <= 0 || quantity <= 0 || quantity > 100000) {
      throw new IllegalArgumentException("San pham/so luong (1-100000) khong hop le.");
    }
    expectedPrice = Money.exact(expectedPrice);
    serials = Objects.requireNonNull(serials, "serials").stream()
        .map(Validation::serial).toList();
    if (new HashSet<>(serials).size() != serials.size()) {
      throw new IllegalArgumentException("Serial/IMEI bi trung.");
    }
  }

  public BigDecimal gross() {
    return Money.exact(expectedPrice.multiply(BigDecimal.valueOf(quantity)));
  }
}
