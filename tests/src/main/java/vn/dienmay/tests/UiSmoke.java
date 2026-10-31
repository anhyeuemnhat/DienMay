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
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import vn.dienmay.client.AppContext;
import vn.dienmay.client.api.HttpApiClient;
import vn.dienmay.client.ui.EditDialog;
import vn.dienmay.client.ui.MainUI;
import vn.dienmay.client.ui.Navigation;
import vn.dienmay.client.ui.RowsPanel;
import vn.dienmay.client.ui.WorkFrame;
import vn.dienmay.common.api.ApiSession;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.api.Operation;
import vn.dienmay.common.model.Role;
import vn.dienmay.server.api.ApiServer;

/** Real Swing and real local HTTP with explicitly synthetic responses; NOT SQL Server acceptance. */
public final class UiSmoke {
  private UiSmoke() {}

  private static <T> T edt(Callable<T> action) throws Exception {
    FutureTask<T> task = new FutureTask<>(action);
    SwingUtilities.invokeAndWait(task);
    return task.get();
  }

  private static List<Component> descendants(Component root) {
    List<Component> result = new ArrayList<>();
    result.add(root);
    if (root instanceof Container container) {
      for (Component child : container.getComponents()) { result.addAll(descendants(child)); }
    }
    return result;
  }

  private static void idle(AppContext app) throws Exception {
    long until = System.nanoTime() + 10_000_000_000L;
    while (System.nanoTime() < until) {
      for (Window window : Window.getWindows()) {
        if (window instanceof JDialog dialog && dialog.isShowing()) {
          throw new AssertionError("Unexpected dialog: " + dialog.getTitle());
        }
      }
      if (edt(app::jobs) == 0) {
        Thread.sleep(120);
        if (edt(app::jobs) == 0) { return; }
      }
      Thread.sleep(25);
    }
    throw new AssertionError("UI worker timeout");
  }

  private static AbstractButton button(Component parent, String nameOrText) {
    return descendants(parent).stream().filter(c -> c instanceof AbstractButton)
        .map(c -> (AbstractButton) c).filter(b -> nameOrText.equals(b.getName()) || nameOrText.equals(b.getText()))
        .findFirst().orElseThrow(() -> new AssertionError("Missing button: " + nameOrText));
  }

  private static JTable table(WorkFrame screen) {
    return descendants(screen).stream().filter(c -> c instanceof JTable).map(c -> (JTable) c).findFirst().orElseThrow();
  }

  private static void oneWindow(AppContext app) {
    equal(1L, java.util.Arrays.stream(Window.getWindows()).filter(w -> w instanceof JFrame &&
        w.isDisplayable()).count());
    check(app.shell().isShowing(), "Shell must be showing");
    check(app.shell().current().isShowing(), "Current JPanel must be showing");
    equal(0L, java.util.Arrays.stream(Window.getWindows()).filter(w -> w instanceof JDialog && w.isShowing()).count());
  }

  private static void inspect(AppContext app) {
    oneWindow(app);
    Rectangle outer = new Rectangle(app.shell().getLocationOnScreen(), app.shell().getSize());
    for (String id : List.of("navigationBack", "navigationForward", "navigationHome", "navigationDrafts")) {
      AbstractButton b = button(app.shell(), id);
      check(outer.contains(new Rectangle(b.getLocationOnScreen(), b.getSize())), "Clipped navigation: " + id);
    }
    for (Component c : descendants(app.shell().current())) {
      if (c instanceof JTable t && c.isShowing()) {
        check(t.getParent().getHeight() >= 120, "Data table viewport too small");
        check(t.getRowCount() == 0 || t.getColumnCount() == 0 || !t.isCellEditable(0, 0),
            "Display table cannot mutate data directly");
      }
    }
  }

