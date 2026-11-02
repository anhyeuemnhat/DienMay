package vn.dienmay.server.api;

import static vn.dienmay.common.api.Data.date;
import static vn.dienmay.common.api.Data.flag;
import static vn.dienmay.common.api.Data.id;
import static vn.dienmay.common.api.Data.money;
import static vn.dienmay.common.api.Data.optionalId;
import static vn.dienmay.common.api.Data.text;

import java.util.Arrays;
import java.util.Map;
import vn.dienmay.common.api.ApiException;
import vn.dienmay.common.api.ApiSession;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.api.Operation;
import vn.dienmay.common.model.InventoryRequest;
import vn.dienmay.common.model.Item;
import vn.dienmay.common.model.ItemQuantity;
import vn.dienmay.common.model.Role;
import vn.dienmay.common.model.SaleRequest;
import vn.dienmay.server.InventorySystem;
import vn.dienmay.server.service.AuthService;

/** Explicit endpoints only. No reflection-based method invocation or SqlDatabase supplied by the client. */
public final class ApplicationApi implements ApiHandler {
  private final InventorySystem system;

  public ApplicationApi(InventorySystem system) {
    this.system = system;
  }

  private ApiSession view(AuthService.Session session) {
    return new ApiSession(session.token(), session.employeeId(), session.name(), session.role(),
        session.mustChange(), session.expiresAt());
  }

