package vn.dienmay.client;

import java.awt.Component;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import vn.dienmay.client.api.ApiGateway;
import vn.dienmay.client.ui.AppShell;
import vn.dienmay.client.ui.CashierMain;
import vn.dienmay.client.ui.EditDialog;
import vn.dienmay.client.ui.MainUI;
import vn.dienmay.client.ui.StoreManagerMain;
import vn.dienmay.client.ui.Ui;
import vn.dienmay.client.ui.WarehouseManagerMain;
import vn.dienmay.client.ui.WorkFrame;
import vn.dienmay.common.api.ApiSession;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.api.Operation;
import vn.dienmay.common.model.SaleRequest;

/** EDT-confined desktop lifecycle. Only AppShell owns a JFrame; screen objects retain their state. */
public final class AppContext {
  private final ApiGateway gateway;
  private final Map<String, WorkFrame> openScreens = new HashMap<>();
  private AppShell shell;
  private ApiSession session;
  private long generation;
  private int jobs;
  private String environmentMode = "UNKNOWN";
  public String environmentMode() { return environmentMode; }

  public AppContext(ApiGateway gateway) { this.gateway = gateway; }
  public ApiGateway gateway() { return gateway; }
  public ApiSession session() { return session; }
  public int jobs() { return jobs; }

  public AppShell shell() {
    if (shell == null) { shell = new AppShell(this); }
    return shell;
  }

  public void open(String key, java.util.function.Supplier<WorkFrame> factory) {
    if (!shell().allowNavigation()) { return; }
    WorkFrame screen = openScreens.get(key);
    if (screen == null || screen.isDisposed()) {
      screen = factory.get();
      openScreens.put(key, screen);
    }
    // Constructors may start an asynchronous load. Navigation was authorized before construction.
    shell().navigateChecked(screen);
  }

  public void show(WorkFrame screen) {
    // New detail screens can already have a constructor load in flight; do not reject their first mount.
    if (shell().current() == null) { shell().reset(screen, false); }
    else { shell().navigateChecked(screen); }
  }

  public void close(WorkFrame screen) {
    if (jobs != 0 || !screen.canLeave()) { return; }
    if (shell != null) { shell.removeScreen(screen); }
    else { screen.release(); }
    openScreens.values().removeIf(value -> value == screen);
  }

  public Path pendingDirectory() {
    String profile = SaleRequest.digest(gateway.profile()).substring(0, 16);
    return Path.of(System.getProperty("dienmay.stateDir",
        Path.of(System.getProperty("user.home"), ".dienmay-api").toString()), profile);
  }

  public String token() {
    if (session == null) { throw new SecurityException("Vui lòng đăng nhập."); }
    return session.token();
  }

  public void acceptSession(ApiSession authenticated) {
    session = authenticated;
    generation++;
    if (session.mustChange()) { changePassword(null, true); }
    else { home(); }
  }

  private void home() {
    WorkFrame next = switch (session.role()) {
      case MANAGER -> new StoreManagerMain(this);
      case CASHIER -> new CashierMain(this);
      case WAREHOUSE -> new WarehouseManagerMain(this);
    };
    openScreens.clear();
    shell().reset(next, true);
    environmentMode = "UNKNOWN";
    shell().environmentMode(environmentMode);
    String bearer = token();
    run(shell(), () -> gateway.call(Operation.SETTINGS, bearer, Data.map()), settings -> {
      String mode = Data.text(Json.object(settings), "mode");
      environmentMode = java.util.Set.of("DEMO", "PRODUCTION").contains(mode) ? mode : "UNKNOWN";
      shell().environmentMode(environmentMode);
    }, error -> shell().environmentMode("UNKNOWN"));
  }

