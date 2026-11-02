package vn.dienmay.common.model;

import java.math.BigDecimal;

/** Explicit acknowledgement bound to exactly one product, revision and monetary tuple. */
public record PriceConfirmation(long productId, int revision, BigDecimal costPrice, BigDecimal sellingPrice) {
  public PriceConfirmation {
    if (productId < 0 || revision < 0) { throw new IllegalArgumentException("Invalid price confirmation target."); }
    costPrice = Money.exact(costPrice);
    sellingPrice = Money.exact(sellingPrice);
  }
  public boolean matches(long id, int version, BigDecimal cost, BigDecimal selling) {
    return productId == id && revision == version && costPrice.compareTo(cost) == 0
        && sellingPrice.compareTo(selling) == 0;
  }
  public String fingerprint() {
    return SaleRequest.digest(productId + ":" + revision + ":" + costPrice.toPlainString()
        + ":" + sellingPrice.toPlainString());
  }
}
