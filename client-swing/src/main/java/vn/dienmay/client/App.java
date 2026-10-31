package vn.dienmay.client;

import java.net.URI;
import javax.swing.SwingUtilities;
import vn.dienmay.client.api.HttpApiClient;
import vn.dienmay.client.ui.MainUI;
import vn.dienmay.client.ui.Ui;

/** Desktop entry point. Its only backend setting is the API base URL, never database credentials. */
public final class App {
  private App() {}

  public static void main(String[] args) {
    System.setProperty("awt.useSystemAAFontSettings", "on");
    System.setProperty("swing.aatext", "true");
    SwingUtilities.invokeLater(() -> {
      try {
        Ui.installTheme();
        String url = System.getenv().getOrDefault("DIENMAY_API_URL", "http://127.0.0.1:8080");
        AppContext context = new AppContext(new HttpApiClient(URI.create(url)));
        new MainUI(context).open();
      } catch (RuntimeException ex) {
        Ui.error(null, ex);
      }
    });
  }
}
