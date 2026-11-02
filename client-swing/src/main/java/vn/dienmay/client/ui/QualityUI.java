package vn.dienmay.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import vn.dienmay.client.AppContext;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.api.Operation;

/** Transparent review queue: pending is not fake, checksum-valid is not verified, demo is not business history. */
@SuppressWarnings("serial")
public final class QualityUI extends RowsFrame {
  private final JComboBox<String> types = new JComboBox<>(new String[] {
      "Sản phẩm", "Thương hiệu", "Loại sản phẩm"});

  public QualityUI(AppContext app) {
    super(app, "Kiểm chứng dữ liệu",
        "Chưa xác minh không có nghĩa là giả. Duyệt phải kèm nguồn và căn cứ.");
    actions.add(types);
    actions.add(Ui.button("Tải lại", this::reload));
    actions.add(Ui.button("Đối chiếu / duyệt", this::review));
    actions.add(Ui.button("Danh mục tham chiếu", () -> {
      RowsFrame screen = new RowsFrame(app, "Danh mục có nguồn",
          "Chỉ danh tính / thông số; không phải giá hay tồn thực tế.");
      screen.open();
      screen.rows.columns("Id", "BrandName", "CategoryName", "ProductName", "Model", "Specifications", "SourceUrl",
          "CheckedOn");
      screen.load(Operation.REFERENCES, Data.map());
    }));
    actions.add(Ui.button("Nhật ký quản trị", () -> {
      RowsFrame screen = new RowsFrame(app, "Nhật ký quản trị",
          "Người duyệt, thời điểm, trạng thái và lý do được giữ lại.");
      screen.open();
      screen.rows.columns("Id", "EntityType", "EntityId", "Action", "OldValue", "NewValue", "Reason", "UserName",
          "CreatedAt");
      screen.load(Operation.AUDIT_LOG, Data.map());
    }));
    RecordDeleteActions.install(app, this, this::type, this::reload);
    types.addActionListener(event -> reload());
    rows.columns("Id", "Name", "Enabled", "Revision", "VerificationStatus", "VerificationSource");
    reload();
  }

  private String type() { return switch (types.getSelectedIndex()) {
    case 1 -> "brand"; case 2 -> "category"; default -> "product"; }; }

  private void reload() { load(Operation.QUALITY_LIST, Data.map("type", type())); }

  private record Reference(long id, String name, String source) {
    @Override public String toString() { return name; }
  }

  private void review() {
    Map<String, Object> selected = rows.selected();
    String type = type();
    app.request(this, Operation.REFERENCES, Data.map(), result -> {
      List<Reference> choices = new ArrayList<>();
      choices.add(new Reference(0,
          "Đối chiếu thủ công với nguồn có thẩm quyền", ""));
      for (Object raw : (List<?>) result) {
        Map<String, Object> row = Json.object(raw);
        choices.add(new Reference(Json.number(row.get("Id")).longValueExact(),
            row.get("BrandName") + " / " + row.get("Model"), String.valueOf(row.get("SourceUrl"))));
      }
      showReview(type, selected, choices);
    });
  }

  private void showReview(String type, Map<String, Object> row, List<Reference> references) {
    JComboBox<String> field = new JComboBox<>(type.equals("product") ? new String[] {
        "Danh tính sản phẩm", "Mã vạch đúng sản phẩm / biến thể"}
            : new String[] {"Danh tính"});
    JComboBox<String> status = new JComboBox<>(new String[] {
        "Chờ xác minh", "Đã đối chiếu", "Không hợp lệ",
            "Dữ liệu mô phỏng"});
    JTextField source = Ui.text(String.valueOf(row.getOrDefault("VerificationSource", "")));
    JTextArea note = new JTextArea(5, 40);
    note.setLineWrap(true);
    note.setWrapStyleWord(true);
    JComboBox<Reference> reference = new JComboBox<>(references.toArray(Reference[]::new));
    reference.setEnabled(type.equals("product"));
    reference.addActionListener(event -> {
      Reference chosen = (Reference) reference.getSelectedItem();
      if (chosen != null && chosen.id() != 0) { source.setText(chosen.source()); }
    });
    JCheckBox confirmed = new
        JCheckBox("Tôi đã mở nguồn và đối chiếu đúng toàn bộ các trường liên quan");
    JTextArea snapshot = new JTextArea(6, 42);
    snapshot.setEditable(false);
    snapshot.setLineWrap(true);
    snapshot.setWrapStyleWord(true);
    StringBuilder details = new StringBuilder();
    for (String key : List.of("Name", "BrandName", "CategoryName", "Model", "Description", "Barcode", "BarcodeType")) {
      if (row.containsKey(key)) { details.append(RowsPanel.label(key)).append(": ").append(row.get(key)).append('\n'); }
    }
    snapshot.setText(details.toString());
    new EditDialog(app, this, "Duyệt bản ghi #" + row.get("Id"),
        Ui.fields("Bản ghi đang duyệt", Ui.scroll(snapshot), "Nhóm trường", field,
            "Danh mục tham chiếu", reference, "Kết quả", status,
            "Nguồn HTTPS", source, "Căn cứ / ghi chú *", Ui.scroll(note),
            "Xác nhận", confirmed), Operation.QUALITY_REVIEW, () -> {
          if (status.getSelectedIndex() == 1 && !confirmed.isSelected()) {
            throw new IllegalArgumentException("Phải thực sự đối chiếu nguồn trước khi đánh dấu đã xác minh.");
          }
          Reference selected = (Reference) reference.getSelectedItem();
          Long ref = !type.equals("product") || field.getSelectedIndex() != 0 || selected == null || selected.id() == 0
              ? null : selected.id();
          return Data.map("type", type, "id", row.get("Id"), "revision", row.get("Revision"),
              "field", field.getSelectedIndex() == 0 ? "identity" : "barcode",
              "status", List.of("PENDING", "VERIFIED", "INVALID", "DEMO").get(status.getSelectedIndex()),
              "source", source.getText(), "note", note.getText(), "referenceId", ref);
        }, ignored -> reload()).open();
  }
}
