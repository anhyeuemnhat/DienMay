package vn.dienmay.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import vn.dienmay.client.AppContext;

/** All work windows share one image/brand shell; business components remain on readable surfaces. */
@SuppressWarnings("serial") // View state is never Java-serialized.
public abstract class WorkFrame extends JFrame {
  protected final AppContext app;
  protected final JPanel body = new WorkArea();
  protected final JLabel status = new JLabel("Sẵn sàng");
  private final Workspace workspace = new Workspace();
  private final String screenTitle;

  protected WorkFrame(AppContext app, String title, String subtitle) {
    super(Ui.windowTitle());
    this.app = app;
    this.screenTitle = title;
    setName(title);
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    BackgroundPanel background = new BackgroundPanel(new BorderLayout());
    setContentPane(background);
    background.add(Ui.heading(title, subtitle), BorderLayout.NORTH);
    body.setOpaque(false);
    body.setBorder(null);
    workspace.setOpaque(false);
    if (app.session() != null) {
      workspace.add(Ui.section(title, subtitle), BorderLayout.NORTH);
    }
    workspace.add(Ui.backgroundScroll(body), BorderLayout.CENTER);
    background.add(workspace, BorderLayout.CENTER);
    SurfacePanel footer = new SurfacePanel(new BorderLayout(), new Color(65, 5, 15, 238), 0);
    status.setForeground(Color.WHITE);
    status.setBorder(BorderFactory.createEmptyBorder(8, 22, 8, 22));
    javax.swing.JScrollPane statusScroll = Ui.backgroundScroll(status);
    footer.add(statusScroll, BorderLayout.CENTER);
    background.add(footer, BorderLayout.SOUTH);
    if (app.session() != null) {
      setJMenuBar(Navigation.menu(app, this));
    }
    setMinimumSize(new Dimension(760, 520));
    setSize(1180, 760);
    setLocationRelativeTo(null);
    setExtendedState(getExtendedState() | Frame.MAXIMIZED_BOTH);
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent event) {
        if (app.jobs() > 0) {
          Ui.message(WorkFrame.this, "Đang xử lý. Hoàn tất hoặc đối soát trước khi đóng.");
          return;
        }
        closeRequested();
      }
    });
  }

  /** Login/home preserve a wider mascot area; dense POS/stock forms prioritize usable work space. */
  protected final void workspaceMode(String mode) {
    workspace.mode = mode;
    workspace.revalidate();
  }

  public final String screenTitle() {
    return screenTitle;
  }

  protected void closeRequested() {
    dispose();
  }

  @Override
  public void setVisible(boolean visible) {
    if (visible) {
      app.register(this);
      Ui.fonts(this);
      setExtendedState(getExtendedState() | Frame.MAXIMIZED_BOTH);
    }
    super.setVisible(visible);
  }

  public final void message(String message) {
    status.setText(message);
    status.setToolTipText(message);
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
        case "home" -> width >= 1250 ? Math.min(400, Math.max(300, width - 1050)) : 20;
        case "dense" -> width >= 1650 ? 180 : 20;
        default -> width >= 1250 ? Math.min(320, (width - 1000) / 2) : 20;
      };
      return new Insets(14, left, 14, 24);
    }
  }
}
