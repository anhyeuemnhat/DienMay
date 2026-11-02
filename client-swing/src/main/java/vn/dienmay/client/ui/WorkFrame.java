package vn.dienmay.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import vn.dienmay.client.AppContext;

/** A business screen, NOT a native window. The legacy class name is retained for source compatibility. */
@SuppressWarnings("serial")
public abstract class WorkFrame extends JPanel {
  protected final AppContext app;
  protected final JPanel body = new WorkArea();
  private final Workspace workspace = new Workspace();
  private final String screenTitle;
  private JButton defaultAction;
  private boolean disposed;

  protected WorkFrame(AppContext app, String title, String subtitle) {
    super(new BorderLayout());
    this.app = app;
    screenTitle = title;
    setName(title);
    BackgroundPanel background = new BackgroundPanel(new BorderLayout());
    add(background, BorderLayout.CENTER);
    background.add(Ui.heading(title, subtitle), BorderLayout.NORTH);
    body.setOpaque(false);
    body.setBorder(null);
    workspace.setOpaque(false);
    if (app.session() != null) {
      workspace.add(Ui.section(title, subtitle), BorderLayout.NORTH);
    }
    workspace.add(Ui.backgroundScroll(body), BorderLayout.CENTER);
    background.add(workspace, BorderLayout.CENTER);

  }

  protected final void workspaceMode(String mode) {
    workspace.mode = mode;
    workspace.revalidate();
  }

  public final String screenTitle() { return screenTitle; }
  public final boolean isDisposed() { return disposed; }
  public final JButton defaultAction() { return defaultAction; }
  protected final void defaultAction(JButton button) { defaultAction = button; }
  public final void open() { app.show(this); }

  /** Navigation retains this instance, including row selection, filters, drafts and scroll positions. */
  public boolean canLeave() { return true; }
  public boolean hasUnsavedChanges() { return false; }
  public void onClosed() { }
  protected void closeRequested() { dispose(); }

  public void dispose() { app.close(this); }

  public final void release() {
    if (!disposed) {
      disposed = true;
      onClosed();
    }
  }

  public final void message(String message) {
    ToastNotifications.show(this, message);
  }

  /** Keep data/actions reachable on small windows without shrinking a table to zero height. */
  private static final class WorkArea extends JPanel implements javax.swing.Scrollable {
    private WorkArea() {
      super(new BorderLayout(12, 12));
      setOpaque(false);
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension preferred = super.getPreferredSize();
      if (getLayout() instanceof BorderLayout layout) {
        int height = 260;
        for (String side : new String[] {BorderLayout.NORTH, BorderLayout.SOUTH}) {
          java.awt.Component component = layout.getLayoutComponent(side);
          if (component != null && component.isVisible()) {
            height += component.getPreferredSize().height + layout.getVgap();
          }
        }
        preferred.height = height;
      }
      return preferred;
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) {
      return 24;
    }

    @Override
    public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) {
      return Math.max(24, visible.height - 24);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      return getParent() instanceof javax.swing.JViewport viewport && viewport.getHeight() >= getPreferredSize().height;
    }
  }

  private static final class Workspace extends JPanel {
    private String mode = "normal";

    private Workspace() {
      super(new BorderLayout(0, 12));
    }

    @Override
    public Insets getInsets() {
      int width = getWidth();
      int left = switch (mode) {
        case "login" -> 24;
        case "home" -> 24;
        case "dense" -> 24;
        default -> width >= 1250 ? Math.min(320, (width - 1000) / 2) : 20;
      };
      return new Insets(14, left, 14, 24);
    }
  }
}
