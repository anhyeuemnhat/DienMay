package vn.dienmay.server.service;

import static vn.dienmay.server.db.Jdbc.insert;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import vn.dienmay.common.model.Money;
import vn.dienmay.common.model.Role;
import vn.dienmay.common.model.Validation;
import vn.dienmay.server.db.SqlDatabase;

public final class PromotionService {
  private final SqlDatabase db;
  private final AuthService auth;

  public PromotionService(SqlDatabase db, AuthService auth) {
    this.db = db;
    this.auth = auth;
  }

  public List<LinkedHashMap<String, Object>> list(AuthService.Session session) throws SQLException {
    return db.read(c -> {
      auth.require(c, session, Role.MANAGER, Role.CASHIER);
      return rows(c, "SELECT Id,Code,Name,StartDate,EndDate,PercentOff,Enabled "
          + "FROM dm.Promotions ORDER BY Id DESC");
    });
  }

  public long save(AuthService.Session session, Long id, String code, String name,
      LocalDate from, LocalDate to, BigDecimal percent, boolean enabled) throws SQLException {
    String normalized = Validation.required(code, 50, "Ma khuyen mai").toUpperCase(Locale.ROOT);
    String validName = Validation.required(name, 150, "Ten khuyen mai");
    Validation.dates(from, to);
    Money.discount(Money.ZERO, percent);
    Money.exact(percent);
    return db.transaction(c -> {
      auth.require(c, session, Role.MANAGER);
      if (id == null) {
        return insert(c, "INSERT dm.Promotions(Code,Name,StartDate,EndDate,PercentOff,Enabled) "
            + "OUTPUT INSERTED.Id VALUES(?,?,?,?,?,?)",
            normalized, validName, from, to, percent, enabled);
      }
      if (update(c, "UPDATE dm.Promotions SET Code=?,Name=?,StartDate=?,EndDate=?,PercentOff=?,"
          + "Enabled=? WHERE Id=?", normalized, validName, from, to, percent, enabled, id) != 1) {
        throw new IllegalArgumentException("Khong tim thay khuyen mai.");
      }
      return id;
    });
  }

  static BigDecimal discount(Connection c, String code, BigDecimal manual, BigDecimal subtotal,
      Instant now, boolean lock) throws SQLException {
    if (code == null || code.isBlank()) {
      Money.total(subtotal, manual);
      return manual;
    }
    if (manual.signum() != 0) {
      throw new IllegalArgumentException("Khong cong giam truc tiep voi khuyen mai.");
    }
    LocalDate day = now.atZone(Validation.ZONE).toLocalDate();
    String query = "SELECT PercentOff FROM dm.Promotions " + (lock ? "WITH (HOLDLOCK) " : "")
        + "WHERE Code=? AND Enabled=1 AND StartDate<=? AND EndDate>=?";
    try (PreparedStatement s = statement(c, query, code, day, day); ResultSet r = s.executeQuery()) {
      if (!r.next()) {
        throw new IllegalArgumentException("Ma khuyen mai khong ton tai hoac da het hieu luc.");
      }
      return Money.discount(subtotal, r.getBigDecimal(1));
    }
  }
}
