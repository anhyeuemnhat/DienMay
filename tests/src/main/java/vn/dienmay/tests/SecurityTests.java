package vn.dienmay.tests;

import static vn.dienmay.tests.TestKit.equal;
import static vn.dienmay.tests.TestKit.throwsType;

import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.dienmay.common.api.ApiException;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Operation;
import vn.dienmay.common.model.Role;
import vn.dienmay.server.InventorySystem;
import vn.dienmay.server.api.ApplicationApi;
import vn.dienmay.server.db.RequestCancellationDao;
import vn.dienmay.server.db.SqlDatabase;
import vn.dienmay.server.service.AuthService;
import vn.dienmay.server.service.Passwords;

/** Real authorization/application classes with explicit JDBC rows, not database authentication proof. */
public final class SecurityTests {
  private SecurityTests() {}

  private static final class MutableClock extends Clock {
    Instant time = Instant.parse("2026-10-29T00:00:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return time;
    }
  }

  private static final class AccountDouble {
    final char[] secret = UUID.randomUUID().toString().toCharArray();
    final String hash = Passwords.hash(secret);
    Role role = Role.CASHIER;
    int version = 1;
    boolean enabled = true;
    boolean mustChange;
    int queries;
    final JdbcDouble transactions = new JdbcDouble();

    Connection connection() {
      return JdbcDouble.proxy(Connection.class, (p, method, args) -> {
        if (!method.getName().equals("prepareStatement")) {
          return transactions.invoke(p, method, args);
        }
        String sql = (String) args[0];
        Map<Integer, Object> params = new HashMap<>();
        return JdbcDouble.proxy(PreparedStatement.class, (statement, called, values) -> {
          if (called.getName().startsWith("set") && values != null && values.length == 2) {
            params.put((Integer) values[0], values[1]);
          }
          if (called.getName().equals("executeQuery")) {
            queries++;
            Object[] row;
            if (sql.startsWith("SELECT Id,FullName,PasswordHash")) {
              if (!"fixture".equals(params.get(1))) {
                return JdbcDouble.rows(List.of());
              }
              row = new Object[] {1L, "Fixture account", hash, role.name(), version, mustChange,
                  enabled, 0, null, Timestamp.valueOf("2026-10-29 00:00:00")};
            } else if (sql.startsWith("SELECT Role,Enabled")) {
              row = new Object[] {role.name(), enabled, version, mustChange};
            } else {
              throw new AssertionError("Unexpected security SQL: " + sql);
            }
            return JdbcDouble.rows(java.util.Collections.singletonList(row));
          }
          if (called.getName().equals("executeUpdate")) {
            return 1;
          }
          return JdbcDouble.defaultValue(called.getReturnType());
        });
      });
    }
  }

