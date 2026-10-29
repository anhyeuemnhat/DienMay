package vn.dienmay.tests;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** Explicit JDBC double: verifies resource/control flow and bindings, NEVER SQL Server semantics. */
public final class JdbcDouble implements InvocationHandler {
  public int commits;
  public int rollbacks;
  public int closes;
  public boolean failCommit;
  public boolean failRollback;
  public boolean failBind;
  public int statementCloses;
  public String lastSql;
  public final TreeMap<Integer, Object> bound = new TreeMap<>();
  public final List<String> queries = new ArrayList<>();
  public List<Object[]> resultRows = List.of(new Object[] {7L}, new Object[] {8L});

  public Connection connection() {
    return proxy(Connection.class, this);
  }

  public static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
  }

  public static Object defaultValue(Class<?> type) {
    if (type == boolean.class) {
      return false;
    }
    if (type == int.class) {
      return 0;
    }
    if (type == long.class) {
      return 0L;
    }
    return null;
  }

  public static ResultSet rows(List<Object[]> values) {
    int[] index = {-1};
    return proxy(ResultSet.class, (p, method, args) -> {
      if (method.getName().equals("next")) {
        return ++index[0] < values.size();
      }
      if (method.getName().startsWith("get") && args != null && args[0] instanceof Integer column) {
        Object value = values.get(index[0])[column - 1];
        return switch (method.getName()) {
          case "getLong" -> value == null ? 0L : ((Number) value).longValue();
          case "getInt" -> value == null ? 0 : ((Number) value).intValue();
          case "getString" -> value == null ? null : value.toString();
          case "getBoolean" -> Boolean.TRUE.equals(value);
          default -> value;
        };
      }
      return defaultValue(method.getReturnType());
    });
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    return switch (method.getName()) {
      case "commit" -> {
        commits++;
        if (failCommit) {
          throw new SQLException("uncertain commit");
        }
        yield null;
      }
      case "rollback" -> {
        rollbacks++;
        if (failRollback) {
          throw new SQLException("rollback failed");
        }
        yield null;
      }
      case "close" -> {
        closes++;
        yield null;
      }
      case "createStatement" -> proxy(Statement.class,
          (p, m, a) -> defaultValue(m.getReturnType()));
      case "prepareStatement" -> {
        lastSql = (String) args[0];
        queries.add(lastSql);
        bound.clear();
        yield proxy(PreparedStatement.class, (p, m, a) -> {
          if (m.getName().startsWith("set") && a != null && a.length == 2 && a[0] instanceof Integer i) {
            if (failBind) {
              throw new SQLException("injected bind failure");
            }
            bound.put(i, a[1]);
          }
          if (m.getName().equals("executeQuery")) {
            return rows(resultRows);
          }
          if (m.getName().equals("executeUpdate")) {
            return 1;
          }
          if (m.getName().equals("close")) {
            statementCloses++;
          }
          return defaultValue(m.getReturnType());
        });
      }
      default -> defaultValue(method.getReturnType());
    };
  }
}
