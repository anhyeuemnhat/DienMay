package vn.dienmay.server.service;

import static vn.dienmay.server.db.Jdbc.insert;
import static vn.dienmay.server.db.Jdbc.lock;
import static vn.dienmay.server.db.Jdbc.now;
import static vn.dienmay.server.db.Jdbc.rows;
import static vn.dienmay.server.db.Jdbc.statement;
import static vn.dienmay.server.db.Jdbc.update;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import vn.dienmay.common.model.Item;
import vn.dienmay.common.model.ItemQuantity;
import vn.dienmay.common.model.Money;
import vn.dienmay.common.model.Role;
import vn.dienmay.common.model.Sale;
import vn.dienmay.common.model.SaleRequest;
import vn.dienmay.common.model.Validation;
import vn.dienmay.common.model.WarrantyRules;
import vn.dienmay.server.db.InvoiceDao;
import vn.dienmay.server.db.ProductDao;
import vn.dienmay.server.db.RequestCancellationDao;
import vn.dienmay.server.db.SqlDatabase;
import vn.dienmay.server.db.StockDao;

/** Checkout is one SqlDatabase transaction: invoice, stock, units, warranty and request identity. */
public final class SalesService {
  @FunctionalInterface
  public interface FailurePoint {
    void hit(String phase) throws SQLException;
  }

  public record Quote(BigDecimal subtotal, BigDecimal discount, BigDecimal total) {}

  private final SqlDatabase db;
  private final AuthService auth;
  private final FailurePoint failures;
  private final ProductDao products;
  private final StockDao stock;
  private final InvoiceDao invoices;

  public SalesService(SqlDatabase db, AuthService auth) {
    this(db, auth, phase -> {});
  }

  /** Failure injection is constructor-only for tests; no production config can activate it. */
  public SalesService(SqlDatabase db, AuthService auth, FailurePoint failures) {
    this(db, auth, failures, new ProductDao(), new StockDao(), new InvoiceDao());
  }

  public SalesService(SqlDatabase db, AuthService auth, FailurePoint failures,
      ProductDao products, StockDao stock, InvoiceDao invoices) {
    this.db = db;
    this.auth = auth;
    this.failures = failures;
    this.products = products;
    this.stock = stock;
    this.invoices = invoices;
  }

  public Quote quote(AuthService.Session session, List<ItemQuantity> lines,
      String code, BigDecimal manual) throws SQLException {
    if (lines.isEmpty() || lines.size() > 100) {
      throw new IllegalArgumentException("Gio hang can 1 den 100 dong.");
    }
    return db.read(c -> {
      auth.require(c, session, Role.MANAGER, Role.CASHIER);
      BigDecimal subtotal = SaleRequest.subtotal(lines);
      BigDecimal discount = PromotionService.discount(c,
          Validation.optional(code, 50, "Ma khuyen mai").toUpperCase(Locale.ROOT),
          Money.exact(manual), subtotal, now(c), false);
      return new Quote(subtotal, discount, Money.total(subtotal, discount));
    });
  }

  public Sale checkout(AuthService.Session session, SaleRequest request) throws SQLException {
    return db.transaction(c -> {
      auth.require(c, session, Role.MANAGER, Role.CASHIER);
      lock(c, "dm:sale:" + request.requestId());
      Long existing = findRequest(c, session, request);
      if (existing != null) {
        return invoices.load(c, existing);
      }
      RequestCancellationDao.requireActive(c, "SALE", request.requestId(), request.fingerprint(),
          session.employeeId());
      List<ItemQuantity> lines = request.lines().stream()
          .sorted(Comparator.comparingLong(ItemQuantity::productId)).toList();
      Map<Long, Item> current = new LinkedHashMap<>();
      // Stable lock order reduces deadlocks between overlapping multi-line carts.
      for (ItemQuantity line : lines) {
        Item item = products.get(c, line.productId(), true);
        if (!item.enabled()) {
          throw new IllegalArgumentException("San pham da ngung kinh doanh: " + item.sku());
        }
        if (item.sellingPrice().compareTo(line.expectedPrice()) != 0) {
          throw new IllegalArgumentException("Gia da thay doi: " + item.sku() + ". Can xac nhan lai.");
        }
        StockDao.checkSerials(item, line.quantity(), line.serials());
        current.put(item.id(), item);
      }
      Instant timestamp = now(c);
      LocalDate saleDate = timestamp.atZone(Validation.ZONE).toLocalDate();
      BigDecimal subtotal = SaleRequest.subtotal(lines);
      BigDecimal discount = PromotionService.discount(c, request.promotionCode(),
          request.manualDiscount(), subtotal, timestamp, true);
      if (discount.compareTo(request.expectedDiscount()) != 0) {
        throw new IllegalArgumentException("Khuyen mai da thay doi. Can xac nhan lai tong tien.");
      }
      String customer = "Khach le";
      String phone = "";
      if (request.customerId() != null) {
        try (PreparedStatement q = statement(c,
            "SELECT FullName,Phone FROM dm.Customers WITH (HOLDLOCK) WHERE Id=?", request.customerId());
            ResultSet r = q.executeQuery()) {
          if (!r.next()) {
            throw new IllegalArgumentException("Khong tim thay khach hang.");
          }
          customer = r.getString(1);
          phone = r.getString(2);
        }
      }
      long invoice = insert(c, "INSERT dm.Invoices(RequestId,RequestHash,SoldAt,EmployeeId,CustomerId,"
          + "CustomerName,CustomerPhone,Subtotal,Discount,Total,PaymentMethod,PromotionCode) "
          + "OUTPUT INSERTED.Id VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", request.requestId(),
          request.fingerprint(), timestamp, session.employeeId(), request.customerId(), customer,
          phone, subtotal, discount, Money.total(subtotal, discount), request.payment(),
          request.promotionCode().isBlank() ? null : request.promotionCode());
      failures.hit("AFTER_INVOICE");
      for (ItemQuantity line : lines) {
        Item item = current.get(line.productId());
        long detail = insert(c, "INSERT dm.InvoiceDetails(InvoiceId,ProductId,SKU,ProductName,"
            + "UnitPrice,Quantity,WarrantyMonths) OUTPUT INSERTED.Id VALUES(?,?,?,?,?,?,?)",
            invoice, item.id(), item.sku(), item.name(), item.sellingPrice(), line.quantity(),
            item.warrantyMonths());
        stock.subtract(c, item.id(), "QUAY", line.quantity());
        long movement = stock.movement(c, request.requestId(), item.id(), "QUAY", -line.quantity(),
            "SALE", "HD-" + invoice, session.employeeId(), invoice, null, null);
        failures.hit("AFTER_STOCK");
        if (item.trackSerial()) {
          for (String serial : line.serials().stream().sorted().toList()) {
            long unit = stock.sellUnit(c, item.id(), serial);
            update(c, "INSERT dm.InvoiceUnits(DetailId,UnitId) VALUES(?,?)", detail, unit);
            stock.attachUnit(c, movement, unit);
            createWarranty(c, detail, unit, saleDate, item.warrantyMonths(), 1);
          }
        } else {
          createWarranty(c, detail, null, saleDate, item.warrantyMonths(), line.quantity());
        }
      }
      failures.hit("BEFORE_COMMIT");
      return invoices.load(c, invoice);
    });
  }

