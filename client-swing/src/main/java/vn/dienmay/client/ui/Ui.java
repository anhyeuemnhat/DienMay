package vn.dienmay.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import vn.dienmay.common.api.ApiException;

/** Shared Swing presentation helpers, deliberately independent of backend service classes. */
public final class Ui {
  public static final String APP_TITLE = "PHẦN MỀM QUẢN LÍ KINH DOANH NỘI BỘ "
      + "CỦA CỬA HÀNG ĐIỆN MÁY ĐỎ";
  public static final Color PRIMARY = new Color(164, 16, 39);
  public static final Color HEADER = new Color(104, 10, 28);
  public static final Color BACKGROUND = new Color(248, 245, 246);
  public static final Color TEXT = new Color(38, 28, 33);
  public static final Color MUTED = new Color(92, 75, 83);
  public static final Color CONTROL_BORDER = new Color(123, 103, 112);
  public static final Color FOCUS = new Color(87, 9, 26);
  public static final Color ALTERNATE_ROW = new Color(247, 241, 243);
  public static final Color SELECTION = new Color(119, 12, 34);
  public static final Color DISABLED_BACKGROUND = new Color(231, 225, 228);
  public static final Color DISABLED_TEXT = new Color(92, 78, 85);
  public static final Color SUCCESS = new Color(23, 91, 65);
  public static final Color CARD = new Color(255, 255, 255, 247);
  public static final Font TEXT_FONT = new Font(Font.DIALOG, Font.PLAIN, 14);
  public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu")
      .withResolverStyle(ResolverStyle.STRICT);
  private static boolean installed;

  private Ui() {}

  /** Initialize before constructing controls, including direct screen construction in tests. */
  public static String windowTitle() {
    installTheme();
    return APP_TITLE;
  }

