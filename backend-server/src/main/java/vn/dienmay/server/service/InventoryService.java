package vn.dienmay.server.service;

import static vn.dienmay.server.db.Jdbc.insert;
import static vn.dienmay.server.db.Jdbc.lock;
import static vn.dienmay.server.db.Jdbc.rows;
import static vn.dienmay.server.db.Jdbc.statement;
import static vn.dienmay.server.db.Jdbc.update;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import vn.dienmay.common.model.InventoryRequest;
import vn.dienmay.common.model.Item;
import vn.dienmay.common.model.Role;
import vn.dienmay.common.model.Validation;
import vn.dienmay.server.db.ProductDao;
import vn.dienmay.server.db.RequestCancellationDao;
import vn.dienmay.server.db.SqlDatabase;
import vn.dienmay.server.db.StockDao;

/** Receive/transfer uses one database transaction and stable request fingerprints. */
public final class InventoryService {
  private final SqlDatabase db;
  private final AuthService auth;
  private final ProductDao products;
  private final StockDao stock;

  public InventoryService(SqlDatabase db, AuthService auth) {
    this(db, auth, new ProductDao(), new StockDao());
  }

  public InventoryService(SqlDatabase db, AuthService auth, ProductDao products, StockDao stock) {
    this.db = db;
    this.auth = auth;
    this.products = products;
    this.stock = stock;
  }

  public void receive(AuthService.Session session, InventoryRequest request) throws SQLException {
    if (request.from() != null || !request.to().equals("KHO") || request.vendorId() == null) {
      throw new IllegalArgumentException("Nhap hang can nha cung cap va vi tri KHO.");
    }
    execute(session, request, true);
  }

  public void transfer(AuthService.Session session, InventoryRequest request) throws SQLException {
    if (request.from() == null) {
      throw new IllegalArgumentException("Can vi tri nguon chuyen.");
    }
    execute(session, request, false);
  }

  private void execute(AuthService.Session session, InventoryRequest request, boolean receiving)
      throws SQLException {
    db.transaction(c -> {
      auth.require(c, session, Role.MANAGER, Role.WAREHOUSE);
      lock(c, "dm:inventory:" + request.id());
      try (PreparedStatement q = statement(c,
          "SELECT RequestHash,EmployeeId FROM dm.InventoryRequests WHERE RequestId=?", request.id());
          ResultSet found = q.executeQuery()) {
        if (found.next()) {
          if (!found.getString(1).equals(request.fingerprint())
              || found.getLong(2) != session.employeeId()) {
            throw new IllegalArgumentException("Ma yeu cau da dung cho noi dung/nhan vien khac.");
          }
          return null;
        }
      }
      RequestCancellationDao.requireActive(c, "STOCK", request.id(), request.fingerprint(),
          session.employeeId());
      Item item = products.get(c, request.productId(), true);
      StockDao.checkSerials(item, request.quantity(), request.serials());
      if (receiving) {
        if (!item.enabled()) {
          throw new IllegalArgumentException("San pham da ngung kinh doanh.");
        }
        try (PreparedStatement q = statement(c,
            "SELECT Id FROM dm.Vendors WITH (HOLDLOCK) WHERE Id=? AND Enabled=1", request.vendorId());
            ResultSet found = q.executeQuery()) {
          if (!found.next()) {
            throw new IllegalArgumentException("Nha cung cap khong hoat dong.");
          }
        }
        stock.add(c, item.id(), "KHO", request.quantity());
        long movement = stock.movement(c, request.id(), item.id(), "KHO", request.quantity(),
            "RECEIVE", request.reference(), session.employeeId(), null, request.vendorId(),
            request.unitCost());
        for (String serial : request.serials()) {
          long unit = insert(c, "INSERT dm.ProductUnits(ProductId,Serial,Location) "
              + "OUTPUT INSERTED.Id VALUES(?,?,'KHO')", item.id(), serial);
          stock.attachUnit(c, movement, unit);
        }
        update(c, "UPDATE dm.Products SET CostPrice=?,Revision=Revision+1 WHERE Id=?",
            request.unitCost(), item.id());
      } else {
        stock.subtract(c, item.id(), request.from(), request.quantity());
        stock.add(c, item.id(), request.to(), request.quantity());
        long out = stock.movement(c, request.id(), item.id(), request.from(), -request.quantity(),
            "TRANSFER", request.reference(), session.employeeId(), null, null, null);
        long in = stock.movement(c, request.id(), item.id(), request.to(), request.quantity(),
            "TRANSFER", request.reference(), session.employeeId(), null, null, null);
        for (String serial : request.serials()) {
          long unit = stock.transferUnit(c, item.id(), serial, request.from(), request.to());
          stock.attachUnit(c, out, unit);
          stock.attachUnit(c, in, unit);
        }
      }
      update(c, "INSERT dm.InventoryRequests(RequestId,RequestHash,EmployeeId) VALUES(?,?,?)",
          request.id(), request.fingerprint(), session.employeeId());
      return null;
    });
  }