  public void changePassword(Component owner, boolean required) {
    if (jobs > 0) { Ui.message(owner,
        "Hoàn tất tác vụ trước khi đổi mật khẩu."); return; }
    if (!required && !confirmDiscard(owner)) { return; }
    JPasswordField old = new JPasswordField(24);
    JPasswordField next = new JPasswordField(24);
    JPasswordField repeat = new JPasswordField(24);
    EditDialog editor = new EditDialog(this, owner,
        required ? "Bắt buộc đổi mật khẩu" : "Đổi mật khẩu",
        Ui.fields("Mật khẩu hiện tại", old, "Mật khẩu mới (12-256 ký tự)",
            next,
            "Nhập lại", repeat), Operation.PASSWORD, () -> {
          char[] a = old.getPassword();
          char[] b = next.getPassword();
          char[] c = repeat.getPassword();
          try {
            if (!java.util.Arrays.equals(b, c) || b.length < 12 || b.length > 256) {
              throw new IllegalArgumentException("Mật khẩu phải có 12-256 ký tự và hai lần nhập phải khớp.");
            }
            return Data.map("oldPassword", new String(a), "newPassword", new String(b));
          } finally {
            java.util.Arrays.fill(a, '\0');
            java.util.Arrays.fill(b, '\0');
            java.util.Arrays.fill(c, '\0');
          }
        }, result -> {
          session = Json.convert(result, ApiSession.class);
          generation++;
          home();
        });
    if (required) {
      editor.onCancel(this::logoutLocal);
      shell().reset(editor, false);
    } else { editor.open(); }
  }

  private boolean confirmDiscard(Component owner) {
    return shell == null || shell.screens().stream().noneMatch(WorkFrame::hasUnsavedChanges)
        || Ui.confirm(owner, "Có dữ liệu chưa lưu. Bản nháp sẽ mất; yêu cầu chờ đối soát vẫn được giữ. Tiếp tục?");
  }

  public void logout(Component owner) {
    if (jobs > 0) { Ui.message(owner, "Đang xử lý; chưa thể đăng xuất.");
        return; }
    if (!confirmDiscard(owner) || !Ui.confirm(owner, "Đăng xuất tài khoản?")) { return; }
    String bearer = token();
    run(owner, () -> gateway.call(Operation.LOGOUT, bearer, Data.map()), ignored -> logoutLocal(), error -> {
      Ui.error(owner, error);
      logoutLocal();
    });
  }

  private void logoutLocal() {
    session = null;
    generation++;
    openScreens.clear();
    shell().reset(new MainUI(this), false);
  }

  public void exit() {
    if (jobs > 0) { Ui.message(shell, "Hoàn tất tác vụ trước khi thoát."); return; }
    if (!confirmDiscard(shell) || !Ui.confirm(shell, "Thoát ứng dụng?")) { return; }
    if (session == null) { shell().dispose(); return; }
    String bearer = token();
    run(shell, () -> gateway.call(Operation.LOGOUT, bearer, Data.map()), result -> terminate(), error -> terminate());
  }

  private void terminate() {
    session = null;
    generation++;
    openScreens.clear();
    shell().dispose();
  }

  public void request(Component owner, Operation operation, Map<String, Object> parameters,
      Consumer<Object> success) {
    String bearer = operation == Operation.LOGIN ? "" : token();
    run(owner, () -> gateway.call(operation, bearer, parameters), success);
  }

  public <T> void run(Component owner, Callable<T> action, Consumer<T> success) {
    run(owner, action, success, error -> Ui.error(owner, error));
  }

  public <T> void run(Component owner, Callable<T> action, Consumer<T> success, Consumer<Throwable> failure) {
    if (!SwingUtilities.isEventDispatchThread()) { throw new IllegalStateException("Submit UI work on the EDT"); }
    long epoch = generation;
    WorkFrame screen = owner instanceof WorkFrame work ? work
        : owner == null ? null : (WorkFrame) SwingUtilities.getAncestorOfClass(WorkFrame.class, owner);
    jobs++;
    busy();
    new SwingWorker<T, Void>() {
      @Override protected T doInBackground() throws Exception { return action.call(); }
      @Override protected void done() {
        jobs--;
        busy();
        boolean active = epoch == generation && (screen == null || !screen.isDisposed());
        try {
          T result = get();
          if (active) { success.accept(result); }
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          if (active) { failure.accept(ex); }
        } catch (ExecutionException ex) {
          if (active) { failure.accept(ex.getCause()); }
        } catch (RuntimeException ex) {
          if (active) { failure.accept(ex); }
        }
      }
    }.execute();
  }

  private void busy() {
    if (shell != null && shell.isDisplayable()) {
      shell.setEnabled(jobs == 0);
      shell.setCursor(java.awt.Cursor.getPredefinedCursor(jobs == 0
          ? java.awt.Cursor.DEFAULT_CURSOR : java.awt.Cursor.WAIT_CURSOR));
    }
  }
}
