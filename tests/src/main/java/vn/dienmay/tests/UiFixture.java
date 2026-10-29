package vn.dienmay.tests;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import vn.dienmay.common.api.ApiSession;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Operation;
import vn.dienmay.common.model.Item;
import vn.dienmay.common.model.Money;
import vn.dienmay.common.model.Role;
import vn.dienmay.server.api.ApiHandler;

/** TEST ONLY: synthetic API responses for visual layout; never claims database/business acceptance. */
public final class UiFixture implements ApiHandler {
  private final Map<String, Role> tokens = new ConcurrentHashMap<>();
  private final Item phone = new Item(1, "DT-DEMO-01", "0001234567890",
      "\u0110i\u1ec7n tho\u1ea1i Demo 128 GB", 1, 1, "\u0110i\u1ec7n tho\u1ea1i", "DemoTech",
      "Demo Phone", "TEST FIXTURE - not production data", Money.of("5000000"), Money.of("6500000"),
      12, true, true, 2, 2, 1, 1);
  private final Item cable = new Item(2, "PK-CAP-01", "0000000000017", "C\u00e1p USB-C Demo",
      2, 1, "Ph\u1ee5 ki\u1ec7n", "DemoTech", "Cable-1", "TEST FIXTURE",
      Money.of("60000"), Money.of("100000"), 6, false, true, 5, 10, 5, 1);

  public Item cable() {
    return cable;
  }

  @Override
  public Object execute(Operation operation, String bearer, Map<String, Object> p) throws Exception {
    // Simulate enough I/O latency to verify EDT responsiveness.
    Thread.sleep(35);
    if (operation == Operation.LOGIN) {
      Role role = Role.valueOf(Data.text(p, "username"));
      String token = UUID.randomUUID().toString();
      tokens.put(token, role);
      return new ApiSession(token, role.ordinal() + 1L, "UI TEST / " + role.name(), role, false,
          Instant.now().plusSeconds(3600));
    }
    Role role = tokens.get(bearer);
    if (role == null || !operation.allows(role)) {
      throw new SecurityException("Test fixture denied operation");
    }
    return switch (operation) {
      case PRODUCT_SEARCH -> List.of(phone, cable);
      case PRODUCT_FIND -> Data.text(p, "code").contains("CAP") ? cable : phone;
      case PRODUCT_GET -> Data.id(p, "id") == 2 ? cable : phone;
      case SERIALS -> List.of("PHONE-DEMO-0001");
      case LOOKUPS -> List.of(Data.map("id", 1, "name", "Danh m\u1ee5c m\u1eabu"));
      case CUSTOMERS -> List.of(Data.map("Id", 1, "FullName", "Kh\u00e1ch h\u00e0ng m\u1eabu",
          "Phone", "0900000000", "Address", "D\u1eef li\u1ec7u ki\u1ec3m th\u1eed"));
      case CUSTOMER -> Data.map("id", 1, "name", "Kh\u00e1ch h\u00e0ng m\u1eabu", "phone", "0900000000");
      case VENDORS -> List.of(Data.map("Id", 1, "Name", "Nh\u00e0 cung c\u1ea5p m\u1eabu",
          "Phone", "0900000001", "Address", "D\u1eef li\u1ec7u ki\u1ec3m th\u1eed", "Enabled", true));
      case EMPLOYEES -> List.of(Data.map("Id", 1, "UserName", "fixture.manager", "FullName", "UI TEST",
          "Phone", "", "Role", "MANAGER", "Enabled", true, "MustChange", false));
      case PROMOTIONS -> List.of(Data.map("Id", 1, "Code", "DEMO5", "Name", "Khuy\u1ebfn m\u00e3i m\u1eabu",
          "StartDate", LocalDate.of(2026, 10, 1), "EndDate", LocalDate.of(2026, 10, 31),
          "PercentOff", Money.of("5"), "Enabled", true));
      case INVOICES -> List.of(Data.map("Id", 7, "Code", "HD-7", "SoldAt", Instant.parse("2026-10-29T01:00:00Z"),
          "CustomerName", "Kh\u00e1ch h\u00e0ng m\u1eabu", "CustomerPhone", "0900000000",
          "EmployeeName", "UI TEST", "Total", Money.of("6700000"), "PaymentMethod", "CASH"));
      case INVOICE -> Fixtures.sale();
      case WARRANTIES -> List.of(Data.map("Id", 1, "Code", "BH-1", "InvoiceCode", "HD-7",
          "SKU", "DT-DEMO-01", "ProductName", phone.name(), "Serial", "PHONE-DEMO-0001",
          "CustomerName", "Kh\u00e1ch h\u00e0ng m\u1eabu", "CustomerPhone", "0900000000",
          "StartDate", "2026-10-29", "EndDate", "2027-10-29", "WarrantyMonths", 12,
          "CoveredQuantity", 1, "Validity", "ACTIVE"));
      case CLAIMS -> List.of(Data.map("Id", 1, "WarrantyCode", "BH-1", "ReceivedAt", "2026-10-29T01:00:00Z",
          "Fault", "M\u00e0n h\u00ecnh c\u1ea7n ki\u1ec3m tra", "EmployeeName", "UI TEST",
          "Status", "RECEIVED", "Notes", "", "CompletedAt", null));
      case CLAIM_HISTORY -> List.of(Data.map("Id", 1, "ChangedAt", "2026-10-29T01:00:00Z",
          "EmployeeName", "UI TEST", "OldStatus", null, "NewStatus", "RECEIVED", "Notes", "Ti\u1ebfp nh\u1eadn"));
      case REPORT -> List.of(Data.map("InvoiceCount", 12, "Gross", Money.of("81200000"),
          "Discount", Money.of("1200000"), "NetRevenue", Money.of("80000000")));
      case LOW_STOCK -> List.of(Data.map("Id", 1, "SKU", phone.sku(), "Name", phone.name(),
          "TotalStock", 2, "ReorderLevel", 2));
      case STOCK_HISTORY, SERIAL_HISTORY -> List.of(Data.map("Id", 1, "CreatedAt", "2026-10-29T01:00:00Z",
          "SKU", phone.sku(), "Location", "KHO", "Delta", 2, "Kind", "RECEIVE",
          "Reference", "TEST FIXTURE", "EmployeeName", "UI TEST", "UnitCost", Money.of("5000000")));
      case SETTINGS -> Data.map("name", "DienMay / UI TEST", "address", "Synthetic data", "phone", "");
      case QUOTE -> Data.map("subtotal", Money.of("200000"), "discount", Money.ZERO, "total", Money.of("200000"));
      case SALE_RESOLVE -> null;
      case STOCK_RESOLVE -> false;
      case SALE_CANCEL, STOCK_CANCEL -> true;
      case LOGOUT -> {
        tokens.remove(bearer);
        yield null;
      }
      default -> throw new UnsupportedOperationException("Visual fixture is read-only: " + operation);
    };
  }
}
