package vn.dienmay.server.service;

import static vn.dienmay.server.db.Jdbc.instant;
import static vn.dienmay.server.db.Jdbc.statement;
import static vn.dienmay.server.db.Jdbc.update;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import vn.dienmay.common.model.Role;
import vn.dienmay.common.model.Validation;
import vn.dienmay.server.db.SqlDatabase;

/** Server-owned sessions; every service rechecks database role, enabled flag and auth version. */
public final class AuthService {
  public static final class Session {
    private final UUID token = UUID.randomUUID();
    private final long employeeId;
    private final String name;
    private final Role role;
    private final int version;
    private final boolean mustChange;
    private final Instant expiresAt;

    private Session(long id, String name, Role role, int version, boolean mustChange,
        Instant expiresAt) {
      employeeId = id;
      this.name = name;
      this.role = role;
      this.version = version;
      this.mustChange = mustChange;
      this.expiresAt = expiresAt;
    }

    public long employeeId() {
      return employeeId;
    }

    public String name() {
      return name;
    }

    public Role role() {
      return role;
    }

    public boolean mustChange() {
      return mustChange;
    }

    public String token() {
      return token.toString();
    }

    public Instant expiresAt() {
      return expiresAt;
    }
  }

  private record Account(long id, String name, String hash, Role role, int version,
      boolean mustChange, boolean enabled, int failed, Instant lockUntil, Instant now) {}

  private static final String DUMMY = Passwords.hash("not-an-account-secret".toCharArray());
  private static final int MAX_SESSIONS = 10_000;
  private final SqlDatabase db;
  private final Clock clock;
  private final Duration lifetime;
  private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

  public AuthService(SqlDatabase db) {
    this(db, Clock.systemUTC(), Duration.ofHours(8));
  }

  public AuthService(SqlDatabase db, Clock clock, Duration lifetime) {
    this.db = Objects.requireNonNull(db, "db");
    this.clock = Objects.requireNonNull(clock, "clock");
    if (lifetime.isNegative() || lifetime.isZero()) {
      throw new IllegalArgumentException("Session lifetime must be positive");
    }
    this.lifetime = lifetime;
  }

  public Session login(String username, char[] password) throws SQLException {
    String user = Validation.required(username, 64, "Ten dang nhap");
    if (password == null || password.length > 256) {
      throw new SecurityException("Thong tin dang nhap khong hop le.");
    }
    Session result = db.transaction(c -> {
      Account account;
      String query = "SELECT Id,FullName,PasswordHash,Role,AuthVersion,MustChange,Enabled,"
          + "FailedAttempts,LockUntil,SYSUTCDATETIME() FROM dm.Employees "
          + "WITH (UPDLOCK,ROWLOCK) WHERE UserName=?";
      try (PreparedStatement s = statement(c, query, user); ResultSet r = s.executeQuery()) {
        if (!r.next()) {
          Passwords.verify(password, DUMMY);
          return null;
        }
        account = new Account(r.getLong(1), r.getString(2), r.getString(3),
            Role.valueOf(r.getString(4)), r.getInt(5), r.getBoolean(6), r.getBoolean(7),
            r.getInt(8), instant(r, 9), instant(r, 10));
      }
      boolean correct = Passwords.verify(password, account.hash());
      if (!account.enabled()
          || (account.lockUntil() != null && account.lockUntil().isAfter(account.now()))) {
        return null;
      }
      if (!correct) {
        int count = account.lockUntil() != null ? 1 : account.failed() + 1;
        update(c, "UPDATE dm.Employees SET FailedAttempts=?,LockUntil=? WHERE Id=?", count,
            count >= 5 ? account.now().plusSeconds(300) : null, account.id());
        return null; // Failed attempt is committed; no authenticated session is issued.
      }
      update(c, "UPDATE dm.Employees SET FailedAttempts=0,LockUntil=NULL WHERE Id=?", account.id());
      return new Session(account.id(), account.name(), account.role(), account.version(),
          account.mustChange(), clock.instant().plus(lifetime));
    });
    if (result == null) {
      throw new SecurityException("Sai ten/mat khau, tai khoan bi khoa hoac tam khoa 5 phut.");
    }
    register(result);
    return result;
  }

