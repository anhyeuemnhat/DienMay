package vn.dienmay.server.service;

import static vn.dienmay.server.db.Jdbc.insert;
import static vn.dienmay.server.db.Jdbc.lock;
import static vn.dienmay.server.db.Jdbc.statement;
import static vn.dienmay.server.db.Jdbc.update;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import vn.dienmay.common.api.BusinessException;
import vn.dienmay.common.model.Item;
import vn.dienmay.common.model.ReversalPreview;
import vn.dienmay.common.model.ReversalRequest;
import vn.dienmay.common.model.Role;
import vn.dienmay.common.model.Validation;
import vn.dienmay.server.db.Jdbc;
import vn.dienmay.server.db.ProductDao;
import vn.dienmay.server.db.RequestCancellationDao;
import vn.dienmay.server.db.SqlDatabase;
import vn.dienmay.server.db.StockDao;

/** Conservative controlled reversal: no later movement, exact cost provenance, original history retained. */
public final class InventoryReversalService {
  private final SqlDatabase db;
  private final AuthService auth;
  private final ProductDao products = new ProductDao();
  private final StockDao stock = new StockDao();
  public InventoryReversalService(SqlDatabase db, AuthService auth) { this.db = db; this.auth = auth; }

  private record State(long movementId, Item item, int quantity, int available, BigDecimal cost,
      BigDecimal previousCost, Long costRevision, Long vendor, List<Long> units, String blocked) {
    private ReversalPreview preview() {
      return new ReversalPreview(movementId, item.id(), item.revision(), item.sku(), item.name(),
          quantity, available, cost, previousCost, blocked.isEmpty(), blocked);
    }
  }

  public List<LinkedHashMap<String, Object>> receipts(AuthService.Session session, String search) throws SQLException {
    String pattern = ProductDao.like(Validation.optional(search, 180, "SKU / chứng từ"));
    return db.read(c -> {
      auth.require(c, session, Role.MANAGER, Role.WAREHOUSE);
      return Jdbc.rows(c, "SELECT TOP(1000) m.Id,m.RequestId,m.CreatedAt,p.SKU,p.Name,m.Delta AS Quantity,"
          + "m.UnitCost,m.Reference,v.Name AS VendorName,e.FullName AS EmployeeName,"
          + "CASE WHEN r.Id IS NULL THEN 'POSTED' ELSE 'REVERSED' END AS Status,"
          + "r.ReversalMovementId,r.Reason AS ReversalReason,p.Revision,m.PreviousCostPrice "
          + "FROM dm.StockMovements m JOIN dm.Products p ON p.Id=m.ProductId "
          + "LEFT JOIN dm.Vendors v ON v.Id=m.VendorId JOIN dm.Employees e ON e.Id=m.EmployeeId "
          + "LEFT JOIN dm.InventoryReversals r ON r.OriginalMovementId=m.Id "
          + "WHERE m.Kind='RECEIVE' AND (p.SKU LIKE ? ESCAPE '~' OR m.Reference LIKE ? ESCAPE '~') "
          + "ORDER BY m.Id DESC", pattern, pattern);
    });
  }

  public ReversalPreview preview(AuthService.Session session, long movementId) throws SQLException {
    if (movementId <= 0) { throw new IllegalArgumentException("Chọn phiếu nhập."); }
    return db.transaction(c -> {
      auth.require(c, session, Role.MANAGER);
      lock(c, "dm:receipt:" + movementId);
      return state(c, movementId).preview();
    });
  }

