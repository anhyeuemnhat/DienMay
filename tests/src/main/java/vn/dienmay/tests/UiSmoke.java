package vn.dienmay.tests;

import static vn.dienmay.tests.TestKit.check;
import static vn.dienmay.tests.TestKit.equal;

import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import vn.dienmay.client.AppContext;
import vn.dienmay.client.api.HttpApiClient;
import vn.dienmay.client.ui.MainUI;
import vn.dienmay.client.ui.MakeSaleUI;
import vn.dienmay.client.ui.Navigation;
import vn.dienmay.client.ui.WorkFrame;
import vn.dienmay.common.api.ApiSession;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.api.Operation;
import vn.dienmay.common.model.Role;
import vn.dienmay.server.api.ApiServer;

/** Actual Swing, actual local HTTP and screenshots. Synthetic responses, NOT database acceptance. */
public final class UiSmoke {
  private UiSmoke() {}

  private static <T> T edt(Callable<T> work) throws Exception {
    FutureTask<T> future = new FutureTask<>(work);
    SwingUtilities.invokeAndWait(future);
    return future.get();
  }

  private static List<Component> descendants(Component root) {
    List<Component> components = new ArrayList<>();
    components.add(root);
    if (root instanceof Container container) {
      for (Component child : container.getComponents()) {
        components.addAll(descendants(child));
      }
    }
    return components;
  }

  private static void idle(AppContext app) throws Exception {
    long end = System.nanoTime() + 8_000_000_000L;
    while (System.nanoTime() < end) {
      if (edt(app::jobs) == 0) {
        Thread.sleep(150);
        if (edt(app::jobs) == 0) {
          return;
        }
      }
      for (Window window : Window.getWindows()) {
        if (window instanceof JDialog && window.isShowing()) {
          throw new AssertionError("Unexpected dialog: " + ((JDialog) window).getTitle());
        }
      }
      Thread.sleep(30);
    }
    throw new AssertionError("UI workers did not finish");
  }

