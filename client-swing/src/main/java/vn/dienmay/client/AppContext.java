package vn.dienmay.client;

import java.awt.Component;
import java.awt.Window;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import vn.dienmay.client.api.ApiGateway;
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

/** Constructor-injected desktop session and navigation lifecycle; all state is confined to the EDT. */
public final class AppContext {
  private final ApiGateway gateway;
  private final List<Window> windows = new ArrayList<>();
  private ApiSession session;
  private long generation;
  private int jobs;

  public AppContext(ApiGateway gateway) {
    this.gateway = gateway;
  }

  private final Map<String, WorkFrame> openFrames = new java.util.HashMap<>();

  public void open(String key, java.util.function.Supplier<WorkFrame> factory) {
    WorkFrame existing = openFrames.get(key);
    if (existing != null && existing.isDisplayable()) {
      existing.setExtendedState(existing.getExtendedState() & ~java.awt.Frame.ICONIFIED);
      existing.toFront();
      return;
    }
    WorkFrame frame = factory.get();
    openFrames.put(key, frame);
    frame.setVisible(true);
  }

  public ApiGateway gateway() {
    return gateway;
  }

  public ApiSession session() {
    return session;
  }

  public int jobs() {
    return jobs;
  }

  public void register(Window window) {
    windows.removeIf(w -> !w.isDisplayable());
    windows.add(window);
  }

  public Path pendingDirectory() {
    String profile = SaleRequest.digest(gateway.profile()).substring(0, 16);
    return Path.of(System.getProperty("dienmay.stateDir",
        Path.of(System.getProperty("user.home"), ".dienmay-api").toString()), profile);
  }

  public String token() {
    if (session == null) {
      throw new SecurityException("Vui lòng đăng nhập.");
    }
    return session.token();
  }

  public void acceptSession(ApiSession authenticated) {
    session = authenticated;
    generation++;
    if (session.mustChange()) {
      changePassword(null, true);
    } else {
      home();
    }
  }

  private void home() {
    WorkFrame next = switch (session.role()) {
      case MANAGER -> new StoreManagerMain(this);
      case CASHIER -> new CashierMain(this);
      case WAREHOUSE -> new WarehouseManagerMain(this);
    };
    next.setVisible(true);
    for (Window window : List.copyOf(windows)) {
      if (window != next) {
        window.dispose();
      }
    }
  }

  public void changePassword(Component owner, boolean required) {
    if (jobs > 0) {
      Ui.message(owner, "Hoàn tất tác vụ trước khi đổi mật khẩu.");
      return;
    }
    JPasswordField old = new JPasswordField(24);
    JPasswordField next = new JPasswordField(24);
    JPasswordField repeat = new JPasswordField(24);
    EditDialog dialog = new EditDialog(this, owner,
        required ? "Bắt buộc đổi mật khẩu" : "Đổi mật khẩu",
        Ui.fields("Mật khẩu hiện tại", old, "Mật khẩu mới (12-256 ký tự)", next,
            "Nhập lại", repeat), Operation.PASSWORD, () -> {
          char[] a = old.getPassword();
          char[] b = next.getPassword();
          char[] c = repeat.getPassword();
          try {
            if (!java.util.Arrays.equals(b, c) || b.length < 12 || b.length > 256) {
              throw new IllegalArgumentException("Mật khẩu mới không hợp lệ hoặc nhập lại không khớp.");
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
    dialog.setVisible(true);
    if (required && session != null && session.mustChange()) {
      logoutLocal();
    }
  }

  public void logout(Component owner) {
    if (jobs > 0) {
      Ui.message(owner, "Đang xử lý giao dịch; chưa thể đăng xuất.");
      return;
    }
    if (!Ui.confirm(owner, "Đăng xuất? Giỏ chưa thanh toán sẽ mất; yêu cầu chờ vẫn được giữ.")) {
      return;
    }
    String bearer = token();
    run(owner, () -> gateway.call(Operation.LOGOUT, bearer, Data.map()), ignored -> logoutLocal(),
        error -> {
          Ui.error(owner, error);
          logoutLocal(); // Local credentials are discarded even when the server is unreachable.
        });
  }

  private void logoutLocal() {
    session = null;
    generation++;
    MainUI login = new MainUI(this);
    login.setVisible(true);
    for (Window window : List.copyOf(windows)) {
      if (window != login) {
        window.dispose();
      }
    }
  }

  public void request(Component owner, Operation operation, Map<String, Object> parameters,
      Consumer<Object> success) {
    String bearer = operation == Operation.LOGIN ? "" : token();
    run(owner, () -> gateway.call(operation, bearer, parameters), success, error -> Ui.error(owner, error));
  }

  public <T> void run(Component owner, Callable<T> action, Consumer<T> success) {
    run(owner, action, success, error -> Ui.error(owner, error));
  }

  public <T> void run(Component owner, Callable<T> action, Consumer<T> success, Consumer<Throwable> failure) {
    if (!SwingUtilities.isEventDispatchThread()) {
      throw new IllegalStateException("Submit UI work on the EDT");
    }
    long epoch = generation;
    Window window = owner instanceof Window w ? w : owner == null ? null : SwingUtilities.getWindowAncestor(owner);
    if (window != null) {
      window.setEnabled(false);
      window.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
    }
    jobs++;
    new SwingWorker<T, Void>() {
      @Override
      protected T doInBackground() throws Exception {
        return action.call();
      }

      @Override
      protected void done() {
        jobs--;
        if (window != null && window.isDisplayable()) {
          window.setEnabled(true);
          window.setCursor(java.awt.Cursor.getDefaultCursor());
        }
        try {
          T result = get();
          if (epoch == generation && (window == null || window.isDisplayable())) {
            success.accept(result);
          }
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          if (epoch == generation) {
            failure.accept(ex);
          }
        } catch (ExecutionException ex) {
          if (epoch == generation) {
            failure.accept(ex.getCause());
          }
        } catch (RuntimeException ex) {
          if (epoch == generation) {
            failure.accept(ex);
          }
        }
      }
    }.execute();
  }
}
