package vn.dienmay.common.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import vn.dienmay.common.api.BusinessException;

/** Shared exact calculations. The server repeats this check using locked, current data. */
public final class PricePolicy {
  private PricePolicy() {}
  public static boolean belowCost(BigDecimal cost, BigDecimal selling) {
    return Money.exact(selling).compareTo(Money.exact(cost)) < 0;
  }
  public static BigDecimal difference(BigDecimal cost, BigDecimal selling) {
    return Money.exact(selling).subtract(Money.exact(cost));
  }
  public static BigDecimal percentage(BigDecimal cost, BigDecimal selling) {
    Money.exact(cost);
    Money.exact(selling);
    return cost.signum() == 0 ? BigDecimal.ZERO.setScale(2)
        : difference(cost, selling).multiply(new BigDecimal("100")).divide(cost, 2, RoundingMode.HALF_UP);
  }
  public static void require(long id, int revision, BigDecimal cost, BigDecimal selling,
      PriceConfirmation confirmation) {
    boolean below = belowCost(cost, selling);
    if (confirmation != null && !confirmation.matches(id, revision, cost, selling)) {
      throw new BusinessException(409, "PRICE_CONFIRMATION_STALE",
          "Giá hoặc phiên bản đã thay đổi. Tải lại và xác nhận lại.");
    }
    if (below && confirmation == null) {
      throw new BusinessException(409, "BELOW_COST_CONFIRMATION_REQUIRED",
          "Giá bán thấp hơn giá nhập. Cần xác nhận đúng giá và phiên bản trước khi ghi.");
    }
  }
  /** Hard form validation runs BEFORE showing an acknowledgement dialog. */
  public static void validateProduct(Item value) {
    Validation.required(value.sku(), 64, "SKU");
    Validation.required(value.name(), 180, "Tên sản phẩm");
    CatalogRules.barcode(value.barcode(), value.barcodeType());
    Validation.optional(value.model(), 100, "Model");
    Validation.optional(value.description(), 1000, "Mô tả");
    if (value.id() < 0 || value.categoryId() <= 0 || value.brandId() <= 0 || value.warrantyMonths() < 0
        || value.warrantyMonths() > 120 || value.reorderLevel() < 0 || value.reorderLevel() > 100000
        || (value.id() != 0 && value.revision() <= 0)) {
      throw new IllegalArgumentException("Danh mục, bảo hành, tồn tối thiểu hoặc phiên bản không hợp lệ.");
    }
    Money.exact(value.costPrice());
    Money.exact(value.sellingPrice());
  }
}
