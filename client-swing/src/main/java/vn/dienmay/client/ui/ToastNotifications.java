package vn.dienmay.client.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.Map;
import java.util.WeakHashMap;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Non-modal, dismissible overlay; never reserves footer space. Errors still use Ui.error dialogs. */
public final class ToastNotifications {
  private static final Map<AppShell, JPanel> ACTIVE = new WeakHashMap<>();
  private static final Map<AppShell, Timer> TIMERS = new WeakHashMap<>();
  private ToastNotifications() {}

  public static void show(Component owner, String message) {
    if (message == null || message.isBlank()) { return; }
    if (!(SwingUtilities.getWindowAncestor(owner) instanceof AppShell shell)) { return; }
    clear(shell);
    JPanel panel = new JPanel(new BorderLayout(8, 0));
    panel.setName("transientNotification");
    panel.setBackground(Ui.HEADER);
    panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(java.awt.Color.WHITE),
        BorderFactory.createEmptyBorder(10, 14, 10, 8)));
    JTextArea text = new JTextArea(message);
    text.setFont(Ui.TEXT_FONT);
    text.setForeground(java.awt.Color.WHITE);
    text.setOpaque(false);
    text.setEditable(false);
    text.setLineWrap(true);
    text.setWrapStyleWord(true);
    text.getAccessibleContext().setAccessibleName(message);
    panel.add(text, BorderLayout.CENTER);
    JButton close = new ThemeButton("✕", Ui.HEADER, java.awt.Color.WHITE);
    close.setToolTipText("Đóng thông báo");
    close.addActionListener(event -> clear(shell));
    panel.add(close, BorderLayout.EAST);
    int width = Math.min(620, Math.max(200, shell.getWidth() - 32));
    text.setSize(new Dimension(width - 80, Short.MAX_VALUE));
    int height = Math.min(180, Math.max(62, text.getPreferredSize().height + 24));
    panel.setBounds(Math.max(8, shell.getLayeredPane().getWidth() - width - 16), 65, width, height);
    shell.getLayeredPane().add(panel, JLayeredPane.POPUP_LAYER);
    ACTIVE.put(shell, panel);
    // Reading time scales with message length. Hover pauses expiry; important errors remain modal.
    Timer timer = new Timer(Math.max(6500, Math.min(20000, message.length() * 80)), event -> clear(shell));
    timer.setRepeats(false);
    java.awt.event.MouseAdapter hover = new java.awt.event.MouseAdapter() {
      @Override public void mouseEntered(java.awt.event.MouseEvent event) { timer.stop(); }
      @Override public void mouseExited(java.awt.event.MouseEvent event) { timer.restart(); }
    };
    panel.addMouseListener(hover);
    text.addMouseListener(hover);
    TIMERS.put(shell, timer);
    timer.start();
    shell.getLayeredPane().repaint();
  }

  public static void clear(AppShell shell) {
    Timer timer = TIMERS.remove(shell);
    if (timer != null) { timer.stop(); }
    JPanel panel = ACTIVE.remove(shell);
    if (panel != null) { shell.getLayeredPane().remove(panel); shell.getLayeredPane().repaint(); }
  }
}