  public long reverse(AuthService.Session session, ReversalRequest request) throws SQLException {
    return db.transaction(c -> {
      auth.require(c, session, Role.MANAGER);
      lock(c, "dm:reversal:" + request.id());
      Long existing = committed(c, session, request);
      if (existing != null) { return existing; }
      RequestCancellationDao.requireActive(c, "REVERSAL", request.id(), request.fingerprint(), session.employeeId());
      lock(c, "dm:receipt:" + request.movementId());
      State state = state(c, request.movementId());
      if (state.item().revision() != request.productRevision()) {
        throw new BusinessException(409, "REVISION_CONFLICT", "Sản phẩm đã thay đổi. Tải lại và xác nhận lại.");
      }
      if (!state.blocked().isEmpty()) { throw new BusinessException(409, "REVERSAL_BLOCKED", state.blocked()); }
      Item item = state.item();
      stock.subtract(c, item.id(), "KHO", state.quantity());
      long movement = stock.movement(c, request.id(), item.id(), "KHO", -state.quantity(),
          "RECEIVE_REVERSAL", "Reversal of receipt #" + request.movementId(), session.employeeId(),
          null, state.vendor(), state.cost());
      for (long unit : state.units()) {
        if (update(c, "UPDATE dm.ProductUnits SET Status='REVERSED' WHERE Id=? AND ProductId=? "
            + "AND Location='KHO' AND Status='IN_STOCK'", unit, item.id()) != 1) {
          throw new BusinessException(409, "SERIAL_CHANGED", "Serial đã thay đổi. Không đảo phiếu.");
        }
        stock.attachUnit(c, movement, unit); // Retain both original and compensating unit history.
      }
      if (update(c, "UPDATE dm.Products SET CostPrice=?,CostRevision=CostRevision+1,Revision=Revision+1 "
          + "WHERE Id=? AND Revision=? AND CostRevision=?", state.previousCost(), item.id(),
          item.revision(), state.costRevision()) != 1) {
        throw new BusinessException(409, "COST_PROVENANCE_CHANGED", "Lịch sử giá vốn đã thay đổi.");
      }
      insert(c, "INSERT dm.InventoryReversals(RequestId,RequestHash,OriginalMovementId,ReversalMovementId,"
          + "EmployeeId,Reason,PreviousCostPrice,RestoredCostPrice) OUTPUT INSERTED.Id VALUES(?,?,?,?,?,?,?,?)",
          request.id(), request.fingerprint(), request.movementId(), movement, session.employeeId(),
          request.reason(), item.costPrice(), state.previousCost());
      QualityService.audit(c, "receipt", request.movementId(), "RECEIPT_REVERSED",
          "original=" + request.movementId() + ";quantity=" + state.quantity() + ";cost=" + item.costPrice(),
          "movement=" + movement + ";restoredCost=" + state.previousCost() + ";request=" + request.id(),
          request.reason(), session.employeeId());
      return movement;
    });
  }

  public boolean resolve(AuthService.Session session, ReversalRequest request) throws SQLException {
    return db.transaction(c -> {
      auth.require(c, session, Role.MANAGER);
      lock(c, "dm:reversal:" + request.id());
      return committed(c, session, request) != null;
    });
  }

  public boolean cancel(AuthService.Session session, ReversalRequest request) throws SQLException {
    return db.transaction(c -> {
      auth.require(c, session, Role.MANAGER);
      lock(c, "dm:reversal:" + request.id());
      if (committed(c, session, request) != null) { return false; }
      RequestCancellationDao.cancel(c, "REVERSAL", request.id(), request.fingerprint(), session.employeeId());
      return true;
    });
  }

  private static Long committed(Connection c, AuthService.Session session, ReversalRequest request) throws SQLException
      {
    try (PreparedStatement s = statement(c, "SELECT RequestHash,EmployeeId,ReversalMovementId "
        + "FROM dm.InventoryReversals WHERE RequestId=?", request.id()); ResultSet rs = s.executeQuery()) {
      if (!rs.next()) { return null; }
      if (!request.fingerprint().equals(rs.getString(1)) || session.employeeId() != rs.getLong(2)) {
        throw new SecurityException("Mã yêu cầu thuộc nhân viên hoặc nội dung khác.");
      }
      return rs.getLong(3);
    }
  }

