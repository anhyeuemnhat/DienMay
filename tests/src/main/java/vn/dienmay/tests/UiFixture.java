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
import vn.dienmay.common.model.Sale;
import vn.dienmay.server.api.ApiHandler;

/** TEST ONLY: synthetic API responses for visual layout; never claims database/business acceptance. */
public final class UiFixture implements ApiHandler {
  private final Map<String, Role> tokens = new ConcurrentHashMap<>();
  private final Item phone = new Item(1, "DEMO-WH-CH520", "", "Tai nghe Sony WH-CH520", 1, 1,
      "Tai nghe", "Sony", "WH-CH520", "TEST FIXTURE - visual layout only", Money.of("700000"),
      Money.of("1000000"), 12, true, true, 3, 2, 1, 1, "VERIFIED", "NONE", "PENDING", true,
      "https://www.sony.com.vn/headphones/products/wh-ch520");
  private final Item cable = new Item(2, "DEMO-24MR400-B", "", "Man hinh LG 24MR400-B", 2, 2,
      "Man hinh may tinh", "LG", "24MR400-B", "TEST FIXTURE - visual layout only", Money.of("1800000"),
      Money.of("2400000"), 24, false, true, 5, 5, 2, 1, "VERIFIED", "NONE", "PENDING", true,
      "https://www.lg.com/vn/man-hinh-may-tinh/man-hinh-van-phong-pho-thong/24mr400-b/");

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
      case PRODUCT_FIND -> (Data.text(p, "code").contains("CAP") || Data.text(p, "code").contains("24MR")) ? cable :
          phone;
      case PRODUCT_GET -> Data.id(p, "id") == 2 ? cable : phone;
      case SERIALS -> List.of("DEMO-SN-SONY-0002");
      case LOOKUPS -> switch (Data.text(p, "type")) {
        case "brand" -> List.of(Data.map("id", 1, "name", "Sony"), Data.map("id", 2, "name", "LG"));
        case "category" -> List.of(Data.map("id", 1, "name", "Tai nghe"), Data.map("id", 2, "name",
            "Man hinh may tinh"));
        default -> List.of(Data.map("id", 1, "name", "Nha cung cap DEMO"));
      };
      case QUALITY_LIST -> {
        String type = Data.text(p, "type");
        yield List.of(type.equals("product") ? Data.map("Id", 1, "Name", phone.name(), "Enabled", true,
            "Revision", 1, "VerificationStatus", "VERIFIED", "VerificationSource", phone.verificationSource(),
            "VerificationNote", "Synthetic visual fixture", "BrandName", "Sony", "CategoryName", "Tai nghe",
            "SKU", phone.sku(), "Model", phone.model(), "Description", phone.description(), "Barcode", "",
            "BarcodeType", "NONE", "BarcodeStatus", "PENDING", "IsDemo", true)
            : Data.map("Id", 1, "Name", type.equals("brand") ? "Sony" : "Tai nghe", "Enabled", true,
                "Revision", 1, "VerificationStatus", "VERIFIED", "VerificationSource", phone.verificationSource(),
                "VerificationNote", "Synthetic visual fixture"));
      }
      case REFERENCES -> List.of(Data.map("Id", 1, "BrandName", "Sony", "CategoryName", "Tai nghe",
          "ProductName", phone.name(), "Model", phone.model(), "Specifications", phone.description(),
          "SourceUrl", phone.verificationSource(), "CheckedOn", "2026-10-31"));
      case AUDIT_LOG -> List.of(Data.map("Id", 1, "EntityType", "product", "EntityId", 1, "Action", "REVIEW_IDENTITY",
          "OldValue", "PENDING", "NewValue", "VERIFIED", "Reason", "TEST FIXTURE - not SQL acceptance",
          "UserName", "TEST", "CreatedAt", "2026-10-31T01:00:00Z"));
      case CUSTOMERS -> List.of(Data.map("Id", 1, "FullName", "Kh\u00e1ch h\u00e0ng m\u1eabu",
          "Phone", "", "Address", "D\u1eef li\u1ec7u ki\u1ec3m th\u1eed", "Enabled", true, "Revision", 1));
      case CUSTOMER -> Data.map("id", 1, "name", "Kh\u00e1ch h\u00e0ng m\u1eabu", "phone", "");
      case VENDORS -> List.of(Data.map("Id", 1, "Name", "Nh\u00e0 cung c\u1ea5p m\u1eabu",
          "Phone", "", "Address", "D\u1eef li\u1ec7u ki\u1ec3m th\u1eed", "Enabled", true, "Revision", 1));
      case EMPLOYEES -> List.of(Data.map("Id", 1, "UserName", "fixture.manager", "FullName", "UI TEST",
          "Phone", "", "Role", "MANAGER", "Enabled", true, "MustChange", false, "Revision", 1));
      case PROMOTIONS -> List.of(Data.map("Id", 1, "Code", "DEMO5", "Name", "Khuy\u1ebfn m\u00e3i m\u1eabu",
          "StartDate", LocalDate.of(2026, 10, 1), "EndDate", LocalDate.of(2026, 10, 31),
          "PercentOff", Money.of("5"), "Enabled", true, "Revision", 1));
      case INVOICES -> List.of(Data.map("Id", 7, "Code", "HD-7", "SoldAt", Instant.parse("2026-10-29T01:00:00Z"),
          "CustomerName", "Kh\u00e1ch h\u00e0ng m\u1eabu", "CustomerPhone", "",
          "EmployeeName", "UI TEST", "Total", Money.of("3230000"), "PaymentMethod", "CASH"));
      case INVOICE -> new Sale(7, "HD-7", Instant.parse("2026-10-29T01:00:00Z"), "UI TEST", "DEMO customer", "",
          "CASH", Money.of("3400000"), Money.of("170000"), Money.of("3230000"),
          List.of(new Sale.Line(phone.sku(), phone.name(), 1, phone.sellingPrice(), 12, "DEMO-SN-SONY-0001"),
              new Sale.Line(cable.sku(), cable.name(), 1, cable.sellingPrice(), 24, "")));
      case WARRANTIES -> List.of(Data.map("Id", 1, "Code", "BH-1", "InvoiceCode", "HD-7",
          "SKU", phone.sku(), "ProductName", phone.name(), "Serial", "DEMO-SN-SONY-0001",
          "CustomerName", "Kh\u00e1ch h\u00e0ng m\u1eabu", "CustomerPhone", "",
          "StartDate", "2026-10-29", "EndDate", "2027-10-29", "WarrantyMonths", 12,
          "CoveredQuantity", 1, "Validity", "ACTIVE"));
      case CLAIMS -> List.of(Data.map("Id", 1, "WarrantyCode", "BH-1", "ReceivedAt", "2026-10-29T01:00:00Z",
          "Fault", "DEMO - tai nghe can kiem tra", "EmployeeName", "UI TEST",
          "Status", "RECEIVED", "Notes", "", "CompletedAt", null));
      case CLAIM_HISTORY -> List.of(Data.map("Id", 1, "ChangedAt", "2026-10-29T01:00:00Z",
          "EmployeeName", "UI TEST", "OldStatus", null, "NewStatus", "RECEIVED", "Notes", "Ti\u1ebfp nh\u1eadn"));
      case REPORT -> switch (Data.text(p, "type")) {
        case "employee" -> List.of(Data.map("UserName", "TEST", "FullName", "UI TEST", "InvoiceCount", 1,
            "NetRevenue", Money.of("3230000")));
        case "best" -> List.of(Data.map("SKU", phone.sku(), "ProductName", phone.name(), "Quantity", 1,
            "GrossSales", phone.sellingPrice()));
        default -> List.of(Data.map("InvoiceCount", 1, "Subtotal", Money.of("3400000"),
            "Discount", Money.of("170000"), "NetRevenue", Money.of("3230000")));
      };
      case LOW_STOCK -> List.of(Data.map("Id", 1, "SKU", phone.sku(), "Name", phone.name(),
          "TotalStock", 3, "ReorderLevel", 3));
      case RECEIPTS -> List.of(Data.map("Id", 1, "RequestId", "00000000-0000-0000-0000-000000000001",
          "CreatedAt", "2026-10-29T01:00:00Z", "SKU", phone.sku(), "Name", phone.name(), "Quantity", 2,
          "UnitCost", phone.costPrice(), "Reference", "Synthetic UI fixture - not real receipt",
          "VendorName", "DEMO vendor", "EmployeeName", "UI TEST", "Status", "POSTED", "ReversalMovementId", null,
          "ReversalReason", null, "Revision", 1, "PreviousCostPrice", Money.of("600000")));
      case STOCK_HISTORY, SERIAL_HISTORY -> List.of(Data.map("Id", 1, "CreatedAt", "2026-10-29T01:00:00Z",
          "SKU", phone.sku(), "Location", "KHO", "Delta", 4, "Kind", "RECEIVE",
          "Reference", "TEST FIXTURE", "EmployeeName", "UI TEST", "UnitCost", phone.costPrice()));
      case SETTINGS -> Data.map("name", "DienMay / UI TEST", "address", "Synthetic data", "phone", "", "mode", "DEMO");
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