  private synchronized void register(Session session) {
    sessions.values().removeIf(s -> !clock.instant().isBefore(s.expiresAt));
    if (sessions.size() >= MAX_SESSIONS) {
      throw new IllegalStateException("Qua nhieu phien. Hay thu lai sau.");
    }
    sessions.put(session.token, session);
  }

  /** Resolves an opaque token; client-supplied employee IDs/roles are never trusted. */
  public Session resolve(String token) {
    Session session;
    try {
      session = token == null ? null : sessions.get(UUID.fromString(token));
    } catch (IllegalArgumentException e) {
      throw new SecurityException("Phien dang nhap khong hop le.");
    }
    if (session == null || !clock.instant().isBefore(session.expiresAt)) {
      logout(session);
      throw new SecurityException("Phien dang nhap da het. Vui long dang nhap lai.");
    }
    return session;
  }

  public void logout(Session session) {
    if (session != null) {
      sessions.remove(session.token);
    }
  }

  public static boolean roleAllowed(Role actual, Role... roles) {
    return Arrays.asList(roles).contains(actual);
  }

  public void require(Connection c, Session session, Role... roles) throws SQLException {
    check(c, session, false, roles);
  }

  private void check(Connection c, Session session, boolean changing, Role... roles)
      throws SQLException {
    if (session == null || sessions.get(session.token) != session
        || !clock.instant().isBefore(session.expiresAt)) {
      logout(session);
      throw new SecurityException("Phien dang nhap da het.");
    }
    // HOLDLOCK retains authorization through completion of a write transaction.
    try (PreparedStatement s = statement(c,
        "SELECT Role,Enabled,AuthVersion,MustChange FROM dm.Employees WITH (HOLDLOCK) WHERE Id=?",
        session.employeeId); ResultSet r = s.executeQuery()) {
      if (!r.next() || !r.getBoolean(2) || r.getInt(3) != session.version) {
        logout(session);
        throw new SecurityException("Tai khoan/quyen da thay doi. Hay dang nhap lai.");
      }
      if (!roleAllowed(Role.valueOf(r.getString(1)), roles)) {
        throw new SecurityException("Ban khong co quyen thuc hien chuc nang nay.");
      }
      if (r.getBoolean(4) && !changing) {
        throw new SecurityException("Can doi mat khau ban dau truoc khi su dung.");
      }
    }
  }

  public Session changePassword(Session session, char[] oldPassword, char[] newPassword)
      throws SQLException {
    Passwords.validate(newPassword);
    if (Arrays.equals(oldPassword, newPassword)) {
      throw new IllegalArgumentException("Mat khau moi phai khac mat khau cu.");
    }
    String hash = Passwords.hash(newPassword);
    Session next = db.transaction(c -> {
      check(c, session, true, Role.values());
      try (PreparedStatement s = statement(c,
          "SELECT PasswordHash FROM dm.Employees WITH (UPDLOCK) WHERE Id=?", session.employeeId);
          ResultSet r = s.executeQuery()) {
        if (!r.next() || !Passwords.verify(oldPassword, r.getString(1))) {
          throw new SecurityException("Mat khau hien tai khong dung.");
        }
      }
      update(c, "UPDATE dm.Employees SET PasswordHash=?,MustChange=0,AuthVersion=AuthVersion+1 "
          + "WHERE Id=?", hash, session.employeeId);
      return new Session(session.employeeId, session.name, session.role, session.version + 1,
          false, clock.instant().plus(lifetime));
    });
    logout(session);
    register(next);
    return next;
  }
}