  private State state(Connection c, long movementId) throws SQLException {
    long product;
    int quantity;
    BigDecimal cost;
    BigDecimal previous;
    Long costRevision;
    Long vendor;
    try (PreparedStatement s = statement(c, "SELECT ProductId,Delta,UnitCost,PreviousCostPrice,CostRevisionAfter,"
        + "VendorId FROM dm.StockMovements WITH(HOLDLOCK) WHERE Id=? AND Kind='RECEIVE' AND Location='KHO'",
        movementId); ResultSet rs = s.executeQuery()) {
      if (!rs.next()) { throw new BusinessException(404, "RECEIPT_NOT_FOUND", "Không tìm thấy phiếu nhập gốc."); }
      product = rs.getLong(1);
      quantity = rs.getInt(2);
      cost = rs.getBigDecimal(3);
      previous = rs.getBigDecimal(4);
      costRevision = rs.getObject(5) == null ? null : rs.getLong(5);
      vendor = rs.getObject(6) == null ? null : rs.getLong(6);
    }
    // Every receive, transfer and sale also takes the same product row lock through ProductDao.
    Item item = products.get(c, product, true);
    int available = (int) scalar(c,
        "SELECT Quantity FROM dm.Stock WITH(UPDLOCK,HOLDLOCK) WHERE ProductId=? AND Location='KHO'", product);
    List<String> reasons = new ArrayList<>();
    if (scalar(c, "SELECT COUNT(*) FROM dm.InventoryReversals WITH(HOLDLOCK) WHERE OriginalMovementId=?",
        movementId) != 0) { reasons.add("Phiếu nhập đã đảo; không đảo lần hai."); }
    if (quantity <= 0 || available < quantity) { reasons.add("Không đủ tồn KHO để đảo phiếu."); }
    if (previous == null || costRevision == null || cost == null) {
      reasons.add("Phiếu lịch sử thiếu snapshot giá vốn; cần Quản lý đối chiếu, không tự đoán giá.");
    } else if (scalar(c, "SELECT CostRevision FROM dm.Products WHERE Id=?", product) != costRevision
        || item.costPrice().compareTo(cost) != 0) {
      reasons.add("Giá vốn đã được cập nhật sau phiếu này; không khôi phục giá cũ không an toàn.");
    }
    if (scalar(c, "SELECT COUNT(*) FROM dm.StockMovements WITH(HOLDLOCK) WHERE ProductId=? AND Id>?",
        product, movementId) != 0) {
      reasons.add("Sản phẩm có giao dịch kho/bán hàng sau phiếu nhập. Không chứng minh được an toàn để đảo.");
    }
    List<Long> units = new ArrayList<>();
    try (PreparedStatement s = statement(c, "SELECT u.Id,u.ProductId,u.Location,u.Status FROM dm.StockMovementUnits mu "
        + "JOIN dm.ProductUnits u WITH(UPDLOCK,HOLDLOCK) ON u.Id=mu.UnitId WHERE mu.MovementId=?", movementId);
        ResultSet rs = s.executeQuery()) {
      while (rs.next()) {
        units.add(rs.getLong(1));
        if (rs.getLong(2) != product || !"KHO".equals(rs.getString(3)) || !"IN_STOCK".equals(rs.getString(4))) {
          reasons.add("Serial đã bán/chuyển/đảo hoặc không đúng sản phẩm.");
        }
      }
    }
    if (item.trackSerial() ? units.size() != quantity : !units.isEmpty()) {
      reasons.add("Số Serial gốc không khớp; không tự xóa hoặc sửa lịch sử.");
    }
    if (scalar(c, "SELECT COUNT(*) FROM dm.StockMovementUnits original JOIN dm.StockMovementUnits later "
        + "WITH(HOLDLOCK) ON later.UnitId=original.UnitId WHERE original.MovementId=? AND later.MovementId<>?",
        movementId, movementId) != 0) {
      reasons.add("Thiết bị đã tham gia giao dịch khác.");
    }
    if (scalar(c, "SELECT COUNT(*) FROM dm.InvoiceUnits i WITH(HOLDLOCK) JOIN dm.StockMovementUnits mu "
        + "ON mu.UnitId=i.UnitId WHERE mu.MovementId=?", movementId) != 0
        || scalar(c, "SELECT COUNT(*) FROM dm.Warranties w WITH(HOLDLOCK) JOIN dm.StockMovementUnits mu "
            + "ON mu.UnitId=w.UnitId WHERE mu.MovementId=?", movementId) != 0) {
      reasons.add("Thiết bị có liên kết hóa đơn/bảo hành cần bảo toàn.");
    }
    return new State(movementId, item, quantity, available, cost, previous, costRevision, vendor,
        List.copyOf(units), String.join("\n", reasons.stream().distinct().toList()));
  }

  private static long scalar(Connection c, String sql, Object... values) throws SQLException {
    try (PreparedStatement s = statement(c, sql, values); ResultSet rs = s.executeQuery()) {
      if (!rs.next()) { throw new SQLException("Missing expected inventory state; no reversal allowed."); }
      return rs.getLong(1);
    }
  }
}
