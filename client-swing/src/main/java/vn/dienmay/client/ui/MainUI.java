package vn.dienmay.client.ui;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import vn.dienmay.client.AppContext;
import vn.dienmay.common.api.ApiSession;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.api.Operation;

/** Login screen adapted from the upstream MainUI workflow to the remote authentication API. */
@SuppressWarnings("serial") // View state is never Java-serialized.
public final class MainUI extends WorkFrame {
  private final JTextField username = new JTextField(26);
  private final JPasswordField password = new JPasswordField(26);

  public MainUI(AppContext app) {
    super(app, "Đăng nhập", "");
    workspaceMode("login");
    body.setLayout(new GridBagLayout());
    JPanel login = new SurfacePanel(new BorderLayout(18, 20), Ui.CARD, 20);
    login.setBorder(BorderFactory.createEmptyBorder(30, 32, 30, 32));
    JPanel welcome = new JPanel(new BorderLayout(0, 8));
    welcome.setOpaque(false);
    JLabel title = new JLabel("Đăng nhập");
    title.setFont(Ui.TEXT_FONT.deriveFont(java.awt.Font.BOLD, 28f));
    title.setForeground(Ui.HEADER);
    welcome.add(title, BorderLayout.NORTH);
    JLabel instruction = new JLabel("Sử dụng tài khoản do quản lý cấp");
    instruction.setForeground(Ui.MUTED);
    welcome.add(instruction, BorderLayout.SOUTH);
    login.add(welcome, BorderLayout.NORTH);
    login.add(Ui.fields("Tên đăng nhập", username, "Mật khẩu", password), BorderLayout.CENTER);
    javax.swing.JButton submit = Ui.button("Đăng nhập", this::login);
    login.add(submit, BorderLayout.SOUTH);
    JPanel space = new JPanel();
    space.setOpaque(false);
    GridBagConstraints g = new GridBagConstraints();
    g.gridx = 0;
    g.weightx = 1;
    g.fill = GridBagConstraints.HORIZONTAL;
    body.add(space, g);
    g.gridx = 1;
    g.weightx = 0;
    g.fill = GridBagConstraints.NONE;
    g.insets = new Insets(20, 16, 20, 36);
    body.add(login, g);
    defaultAction(submit);
    username.setName("username");
    password.setName("password");
    message("Sẵn sàng đăng nhập • Vui lòng nhập tên đăng nhập và mật khẩu.");
  }

  private void login() {
    String user = username.getText().trim();
    char[] secret = password.getPassword();
    try {
      if (user.isEmpty() || secret.length == 0) {
        throw new IllegalArgumentException("Nhập tên đăng nhập và mật khẩu.");
      }
      app.request(this, Operation.LOGIN, Data.map("username", user, "password", new String(secret)), result -> {
        password.setText("");
        app.acceptSession(Json.convert(result, ApiSession.class));
      });
    } finally {
      Arrays.fill(secret, '\0');
    }
  }
}
