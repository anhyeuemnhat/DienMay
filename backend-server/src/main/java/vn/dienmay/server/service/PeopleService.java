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
import java.util.Objects;
import vn.dienmay.common.model.Role;
import vn.dienmay.common.model.Validation;
import vn.dienmay.server.db.ProductDao;
import vn.dienmay.server.db.SqlDatabase;

/** Customers, vendors and employee administration with the baseline role boundaries. */
public final class PeopleService {
  public record CustomerSummary(long id, String name, String phone) {}

  private final SqlDatabase db;
  private final AuthService auth;

  public PeopleService(SqlDatabase db, AuthService auth) {
    this.db = db;
    this.auth = auth;
  }

  public List<LinkedHashMap<String, Object>> customers(AuthService.Session session, String text)
      throws SQLException {
    String pattern = ProductDao.like(Validation.optional(text, 180, "Tu khoa"));
    return db.read(c -> {
      auth.require(c, session, Role.MANAGER, Role.CASHIER);
      return rows(c, "SELECT TOP (500) Id,FullName,Phone,Address FROM dm.Customers "
          + "WHERE FullName LIKE ? ESCAPE '~' OR Phone LIKE ? ESCAPE '~' ORDER BY Id DESC",
          pattern, pattern);
    });
  }

  public CustomerSummary customer(AuthService.Session session, long id) throws SQLException {
    if (id <= 0) {
      throw new IllegalArgumentException("Ma khach hang phai la so duong.");
    }
    return db.read(c -> {
      auth.require(c, session, Role.MANAGER, Role.CASHIER);
      try (PreparedStatement q = statement(c, "SELECT Id,FullName,Phone FROM dm.Customers WHERE Id=?",
          id); ResultSet r = q.executeQuery()) {
        if (!r.next()) {
          throw new IllegalArgumentException("Khong tim thay khach hang.");
        }
        return new CustomerSummary(r.getLong(1), r.getString(2), r.getString(3));
      }
    });
  }

  public long saveCustomer(AuthService.Session session, Long id, String name, String phone,
      String address) throws SQLException {
    String validName = Validation.required(name, 120, "Ho ten");
    String validPhone = Validation.optional(phone, 30, "Dien thoai");
    String validAddress = Validation.optional(address, 300, "Dia chi");
    return db.transaction(c -> {
      auth.require(c, session, Role.MANAGER, Role.CASHIER);
      if (id == null) {
        return insert(c, "INSERT dm.Customers(FullName,Phone,Address) OUTPUT INSERTED.Id VALUES(?,?,?)",
            validName, validPhone, validAddress);
      }
      if (update(c, "UPDATE dm.Customers SET FullName=?,Phone=?,Address=? WHERE Id=?",
          validName, validPhone, validAddress, id) != 1) {
        throw new IllegalArgumentException("Khong tim thay khach hang.");
      }
      return id;
    });
  }

  public List<LinkedHashMap<String, Object>> vendors(AuthService.Session session) throws SQLException {
    return db.read(c -> {
      auth.require(c, session, Role.MANAGER, Role.WAREHOUSE);
      return rows(c, "SELECT Id,Name,Phone,Address,Enabled FROM dm.Vendors ORDER BY Name");
    });
  }

  public long saveVendor(AuthService.Session session, Long id, String name, String phone,
      String address, boolean enabled) throws SQLException {
    String validName = Validation.required(name, 150, "Ten nha cung cap");
    String validPhone = Validation.optional(phone, 30, "Dien thoai");
    String validAddress = Validation.optional(address, 300, "Dia chi");
    return db.transaction(c -> {
      auth.require(c, session, Role.MANAGER);
      if (id == null) {
        return insert(c, "INSERT dm.Vendors(Name,Phone,Address,Enabled) "
            + "OUTPUT INSERTED.Id VALUES(?,?,?,?)", validName, validPhone, validAddress, enabled);
      }
      if (update(c, "UPDATE dm.Vendors SET Name=?,Phone=?,Address=?,Enabled=? WHERE Id=?",
          validName, validPhone, validAddress, enabled, id) != 1) {
        throw new IllegalArgumentException("Khong tim thay nha cung cap.");
      }
      return id;
    });
  }

  public List<LinkedHashMap<String, Object>> employees(AuthService.Session session) throws SQLException {
    return db.read(c -> {
      auth.require(c, session, Role.MANAGER);
      return rows(c, "SELECT Id,UserName,FullName,Phone,Role,Enabled,MustChange "
          + "FROM dm.Employees ORDER BY Id");
    });
  }

  public long saveEmployee(AuthService.Session session, Long id, String username, String name,
      String phone, Role role, boolean enabled, char[] password) throws SQLException {
    String validUser = Validation.required(username, 64, "Tai khoan");
    String validName = Validation.required(name, 120, "Ho ten");
    String validPhone = Validation.optional(phone, 30, "Dien thoai");
    String hash = password != null && password.length > 0 ? Passwords.hash(password) : null;
    if (id == null && hash == null) {
      throw new IllegalArgumentException("Can mat khau cho tai khoan moi.");
    }
    Objects.requireNonNull(role, "role");
    return db.transaction(c -> {
      lock(c, "dm:employee-admin");
      auth.require(c, session, Role.MANAGER);
      if (id == null) {
        return insert(c, "INSERT dm.Employees(UserName,FullName,Phone,PasswordHash,Role,Enabled,"
            + "MustChange) OUTPUT INSERTED.Id VALUES(?,?,?,?,?,?,1)",
            validUser, validName, validPhone, hash, role.name(), enabled);
      }
      if (id == session.employeeId() && (!enabled || role != Role.MANAGER)) {
        throw new IllegalArgumentException("Khong tu khoa hoac ha quyen tai khoan dang dung.");
      }
      if (id == session.employeeId() && hash != null) {
        throw new IllegalArgumentException("Dung menu Doi mat khau cho tai khoan dang dang nhap.");
      }
      if (!enabled || role != Role.MANAGER) {
        try (PreparedStatement q = statement(c, "SELECT COUNT(*) FROM dm.Employees "
            + "WITH (UPDLOCK,HOLDLOCK) WHERE Role='MANAGER' AND Enabled=1 AND Id<>?", id);
            ResultSet r = q.executeQuery()) {
          if (!r.next() || r.getInt(1) == 0) {
            throw new IllegalArgumentException("Phai giu it nhat mot quan ly hoat dong.");
          }
        }
      }
      if (update(c, "UPDATE dm.Employees SET UserName=?,FullName=?,Phone=?,Role=?,Enabled=?,"
          + "AuthVersion=AuthVersion+1,FailedAttempts=0,LockUntil=NULL WHERE Id=?",
          validUser, validName, validPhone, role.name(), enabled, id) != 1) {
        throw new IllegalArgumentException("Khong tim thay nhan vien.");
      }
      if (hash != null) {
        update(c, "UPDATE dm.Employees SET PasswordHash=?,MustChange=1 WHERE Id=?", hash, id);
      }
      return id;
    });
  }
}