  private void createWarranty(Connection c, long detail, Long unit, LocalDate start,
      int months, int quantity) throws SQLException {
    if (months == 0) {
      return;
    }
    update(c, "INSERT dm.Warranties(DetailId,UnitId,StartDate,EndDate,WarrantyMonths,CoveredQuantity) "
        + "VALUES(?,?,?,?,?,?)", detail, unit, start, WarrantyRules.end(start, months), months, quantity);
  }

  private Long findRequest(Connection c, AuthService.Session session, SaleRequest request)
      throws SQLException {
    try (PreparedStatement s = statement(c,
        "SELECT Id,RequestHash,EmployeeId FROM dm.Invoices WHERE RequestId=?", request.requestId());
        ResultSet found = s.executeQuery()) {
      if (!found.next()) {
        return null;
      }
      if (found.getLong(3) != session.employeeId()
          || !found.getString(2).equals(request.fingerprint())) {
        throw new SecurityException("Ma thanh toan da dung boi nhan vien hoac noi dung khac.");
      }
      return found.getLong(1);
    }
  }

  /** Waits on the checkout lock, so a missing invoice is not an in-flight false negative. */
  public Sale resolve(AuthService.Session session, SaleRequest request) throws SQLException {
    return db.transaction(c -> {
      auth.require(c, session, Role.MANAGER, Role.CASHIER);
      lock(c, "dm:sale:" + request.requestId());
      Long id = findRequest(c, session, request);
      return id == null ? null : invoices.load(c, id);
    });
  }

  /** Atomically refuses cancellation after commit; fences delayed requests when no sale exists. */
  public boolean cancel(AuthService.Session session, SaleRequest request) throws SQLException {
    return db.transaction(c -> {
      auth.require(c, session, Role.MANAGER, Role.CASHIER);
      lock(c, "dm:sale:" + request.requestId());
      if (findRequest(c, session, request) != null) {
        return false;
      }
      RequestCancellationDao.cancel(c, "SALE", request.requestId(), request.fingerprint(),
          session.employeeId());
      return true;
    });
  }

  public Sale load(AuthService.Session session, long id) throws SQLException {
    return db.read(c -> {
      auth.require(c, session, Role.MANAGER, Role.CASHIER);
      return invoices.load(c, id);
    });
  }

  public List<LinkedHashMap<String, Object>> search(AuthService.Session session, String text,
      LocalDate from, LocalDate to) throws SQLException {
    Validation.dates(from, to);
    String pattern = ProductDao.like(Validation.optional(text, 180, "Tu khoa"));
    return db.read(c -> {
      auth.require(c, session, Role.MANAGER, Role.CASHIER);
      return rows(c, "SELECT TOP (1000) i.Id,i.Code,i.SoldAt,i.CustomerName,i.CustomerPhone,"
          + "e.FullName AS EmployeeName,i.Total,i.PaymentMethod FROM dm.Invoices i "
          + "JOIN dm.Employees e ON e.Id=i.EmployeeId WHERE i.SoldAt>=? AND i.SoldAt<? "
          + "AND (i.Code LIKE ? ESCAPE '~' OR i.CustomerName LIKE ? ESCAPE '~' "
          + "OR i.CustomerPhone LIKE ? ESCAPE '~' OR e.FullName LIKE ? ESCAPE '~') ORDER BY i.Id DESC",
          Validation.start(from), Validation.start(to.plusDays(1)), pattern, pattern, pattern, pattern);
    });
  }
}
