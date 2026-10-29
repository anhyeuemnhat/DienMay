package vn.dienmay.tests;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import vn.dienmay.common.model.InventoryRequest;
import vn.dienmay.common.model.Item;
import vn.dienmay.common.model.ItemQuantity;
import vn.dienmay.common.model.Money;
import vn.dienmay.common.model.Sale;
import vn.dienmay.common.model.SaleRequest;

/** Synthetic records used only in tests, never bundled into production application JARs. */
public final class Fixtures {
  private Fixtures() {}

  public static Item item(long id, boolean serial, int counter) {
    return new Item(id, "SKU-" + id, "000123", "Dien thoai mau", 1, 1, "Phone", "Demo",
        "Model-1", "Synthetic fixture", Money.of("60"), Money.of("100.25"), 12, serial,
        true, 5, 4, counter, 1);
  }

  public static ItemQuantity line(long id, int quantity, String... serials) {
    return new ItemQuantity(id, "SKU-" + id, "Fixture product", Money.of("100.25"), quantity,
        List.of(serials));
  }

  public static SaleRequest request(UUID id, ItemQuantity... lines) {
    return new SaleRequest(id, null, "CASH", Money.ZERO, "", Money.ZERO, List.of(lines));
  }

  public static InventoryRequest stock() {
    return new InventoryRequest(UUID.randomUUID(), 1, 2, List.of("0001", "0002"),
        "Test document", 3L, Money.of("60"), null, "KHO");
  }

  public static Sale sale() {
    return new Sale(7, "HD-7", Instant.parse("2026-10-19T01:00:00Z"), "Cashier", "Customer", "",
        "CASH", Money.of("200.50"), Money.of("0.50"), Money.of("200"),
        List.of(new Sale.Line("SKU", "Fixture product", 2, Money.of("100.25"), 12, "0001, 0002")));
  }
}