  private static void inspect(WorkFrame frame, int width, int height) {
    check(frame.isShowing(), "Window not shown");
    check((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH,
        "Window not maximized: " + frame.getTitle());
    check(frame.getWidth() >= width - 30 && frame.getHeight() >= height - 80,
        "Window does not fill work area: " + frame.getBounds());
    Rectangle outer = new Rectangle(frame.getLocationOnScreen(), frame.getSize());
    for (Component component : descendants(frame)) {
      if (component instanceof AbstractButton button && button.isShowing()
          && !(button instanceof JMenuItem)) {
        Rectangle bounds = new Rectangle(button.getLocationOnScreen(), button.getSize());
        check(bounds.width > 0 && bounds.height > 0 && outer.contains(bounds),
            "Clipped button: " + button.getText() + " " + bounds);
      }
      if (component instanceof JTable table && table.isShowing()) {
        check(table.getParent().getHeight() >= 150, "Table viewport too short");
      }
    }
  }

  private static void screenshot(WorkFrame frame, Path file) throws Exception {
    edt(() -> {
      frame.message("UI TEST / SYNTHETIC API DATA / Linux Xvfb - NOT SQL Server acceptance");
      frame.toFront();
      return null;
    });
    Thread.sleep(200);
    ImageIO.write(new Robot().createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())),
        "png", file.toFile());
  }

  public static void main(String[] args) throws Exception {
    if (GraphicsEnvironment.isHeadless()) {
      throw new IllegalStateException("UI tests require a real display or Xvfb plus a window manager");
    }
    int width = Toolkit.getDefaultToolkit().getScreenSize().width;
    int height = Toolkit.getDefaultToolkit().getScreenSize().height;
    Path output = Path.of(args.length == 0 ? "docs/evidence/ui" : args[0], width + "x" + height);
    Files.createDirectories(output);
    System.setProperty("dienmay.stateDir", Files.createTempDirectory("dm-ui-test-").toString());
    edt(() -> {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      return null;
    });
    TestKit tests = new TestKit();
    try (ApiServer server = new ApiServer(new InetSocketAddress("127.0.0.1", 0), null, new UiFixture())) {
      server.start();
      HttpApiClient gateway = new HttpApiClient(URI.create("http://127.0.0.1:" + server.port()));
      AppContext app = new AppContext(gateway);
      WorkFrame login = edt(() -> {
        WorkFrame frame = new MainUI(app);
        frame.setVisible(true);
        return frame;
      });
      Thread.sleep(400);
      tests.test("UI / login maximized " + width + "x" + height, () -> edt(() -> {
        inspect(login, width, height);
        return null;
      }));
      screenshot(login, output.resolve("01-login.png"));
      ApiSession session = Json.convert(gateway.call(Operation.LOGIN, "", Data.map("username", "MANAGER")),
          ApiSession.class);
      edt(() -> { app.acceptSession(session); return null; });
      idle(app);
      WorkFrame home = edt(() -> java.util.Arrays.stream(Window.getWindows())
          .filter(w -> w instanceof WorkFrame && w.isShowing()).map(w -> (WorkFrame) w).findFirst().orElseThrow());
      tests.test("UI / manager functional menu has six groups", () -> edt(() -> {
        equal(6, home.getJMenuBar().getMenuCount());
        inspect(home, width, height);
        return null;
      }));
      screenshot(home, output.resolve("02-manager-home.png"));
      int index = 3;
      for (Navigation.Destination destination : edt(() -> Navigation.destinations(app))) {
        WorkFrame frame = edt(() -> {
          WorkFrame value = destination.create().apply(app);
          value.setVisible(true);
          return value;
        });
        idle(app);
        Thread.sleep(200);
        tests.test("UI / working screen " + destination.title(), () -> edt(() -> {
          inspect(frame, width, height);
          equal(6, frame.getJMenuBar().getMenuCount());
          return null;
        }));
        if (frame instanceof MakeSaleUI) {
          edt(() -> {
            JTextField code = (JTextField) descendants(frame).stream()
                .filter(c -> "productCode".equals(c.getName())).findFirst().orElseThrow();
            code.setText("PK-CAP-01");
            code.postActionEvent();
            return null;
          });
          idle(app);
          tests.test("UI / actual POS add-to-cart over HTTP", () -> edt(() -> {
            JTable table = (JTable) descendants(frame).stream().filter(JTable.class::isInstance)
                .findFirst().orElseThrow();
            equal(1, table.getRowCount());
            return null;
          }));
        }
        screenshot(frame, output.resolve(String.format("%02d-%s.png", index++, frame.getClass().getSimpleName()
            + "-" + destination.permission().name().toLowerCase(java.util.Locale.ROOT))));
        edt(() -> { frame.dispose(); return null; });
      }
      // Navigate by a real JMenuItem action rather than only constructing screens.
      tests.test("UI / menu action opens real work screen", () -> {
        edt(() -> {
          JMenu menu = home.getJMenuBar().getMenu(1);
          menu.getItem(0).doClick();
          return null;
        });
        idle(app);
        check(edt(() -> java.util.Arrays.stream(Window.getWindows()).anyMatch(w -> w.isShowing()
            && w.getClass().getSimpleName().equals("ProductsUI"))), "Menu did not open products");
      });
      for (Role role : List.of(Role.CASHIER, Role.WAREHOUSE)) {
        ApiSession next = Json.convert(gateway.call(Operation.LOGIN, "", Data.map("username", role.name())),
            ApiSession.class);
        edt(() -> { app.acceptSession(next); return null; });
        idle(app);
        WorkFrame current = edt(() -> java.util.Arrays.stream(Window.getWindows())
            .filter(w -> w instanceof WorkFrame && w.isShowing()).map(w -> (WorkFrame) w).findFirst().orElseThrow());
        tests.test("UI / " + role.name() + " permissions and maximized home", () -> edt(() -> {
          inspect(current, width, height);
          for (Navigation.Destination destination : Navigation.destinations(app)) {
            check(destination.permission().allows(role), "Forbidden destination visible");
          }
          check(Navigation.destinations(app).stream().noneMatch(d -> d.permission() == Operation.EMPLOYEES
              || d.permission() == Operation.REPORT), "Privileged menu exposed");
          return null;
        }));
        screenshot(current, output.resolve(String.format("%02d-%s-home.png", index++,
            role.name().toLowerCase(java.util.Locale.ROOT))));
      }
      tests.test("UI / EDT remains responsive during slow API work", () -> {
        edt(() -> { app.run(null, () -> { Thread.sleep(300); return true; }, result -> {}); return null; });
        long start = System.nanoTime();
        edt(() -> true);
        check(System.nanoTime() - start < 200_000_000L, "EDT blocked on API");
        idle(app);
      });
    } finally {
      edt(() -> {
        for (Window window : Window.getWindows()) {
          window.dispose();
        }
        return null;
      });
    }
    tests.finish("swing-" + width + "x" + height);
    System.out.println("SCOPE | Linux Xvfb/Openbox; synthetic API data. Windows DPI/peripherals/SQL NOT tested.");
  }
}