  @Override
  public Object execute(Operation operation, String bearer, Map<String, Object> p) throws Exception {
    if (operation == Operation.LOGIN) {
      char[] password = text(p, "password").toCharArray();
      try {
        return view(system.auth().login(text(p, "username"), password));
      } finally {
        Arrays.fill(password, '\0');
        p.remove("password");
      }
    }
    AuthService.Session session;
    try {
      session = system.auth().resolve(bearer);
    } catch (SecurityException ex) {
      throw new ApiException(401, "SESSION_EXPIRED", "Phien dang nhap da het. Dang nhap lai.", "");
    }
    if (!operation.allows(session.role())) {
      throw new ApiException(403, "FORBIDDEN", "Ban khong co quyen thuc hien chuc nang nay.", "");
    }
    if (session.mustChange() && operation != Operation.PASSWORD && operation != Operation.LOGOUT) {
      throw new ApiException(403, "PASSWORD_CHANGE_REQUIRED", "Can doi mat khau ban dau.", "");
    }
    return switch (operation) {
      case LOGOUT -> {
        system.auth().logout(session);
        yield null;
      }
      case PASSWORD -> changePassword(session, p);
      case SETTINGS -> system.database().read(c -> {
        system.auth().require(c, session, Role.values());
        return Data.map("name", system.database().setting("store.name", "Dien May"),
            "address", system.database().setting("store.address", ""),
            "phone", system.database().setting("store.phone", ""),
            "mode", vn.dienmay.server.service.QualityService.mode(c));
      });
      case LIFECYCLE -> {
        system.lifecycle().setActive(session, text(p, "type"), id(p, "id"),
            Json.number(p.get("revision")).intValueExact(), flag(p, "enabled"), text(p, "reason"));
        yield null;
      }
      case DELETE_PREVIEW -> system.deletion().preview(session, text(p, "type"), id(p, "id"),
          Json.number(p.get("revision")).intValueExact());
      case DELETE_RECORD -> system.deletion().delete(session,
          Json.convert(p.get("request"), vn.dienmay.common.model.DeletionRequest.class));
      case DELETE_RESOLVE -> system.deletion().resolve(session,
          Json.convert(p.get("request"), vn.dienmay.common.model.DeletionRequest.class));
      case DELETE_CANCEL -> system.deletion().cancel(session,
          Json.convert(p.get("request"), vn.dienmay.common.model.DeletionRequest.class));
      case QUALITY_LIST -> system.quality().list(session, text(p, "type"));
      case REFERENCES -> system.quality().references(session);
      case AUDIT_LOG -> system.quality().auditLog(session);
      case QUALITY_REVIEW -> {
        system.quality().review(session, text(p, "type"), id(p, "id"),
            Json.number(p.get("revision")).intValueExact(), text(p, "field"), text(p, "status"),
            text(p, "source"), text(p, "note"), optionalId(p, "referenceId"));
        yield null;
      }
      case PRODUCT_SEARCH -> system.catalog().search(session, text(p, "text"));
      case PRODUCT_FIND -> system.catalog().find(session, text(p, "code"));
      case PRODUCT_GET -> system.catalog().get(session, id(p, "id"));
      case PRODUCT_SAVE -> system.catalog().save(session, Json.convert(p.get("item"), Item.class),
          Json.convert(p.get("priceConfirmation"), vn.dienmay.common.model.PriceConfirmation.class));
      case SERIALS -> system.catalog().serials(session, id(p, "id"), text(p, "location"));
      case LOOKUPS -> system.catalog().lookups(session, text(p, "type"));
      case LOOKUP_SAVE -> system.catalog().saveLookup(session, text(p, "type"), optionalId(p, "id"),
          text(p, "name"), revision(p));
      case CUSTOMERS -> system.people().customers(session, text(p, "text"));
      case CUSTOMER -> system.people().customer(session, id(p, "id"));
      case CUSTOMER_SAVE -> system.people().saveCustomer(session, optionalId(p, "id"),
          text(p, "name"), text(p, "phone"), text(p, "address"), revision(p));
      case VENDORS -> system.people().vendors(session);
      case VENDOR_SAVE -> system.people().saveVendor(session, optionalId(p, "id"), text(p, "name"),
          text(p, "phone"), text(p, "address"), flag(p, "enabled"), revision(p));
      case EMPLOYEES -> system.people().employees(session);
      case EMPLOYEE_SAVE -> saveEmployee(session, p);
      case PROMOTIONS -> system.promotions().list(session);
      case PROMOTION_SAVE -> system.promotions().save(session, optionalId(p, "id"), text(p, "code"),
          text(p, "name"), date(p, "from"), date(p, "to"), money(p, "percent"), flag(p, "enabled"), revision(p));
      case QUOTE -> system.sales().quote(session, Json.list(p.get("lines"), ItemQuantity.class),
          text(p, "code"), money(p, "manual"));
      case CHECKOUT -> system.sales().checkout(session, Json.convert(p.get("request"), SaleRequest.class));
      case SALE_CANCEL -> system.sales().cancel(session, Json.convert(p.get("request"), SaleRequest.class));
      case SALE_RESOLVE -> system.sales().resolve(session, Json.convert(p.get("request"), SaleRequest.class));
      case INVOICE -> system.sales().load(session, id(p, "id"));
      case INVOICES -> system.sales().search(session, text(p, "text"), date(p, "from"), date(p, "to"));
      case RECEIVE -> {
        system.inventory().receive(session, Json.convert(p.get("request"), InventoryRequest.class));
        yield null;
      }
      case TRANSFER -> {
        system.inventory().transfer(session, Json.convert(p.get("request"), InventoryRequest.class));
        yield null;
      }
      case STOCK_CANCEL -> system.inventory().cancel(session,
          Json.convert(p.get("request"), InventoryRequest.class));
      case STOCK_RESOLVE -> system.inventory().resolve(session,
          Json.convert(p.get("request"), InventoryRequest.class));
      case RECEIPTS -> system.reversals().receipts(session, text(p, "text"));
      case REVERSAL_PREVIEW -> system.reversals().preview(session, id(p, "id"));
      case REVERSE_RECEIPT -> system.reversals().reverse(session,
          Json.convert(p.get("request"), vn.dienmay.common.model.ReversalRequest.class));
      case REVERSAL_RESOLVE -> system.reversals().resolve(session,
          Json.convert(p.get("request"), vn.dienmay.common.model.ReversalRequest.class));
      case REVERSAL_CANCEL -> system.reversals().cancel(session,
          Json.convert(p.get("request"), vn.dienmay.common.model.ReversalRequest.class));
      case STOCK_HISTORY -> system.inventory().history(session, text(p, "sku"));
      case SERIAL_HISTORY -> system.inventory().unitHistory(session, text(p, "serial"));
      case WARRANTIES -> system.warranties().search(session, text(p, "text"));
      case CLAIM_RECEIVE -> system.warranties().receive(session, id(p, "warranty"), text(p, "fault"));
      case CLAIMS -> system.warranties().claims(session, optionalId(p, "warranty"));
      case CLAIM_TRANSITION -> {
        system.warranties().transition(session, id(p, "claim"), text(p, "expected"),
            text(p, "next"), text(p, "notes"));
        yield null;
      }
      case CLAIM_HISTORY -> system.warranties().history(session, id(p, "claim"));
      case REPORT -> system.reports().report(session, text(p, "type"), date(p, "from"), date(p, "to"));
      case LOW_STOCK -> system.reports().lowStock(session);
      default -> throw new IllegalArgumentException("Unsupported operation");
    };
  }

  private static int revision(Map<String, Object> p) {
    return optionalId(p, "id") == null ? 0 : Json.number(p.get("revision")).intValueExact();
  }

  private ApiSession changePassword(AuthService.Session session, Map<String, Object> p)
      throws Exception {
    char[] oldPassword = text(p, "oldPassword").toCharArray();
    char[] newPassword = text(p, "newPassword").toCharArray();
    try {
      return view(system.auth().changePassword(session, oldPassword, newPassword));
    } finally {
      Arrays.fill(oldPassword, '\0');
      Arrays.fill(newPassword, '\0');
      p.remove("oldPassword");
      p.remove("newPassword");
    }
  }

  private long saveEmployee(AuthService.Session session, Map<String, Object> p) throws Exception {
    char[] password = text(p, "password").toCharArray();
    try {
      return system.people().saveEmployee(session, optionalId(p, "id"), text(p, "username"),
          text(p, "name"), text(p, "phone"), Role.valueOf(text(p, "role")),
          flag(p, "enabled"), password, revision(p));
    } finally {
      Arrays.fill(password, '\0');
      p.remove("password");
    }
  }
}
