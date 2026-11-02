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
  private final JButton back;
  private final JButton home;
  private final JLabel breadcrumb = new JLabel();
  private WorkFrame current;
  private WorkFrame dashboard;
  private int sequence;
  private boolean fullscreen = true;
  private java.awt.Rectangle restoredBounds;
  private final JPanel windowControls = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 2, 0));
  private java.awt.Point dragOrigin;
  private java.awt.Point dragWindowOrigin;

  public AppShell(AppContext app) {
    super(Ui.windowTitle());
    this.app = app;
    setName("dienmayMainWindow");
    setUndecorated(true); // Must precede peer creation, including the first setVisible().
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    back = Ui.button("← Quay lại", this::back);
    back.setName("navigationBack");
    home = Ui.button("Trang chủ", this::home);
    home.setName("navigationHome");
    forward = Ui.button("Tiến →", this::forward);
    forward.setName("navigationForward");
    breadcrumb.setName("navigationBreadcrumb");
    JPanel navigation = new JPanel(new BorderLayout());
    navigation.add(Ui.toolbar(back, forward, home, breadcrumb), BorderLayout.CENTER);
    add(navigation, BorderLayout.NORTH);
    add(cards, BorderLayout.CENTER);
    java.awt.Rectangle bounds = getGraphicsConfiguration().getBounds();
    setMinimumSize(new Dimension(Math.min(720, bounds.width), Math.min(480, bounds.height)));
    restoredBounds = new java.awt.Rectangle(bounds.x + bounds.width / 10, bounds.y + bounds.height / 10,
        bounds.width * 4 / 5, bounds.height * 4 / 5);
    setBounds(bounds); // Full monitor bounds, never ScreenInsets: includes the taskbar area.
    windowControls.setOpaque(false);
    windowControls.setName("customWindowControls");
    windowControls.add(windowButton("–", "windowMinimize", "Thu nhỏ ứng dụng", this::minimize));
    windowControls.add(windowButton("□", "windowRestore", "Cửa sổ / Toàn màn hình (F11)", this::toggleFullscreen));
    windowControls.add(windowButton("✕", "windowClose", "Đóng ứng dụng", app::exit));
    bind("F11", "toggleFullscreen", this::toggleFullscreen);
    bind("alt F4", "guardedClose", app::exit);
    addWindowStateListener(event -> {
      if (fullscreen && (event.getOldState() & ICONIFIED) != 0
          && (event.getNewState() & ICONIFIED) == 0) {
        javax.swing.SwingUtilities.invokeLater(this::applyFullscreen);
      }
    });
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
    updateChrome();
    getRootPane().setDefaultButton(screen.defaultAction());
    breadcrumb.setText(screen == dashboard ? "" :
        (dashboard == null ? "" : "Trang chủ / ") + screen.screenTitle());
    forward.setEnabled(!forwardHistory.isEmpty() && app.session() != null && !app.session().mustChange());
    back.setEnabled(!history.isEmpty() && (app.session() == null || !app.session().mustChange()));
    home.setEnabled(dashboard != null && screen != dashboard && app.session() != null && !app.session().mustChange());
    Ui.fonts(screen);
    revalidate();
    repaint();
    if (!isVisible()) {
      // Map first, then expand the borderless frame to its monitor (without exclusive mode).
      setVisible(true);
      javax.swing.SwingUtilities.invokeLater(() -> { applyFullscreen(); toFront(); validate(); repaint(); });
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

  /** Data mode is retained in AppContext/backend; no application-wide banner or native title suffix. */
  public void environmentMode(String mode) {
    getRootPane().putClientProperty("dataMode", mode);
  }

  private JButton windowButton(String label, String name, String help, Runnable action) {
    JButton button = new ThemeButton(label, Ui.HEADER, java.awt.Color.WHITE);
    button.setName(name);
    button.setToolTipText(help);
    button.getAccessibleContext().setAccessibleName(help);
    button.setMargin(new java.awt.Insets(5, 13, 5, 13));
    button.addActionListener(event -> Ui.safe(this, action));
    return button;
  }

  private void bind(String stroke, String name, Runnable action) {
    getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(javax.swing.KeyStroke.getKeyStroke(stroke), name);
    getRootPane().getActionMap().put(name, new javax.swing.AbstractAction() {
      @Override public void actionPerformed(java.awt.event.ActionEvent event) { Ui.safe(AppShell.this, action); }
    });
  }

  private void updateChrome() {
    javax.swing.JMenuBar bar = app.session() == null || app.session().mustChange()
        ? new javax.swing.JMenuBar() : Navigation.menu(app, this);
    bar.setBackground(Ui.HEADER);
    bar.setOpaque(true);
    bar.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 8, 2, 6));
    javax.swing.Box.Filler dragSpace = (javax.swing.Box.Filler) javax.swing.Box.createHorizontalGlue();
    dragSpace.setMinimumSize(new Dimension(16, 30));
    java.awt.event.MouseAdapter drag = new java.awt.event.MouseAdapter() {
      @Override public void mousePressed(java.awt.event.MouseEvent event) {
        if (!fullscreen) { dragOrigin = event.getLocationOnScreen(); dragWindowOrigin = getLocation(); }
      }
      @Override public void mouseDragged(java.awt.event.MouseEvent event) {
        if (!fullscreen && dragOrigin != null) {
          java.awt.Point now = event.getLocationOnScreen();
          setLocation(dragWindowOrigin.x + now.x - dragOrigin.x, dragWindowOrigin.y + now.y - dragOrigin.y);
        }
      }
      @Override public void mouseReleased(java.awt.event.MouseEvent event) { dragOrigin = null; }
      @Override public void mouseClicked(java.awt.event.MouseEvent event) {
        if (event.getClickCount() == 2) { toggleFullscreen(); }
      }
    };
    bar.addMouseListener(drag);
    bar.addMouseMotionListener(drag);
    dragSpace.addMouseListener(drag);
    dragSpace.addMouseMotionListener(drag);
    bar.add(dragSpace);
    bar.add(windowControls);
    setJMenuBar(bar); // Always present, including login and mandatory password change.
  }

  public boolean isBorderlessFullscreen() { return fullscreen; }

  public void toggleFullscreen() {
    if (fullscreen) {
      fullscreen = false;
      setExtendedState(NORMAL);
      setBounds(restoredBounds);
    } else {
      restoredBounds = getBounds();
      fullscreen = true;
      applyFullscreen();
    }
    validate();
    repaint();
  }

  public void minimize() {
    setExtendedState(ICONIFIED);
  }

  private void applyFullscreen() {
    if (!fullscreen || !isDisplayable() || (getExtendedState() & ICONIFIED) != 0) { return; }
    // Use borderless, full-monitor bounds rather than exclusive GraphicsDevice fullscreen.
    // Exclusive mode puts owned confirmation dialogs below the main window on Windows;
    // clicking "Xoa ban ghi" can then look as though the application has disappeared.
    // Keeping normal window-manager ownership preserves modal dialogs and Alt-Tab.
    java.awt.GraphicsDevice device = getGraphicsConfiguration().getDevice();
    java.awt.Rectangle monitor = device.getDefaultConfiguration().getBounds();
    setBounds(monitor);
    // Some window managers keep the last restored x/y when expanding to monitor size.
    // Explicitly reset the location so F11 returns to the exact monitor rectangle.
    setLocation(monitor.getLocation());
    // A window manager may process the preceding restored-window move asynchronously.
    // Reapply only x/y after native configure events have settled; never resize a modal.
    javax.swing.Timer positionCorrection = new javax.swing.Timer(100, event -> {
      if (fullscreen && isDisplayable() && (getExtendedState() & ICONIFIED) == 0) {
        java.awt.Point topLeft = getGraphicsConfiguration().getDevice().getDefaultConfiguration().getBounds()
            .getLocation();
        if (!topLeft.equals(getLocation())) { setLocation(topLeft); }
      }
    });
    positionCorrection.setRepeats(false);
    positionCorrection.start();
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
    if (app.session() == null) { environmentMode("UNKNOWN"); }
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
    ToastNotifications.clear(this);
    super.dispose();
  }
}
