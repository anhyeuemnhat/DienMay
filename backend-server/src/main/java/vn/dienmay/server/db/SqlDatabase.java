package vn.dienmay.server.db;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Properties;

/** Backend-only connection boundary. Every unit of work owns and closes its connection. */
public final class SqlDatabase {
  @FunctionalInterface
  public interface Factory {
    Connection open() throws SQLException;
  }

  @FunctionalInterface
  public interface Work<T> {
    T run(Connection connection) throws SQLException;
  }

  private final Factory factory;
  private final Properties properties;

  public SqlDatabase() {
    properties = load();
    factory = this::connect;
  }

  public SqlDatabase(Factory factory) {
    this.factory = Objects.requireNonNull(factory, "factory");
    properties = new Properties();
  }

  public String setting(String key, String fallback) {
    return properties.getProperty(key, fallback);
  }

  private static Properties load() {
    Properties p = new Properties();
    String path = System.getProperty("dienmay.config",
        System.getenv().getOrDefault("DIENMAY_CONFIG", "config/database.properties"));
    if (Files.isRegularFile(Path.of(path))) {
      try (Reader reader = Files.newBufferedReader(Path.of(path), StandardCharsets.UTF_8)) {
        p.load(reader);
      } catch (IOException e) {
        throw new IllegalStateException("Khong doc duoc cau hinh backend: " + path, e);
      }
    }
    override(p, "db.url", "DIENMAY_DB_URL");
    override(p, "db.user", "DIENMAY_DB_USER");
    override(p, "db.password", "DIENMAY_DB_PASSWORD");
    return p;
  }

  private static void override(Properties p, String key, String env) {
    String value = System.getenv(env);
    if (value != null) {
      p.setProperty(key, value);
    }
  }

  private Connection connect() throws SQLException {
    String url = properties.getProperty("db.url", "");
    if (!url.startsWith("jdbc:sqlserver://")) {
      throw new SQLException("Chua cau hinh SQL Server tren backend.");
    }
    Properties credentials = new Properties();
    credentials.setProperty("user", properties.getProperty("db.user", ""));
    credentials.setProperty("password", properties.getProperty("db.password", ""));
    credentials.setProperty("loginTimeout", "5");
    credentials.setProperty("socketTimeout", "20000");
    try {
      Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
    } catch (ClassNotFoundException e) {
      throw new SQLException("Thieu Microsoft JDBC Driver trong backend-server/lib.", e);
    }
    return DriverManager.getConnection(url, credentials);
  }

  public Connection open() throws SQLException {
    return factory.open();
  }

  public <T> T read(Work<T> work) throws SQLException {
    try (Connection c = open()) {
      return work.run(c);
    }
  }

  /** Preserves the baseline SQL Server application-lock transaction boundary. */
  public <T> T transaction(Work<T> work) throws SQLException {
    try (Connection c = open()) {
      c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
      c.setAutoCommit(false);
      try {
        // The table SELECT starts the manual transaction before sp_getapplock.
        // An extra BEGIN would be incorrect with IMPLICIT_TRANSACTIONS enabled.
        try (Statement s = c.createStatement()) {
          s.setQueryTimeout(20);
          s.execute("SET XACT_ABORT ON; SET LOCK_TIMEOUT 10000; "
              + "SELECT Version FROM dm.SchemaVersion WHERE Version=1;");
        }
        T result = work.run(c);
        c.commit();
        return result;
      } catch (SQLException | RuntimeException | Error e) {
        try {
          c.rollback();
        } catch (SQLException rollback) {
          e.addSuppressed(rollback);
        }
        throw e;
      }
    }
  }

  public String checkConnection() throws SQLException {
    return read(c -> {
      try (PreparedStatement s = Jdbc.statement(c,
          "SELECT DB_NAME(), Version FROM dm.SchemaVersion WHERE Version=5");
          ResultSet r = s.executeQuery()) {
        if (!r.next()) {
          throw new SQLException("Thieu schema dm phien ban 5.");
        }
        return r.getString(1) + " / schema " + r.getInt(2);
      }
    });
  }
}