  public static void installTheme() {
    if (installed) {
      return;
    }
    try {
      // Metal avoids native Windows/GTK delegates ignoring contrast-critical palette values.
      UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
    } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
      throw new IllegalStateException("Không khởi tạo được giao diện Swing.", ex);
    }
    for (String type : new String[] {"Label", "Button", "ToggleButton", "TextField", "PasswordField",
        "TextArea", "ComboBox", "Spinner", "CheckBox", "RadioButton", "List", "Table", "TableHeader",
        "Menu", "MenuItem", "MenuBar", "OptionPane", "ToolTip", "TabbedPane", "TitledBorder"}) {
      UIManager.put(type + ".font", TEXT_FONT);
      UIManager.put(type + ".foreground", TEXT);
      UIManager.put(type + ".background", Color.WHITE);
      UIManager.put(type + ".disabledForeground", DISABLED_TEXT);
      UIManager.put(type + ".selectionBackground", SELECTION);
      UIManager.put(type + ".selectionForeground", Color.WHITE);
    }
    UIManager.put("Panel.background", Color.WHITE);
    UIManager.put("Viewport.background", Color.WHITE);
    UIManager.put("ScrollPane.background", Color.WHITE);
    UIManager.put("control", BACKGROUND);
    UIManager.put("text", TEXT);
    UIManager.put("textHighlight", SELECTION);
    UIManager.put("textHighlightText", Color.WHITE);
    UIManager.put("Button.disabledText", DISABLED_TEXT);
    UIManager.put("Label.disabledForeground", DISABLED_TEXT);
    UIManager.put("ComboBox.disabledBackground", DISABLED_BACKGROUND);
    UIManager.put("TextField.inactiveForeground", DISABLED_TEXT);
    UIManager.put("PasswordField.inactiveForeground", DISABLED_TEXT);
    UIManager.put("TextArea.inactiveForeground", DISABLED_TEXT);
    UIManager.put("TextField.selectionBackground", SELECTION);
    UIManager.put("PasswordField.selectionBackground", SELECTION);
    UIManager.put("TextArea.selectionBackground", SELECTION);
    UIManager.put("TextField.selectionForeground", Color.WHITE);
    UIManager.put("PasswordField.selectionForeground", Color.WHITE);
    UIManager.put("TextArea.selectionForeground", Color.WHITE);
    Border inputBorder = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(CONTROL_BORDER), BorderFactory.createEmptyBorder(7, 9, 7, 9));
    UIManager.put("TextField.border", inputBorder);
    UIManager.put("PasswordField.border", inputBorder);
    UIManager.put("TextArea.border", BorderFactory.createEmptyBorder(8, 10, 8, 10));
    UIManager.put("ComboBox.border", BorderFactory.createLineBorder(CONTROL_BORDER));
    UIManager.put("Spinner.border", BorderFactory.createLineBorder(CONTROL_BORDER));
    UIManager.put("ScrollPane.border", BorderFactory.createLineBorder(CONTROL_BORDER));
    UIManager.put("ScrollBar.width", 16);
    UIManager.put("MenuBar.background", HEADER);
    UIManager.put("Menu.foreground", Color.WHITE);
    UIManager.put("Menu.background", HEADER);
    UIManager.put("Menu.selectionBackground", PRIMARY);
    UIManager.put("Menu.selectionForeground", Color.WHITE);
    UIManager.put("Menu.disabledForeground", new Color(231, 208, 214));
    UIManager.put("MenuItem.selectionBackground", PRIMARY);
    UIManager.put("MenuItem.selectionForeground", Color.WHITE);
    UIManager.put("OptionPane.okButtonText", "Đồng ý");
    UIManager.put("OptionPane.cancelButtonText", "Hủy");
    UIManager.put("OptionPane.yesButtonText", "Có");
    UIManager.put("OptionPane.noButtonText", "Không");
    UIManager.put("OptionPane.messageForeground", TEXT);
    UIManager.put("ToolTip.background", Color.WHITE);
    UIManager.put("ToolTip.foreground", TEXT);
    installed = true;
  }

  public static JButton button(String title, Runnable action) {
    String lower = title.toLowerCase(Locale.ROOT);
    boolean primary = lower.startsWith("thêm") || lower.startsWith("lưu")
        || lower.startsWith("đăng nhập") || lower.startsWith("lập báo cáo");
    boolean commit = lower.contains("thanh toán") || lower.startsWith("xác nhận ghi");
    boolean danger = lower.startsWith("xóa");
    Color fill = commit ? SUCCESS : primary ? PRIMARY : danger ? HEADER : Color.WHITE;
    JButton button = new ThemeButton(title, fill, fill.equals(Color.WHITE) ? TEXT : Color.WHITE);
    button.addActionListener(event -> safe(button, action));
    return button;
  }

  public static void safe(Component owner, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException ex) {
      error(owner, ex);
    }
  }

  public static void error(Component owner, Throwable error) {
    String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    if (error instanceof ApiException api && !api.correlationId().isBlank()) {
      message += "\nMã hỗ trợ: " + api.correlationId();
    }
    if (error instanceof IOException || error instanceof InterruptedException) {
      message = "Không liên lạc được backend. Kiểm tra mạng và địa chỉ API.\n"
          + "Với bán hàng/nhập/chuyển kho: giữ nguyên yêu cầu, dùng Đối soát trước khi thử lại.\n"
          + message;
    }
    option(owner, message, "Không thể thực hiện", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE);
  }

  public static void message(Component owner, String message) {
    option(owner, message, "Điện Máy Đỏ", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE);
  }

  public static boolean confirm(Component owner, String message) {
    return option(owner, message, "Xác nhận", JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION;
  }

  /** Keep standard JOptionPane return values and modal semantics; skin only its surrounding window. */
  public static int option(Component owner, Object message, String title, int options, int messageType) {
    installTheme();
    Object content = message;
    if (message instanceof String text) {
      JTextArea area = new JTextArea(text, 0, 45);
      area.setRows(Math.min(12, Math.max(3, text.split("\\R", -1).length + 2)));
      area.setEditable(false);
      area.setLineWrap(true);
      area.setWrapStyleWord(true);
      area.setForeground(messageType == JOptionPane.ERROR_MESSAGE ? HEADER : TEXT);
      area.setCaretPosition(0);
      content = scroll(area);
    }
    JOptionPane pane = new JOptionPane(content, messageType, options);
    JDialog dialog = pane.createDialog(owner, title + " — Điện Máy Đỏ");
    decorateDialog(dialog, title);
    fonts(dialog);
    fitDialog(dialog);
    dialog.setVisible(true);
    Object value = pane.getValue();
    dialog.dispose();
    return value instanceof Integer number ? number : JOptionPane.CLOSED_OPTION;
  }

  public static JTextField text(String value) {
    return new JTextField(value == null ? "" : value, 24);
  }

  public static LocalDate date(JTextField field) {
    try {
      return LocalDate.parse(field.getText().trim(), DATE);
    } catch (DateTimeParseException ex) {
      throw new IllegalArgumentException("Ngày không hợp lệ. Nhập dd/MM/yyyy.");
    }
  }

  public static int integer(javax.swing.JSpinner spinner) {
    try {
      spinner.commitEdit();
      return ((Number) spinner.getValue()).intValue();
    } catch (java.text.ParseException ex) {
      throw new IllegalArgumentException("So nguyen khong hop le.");
    }
  }

  public static JPanel fields(Object... values) {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBackground(Color.WHITE);
    GridBagConstraints g = new GridBagConstraints();
    g.insets = new Insets(7, 10, 7, 10);
    g.anchor = GridBagConstraints.NORTHWEST;
    for (int i = 0; i < values.length; i += 2) {
      g.gridy = i / 2;
      g.gridx = 0;
      g.weightx = 0;
      g.fill = GridBagConstraints.NONE;
      JLabel label = new JLabel(String.valueOf(values[i]));
      label.setForeground(TEXT);
      label.setLabelFor((Component) values[i + 1]);
      panel.add(label, g);
      g.gridx = 1;
      g.weightx = 1;
      g.fill = GridBagConstraints.HORIZONTAL;
      GuidedInput.apply(String.valueOf(values[i]), (Component) values[i + 1]);
      panel.add((Component) values[i + 1], g);
    }
    return panel;
  }

  /** Tighten only dense transaction forms; labels, fields and handlers are unchanged. */
  public static void compactFields(JPanel panel) {
    if (panel.getLayout() instanceof GridBagLayout layout) {
      for (Component child : panel.getComponents()) {
        GridBagConstraints constraints = layout.getConstraints(child);
        constraints.insets = new Insets(3, 8, 3, 8);
        layout.setConstraints(child, constraints);
      }
    }
  }

  public static JPanel heading(String title, String subtitle) {
    return new BrandHeading();
  }

  /** Functional context stays in the workspace, never in the one-line brand strip. */
  public static JPanel section(String title, String detail) {
    JPanel panel = new SurfacePanel(new BorderLayout(4, 4), CARD, 14);
    panel.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
    JLabel label = new JLabel(title);
    label.setFont(TEXT_FONT.deriveFont(Font.BOLD, 21f));
    label.setForeground(HEADER);
    panel.add(label, BorderLayout.NORTH);
    if (detail != null && !detail.isBlank()) {
      JTextArea context = new JTextArea(detail);
      context.setFont(TEXT_FONT);
      context.setForeground(MUTED);
      context.setOpaque(false);
      context.setEditable(false);
      context.setLineWrap(true);
      context.setWrapStyleWord(true);
      context.setBorder(null);
      panel.add(context, BorderLayout.CENTER);
    }
    return panel;
  }

  public static JPanel toolbar(Component... components) {
    JPanel panel = new SurfacePanel(new WrapLayout(), CARD, 12);
    for (Component component : components) {
      panel.add(component);
    }
    return panel;
  }

  public static JScrollPane scroll(JComponent component) {
    JScrollPane pane = new JScrollPane(component);
    pane.getViewport().setBackground(Color.WHITE);
    pane.getVerticalScrollBar().setUnitIncrement(24);
    pane.getHorizontalScrollBar().setUnitIncrement(24);
    return pane;
  }

  /** Only decorative containers opt into this; never use it for a data table or editable field. */
  public static JScrollPane backgroundScroll(JComponent component) {
    JScrollPane pane = scroll(component);
    pane.setOpaque(false);
    pane.getViewport().setOpaque(false);
    pane.setBorder(null);
    return pane;
  }

  public static void fonts(Component component) {
    Font previous = component.getFont();
    if (previous != null && previous.getSize() < 14 && !"applicationBrandTitle".equals(component.getName())) {
      component.setFont(new Font(Font.DIALOG, previous.getStyle(), 14));
    }
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) {
        fonts(child);
      }
    }
  }

  public static void decorateDialog(JDialog dialog, String title) {
    Container original = dialog.getContentPane();
    BackgroundPanel background = new BackgroundPanel(new BorderLayout(12, 12));
    background.setBorder(BorderFactory.createEmptyBorder(18, 22, 22, 22));
    JLabel heading = new JLabel(title);
    heading.setFont(TEXT_FONT.deriveFont(Font.BOLD, 20f));
    heading.setForeground(Color.WHITE);
    SurfacePanel strip = new SurfacePanel(new BorderLayout(), new Color(65, 5, 15, 238), 10);
    strip.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
    strip.add(heading);
    background.add(strip, BorderLayout.NORTH);
    SurfacePanel card = new SurfacePanel(new BorderLayout(), Color.WHITE, 10);
    card.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    card.add(original, BorderLayout.CENTER);
    background.add(card, BorderLayout.CENTER);
    dialog.setContentPane(background);
  }

  public static void fitDialog(Window dialog) {
    java.awt.Rectangle bounds = dialog.getGraphicsConfiguration().getBounds();
    Insets insets = dialog.getToolkit().getScreenInsets(dialog.getGraphicsConfiguration());
    int width = bounds.width - insets.left - insets.right;
    int height = bounds.height - insets.top - insets.bottom;
    dialog.pack();
    dialog.setSize(Math.min(Math.max(500, dialog.getWidth() + 20), (int) (width * 0.88)),
        Math.min(Math.max(240, dialog.getHeight()), (int) (height * 0.85)));
    dialog.setMinimumSize(new Dimension(Math.min(460, width), Math.min(240, height)));
    dialog.setLocationRelativeTo(dialog.getOwner());
  }

  public static int saveFile(Component owner, JFileChooser chooser) {
    Window window = owner instanceof Window w ? w : SwingUtilities.getWindowAncestor(owner);
    JDialog dialog = new JDialog(window, "Xuất CSV — Điện Máy Đỏ",
        Dialog.ModalityType.APPLICATION_MODAL);
    int[] result = {JFileChooser.CANCEL_OPTION};
    chooser.setDialogType(JFileChooser.SAVE_DIALOG);
    java.awt.event.ActionListener listener = event -> {
      if (JFileChooser.APPROVE_SELECTION.equals(event.getActionCommand())) {
        result[0] = JFileChooser.APPROVE_OPTION;
      }
      dialog.dispose();
    };
    chooser.addActionListener(listener);
    dialog.add(chooser);
    decorateDialog(dialog, "Xuất bảng dữ liệu CSV");
    fitDialog(dialog);
    dialog.setVisible(true);
    chooser.removeActionListener(listener);
    dialog.dispose();
    return result[0];
  }

  public record Choice(long id, String name) {
    @Override
    public String toString() {
      return name;
    }
  }
}
