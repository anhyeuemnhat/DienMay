package vn.dienmay.server.db;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Bound SqlDatabase helpers. SqlDatabase text is application-owned; values are never concatenated. */
public final class Jdbc {
  private Jdbc() {}

  public static PreparedStatement statement(Connection c, String sql, Object... args)
      throws SQLException {
    int expected = SqlParameters.count(sql);
    if (expected != args.length) {
      throw new SQLException("SqlDatabase parameter mismatch: " + expected + " placeholders, "
          + args.length + " arguments", "07001");
    }
    PreparedStatement s = c.prepareStatement(sql);
    try {
      s.setQueryTimeout(20);
      bind(s, args);
      return s;
    } catch (SQLException | RuntimeException e) {
      try {
        s.close();
      } catch (SQLException close) {
        e.addSuppressed(close);
      }
      throw e;
    }
  }

  public static void bind(PreparedStatement s, Object... args) throws SQLException {
    for (int i = 0; i < args.length; i++) {
      Object value = args[i];
      if (value instanceof LocalDate date) {
        s.setDate(i + 1, java.sql.Date.valueOf(date));
      } else if (value instanceof Instant instant) {
        s.setTimestamp(i + 1, Timestamp.valueOf(LocalDateTime.ofInstant(instant, ZoneOffset.UTC)));
      } else if (value instanceof UUID) {
        s.setString(i + 1, value.toString());
      } else if (value instanceof String text) {
        s.setNString(i + 1, text);
      } else if (value instanceof BigDecimal decimal) {
        s.setBigDecimal(i + 1, decimal);
      } else {
        s.setObject(i + 1, value);
      }
    }
  }

  public static int update(Connection c, String sql, Object... args) throws SQLException {
    try (PreparedStatement s = statement(c, sql, args)) {
      return s.executeUpdate();
    }
  }

  public static long insert(Connection c, String sql, Object... args) throws SQLException {
    try (PreparedStatement s = statement(c, sql, args); ResultSet r = s.executeQuery()) {
      if (!r.next()) {
        throw new SQLException("Khong nhan duoc ma sau khi luu.");
      }
      return r.getLong(1);
    }
  }

  public static void lock(Connection c, String resource) throws SQLException {
    String sql = "DECLARE @r int; EXEC @r=sys.sp_getapplock @Resource=?, "
        + "@LockMode='Exclusive', @LockOwner='Transaction', @LockTimeout=10000; SELECT @r";
    try (PreparedStatement s = statement(c, sql, resource); ResultSet r = s.executeQuery()) {
      if (!r.next() || r.getInt(1) < 0) {
        throw new SQLException("Giao dich dang xu ly. Giu nguyen ma yeu cau va thu lai.", "40001");
      }
    }
  }

  public static Instant now(Connection c) throws SQLException {
    try (PreparedStatement s = statement(c, "SELECT SYSUTCDATETIME()");
        ResultSet r = s.executeQuery()) {
      if (!r.next()) {
        throw new SQLException("Khong doc duoc thoi gian SQL Server.");
      }
      return instant(r, 1);
    }
  }

  public static Instant instant(ResultSet r, int column) throws SQLException {
    Timestamp t = r.getTimestamp(column);
    return t == null ? null : t.toLocalDateTime().toInstant(ZoneOffset.UTC);
  }

  public static List<LinkedHashMap<String, Object>> rows(Connection c, String sql, Object... args)
      throws SQLException {
    List<LinkedHashMap<String, Object>> list = new ArrayList<>();
    try (PreparedStatement s = statement(c, sql, args); ResultSet r = s.executeQuery()) {
      ResultSetMetaData metadata = r.getMetaData();
      while (r.next()) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= metadata.getColumnCount(); i++) {
          Object value = r.getObject(i);
          if (value instanceof Timestamp) {
            value = instant(r, i).toString();
          } else if (value instanceof java.sql.Date date) {
            value = date.toLocalDate().toString();
          }
          row.put(metadata.getColumnLabel(i), value);
        }
        list.add(row);
      }
    }
    return list;
  }
}
