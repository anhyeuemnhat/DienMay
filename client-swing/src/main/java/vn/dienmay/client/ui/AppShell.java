package vn.dienmay.client.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import vn.dienmay.client.AppContext;

/** The sole application JFrame. No business screen creates a native window. EDT-only lifecycle. */
@SuppressWarnings("serial")
public final class AppShell extends JFrame {
  private final AppContext app;
  private final CardLayout layout = new CardLayout();
  private final JPanel cards = new JPanel(layout);
  private final Map<WorkFrame, String> names = new IdentityHashMap<>();
  private final Deque<WorkFrame> history = new ArrayDeque<>();
  private final Deque<WorkFrame> forwardHistory = new ArrayDeque<>();
  private final JButton forward;
  private final JButton drafts;
  private final JLabel environment = new JLabel(" ");
  private final JButton back;
  private final JButton home;
  private final JLabel breadcrumb = new JLabel();
  private WorkFrame current;
  private WorkFrame dashboard;
  private int sequence;

  public AppShell(AppContext app) {
    super(Ui.windowTitle());
    this.app = app;
    setName("dienmayMainWindow");
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    back = Ui.button("← Quay lại", this::back);
    back.setName("navigationBack");
    home = Ui.button("Trang chủ", this::home);
    home.setName("navigationHome");
    forward = Ui.button("Tiến →", this::forward);
    forward.setName("navigationForward");
    drafts = Ui.button("Nháp chưa lưu", this::showDrafts);
    drafts.setName("navigationDrafts");
    environment.setName("environmentBanner");
    environment.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 16, 5, 16));
    environment.setFont(Ui.TEXT_FONT.deriveFont(java.awt.Font.BOLD, 13f));
    JPanel navigation = new JPanel(new BorderLayout());
    navigation.add(Ui.toolbar(back, forward, home, drafts, breadcrumb), BorderLayout.NORTH);
    navigation.add(environment, BorderLayout.SOUTH);
    add(navigation, BorderLayout.NORTH);
    add(cards, BorderLayout.CENTER);
    setMinimumSize(new Dimension(840, 600));
    java.awt.Rectangle bounds = getGraphicsConfiguration().getBounds();
    java.awt.Insets insets = getToolkit().getScreenInsets(getGraphicsConfiguration());
    setBounds(bounds.x + insets.left, bounds.y + insets.top,
        bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom);
    addComponentListener(new java.awt.event.ComponentAdapter() {
      @Override public void componentResized(java.awt.event.ComponentEvent event) {
        validate();
        repaint();
      }
    });
    addWindowListener(new WindowAdapter() {
      @Override public void windowOpened(WindowEvent event) { validate(); repaint(); }
      @Override public void windowClosing(WindowEvent event) { app.exit(); }
    });
  }

  public WorkFrame current() { return current; }
  public List<WorkFrame> screens() { return List.copyOf(names.keySet()); }
  public int historySize() { return history.size(); }

  public boolean allowNavigation() {
    if (app.jobs() != 0) {
      Ui.message(this, "Đang xử lý. Vui lòng hoàn tất tác vụ trước khi chuyển màn hình.");
      return false;
    }
    return current == null || current.canLeave();
  }

  public void navigate(WorkFrame screen) {
    if (current == screen) { return; }
    if (!allowNavigation()) { return; }
    if (current != null) { history.push(current); }
    forwardHistory.clear();
    activate(screen);
  }

  /** Internal entry after the caller has already performed the leave guard. */
  public void navigateChecked(WorkFrame screen) {
    if (current == screen) { return; }
    if (current != null) { history.push(current); }
    forwardHistory.clear();
    activate(screen);
  }

  private void activate(WorkFrame screen) {
    if (screen.isDisposed()) { throw new IllegalStateException("Screen already released"); }
    if (!names.containsKey(screen)) {
      String name = "screen-" + ++sequence;
      names.put(screen, name);
      cards.add(screen, name);
    }
    current = screen;
    layout.show(cards, names.get(screen));
    setJMenuBar(app.session() == null || app.session().mustChange() ? null : Navigation.menu(app, this));
    getRootPane().setDefaultButton(screen.defaultAction());
    breadcrumb.setText((dashboard == null || screen == dashboard ? "" : "Trang chủ / ") + screen.screenTitle());
    forward.setEnabled(!forwardHistory.isEmpty() && app.session() != null && !app.session().mustChange());
    drafts.setEnabled(app.session() != null && !app.session().mustChange());
    back.setEnabled(!history.isEmpty() && (app.session() == null || !app.session().mustChange()));
    home.setEnabled(dashboard != null && screen != dashboard && !app.session().mustChange());
    Ui.fonts(screen);
    revalidate();
    repaint();
    if (!isVisible()) {
      // Use the usable display bounds without a pre-map maximize race on X11.
      setVisible(true);
      javax.swing.SwingUtilities.invokeLater(() -> { toFront(); validate(); repaint(); });
    }
  }

  public void back() {
    if (history.isEmpty() || !allowNavigation()) { return; }
    if (current != null) { forwardHistory.push(current); }
    activate(history.pop());
  }

  public void forward() {
    if (forwardHistory.isEmpty() || !allowNavigation()) { return; }
    if (current != null) { history.push(current); }
    activate(forwardHistory.pop());
  }

  public void environmentMode(String mode) {
    String text = switch (mode) {
      case "DEMO" -> "DEMO / MÔ PHỎNG - Giá, tồn kho, khách hàng và giao dịch không phải dữ liệu cửa hàng thật";
      case "PRODUCTION" -> "DỮ LIỆU CỬA HÀNG - Chỉ ghi nghiệp vụ theo chứng từ và quyền được cấp";
      default -> "CHƯA XÁC NHẬN MÔI TRƯỜNG - Kiểm tra kết nối / phân loại database trước khi thao tác";
    };
    environment.setText(text);
    environment.setToolTipText(text);
    setTitle(Ui.windowTitle() + " | " + mode);
  }

  private void showDrafts() {
    if (!allowNavigation()) { return; }
    List<WorkFrame> values = screens().stream().filter(WorkFrame::hasUnsavedChanges).toList();
    if (values.isEmpty()) { Ui.message(this, "Không có bản nháp chưa lưu trong phiên."); return; }
    javax.swing.JList<String> choices = new javax.swing.JList<>(values.stream()
        .map(WorkFrame::screenTitle).toArray(String[]::new));
    choices.setSelectedIndex(0);
    choices.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
    if (Ui.option(this, Ui.scroll(choices), "Mở lại bản nháp trong phiên",
        javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE)
        == javax.swing.JOptionPane.OK_OPTION && choices.getSelectedIndex() >= 0) {
      navigateChecked(values.get(choices.getSelectedIndex()));
    }
  }

  public void home() {
    if (dashboard != null && current != dashboard) { navigate(dashboard); }
  }

  public void reset(WorkFrame first, boolean isDashboard) {
    for (WorkFrame screen : List.copyOf(names.keySet())) { screen.release(); }
    names.clear();
    history.clear();
    forwardHistory.clear();
    cards.removeAll();
    current = null;
    dashboard = isDashboard ? first : null;
    if (app.session() == null) { environment.setText(" "); setTitle(Ui.windowTitle()); }
    activate(first);
  }

  public void removeScreen(WorkFrame screen) {
    history.removeIf(value -> value == screen);
    forwardHistory.removeIf(value -> value == screen);
    String name = names.remove(screen);
    if (name != null) { cards.remove(screen); }
    screen.release();
    if (current == screen) {
      current = null;
      if (!history.isEmpty()) { activate(history.pop()); }
      else if (dashboard != null && dashboard != screen) { activate(dashboard); }
    }
  }

  @Override public void dispose() {
    for (WorkFrame screen : List.copyOf(names.keySet())) { screen.release(); }
    names.clear();
    history.clear();
    forwardHistory.clear();
    super.dispose();
  }
}
