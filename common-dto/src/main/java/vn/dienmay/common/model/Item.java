package vn.dienmay.common.model;

import java.math.BigDecimal;

/** A SKU/model, not an individual serialized device. Database field order is unchanged. */
public record Item(
    long id, String sku, String barcode, String name, long categoryId, long brandId,
    String category, String brand, String model, String description, BigDecimal costPrice,
    BigDecimal sellingPrice, int warrantyMonths, boolean trackSerial, boolean enabled,
    int reorderLevel, int warehouseQuantity, int counterQuantity, int revision) {

  @Override
  public String toString() {
    return sku + " - " + name;
  }
}