  public boolean resolve(AuthService.Session session, InventoryRequest request) throws SQLException {
    return db.transaction(c -> {
      auth.require(c, session, Role.MANAGER, Role.WAREHOUSE);
      lock(c, "dm:inventory:" + request.id());
      try (PreparedStatement s = statement(c,
          "SELECT RequestHash,EmployeeId FROM dm.InventoryRequests WHERE RequestId=?", request.id());
          ResultSet r = s.executeQuery()) {
        if (!r.next()) {
          return false;
        }
        if (!r.getString(1).equals(request.fingerprint()) || r.getLong(2) != session.employeeId()) {
          throw new SecurityException("Yeu cau khong thuoc phien nay.");
        }
        return true;
      }
    });
  }

  /** A durable cancellation fence uses the same lock as receive/transfer and replay resolution. */
  public boolean cancel(AuthService.Session session, InventoryRequest request) throws SQLException {
    return db.transaction(c -> {
      auth.require(c, session, Role.MANAGER, Role.WAREHOUSE);
      lock(c, "dm:inventory:" + request.id());
      try (PreparedStatement q = statement(c,
          "SELECT RequestHash,EmployeeId FROM dm.InventoryRequests WHERE RequestId=?", request.id());
          ResultSet found = q.executeQuery()) {
        if (found.next()) {
          if (!found.getString(1).equals(request.fingerprint())
              || found.getLong(2) != session.employeeId()) {
            throw new SecurityException("Yeu cau thuoc nhan vien/noi dung khac.");
          }
          return false;
        }
      }
      RequestCancellationDao.cancel(c, "STOCK", request.id(), request.fingerprint(), session.employeeId());
      return true;
    });
  }

  public List<LinkedHashMap<String, Object>> history(AuthService.Session session, String sku)
      throws SQLException {
    String pattern = ProductDao.like(Validation.optional(sku, 64, "SKU"));
    return db.read(c -> {
      auth.require(c, session, Role.MANAGER, Role.WAREHOUSE);
      return rows(c, "SELECT TOP (1000) m.Id,m.CreatedAt,p.SKU,m.Location,m.Delta,m.Kind,m.Reference,"
          + "e.FullName AS EmployeeName,m.UnitCost,m.RequestId FROM dm.StockMovements m "
          + "JOIN dm.Products p ON p.Id=m.ProductId JOIN dm.Employees e ON e.Id=m.EmployeeId "
          + "WHERE p.SKU LIKE ? ESCAPE '~' ORDER BY m.Id DESC", pattern);
    });
  }

  public List<LinkedHashMap<String, Object>> unitHistory(AuthService.Session session, String serial)
      throws SQLException {
    return db.read(c -> {
      auth.require(c, session, Role.MANAGER, Role.WAREHOUSE);
      return rows(c, "SELECT u.Serial,p.SKU,u.Status,m.CreatedAt,m.Kind,m.Location,m.Delta,m.Reference "
          + "FROM dm.ProductUnits u JOIN dm.Products p ON p.Id=u.ProductId "
          + "LEFT JOIN dm.StockMovementUnits mu ON mu.UnitId=u.Id "
          + "LEFT JOIN dm.StockMovements m ON m.Id=mu.MovementId WHERE u.Serial=? ORDER BY m.Id",
          Validation.serial(serial));
    });
  }
}