  private static void screenshot(AppContext app, Path target) throws Exception {
    edt(() -> {
      app.shell().current().message("KIEM THU GIAO DIEN / DU LIEU MO PHONG API / CHUA KIEM THU SQL SERVER");
      app.shell().toFront();
      return null;
    });
    Thread.sleep(150);
    ImageIO.write(new Robot().createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())),
        "png", target.toFile());
  }

  private static void confirm(Callable<Void> action, int answer) throws Exception {
    edt(() -> {
      javax.swing.Timer closer = new javax.swing.Timer(70, event -> {
        for (Window window : Window.getWindows()) {
          if (window instanceof JDialog && window.isShowing()) {
            for (Component c : descendants(window)) {
              if (c instanceof JOptionPane option) { option.setValue(answer); return; }
            }
          }
        }
      });
      closer.start();
      try { action.call(); } finally { closer.stop(); }
      return null;
    });
  }

  public static void main(String[] args) throws Exception {
    if (GraphicsEnvironment.isHeadless()) { throw new IllegalStateException("Run under Xvfb or a desktop display"); }
    int width = Toolkit.getDefaultToolkit().getScreenSize().width;
    int height = Toolkit.getDefaultToolkit().getScreenSize().height;
    Path out = Path.of(args.length == 0 ? "docs/evidence/ui" : args[0], width + "x" + height);
    Files.createDirectories(out);
    System.setProperty("dienmay.stateDir", Files.createTempDirectory("dienmay-ui-fixture-").toString());
    edt(() -> { vn.dienmay.client.ui.Ui.installTheme(); return null; });
    TestKit tests = new TestKit();
    try (ApiServer server = new ApiServer(new InetSocketAddress("127.0.0.1", 0), null, new UiFixture())) {
      server.start();
      HttpApiClient gateway = new HttpApiClient(URI.create("http://127.0.0.1:" + server.port()));
      for (Role role : Role.values()) {
        AppContext app = new AppContext(gateway);
        try {
          edt(() -> { new MainUI(app).open(); return null; });
          Thread.sleep(250);
          tests.test(role + " / login uses single JFrame", () -> edt(() -> { oneWindow(app); return null; }));
          if (role == Role.MANAGER) { screenshot(app, out.resolve("01-login.png")); }
          ApiSession session = Json.convert(gateway.call(Operation.LOGIN, "", Data.map("username", role.name())),
              ApiSession.class);
          edt(() -> { app.acceptSession(session); return null; });
          idle(app);
          WorkFrame home = edt(() -> app.shell().current());
          tests.test(role + " / dashboard and DEMO banner", () -> edt(() -> {
            inspect(app); equal("DEMO", app.environmentMode());
            equal(6, app.shell().getJMenuBar().getMenuCount());
            return null;
          }));
          screenshot(app, out.resolve("02-home-" + role.name() + ".png"));
          List<Navigation.Destination> destinations = edt(() -> Navigation.destinations(app));
          tests.test(role + " / allowed destinations only", () -> {
            check(destinations.stream().allMatch(d -> d.permission().allows(role)), "Role menu mismatch");
            equal(role == Role.MANAGER ? 17 : role == Role.CASHIER ? 6 : 7, destinations.size());
          });
          int index = 3;
          for (Navigation.Destination destination : destinations) {
            edt(() -> { Navigation.open(app, destination); return null; });
            idle(app);
            WorkFrame screen = edt(() -> app.shell().current());
            tests.test(role + " / screen " + destination.title(), () -> edt(() -> { inspect(app); return null; }));
            if (role == Role.MANAGER) {
              screenshot(app, out.resolve(String.format("%02d-%s.png", index++, destination.permission().name())));
            }
            edt(() -> { app.shell().back(); return null; });
            tests.test(role + " / Back " + destination.title(), () -> edt(() -> {
              equal(home, app.shell().current()); oneWindow(app); return null;
            }));
            edt(() -> { app.shell().forward(); return null; });
            tests.test(role + " / Forward retains screen " + destination.title(), () -> edt(() -> {
              equal(screen, app.shell().current()); return null;
            }));
            edt(() -> { app.shell().back(); return null; });
          }
          if (role == Role.MANAGER) {
            Navigation.Destination products = destinations.stream().filter(d -> d.permission() ==
                Operation.PRODUCT_SEARCH)
                .findFirst().orElseThrow();
            edt(() -> { Navigation.open(app, products); return null; });
            idle(app);
            WorkFrame productScreen = edt(() -> app.shell().current());
            edt(() -> { table(productScreen).setRowSelectionInterval(0, 0); return null; });
            edt(() -> { button(productScreen, "S\u1eeda d\u00f2ng ch\u1ecdn").doClick(); return null; });
            idle(app);
            WorkFrame editor = edt(() -> app.shell().current());
            tests.test("Product editor is a JPanel, not a window", () -> edt(() -> {
              check(editor instanceof EditDialog, "Expected in-window editor"); oneWindow(app); return null;
            }));
            screenshot(app, out.resolve("30-product-editor.png"));
            JTextField field = edt(() -> descendants(editor).stream().filter(c -> c instanceof JTextField)
                .map(c -> (JTextField) c).findFirst().orElseThrow());
            String original = edt(field::getText);
            edt(() -> { field.setText("000-DRAFT"); return null; });
            confirm(() -> { app.shell().back(); return null; }, JOptionPane.NO_OPTION);
            tests.test("Unsaved-change rejection keeps current editor", () -> edt(() -> {
              equal(editor, app.shell().current()); return null;
            }));
            confirm(() -> { app.shell().back(); return null; }, JOptionPane.YES_OPTION);
            tests.test("Back preserves parent selection and retained editor draft", () -> edt(() -> {
              equal(productScreen, app.shell().current()); equal(0, table(productScreen).getSelectedRow());
              equal("000-DRAFT", field.getText()); check(editor.hasUnsavedChanges(), "Dirty state lost");
              return null;
            }));
            edt(() -> { app.shell().forward(); return null; });
            tests.test("Forward restores unsaved form without reconstructing", () -> edt(() -> {
              equal(editor, app.shell().current()); equal("000-DRAFT", field.getText());
              field.setText(original); check(!editor.hasUnsavedChanges(),
                  "Returning original value must clear dirty flag");
              return null;
            }));
            edt(() -> { button(editor, "editorCancel").doClick(); return null; });
            tests.test("Cancel releases editor and returns parent", () -> edt(() -> {
              check(editor.isDisposed(), "Cancelled editor retained"); equal(productScreen, app.shell().current());
              oneWindow(app); return null;
            }));
            tests.test("Table filter/selection survive Home and Back", () -> edt(() -> {
              RowsPanel rows = (RowsPanel) descendants(productScreen).stream().filter(c -> c instanceof RowsPanel)
                  .findFirst().orElseThrow();
              JTextField filter = (JTextField) descendants(rows).stream().filter(c -> c instanceof JTextField)
                  .findFirst().orElseThrow();
              filter.setText("Sony"); equal(1, rows.table().getRowCount());
              rows.table().setRowSelectionInterval(0, 0);
              app.shell().home(); app.shell().back();
              equal(productScreen, app.shell().current()); equal("Sony", filter.getText());
              equal(0, rows.table().getSelectedRow()); return null;
            }));
          }
        } finally {
          idle(app);
          edt(() -> { app.shell().dispose(); return null; });
        }
      }
    }
    edt(() -> { for (Window window : Window.getWindows()) { window.dispose(); } return null; });
    tests.finish("Swing single-window / " + width + "x" + height + " / SYNTHETIC HTTP, NOT SQL SERVER");
  }
}
