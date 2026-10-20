package ImplementationDB;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.Properties;

/** Connection boundary: external configuration, no cached ResultSets or shared connections. */
public final class SQL {
    @FunctionalInterface public interface Factory { Connection open() throws SQLException; }
    @FunctionalInterface public interface Work<T> { T run(Connection c) throws SQLException; }
    private final Factory factory;
    private final Properties properties;
    public SQL() { this.properties=load(); this.factory=this::connect; }
    public SQL(Factory factory) { this.factory=factory; this.properties=new Properties(); }
    public String setting(String key, String fallback) { return properties.getProperty(key,fallback); }
    private static Properties load() {
        Properties p=new Properties();
        String path=System.getProperty("dienmay.config",System.getenv().getOrDefault("DIENMAY_CONFIG","config/database.properties"));
        if (Files.isRegularFile(Path.of(path))) {
            try (Reader r=Files.newBufferedReader(Path.of(path),StandardCharsets.UTF_8)) { p.load(r); }
            catch(IOException e) { throw new IllegalStateException("Không đọc được cấu hình: "+path,e); }
        }
        override(p,"db.url","DIENMAY_DB_URL"); override(p,"db.user","DIENMAY_DB_USER"); override(p,"db.password","DIENMAY_DB_PASSWORD");
        return p;
    }
    private static void override(Properties p,String key,String env) { if(System.getenv(env)!=null) p.setProperty(key,System.getenv(env)); }
    private Connection connect() throws SQLException {
        String url=properties.getProperty("db.url","");
        if(!url.startsWith("jdbc:sqlserver://")) throw new SQLException("Chưa cấu hình SQL Server. Sao chép config/database.properties.example và điền thông tin.");
        Properties credentials=new Properties();
        credentials.setProperty("user",properties.getProperty("db.user",""));
        credentials.setProperty("password",properties.getProperty("db.password",""));
        credentials.setProperty("loginTimeout","5"); credentials.setProperty("socketTimeout","20000");
        try { Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver"); }
        catch(ClassNotFoundException e) { throw new SQLException("Thiếu Microsoft JDBC Driver trong lib/.",e); }
        return DriverManager.getConnection(url,credentials);
    }
    public Connection open() throws SQLException { return factory.open(); }
    public <T> T read(Work<T> work) throws SQLException { try(Connection c=open()) { return work.run(c); } }
    public <T> T transaction(Work<T> work) throws SQLException {
        try(Connection c=open()) {
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            try {
                // A SELECT from a real table opens the JDBC manual transaction before
                // sp_getapplock(..., LockOwner='Transaction'), including admin/bootstrap calls.
                // Do not issue an extra BEGIN under IMPLICIT_TRANSACTIONS ON.
                try(Statement s=c.createStatement()) {
                    s.execute("SET XACT_ABORT ON; SET LOCK_TIMEOUT 10000; SELECT Version FROM dm.SchemaVersion WHERE Version=1;");
                }
                T result=work.run(c); c.commit(); return result;
            } catch(SQLException | RuntimeException e) {
                try { c.rollback(); } catch(SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        }
    }
    public String checkConnection() throws SQLException {
        return read(c -> {
            try(PreparedStatement s=c.prepareStatement("SELECT DB_NAME(), Version FROM dm.SchemaVersion WHERE Version=1"); ResultSet r=s.executeQuery()) {
                if(!r.next()) throw new SQLException("Thiếu schema dm phiên bản 1. Hãy chạy database/001_schema.sql.");
                return r.getString(1)+" / schema "+r.getInt(2);
            }
        });
    }
}
