package vn.dienmay.server;

import static vn.dienmay.server.db.Jdbc.insert;
import static vn.dienmay.server.db.Jdbc.lock;
import static vn.dienmay.server.db.Jdbc.statement;

import java.io.Console;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import vn.dienmay.common.model.Validation;
import vn.dienmay.server.db.SqlDatabase;
import vn.dienmay.server.service.Passwords;

/** Preserves the DBA-only bootstrap command; there is no bootstrap endpoint or default password. */
public final class DatabaseTool {
  private DatabaseTool() {}

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      throw new IllegalArgumentException("Use: check | bootstrap <username> | hash");
    }
    SqlDatabase database = new SqlDatabase();
    if (args[0].equals("check")) {
      System.out.println("SQL Server: " + database.checkConnection());
      return;
    }
    if (!(args[0].equals("hash") && args.length == 1)
        && !(args[0].equals("bootstrap") && args.length == 2)) {
      throw new IllegalArgumentException("Use: check | bootstrap <username> | hash");
    }
    Console console = System.console();
    if (console == null) {
      throw new IllegalStateException("Run in an interactive terminal; passwords are never echoed.");
    }
    char[] password = console.readPassword("Mat khau (12-256 ky tu): ");
    char[] again = console.readPassword("Nhap lai: ");
    try {
      if (password == null || again == null || !Arrays.equals(password, again)) {
        throw new IllegalArgumentException("Mat khau khong khop hoac da huy.");
      }
      String hash = Passwords.hash(password);
      if (args[0].equals("hash")) {
        System.out.println(hash);
        return;
      }
      String username = Validation.required(args[1], 64, "Ten tai khoan");
      long id = database.transaction(c -> {
        lock(c, "dm:employee-admin");
        try (PreparedStatement s = statement(c, "SELECT COUNT(*) FROM dm.Employees WITH (UPDLOCK,HOLDLOCK)");
            ResultSet r = s.executeQuery()) {
          if (!r.next() || r.getInt(1) != 0) {
            throw new IllegalArgumentException("Chi bootstrap khi bang nhan vien trong.");
          }
        }
        return insert(c, "INSERT dm.Employees(UserName,FullName,PasswordHash,Role,MustChange) "
            + "OUTPUT INSERTED.Id VALUES(?,N'Quan ly ban dau',?,'MANAGER',0)", username, hash);
      });
      System.out.println("Created initial manager, ID=" + id);
    } finally {
      if (password != null) {
        Arrays.fill(password, '\0');
      }
      if (again != null) {
        Arrays.fill(again, '\0');
      }
    }
  }
}
