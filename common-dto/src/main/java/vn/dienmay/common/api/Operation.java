package vn.dienmay.common.api;

import java.util.Arrays;
import java.util.Set;
import vn.dienmay.common.model.Role;

/** Explicit operation allowlist shared by navigation and transport; services authorize again. */
public enum Operation {
  LOGIN("auth/login"),
  LOGOUT("auth/logout", Role.MANAGER, Role.CASHIER, Role.WAREHOUSE),
  PASSWORD("auth/password", Role.MANAGER, Role.CASHIER, Role.WAREHOUSE),
  SETTINGS("settings", Role.MANAGER, Role.CASHIER, Role.WAREHOUSE),
  PRODUCT_SEARCH("products/search", Role.MANAGER, Role.CASHIER, Role.WAREHOUSE),
  PRODUCT_FIND("products/find", Role.MANAGER, Role.CASHIER, Role.WAREHOUSE),
  PRODUCT_GET("products/get", Role.MANAGER, Role.CASHIER, Role.WAREHOUSE),
  PRODUCT_SAVE("products/save", Role.MANAGER),
  LIFECYCLE("admin/lifecycle", Role.MANAGER),
  QUALITY_LIST("quality/list", Role.MANAGER),
  QUALITY_REVIEW("quality/review", Role.MANAGER),
  REFERENCES("quality/references", Role.MANAGER),
  AUDIT_LOG("admin/audit", Role.MANAGER),
  SERIALS("products/serials", Role.MANAGER, Role.CASHIER, Role.WAREHOUSE),
  LOOKUPS("lookups/list", Role.MANAGER, Role.CASHIER, Role.WAREHOUSE),
  LOOKUP_SAVE("lookups/save", Role.MANAGER),
  CUSTOMERS("customers/search", Role.MANAGER, Role.CASHIER),
  CUSTOMER("customers/get", Role.MANAGER, Role.CASHIER),
  CUSTOMER_SAVE("customers/save", Role.MANAGER, Role.CASHIER),
  VENDORS("vendors/list", Role.MANAGER, Role.WAREHOUSE),
  VENDOR_SAVE("vendors/save", Role.MANAGER),
  EMPLOYEES("employees/list", Role.MANAGER),
  EMPLOYEE_SAVE("employees/save", Role.MANAGER),
  PROMOTIONS("promotions/list", Role.MANAGER, Role.CASHIER),
  PROMOTION_SAVE("promotions/save", Role.MANAGER),
  QUOTE("sales/quote", Role.MANAGER, Role.CASHIER),
  CHECKOUT("sales/checkout", Role.MANAGER, Role.CASHIER),
  SALE_CANCEL("sales/cancel", Role.MANAGER, Role.CASHIER),
  SALE_RESOLVE("sales/resolve", Role.MANAGER, Role.CASHIER),
  INVOICE("sales/get", Role.MANAGER, Role.CASHIER),
  INVOICES("sales/search", Role.MANAGER, Role.CASHIER),
  RECEIVE("inventory/receive", Role.MANAGER, Role.WAREHOUSE),
  TRANSFER("inventory/transfer", Role.MANAGER, Role.WAREHOUSE),
  STOCK_CANCEL("inventory/cancel", Role.MANAGER, Role.WAREHOUSE),
  STOCK_RESOLVE("inventory/resolve", Role.MANAGER, Role.WAREHOUSE),
  STOCK_HISTORY("inventory/history", Role.MANAGER, Role.WAREHOUSE),
  SERIAL_HISTORY("inventory/serial-history", Role.MANAGER, Role.WAREHOUSE),
  WARRANTIES("warranties/search", Role.MANAGER, Role.CASHIER),
  CLAIM_RECEIVE("warranties/receive", Role.MANAGER, Role.CASHIER),
  CLAIMS("warranties/claims", Role.MANAGER, Role.CASHIER),
  CLAIM_TRANSITION("warranties/transition", Role.MANAGER),
  CLAIM_HISTORY("warranties/history", Role.MANAGER, Role.CASHIER),
  REPORT("reports/query", Role.MANAGER),
  LOW_STOCK("reports/low-stock", Role.MANAGER, Role.WAREHOUSE);

  private final String path;
  private final Set<Role> roles;

  Operation(String path, Role... roles) {
    this.path = "/api/v1/" + path;
    this.roles = Set.copyOf(Arrays.asList(roles));
  }

  public String path() {
    return path;
  }

  public boolean allows(Role role) {
    return roles.contains(role);
  }

  public static Operation fromPath(String path) {
    return Arrays.stream(values()).filter(op -> op.path.equals(path)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown API operation"));
  }
}
