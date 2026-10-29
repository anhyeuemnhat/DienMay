package vn.dienmay.tests;

import static vn.dienmay.tests.TestKit.check;
import static vn.dienmay.tests.TestKit.equal;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import vn.dienmay.client.AppContext;
import vn.dienmay.client.api.HttpApiClient;
import vn.dienmay.client.ui.BackgroundPanel;
import vn.dienmay.client.ui.MainUI;
import vn.dienmay.client.ui.MakeSaleUI;
import vn.dienmay.client.ui.Navigation;
import vn.dienmay.client.ui.PeopleUI;
import vn.dienmay.client.ui.ProductsUI;
import vn.dienmay.client.ui.RowsPanel;
import vn.dienmay.client.ui.StockReceiveUI;
import vn.dienmay.client.ui.TableStyling;
import vn.dienmay.client.ui.ThemeButton;
import vn.dienmay.client.ui.Ui;
import vn.dienmay.client.ui.WorkFrame;
import vn.dienmay.common.api.ApiSession;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.api.Operation;
import vn.dienmay.common.model.Money;
import vn.dienmay.common.model.Role;
import vn.dienmay.server.api.ApiServer;

/** Additional real Swing checks. All HTTP data is TEST-ONLY; no live database is used or modified. */
public final class RedUiChecks {
  private static final String EXPECTED_TITLE = "PHẦN MỀM QUẢN LÍ KINH DOANH NỘI BỘ "
      + "CỦA CỬA HÀNG ĐIỆN MÁY ĐỎ";
  private static final String LONG_NAME = "Máy giặt cửa trước tiết kiệm điện, "
      + "bảo hành chính hãng — kiểm tra tiếng Việt đầy đủ dấu và nội dung dài.";

  private RedUiChecks() {}

  private static <T> T edt(Callable<T> work) throws Exception {
    FutureTask<T> future = new FutureTask<>(work);
    SwingUtilities.invokeAndWait(future);
    return future.get();
  }

  private static List<Component> all(Component root) {
    List<Component> result = new ArrayList<>();
    result.add(root);
    if (root instanceof Container container) {
      for (Component child : container.getComponents()) {
        result.addAll(all(child));
      }
    }
    return result;
  }

  private static JButton button(Component root, String text) {
    return all(root).stream().filter(c -> c instanceof JButton b && text.equals(b.getText()))
        .map(JButton.class::cast).findFirst().orElseThrow(() -> new AssertionError("Missing button " + text));
  }

  private static void idle(AppContext app) throws Exception {
    long end = System.nanoTime() + 10_000_000_000L;
    while (System.nanoTime() < end) {
      if (edt(app::jobs) == 0) {
        Thread.sleep(180);
        if (edt(app::jobs) == 0) {
          return;
        }
      }
      Thread.sleep(30);
    }
    throw new AssertionError("API workers did not finish");
  }

  private static WorkFrame visibleFrame() {
    return Arrays.stream(Window.getWindows()).filter(w -> w instanceof WorkFrame && w.isShowing())
        .map(WorkFrame.class::cast).reduce((first, last) -> last).orElseThrow();
  }

  private static JDialog awaitDialog() throws Exception {
    for (int tries = 0; tries < 100; tries++) {
      JDialog dialog = edt(() -> Arrays.stream(Window.getWindows())
          .filter(w -> w instanceof JDialog && w.isShowing()).map(JDialog.class::cast)
          .reduce((first, last) -> last).orElse(null));
      if (dialog != null) {
        Thread.sleep(150);
        return dialog;
      }
      Thread.sleep(40);
    }
    throw new AssertionError("No modal dialog appeared");
  }