  public static void main(String[] args) throws Exception {
    TestKit t = new TestKit();
    AccountDouble account = new AccountDouble();
    MutableClock clock = new MutableClock();
    AuthService auth = new AuthService(new SqlDatabase(account::connection), clock, Duration.ofSeconds(5));
    AuthService.Session session = auth.login("fixture", account.secret);
    t.test("security / server-issued token resolves exact session", () -> equal(session,
        auth.resolve(session.token())));
    t.test("security / random and malformed tokens denied", () -> {
      throwsType(SecurityException.class, () -> auth.resolve(UUID.randomUUID().toString()));
      throwsType(SecurityException.class, () -> auth.resolve("not-a-uuid"));
      throwsType(SecurityException.class, () -> auth.resolve(null));
    });
    t.test("security / client roles cannot elevate DB account", () -> {
      auth.require(account.connection(), session, Role.CASHIER);
      throwsType(SecurityException.class, () -> auth.require(account.connection(), session, Role.MANAGER));
    });
    t.test("security / locally forged session is unregistered", () -> {
      Constructor<AuthService.Session> constructor = AuthService.Session.class.getDeclaredConstructor(
          long.class, String.class, Role.class, int.class, boolean.class, Instant.class);
      constructor.setAccessible(true);
      AuthService.Session forged = constructor.newInstance(1L, "Forged", Role.MANAGER, 1, false,
          clock.instant().plusSeconds(3600));
      throwsType(SecurityException.class, () -> auth.require(null, forged, Role.MANAGER));
    });
    t.test("security / must-change enforced by service", () -> {
      account.mustChange = true;
      throwsType(SecurityException.class, () -> auth.require(account.connection(), session, Role.CASHIER));
      account.mustChange = false;
    });
    t.test("security / exact expiration boundary invalidates token", () -> {
      clock.time = clock.time.plusSeconds(5);
      throwsType(SecurityException.class, () -> auth.resolve(session.token()));
    });
    t.test("security / version change revokes existing session", () -> {
      AuthService.Session active = auth.login("fixture", account.secret);
      account.version++;
      throwsType(SecurityException.class, () -> auth.require(account.connection(), active, Role.CASHIER));
      throwsType(SecurityException.class, () -> auth.resolve(active.token()));
    });
    t.test("security / disabled account revokes existing session", () -> {
      AuthService.Session active = auth.login("fixture", account.secret);
      account.enabled = false;
      throwsType(SecurityException.class, () -> auth.require(account.connection(), active, Role.CASHIER));
      account.enabled = true;
    });
    t.test("security / logout invalidates bearer", () -> {
      AuthService.Session active = auth.login("fixture", account.secret);
      auth.logout(active);
      throwsType(SecurityException.class, () -> auth.resolve(active.token()));
    });
    t.test("security / failed login commits lockout accounting but returns no session", () -> {
      int committed = account.transactions.commits;
      throwsType(SecurityException.class, () -> auth.login("fixture", UUID.randomUUID().toString().toCharArray()));
      equal(committed + 1, account.transactions.commits);
    });
    t.test("security / unknown username denied", () -> throwsType(SecurityException.class,
        () -> auth.login("unknown", account.secret)));
    InventorySystem system = new InventorySystem(new SqlDatabase(account::connection));
    AuthService.Session current = system.auth().login("fixture", account.secret);
    ApplicationApi api = new ApplicationApi(system);
    t.test("application API / role denial happens before business SQL", () -> {
      int queries = account.queries;
      ApiException denied = throwsType(ApiException.class,
          () -> api.execute(Operation.EMPLOYEE_SAVE, current.token(), Data.map("role", "MANAGER")));
      equal(403, denied.status());
      equal(queries, account.queries);
    });
    for (Operation operation : List.of(Operation.LIFECYCLE, Operation.QUALITY_LIST,
        Operation.QUALITY_REVIEW, Operation.REFERENCES, Operation.AUDIT_LOG)) {
      t.test("application API / cashier cannot call " + operation, () -> {
        int queries = account.queries;
        equal(403, throwsType(ApiException.class,
            () -> api.execute(operation, current.token(), Data.map("role", "MANAGER"))).status());
        equal(queries, account.queries);
      });
    }
    t.test("application API / client-auth metadata cannot replace bearer", () -> equal(401,
        throwsType(ApiException.class, () -> api.execute(Operation.REPORT, UUID.randomUUID().toString(),
            Data.map("employeeId", 1, "role", "MANAGER"))).status()));
    t.test("application API / must-change blocks every business endpoint", () -> {
      account.mustChange = true;
      AuthService.Session limited = system.auth().login("fixture", account.secret);
      ApiException denied = throwsType(ApiException.class, () -> api.execute(Operation.PRODUCT_SEARCH,
          limited.token(), Data.map("text", "")));
      equal("PASSWORD_CHANGE_REQUIRED", denied.code());
      account.mustChange = false;
    });
    t.test("cancellation DAO double / matching marker blocks delayed execution", () -> {
      JdbcDouble database = new JdbcDouble();
      database.resultRows = java.util.Collections.singletonList(new Object[] {"hash", 7L});
      throwsType(IllegalArgumentException.class, () -> RequestCancellationDao.requireActive(
          database.connection(), "SALE", UUID.randomUUID(), "hash", 7L));
    });
    t.test("cancellation DAO double / another employee cannot own same marker", () -> {
      JdbcDouble database = new JdbcDouble();
      database.resultRows = java.util.Collections.singletonList(new Object[] {"hash", 7L});
      throwsType(SecurityException.class, () -> RequestCancellationDao.cancel(
          database.connection(), "STOCK", UUID.randomUUID(), "hash", 8L));
    });
    t.test("cancellation DAO double / payload change is refused", () -> {
      JdbcDouble database = new JdbcDouble();
      database.resultRows = java.util.Collections.singletonList(new Object[] {"hash", 7L});
      throwsType(SecurityException.class, () -> RequestCancellationDao.cancel(
          database.connection(), "SALE", UUID.randomUUID(), "other-hash", 7L));
    });
    java.util.Arrays.fill(account.secret, '\0');
    t.finish("authorization-boundaries");
    System.out.println("SCOPE | JDBC test doubles. DB locks, constraints and concurrent transactions NOT tested here.");
  }
}
