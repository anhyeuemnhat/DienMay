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
import vn.dienmay.common.model.CatalogRules;
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
    super(app, kind.title, "Thêm / cập nhật / tra cứu");
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
    if (Operation.LIFECYCLE.allows(app.session().role())) {
      actions.add(Ui.button("Ngừng sử dụng", () -> lifecycle(false)));
      actions.add(Ui.button("Khôi phục", () -> lifecycle(true)));
    }
    if (kind == Kind.CATEGORIES || kind == Kind.BRANDS) {
      actions.add(Ui.button("Kiểm chứng", () -> app.open("quality", () -> new QualityUI(app))));
    }
    RecordDeleteActions.install(app, this, this::entityType, this::reload);
    switch (kind) {
      case CUSTOMERS -> rows.columns("Id", "FullName", "Phone", "Address", "Enabled", "Revision");
      case VENDORS -> rows.columns("Id", "Name", "Phone", "Address", "Enabled", "Revision");
      case EMPLOYEES -> rows.columns("Id", "UserName", "FullName", "Phone", "Role", "Enabled", "MustChange",
          "Revision");
      case PROMOTIONS -> rows.columns("Id", "Code", "Name", "StartDate", "EndDate", "PercentOff", "Enabled",
          "Revision");
      default -> rows.columns("Id", "Name", "Enabled", "Revision", "VerificationStatus", "VerificationSource");
    }
    reload();
  }

  private void lifecycle(boolean enabled) {
    LifecycleActions.edit(app, this, entityType(), rows.selected(), enabled, this::reload);
  }

  private String entityType() {
    return switch (kind) {
      case CUSTOMERS -> "customer"; case VENDORS -> "vendor"; case EMPLOYEES -> "employee";
      case PROMOTIONS -> "promotion"; case CATEGORIES -> "category"; case BRANDS -> "brand";
    };
  }

  private String lookupType() {
    return kind == Kind.CATEGORIES ? "category" : "brand";
  }

  private void reload() {
    Map<String, Object> input = kind == Kind.CUSTOMERS ? Data.map("text", search.getText())
        : kind == Kind.CATEGORIES || kind == Kind.BRANDS ? Data.map("type", lookupType()) : Data.map();
    Operation query = kind == Kind.CATEGORIES || kind == Kind.BRANDS ? Operation.QUALITY_LIST : kind.query;
    app.request(this, query, input, result -> {
      rows.setRows(result);
      message("Đã tải dữ liệu. Mọi thay đổi được backend kiểm tra quyền.");
    });
  }

  private static String text(Map<String, Object> row, String key) {
    Object value = row.get(key);
    return value == null ? "" : value.toString();
  }

  private void edit(Map<String, Object> row) {
    Long id = row.isEmpty() ? null : Json.number(row.get("Id")).longValueExact();
    int revision = id == null ? 0 : Json.number(row.get("Revision")).intValueExact();
    String title = (id == null ? "Thêm: " : "Sửa: ") + kind.title;
    if (kind == Kind.CATEGORIES || kind == Kind.BRANDS) {
      JTextField name = Ui.text(text(row, "Name"));
      new EditDialog(app, this, title, Ui.fields("Tên *", name), kind.save,
          () -> Data.map("id", id, "revision", revision, "type", lookupType(), "name", name.getText()), result ->
              reload()).open();
      return;
    }
    JTextField name = Ui.text(text(row, kind == Kind.CUSTOMERS || kind == Kind.EMPLOYEES ? "FullName" : "Name"));
    JCheckBox enabled = new JCheckBox("Đang hoạt động", id == null || Boolean.TRUE.equals(row.get("Enabled")));
    enabled.setEnabled(id == null);
    enabled.setToolTipText("Bản ghi cũ: dùng Ngừng sử dụng / Khôi phục để ghi lý do.");
    if (kind == Kind.PROMOTIONS) {
      JTextField code = Ui.text(text(row, "Code"));
      JTextField from = Ui.text(Ui.DATE.format(id == null ? LocalDate.now() : LocalDate.parse(text(row, "StartDate"))));
      JTextField to = Ui.text(Ui.DATE.format(id == null ? LocalDate.now().plusMonths(1) : LocalDate.parse(text(row,
          "EndDate"))));
      JTextField percent = Ui.text(id == null ? "5" : text(row, "PercentOff"));
      new EditDialog(app, this, title, Ui.fields("Mã khuyến mãi *", code, "Tên *", name,
          "Từ ngày (dd/MM/yyyy)", from, "Đến ngày", to, "Giảm (0-100%)", percent,
          "Trạng thái", enabled), kind.save,
          () -> Data.map("id", id, "revision", revision, "code", code.getText(), "name", name.getText(), "from",
              Ui.date(from),
              "to", Ui.date(to), "percent", Money.of(percent.getText()), "enabled", enabled.isSelected()),
          result -> reload()).open();
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
              return Data.map("id", id, "revision", revision, "username", username.getText(), "name", name.getText(),
                  "phone", CatalogRules.phone(phone.getText()), "role", ((Role) role.getSelectedItem()).name(),
                  "enabled", enabled.isSelected(), "password", new String(secret));
            } finally {
              Arrays.fill(secret, '\0');
            }
          }, result -> reload()).open();
      return;
    }
    JTextField address = Ui.text(text(row, "Address"));
    javax.swing.JPanel fields = kind == Kind.VENDORS
        ? Ui.fields("Tên *", name, "Điện thoại", phone, "Địa chỉ", address, "Trạng thái", enabled)
        : Ui.fields("Họ tên *", name, "Điện thoại", phone, "Địa chỉ", address);
    new EditDialog(app, this, title, fields, kind.save, () -> Data.map("id", id, "revision", revision, "name",
        name.getText(),
        "phone", CatalogRules.phone(phone.getText()), "address", address.getText(), "enabled", enabled.isSelected()),
        result -> reload()).open();
  }
}
