package vn.dienmay.server;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import vn.dienmay.server.db.Jdbc;
import vn.dienmay.server.db.SqlDatabase;

/** DBA CLI only. No HTTP route; no automatic production migration, deletion or seed on server start. */
public final class DatabaseInstaller {
  private DatabaseInstaller() {}

  public static List<String> batches(String source) {
    // Deliberately supports only the supplied SQL scripts, not SQLCMD variables/includes or GO counts.
    if (source.lines().anyMatch(line -> line.stripLeading().startsWith(":"))) {
      throw new IllegalArgumentException("SQLCMD commands are not supported by the installer.");
    }
    return Arrays.stream(source.replace("﻿", "").split("(?im)^\\s*GO\\s*(?:--[^\\r\\n]*)?$"))
        .map(String::strip).filter(value -> !value.isEmpty()).toList();
  }

  public static void validateTarget(String actual, String expected, String command, String confirmation) {
    if (expected == null || !expected.matches("[A-Za-z][A-Za-z0-9_]{0,119}") || !expected.equals(actual)
        || List.of("master", "model", "msdb", "tempdb").contains(actual.toLowerCase(Locale.ROOT))) {
      throw new SecurityException("Exact non-system database name required; no database was cha" +
          "nged.");
    }
    switch (command) {
      case "init-demo", "seed-demo" -> {
        if (!actual.endsWith("_Demo") || !confirmation.equals("--confirm-demo")) {
          throw new SecurityException("Requires *_Demo and --confirm-demo. Never use a live database.");
        }
      }
      case "init-test" -> {
        if (!actual.endsWith("_Test") || !confirmation.equals("--confirm-test")) {
          throw new SecurityException("Requires *_Test and --confirm-test.");
        }
      }
      case "migrate" -> {
        if (!confirmation.equals("--backup-confirmed")) {
          throw new SecurityException("Restore and verify a backup first; then use --backup-confirm" +
              "ed.");
        }
      }
      case "classify-production" -> {
        if (actual.endsWith("_Demo") || actual.endsWith("_Test")
            || !confirmation.equals("--acknowledge-unverified")) {
          throw new SecurityException("Explicit real database classification required. Demo/test ca" +
              "nnot become production.");
        }
      }
      default -> throw new IllegalArgumentException("Unsupported installer operation");
    }
  }

  private static void script(Connection c, String relative) throws Exception {
    Path root = Path.of("database").toAbsolutePath().normalize();
    Path path = root.resolve(relative).normalize();
    if (!path.startsWith(root) || !Files.isRegularFile(path)) {
      throw new IllegalArgumentException("Missing bundled SQL script: " + relative);
    }
    try {
      for (String batch : batches(Files.readString(path, StandardCharsets.UTF_8))) {
        try (Statement s = c.createStatement()) {
          s.setQueryTimeout(120);
          s.execute(batch);
        }
      }
      System.out.println("APPLIED OR ALREADY PRESENT | " + relative);
    } catch (Exception failure) {
      try (Statement rollback = c.createStatement()) {
        rollback.execute("IF @@TRANCOUNT>0 ROLLBACK TRANSACTION;");
      } catch (SQLException suppressed) {
        failure.addSuppressed(suppressed);
      }
      throw failure;
    }
  }

  private static boolean exists(Connection c, String query) throws SQLException {
    try (PreparedStatement s = Jdbc.statement(c, query); ResultSet r = s.executeQuery()) {
      return r.next() && r.getInt(1) != 0;
    }
  }

  private static void upgrades(Connection c) throws Exception {
    script(c, "migration/007_api_transition.sql");
    script(c, "migration/010_quality_lifecycle.sql");
    script(c, "migration/011_identifiers.sql");
    script(c, "reference/012_reference_catalog.sql");
  }

