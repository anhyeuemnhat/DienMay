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
    for (String id : List.of("navigationBack", "navigationForward", "navigationHome", "windowMinimize", "windowRestore",
        "windowClose")) {
      AbstractButton b = button(app.shell(), id);
      check(outer.contains(new Rectangle(b.getLocationOnScreen(), b.getSize())), "Clipped navigation: " + id);
    }
    check(app.shell().isUndecorated(), "Native title bar returned");
    check(descendants(app.shell()).stream().noneMatch(c -> "navigationDrafts".equals(c.getName())
        || "environmentBanner".equals(c.getName())), "Removed UI component returned");
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

  private static double luminance(java.awt.Color color) {
    double[] v = {color.getRed() / 255.0, color.getGreen() / 255.0, color.getBlue() / 255.0};
    for (int i = 0; i < v.length; i++) { v[i] = v[i] <= 0.04045 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
        }
    return v[0] * 0.2126 + v[1] * 0.7152 + v[2] * 0.0722;
  }
  private static double contrast(java.awt.Color first, java.awt.Color second) {
    double a = luminance(first), b = luminance(second);
    return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
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
          tests.test(role + " / login keeps borderless chrome on single JFrame", () -> edt(() -> {
            inspect(app); return null;
          }));
          if (role == Role.MANAGER) { screenshot(app, out.resolve("01-login.png")); }
          ApiSession session = Json.convert(gateway.call(Operation.LOGIN, "", Data.map("username", role.name())),
              ApiSession.class);
          edt(() -> { app.acceptSession(session); return null; });
          idle(app);
          WorkFrame home = edt(() -> app.shell().current());
          tests.test(role + " / dashboard without global DEMO banner", () -> edt(() -> {
            inspect(app); equal("DEMO", app.environmentMode());
            equal(6L, java.util.Arrays.stream(app.shell().getJMenuBar().getComponents())
                .filter(c -> c instanceof javax.swing.JMenu).count());
            javax.swing.JLabel crumb = (javax.swing.JLabel) descendants(app.shell()).stream()
                .filter(c -> "navigationBreadcrumb".equals(c.getName())).findFirst().orElseThrow();
            equal("", crumb.getText());
            equal(app.shell().getGraphicsConfiguration().getBounds(), app.shell().getBounds());
            return null;
          }));
          screenshot(app, out.resolve("02-home-" + role.name() + ".png"));
          List<Navigation.Destination> destinations = edt(() -> Navigation.destinations(app));
          tests.test(role + " / allowed destinations only", () -> {
            check(destinations.stream().allMatch(d -> d.permission().allows(role)), "Role menu mismatch");
            equal(role == Role.MANAGER ? 17 : role == Role.CASHIER ? 6 : 7, destinations.size());
          });
          tests.test(role + " / transparent full-width filtered dashboard cards", () -> edt(() -> {
            List<Component> cards = descendants(home).stream()
                .filter(c -> "dashboardFunctionCard".equals(c.getName())).toList();
            equal((long) cards.size(), destinations.stream().filter(Navigation.Destination::showOnDashboard).count());
            check(!cards.isEmpty(), "Missing cards");
            java.awt.Dimension cardSize = cards.get(0).getSize();
            for (Component card : cards) {
              check(!((javax.swing.JPanel) card).isOpaque(), "Opaque function card");
              equal(cardSize, card.getSize());
              check(!button(card, ((AbstractButton) descendants(card).stream().filter(c -> c instanceof AbstractButton)
                  .findFirst().orElseThrow()).getText()).getText().contains("Ki\u1ec3m ch\u1ee9ng"),
                  "Quality dashboard shortcut was not removed");
              javax.swing.JLabel label = (javax.swing.JLabel) descendants(card).stream()
                  .filter(c -> c instanceof javax.swing.JLabel).findFirst().orElseThrow();
              check(contrast(label.getForeground(), label.getBackground()) >= 4.5, "Label contrast below AA");
            }
            check(cards.get(0).getParent().getWidth() >= app.shell().getWidth() - 100,
                "Dashboard still reserves a mascot column");
            if (role == Role.MANAGER) {
              Navigation.Destination quality = destinations.stream()
                  .filter(d -> d.permission() == Operation.QUALITY_LIST).findFirst().orElseThrow();
              equal("H\u1ec7 th\u1ed1ng", quality.group()); check(!quality.showOnDashboard(), "Quality shortcut");
            }
            return null;
          }));
          if (role == Role.MANAGER) {
            tests.test("Custom restore/fullscreen preserves geometry and undecorated state", () -> {
              edt(() -> { button(app.shell(), "windowRestore").doClick(); return null; });
              Thread.sleep(200);
              Rectangle restored = edt(() -> app.shell().getBounds());
              edt(() -> { check(!app.shell().isBorderlessFullscreen(), "Restore failed");
                app.shell().setLocation(37, 41); app.shell().toggleFullscreen(); return null; });
              Thread.sleep(200);
              edt(() -> { equal(app.shell().getGraphicsConfiguration().getBounds(), app.shell().getBounds());
                app.shell().toggleFullscreen(); return null; });
              Thread.sleep(200);
              edt(() -> { equal(new Rectangle(37, 41, restored.width, restored.height), app.shell().getBounds());
                check(app.shell().isUndecorated(), "Restored native title bar");
                app.shell().toggleFullscreen(); return null; });
            });
            tests.test("Custom minimize and restore returns to fullscreen", () -> {
              edt(() -> { button(app.shell(), "windowMinimize").doClick(); return null; });
              Thread.sleep(300);
              edt(() -> { check((app.shell().getExtendedState() & Frame.ICONIFIED) != 0, "Not iconified");
                app.shell().setExtendedState(Frame.NORMAL); return null; });
              Thread.sleep(300);
              edt(() -> { inspect(app); equal(app.shell().getGraphicsConfiguration().getBounds(),
                app.shell().getBounds()); return null; });
            });
          }
          int index = 3;
          for (Navigation.Destination destination : destinations) {
            edt(() -> { Navigation.open(app, destination); return null; });
            idle(app);
            WorkFrame screen = edt(() -> app.shell().current());
            tests.test(role + " / screen " + destination.title(), () -> edt(() -> { inspect(app); return null; }));
            tests.test(role + " / scoped destructive action " + destination.permission(), () -> edt(() -> {
              boolean recordScreen = List.of(Operation.QUALITY_LIST, Operation.EMPLOYEES,
                  Operation.PRODUCT_SEARCH, Operation.CUSTOMERS, Operation.VENDORS, Operation.LOOKUP_SAVE,
                  Operation.RECEIVE, Operation.PROMOTIONS).contains(destination.permission());
              List<Component> removes = descendants(screen).stream()
                  .filter(c -> "recordDelete".equals(c.getName())).toList();
              equal(recordScreen && role == Role.MANAGER ? 1 : 0, removes.size());
              if (!removes.isEmpty()) { check(!removes.get(0).isEnabled(), "Delete enabled without selection"); }
              return null;
            }));
            if (destination.permission() == Operation.RECEIVE) {
              edt(() -> {
                javax.swing.JTabbedPane tabs = (javax.swing.JTabbedPane) descendants(screen).stream()
                    .filter(c -> "receiveTabs".equals(c.getName())).findFirst().orElseThrow();
                equal(2, tabs.getTabCount()); tabs.setSelectedIndex(1); return null;
              });
              idle(app);
              tests.test(role + " / posted receipts are inside Receive and retain history columns", () -> edt(() -> {
                inspect(app);
                Component panel = descendants(screen).stream().filter(c -> "postedReceiptsPanel".equals(c.getName()))
                    .findFirst().orElseThrow();
                JTable receiptTable = (JTable) descendants(panel).stream().filter(c -> c instanceof JTable)
                    .findFirst().orElseThrow();
                equal(1, receiptTable.getRowCount());
                receiptTable.setRowSelectionInterval(0, 0);
                if (role == Role.MANAGER) { check(button(panel, "recordDelete").isEnabled(),
                    "Selected delete disabled"); }
                return null;
              }));
            }

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
            confirm(() -> { button(app.shell(), "windowClose").doClick(); return null; }, JOptionPane.NO_OPTION);
            tests.test("Custom close Cancel preserves dirty form and main window", () -> edt(() -> {
              oneWindow(app); equal(editor, app.shell().current()); equal("000-DRAFT", field.getText()); return null;
            }));
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
