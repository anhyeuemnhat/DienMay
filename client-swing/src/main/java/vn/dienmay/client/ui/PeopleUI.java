package vn.dienmay.client.ui;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import vn.dienmay.client.AppContext;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.api.Operation;
import vn.dienmay.common.model.Money;
import vn.dienmay.common.model.Role;

/** Upstream people/lookups/promotion workflows with real typed editors and server-authorized writes. */
@SuppressWarnings("serial") // View state is never Java-serialized.
public final class PeopleUI extends RowsFrame {
  public enum Kind {
    CUSTOMERS("Khách hàng", Operation.CUSTOMERS, Operation.CUSTOMER_SAVE),
    VENDORS("Nhà cung cấp", Operation.VENDORS, Operation.VENDOR_SAVE),
    EMPLOYEES("Nhân viên / tài khoản", Operation.EMPLOYEES, Operation.EMPLOYEE_SAVE),
    PROMOTIONS("Khuyến mãi", Operation.PROMOTIONS, Operation.PROMOTION_SAVE),
    CATEGORIES("Loại sản phẩm", Operation.LOOKUPS, Operation.LOOKUP_SAVE),
    BRANDS("Thương hiệu", Operation.LOOKUPS, Operation.LOOKUP_SAVE);

    private final String title;
    private final Operation query;
    private final Operation save;

    Kind(String title, Operation query, Operation save) {
      this.title = title;
      this.query = query;
      this.save = save;
    }
  }

  private final Kind kind;
  private final JTextField search = new JTextField(22);

  public PeopleUI(AppContext app, Kind kind) {
    super(app, kind.title, "Thêm / cập nhật / tra cứu • Không xóa lịch sử nghiệp vụ");
    this.kind = kind;
    if (kind == Kind.CUSTOMERS) {
      actions.add(new JLabel("Tên / điện thoại:"));
      actions.add(search);
      search.addActionListener(event -> reload());
    }
    actions.add(Ui.button("Tải lại", this::reload));
    if (kind.save.allows(app.session().role())) {
      actions.add(Ui.button("Thêm mới", () -> edit(Map.of())));
      actions.add(Ui.button("Sửa dòng chọn", () -> edit(rows.selected())));
    }
    reload();
  }

  private String lookupType() {
    return kind == Kind.CATEGORIES ? "category" : "brand";
  }

  private void reload() {
    Map<String, Object> input = kind == Kind.CUSTOMERS ? Data.map("text", search.getText())
        : kind == Kind.CATEGORIES || kind == Kind.BRANDS ? Data.map("type", lookupType()) : Data.map();
    app.request(this, kind.query, input, result -> {
      if (kind == Kind.CATEGORIES || kind == Kind.BRANDS) {
        List<Map<String, Object>> data = Json.list(result, Ui.Choice.class).stream()
            .map(choice -> Data.map("Id", choice.id(), "Name", choice.name())).toList();
        rows.setData(data);
      } else {
        rows.setRows(result);
      }
      message("Đã tải dữ liệu. Mọi thay đổi được backend kiểm tra quyền.");
    });
  }

  private static String text(Map<String, Object> row, String key) {
    Object value = row.get(key);
    return value == null ? "" : value.toString();
  }

  private void edit(Map<String, Object> row) {
    Long id = row.isEmpty() ? null : Json.number(row.get("Id")).longValueExact();
    String title = (id == null ? "Thêm: " : "Sửa: ") + kind.title;
    if (kind == Kind.CATEGORIES || kind == Kind.BRANDS) {
      JTextField name = Ui.text(text(row, "Name"));
      new EditDialog(app, this, title, Ui.fields("Tên *", name), kind.save,
          () -> Data.map("id", id, "type", lookupType(), "name", name.getText()), result -> reload()).setVisible(true);
      return;
    }
    JTextField name = Ui.text(text(row, kind == Kind.CUSTOMERS || kind == Kind.EMPLOYEES ? "FullName" : "Name"));
    JCheckBox enabled = new JCheckBox("Đang hoạt động", id == null || Boolean.TRUE.equals(row.get("Enabled")));
    if (kind == Kind.PROMOTIONS) {
      JTextField code = Ui.text(text(row, "Code"));
      JTextField from = Ui.text(Ui.DATE.format(id == null ? LocalDate.now() : LocalDate.parse(text(row, "StartDate"))));
      JTextField to = Ui.text(Ui.DATE.format(id == null ? LocalDate.now().plusMonths(1) : LocalDate.parse(text(row,
          "EndDate"))));
      JTextField percent = Ui.text(id == null ? "5" : text(row, "PercentOff"));
      new EditDialog(app, this, title, Ui.fields("Mã khuyến mãi *", code, "Tên *", name,
          "Từ ngày (dd/MM/yyyy)", from, "Đến ngày", to, "Giảm (0-100%)", percent,
          "Trạng thái", enabled), kind.save,
          () -> Data.map("id", id, "code", code.getText(), "name", name.getText(), "from", Ui.date(from),
              "to", Ui.date(to), "percent", Money.of(percent.getText()), "enabled", enabled.isSelected()),
          result -> reload()).setVisible(true);
      return;
    }
    JTextField phone = Ui.text(text(row, "Phone"));
    if (kind == Kind.EMPLOYEES) {
      JTextField username = Ui.text(text(row, "UserName"));
      JComboBox<Role> role = new JComboBox<>(Role.values());
      if (id != null) {
        role.setSelectedItem(Role.valueOf(text(row, "Role")));
      }
      JPasswordField password = new JPasswordField(24);
      new EditDialog(app, this, title, Ui.fields("Tài khoản *", username, "Họ tên *", name,
          "Điện thoại", phone, "Vai trò", role, "Trạng thái", enabled,
          "Mật khẩu (trống = giữ nguyên)", password), kind.save, () -> {
            char[] secret = password.getPassword();
            try {
              if ((id == null || secret.length > 0) && (secret.length < 12 || secret.length > 256)) {
                throw new IllegalArgumentException("Mật khẩu phải có 12-256 ký tự.");
              }
              return Data.map("id", id, "username", username.getText(), "name", name.getText(),
                  "phone", phone.getText(), "role", ((Role) role.getSelectedItem()).name(),
                  "enabled", enabled.isSelected(), "password", new String(secret));
            } finally {
              Arrays.fill(secret, '\0');
            }
          }, result -> reload()).setVisible(true);
      return;
    }
    JTextField address = Ui.text(text(row, "Address"));
    javax.swing.JPanel fields = kind == Kind.VENDORS
        ? Ui.fields("Tên *", name, "Điện thoại", phone, "Địa chỉ", address, "Trạng thái", enabled)
        : Ui.fields("Họ tên *", name, "Điện thoại", phone, "Địa chỉ", address);
    new EditDialog(app, this, title, fields, kind.save, () -> Data.map("id", id, "name", name.getText(),
        "phone", phone.getText(), "address", address.getText(), "enabled", enabled.isSelected()),
        result -> reload()).setVisible(true);
  }
}