  public static void run(SqlDatabase database, String[] args) throws Exception {
    if (args.length != 3) {
      throw new IllegalArgumentException("Use: init-demo <exact_DB_Demo> --confirm-demo | "
          + "init-test <exact_DB_Test> --confirm-test | seed-demo <exact_" +
              "DB_Demo> --confirm-demo | "
          + "migrate <exact_DB> --backup-confirmed | classify-production " +
              "<exact_DB> --acknowledge-u" +
              "nverified");
    }
    try (Connection c = database.open()) {
      String actual;
      try (PreparedStatement s = Jdbc.statement(c, "SELECT DB_NAME()"); ResultSet r = s.executeQuery()) {
        if (!r.next()) { throw new SQLException("Cannot determine actual database"); }
        actual = r.getString(1);
      }
      validateTarget(actual, args[1], args[0], args[2]);
      // Session lock spans separately committed additive migrations; connection close releases it.
      try (Statement s = c.createStatement()) {
        s.execute("DECLARE @result int; EXEC @result=sys.sp_getapplock @Resourc" +
            "e=N'dm:installer',"
            + "@LockMode='Exclusive',@LockOwner='Session',@LockTimeout=10000;"
            + "IF @result<0 THROW 51230,N'Another installer is running. Sto" +
                "p and inspect.',1;");
      }
      boolean initialized = exists(c, "SELECT CASE WHEN OBJECT_ID(N'dm.SchemaVersion',N'U') IS NULL" +
          " THEN 0 ELSE 1 END");
      boolean ownedInit = exists(c, "SELECT COUNT(*) FROM sys.extended_properties WHERE class=0 "
          + "AND name=N'DienMayDemoInitializer' AND CONVERT(nvarchar(100)" +
              ",value)=N'isolated-demo-v4'");
      switch (args[0]) {
        case "init-demo", "init-test" -> {
          if (!initialized) {
            if (exists(c, "SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0")) {
              throw new SecurityException("Database is not empty. Init never adopts legacy or unrelated" +
                  " tables.");
            }
            if (!ownedInit) {
              try (Statement marker = c.createStatement()) {
                marker.execute("EXEC sys.sp_addextendedproperty @name=N'DienMayDemoInitializ" +
                    "er',"
                    + "@value=N'isolated-demo-v4';");
              }
              ownedInit = true;
            }
            script(c, "schema/001_schema.sql");
          } else if (exists(c, "SELECT CASE WHEN OBJECT_ID(N'dm.DeploymentInfo',N'U') IS NUL" +
              "L THEN 1 ELSE 0 END")) {
            if (!ownedInit) {
              throw new SecurityException("Existing legacy schema: use reviewed migrate, not init-demo.");
            }
          } else if (!exists(c, "SELECT COUNT(*) FROM dm.DeploymentInfo WHERE Id=1 AND Mode='" +
              "DEMO'")) {
            if (!ownedInit || !exists(c, "SELECT COUNT(*) FROM dm.DeploymentInfo WHERE Id=1 AND Mode='" +
                "UNCLASSIFIED'")) {
              throw new SecurityException("Existing schema is not classified DEMO; init refuses to chan" +
                  "ge its mode.");
            }
          }
          upgrades(c);
          if (ownedInit) { Jdbc.update(c,
              "UPDATE dm.DeploymentInfo SET Mode='DEMO' WHERE Id=1 AND Mode" +
                  "='UNCLASSIFIED'"); }
          System.out.println("READY SCHEMA | " + actual +
              " | DEMO | bootstrap manager, then seed-demo if appropriate.");
        }
        case "migrate" -> {
          if (!initialized) { throw new
              SecurityException("No dm schema. Migration cannot initialize or replace a datab" +
                  "ase."); }
          upgrades(c);
          System.out.println("MIGRATED | legacy records retained PENDING; review quality a" +
              "udit before real transacti" +
              "ons.");
        }
        case "seed-demo" -> {
          if (!initialized) { throw new SecurityException("Initialize the isolated demo database first."); }
          script(c, "demo/002_demo.sql");
        }
        case "classify-production" -> {
          if (!initialized || !exists(c, "SELECT COUNT(*) FROM dm.SchemaVersion WHERE Version=4")) {
            throw new SecurityException("Reviewed schema version 4 required.");
          }
          c.setAutoCommit(false);
          try {
            if (!exists(c, "SELECT COUNT(*) FROM dm.DeploymentInfo WITH (UPDLOCK,HOLDLOC" +
                "K) "
                + "WHERE Id=1 AND Mode IN ('UNCLASSIFIED','PRODUCTION') AND Dat" +
                    "asetVersion=N''")
                || exists(c, "SELECT COUNT(*) FROM dm.Products WITH (HOLDLOCK) WHERE IsDem" +
                    "o=1")) {
              throw new SecurityException("Demo or mixed data cannot be relabeled production.");
            }
            Jdbc.update(c, "UPDATE dm.DeploymentInfo SET Mode='PRODUCTION' WHERE Id=1");
            c.commit();
          } catch (Exception failure) {
            c.rollback();
            throw failure;
          }
          System.out.println("CLASSIFIED PRODUCTION | No product verification status was c" +
              "hanged.");
        }
        default -> throw new IllegalArgumentException("Unsupported installer operation");
      }
    }
  }
}