  private static void shot(Window window, Path file) throws Exception {
    edt(() -> {
      if (window instanceof WorkFrame work) {
        work.message("UI TEST / SYNTHETIC API DATA / JAR resources / NOT SQL Server acceptance");
      }
      window.toFront();
      return null;
    });
    Thread.sleep(180);
    ImageIO.write(new Robot().createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())),
        "png", file.toFile());
  }

  private static void inspectBrand(WorkFrame frame) {
    equal(EXPECTED_TITLE, frame.getTitle());
    check(frame.getContentPane() instanceof BackgroundPanel, "Missing shared background");
    List<JLabel> titles = all(frame).stream().filter(c -> "applicationBrandTitle".equals(c.getName()))
        .map(JLabel.class::cast).toList();
    equal(1, titles.size());
    JLabel label = titles.get(0);
    equal(EXPECTED_TITLE, label.getText());
    check(label.getFont().canDisplayUpTo(EXPECTED_TITLE) < 0, "Vietnamese glyph missing");
    check(label.getFontMetrics(label.getFont()).stringWidth(EXPECTED_TITLE) <= label.getWidth(),
        "Brand title clipped: " + label.getSize());
    for (Component component : all(frame)) {
      if (component instanceof JLabel text) {
        String value = String.valueOf(text.getText());
        check(!value.contains("DIEN MAY /") && !value.contains("Swing → API → SQL Server")
            && !value.contains("| DienMay"), "Old brand still visible");
      }
    }
  }

  private static void reachableButtons(Window window) {
    Rectangle outer = new Rectangle(window.getLocationOnScreen(), window.getSize());
    for (Component component : all(window)) {
      if (!(component instanceof ThemeButton button) || !button.isShowing()) {
        continue;
      }
      Rectangle bounds = new Rectangle(button.getLocationOnScreen(), button.getSize());
      check(bounds.width > 0 && bounds.height > 0, "Zero-sized action " + button.getText());
      if (!outer.contains(bounds) || button.getVisibleRect().width < button.getWidth()
          || button.getVisibleRect().height < button.getHeight()) {
        JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, button);
        check(scroll != null && (scroll.getVerticalScrollBar().isVisible()
            || scroll.getHorizontalScrollBar().isVisible()), "Unreachable action " + button.getText() + bounds);
      }
    }
  }

  private static double channel(int value) {
    double srgb = value / 255.0;
    return srgb <= 0.04045 ? srgb / 12.92 : Math.pow((srgb + 0.055) / 1.055, 2.4);
  }

  private static double luminance(Color color) {
    return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen())
        + 0.0722 * channel(color.getBlue());
  }

  private static double contrast(Color a, Color b) {
    double first = luminance(a);
    double second = luminance(b);
    return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
  }

  private static void palette(TestKit tests) {
    Color[][] pairs = {{Ui.TEXT, Color.WHITE}, {Ui.MUTED, Color.WHITE}, {Color.WHITE, Ui.PRIMARY},
        {Color.WHITE, Ui.HEADER}, {Color.WHITE, Ui.SELECTION}, {Color.WHITE, Ui.SUCCESS},
        {Ui.DISABLED_TEXT, Ui.DISABLED_BACKGROUND}, {Ui.TEXT, Ui.ALTERNATE_ROW}};
    for (int i = 0; i < pairs.length; i++) {
      Color[] pair = pairs[i];
      tests.test("Contrast text palette pair " + i, () -> {
        double ratio = contrast(pair[0], pair[1]);
        System.out.printf(java.util.Locale.ROOT, "CONTRAST | #%06x on #%06x = %.2f:1%n",
            pair[0].getRGB() & 0xffffff, pair[1].getRGB() & 0xffffff, ratio);
        check(ratio >= 4.5, "Text contrast below 4.5:1");
      });
    }
    tests.test("Important control border contrast", () -> {
      check(contrast(Ui.CONTROL_BORDER, Color.WHITE) >= 3.0, "Border below 3:1");
      check(contrast(Ui.CONTROL_BORDER, Ui.DISABLED_BACKGROUND) >= 3.0, "Disabled border below 3:1");
      System.out.printf(java.util.Locale.ROOT, "BORDER CONTRAST | normal=%.2f disabled=%.2f%n",
          contrast(Ui.CONTROL_BORDER, Color.WHITE), contrast(Ui.CONTROL_BORDER, Ui.DISABLED_BACKGROUND));
    });
  }

  private static void inspectDialog(JDialog dialog) {
    check(dialog.getContentPane() instanceof BackgroundPanel, "Dialog has no mascot background");
    check(dialog.getTitle().contains("Điện Máy Đỏ"), "Dialog brand mismatch");
    for (Component component : all(dialog)) {
      if (component instanceof JTextField field) {
        check(field.isOpaque(), "Transparent input");
        check(contrast(field.getForeground(), field.getBackground()) >= 4.5, "Input contrast");
      }
    }
    reachableButtons(dialog);
  }

  private static void editor(TestKit tests, WorkFrame frame, String action, Path file) throws Exception {
    SwingUtilities.invokeLater(() -> button(frame, action).doClick());
    JDialog dialog = awaitDialog();
    tests.test("Editor background/readability " + dialog.getTitle(), () -> edt(() -> {
      inspectDialog(dialog);
      return null;
    }));
    shot(dialog, file);
    edt(() -> { button(dialog, "Hủy").doClick(); return null; });
  }

  public static void main(String[] args) throws Exception {
    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    Path output = Path.of(args.length > 0 ? args[0] : "docs/red-ui/checks", screen.width + "x" + screen.height);
    Files.createDirectories(output);
    System.setProperty("dienmay.stateDir", Files.createTempDirectory("dm-red-ui-test-").toString());
    TestKit tests = new TestKit();
    palette(tests);
    tests.test("Background is loaded from packaged JAR, not the working directory", () -> {
      String resource = BackgroundPanel.class.getResource(BackgroundPanel.RESOURCE).toString();
      System.out.println("RESOURCE | " + resource);
      check(resource.startsWith("jar:"), "Run this suite with dist/client-swing.jar");
      check(ImageIO.read(BackgroundPanel.class.getResource(BackgroundPanel.RESOURCE)) != null, "PNG invalid");
    });
    tests.test("Cover background fills portrait, small and large canvases", () -> edt(() -> {
      for (Dimension size : List.of(new Dimension(1366, 768), new Dimension(1920, 1080),
          new Dimension(760, 520), new Dimension(500, 700))) {
        BackgroundPanel panel = new BackgroundPanel(new java.awt.BorderLayout());
        panel.setSize(size);
        BufferedImage canvas = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
        var graphics = canvas.createGraphics();
        panel.paint(graphics);
        graphics.dispose();
        for (int x : new int[] {0, size.width - 1}) {
          for (int y : new int[] {0, size.height - 1}) {
            Color color = new Color(canvas.getRGB(x, y));
            check(color.getRed() > color.getGreen() + 30, "Blank edge at " + size + ":" + x + "," + y);
          }
        }
      }
      return null;
    }));
    try (ApiServer server = new ApiServer(new InetSocketAddress("127.0.0.1", 0), null, new UiFixture())) {
      server.start();
      HttpApiClient gateway = new HttpApiClient(URI.create("http://127.0.0.1:" + server.port()));
      AppContext app = new AppContext(gateway);
      MainUI login = edt(() -> { MainUI value = new MainUI(app); value.setVisible(true); return value; });
      Thread.sleep(500);
      tests.test("Exact title and retained username/password controls", () -> edt(() -> {
        inspectBrand(login);
        check(all(login).stream().anyMatch(c -> c instanceof JPasswordField), "Password control removed");
        return null;
      }));
      tests.test("Actual login button performs HTTP authentication", () -> {
        edt(() -> {
          ((JTextField) all(login).stream().filter(c -> "username".equals(c.getName())).findFirst().orElseThrow())
              .setText("MANAGER");
          ((JPasswordField) all(login).stream().filter(c -> "password".equals(c.getName())).findFirst().orElseThrow())
              .setText("Synthetic-ui-test-only");
          button(login, "Đăng nhập").doClick();
          return null;
        });
        idle(app);
        equal(Role.MANAGER, edt(() -> app.session().role()));
        check(!edt(login::isShowing), "Login window did not close");
      });
      WorkFrame home = edt(RedUiChecks::visibleFrame);
      ProductsUI products = edt(() -> { ProductsUI value = new ProductsUI(app); value.setVisible(true); return value;
          });
      idle(app);
      tests.test("Opaque table, selected row and header contrast", () -> edt(() -> {
        JTable table = all(products).stream().filter(JTable.class::isInstance).map(JTable.class::cast)
            .findFirst().orElseThrow();
        table.setRowSelectionInterval(0, 0);
        Component cell = table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0);
        check(cell instanceof JTextArea area && area.isOpaque(), "Cell transparency");
        check(contrast(cell.getForeground(), cell.getBackground()) >= 4.5, "Selected text contrast");
        Component header = table.getTableHeader().getDefaultRenderer().getTableCellRendererComponent(table,
            table.getColumnName(0), false, false, -1, 0);
        check(contrast(header.getForeground(), header.getBackground()) >= 4.5, "Header contrast");
        return null;
      }));
      editor(tests, products, "Thêm sản phẩm", output.resolve("21-product-add-dialog.png"));
      editor(tests, products, "Sửa dòng chọn", output.resolve("22-product-edit-dialog.png"));
      int index = 23;
      for (PeopleUI.Kind kind : PeopleUI.Kind.values()) {
        PeopleUI people = edt(() -> { PeopleUI value = new PeopleUI(app, kind); value.setVisible(true); return value;
            });
        idle(app);
        editor(tests, people, "Thêm mới", output.resolve(index++ + "-add-" + kind.name().toLowerCase() + ".png"));
        edt(() -> { people.dispose(); return null; });
      }
      SwingUtilities.invokeLater(() -> app.changePassword(products, false));
      JDialog password = awaitDialog();
      tests.test("Change-password dialog retains three masked controls", () -> edt(() -> {
        inspectDialog(password);
        equal(3L, all(password).stream().filter(JPasswordField.class::isInstance).count());
        return null;
      }));
      shot(password, output.resolve("29-password-dialog.png"));
      edt(() -> { button(password, "Hủy").doClick(); return null; });
      SwingUtilities.invokeLater(() -> Ui.error(products,
          new IllegalArgumentException("Dữ liệu kiểm thử không hợp lệ.\n" + LONG_NAME)));
      JDialog error = awaitDialog();
      tests.test("Error popup has readable Vietnamese text and background", () -> edt(() -> {
        inspectDialog(error);
        check(all(error).stream().anyMatch(c -> c instanceof JTextArea area && area.getText().contains(LONG_NAME)),
            "Error text lost");
        return null;
      }));
      shot(error, output.resolve("30-error-dialog.png"));
      edt(() -> { button(error, "Đồng ý").doClick(); return null; });
      RowsPanel rows = edt(() -> all(products).stream().filter(RowsPanel.class::isInstance)
          .map(RowsPanel.class::cast).findFirst().orElseThrow());
      tests.test("500 rows / 22 columns / long text / large currency / both scrollbars", () -> {
        edt(() -> {
          List<Map<String, Object>> data = new ArrayList<>();
          for (int row = 0; row < 500; row++) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("Id", row + 1);
            values.put("Name", LONG_NAME + " / " + row);
            values.put("Phone", "0001234567890");
            values.put("Total", Money.of("999999999999999.99"));
            values.put("Notes", LONG_NAME + "\n" + LONG_NAME);
            for (int col = 0; col < 17; col++) {
              values.put("Column " + col, "Dữ liệu " + row + "/" + col);
            }
            data.add(values);
          }
          rows.setData(data);
          equal(500, rows.table().getRowCount());
          equal(22, rows.table().getColumnCount());
          rows.table().setRowSelectionInterval(1, 1);
          return null;
        });
        Thread.sleep(300);
        edt(() -> {
          JScrollPane pane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, rows.table());
          check(pane.getVerticalScrollBar().isVisible(), "Missing vertical scrollbar");
          check(pane.getHorizontalScrollBar().isVisible(), "Missing horizontal scrollbar");
          check(rows.table().getRowHeight(0) > 38, "Long cells were not wrapped");
          return null;
        });
      });
      shot(products, output.resolve("31-many-rows-long-vietnamese-selected.png"));
      tests.test("Column resizing recomputes wrapped row heights", () -> {
        edt(() -> { rows.table().getColumnModel().getColumn(1).setWidth(180); return null; });
        Thread.sleep(300);
        edt(() -> {
          JTable table = rows.table();
          Component cell = table.prepareRenderer(table.getCellRenderer(0, 1), 0, 1);
          cell.setSize(179, Short.MAX_VALUE);
          check(table.getRowHeight(0) >= cell.getPreferredSize().height, "Clipped wrapped row");
          return null;
        });
      });
      tests.test("Filter/empty-state/recovery preserve table data", () -> edt(() -> {
        JTextField filter = all(rows).stream().filter(JTextField.class::isInstance)
            .map(JTextField.class::cast).findFirst().orElseThrow();
        filter.setText("NO_SUCH_SYNTHETIC_ROW_73912");
        equal(0, rows.table().getRowCount());
        return null;
      }));
      shot(products, output.resolve("32-empty-filtered-table.png"));
      edt(() -> {
        all(rows).stream().filter(JTextField.class::isInstance).map(JTextField.class::cast)
            .findFirst().orElseThrow().setText("");
        equal(500, rows.table().getRowCount());
        return null;
      });
      edt(() -> { products.dispose(); return null; });
      for (WorkFrame frame : List.of(home,
          edt(() -> { ProductsUI value = new ProductsUI(app); value.setVisible(true); return value; }),
          edt(() -> { MakeSaleUI value = new MakeSaleUI(app); value.setVisible(true); return value; }),
          edt(() -> { StockReceiveUI value = new StockReceiveUI(app); value.setVisible(true); return value; }))) {
        idle(app);
        for (Dimension size : List.of(new Dimension(1180, 700), new Dimension(960, 640), new Dimension(760, 520))) {
          edt(() -> {
            frame.setExtendedState(Frame.NORMAL);
            frame.setBounds(20, 30, size.width, size.height);
            frame.toFront();
            return null;
          });
          Thread.sleep(250);
          tests.test("Resize " + frame.getClass().getSimpleName() + " " + size.width + "x" + size.height,
              () -> edt(() -> { inspectBrand(frame); reachableButtons(frame); return null; }));
        }
        shot(frame, output.resolve("33-resize-" + frame.getClass().getSimpleName() + ".png"));
        edt(() -> { if (frame != home) { frame.dispose(); } return null; });
      }
      for (Role role : List.of(Role.CASHIER, Role.WAREHOUSE)) {
        ApiSession session = Json.convert(gateway.call(Operation.LOGIN, "", Data.map("username", role.name())),
            ApiSession.class);
        edt(() -> { app.acceptSession(session); return null; });
        idle(app);
        for (Navigation.Destination destination : edt(() -> Navigation.destinations(app))) {
          WorkFrame frame = edt(() -> {
            WorkFrame value = destination.create().apply(app);
            value.setVisible(true);
            return value;
          });
          idle(app);
          tests.test("Role " + role + " / " + destination.title(), () -> edt(() -> {
            inspectBrand(frame);
            check(destination.permission().allows(role), "Permission changed");
            reachableButtons(frame);
            return null;
          }));
          edt(() -> { frame.dispose(); return null; });
        }
      }
      WorkFrame current = edt(RedUiChecks::visibleFrame);
      SwingUtilities.invokeLater(() -> app.logout(current));
      JDialog confirmation = awaitDialog();
      tests.test("Logout confirmation keeps standard modal controls", () -> edt(() -> {
        inspectDialog(confirmation);
        button(confirmation, "Đồng ý").doClick();
        return null;
      }));
      idle(app);
      tests.test("Logout returns to the same branded login window", () -> edt(() -> {
        check(app.session() == null, "Session not cleared");
        WorkFrame frame = visibleFrame();
        check(frame instanceof MainUI, "Login did not return");
        inspectBrand(frame);
        return null;
      }));
    } finally {
      edt(() -> {
        for (Window window : Window.getWindows()) {
          window.dispose();
        }
        return null;
      });
    }
    tests.finish("red-ui-jar-" + screen.width + "x" + screen.height);
    System.out.println("SCOPE | Real Swing + local synthetic HTTP. "
        + "No SQL Server, Windows DPI or peripheral acceptance.");
  }
}
