package vn.dienmay.client.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.text.JTextComponent;
import vn.dienmay.client.AppContext;
import vn.dienmay.common.api.Operation;

/** Legacy name; the editor is now a retained JPanel in AppShell, never a JDialog. */
@SuppressWarnings("serial")
public final class EditDialog extends WorkFrame {
  private final JComponent fields;
  private final List<Object> original;
  private boolean completed;
  private Runnable cancelled;
  private boolean submitting;
  private java.util.function.UnaryOperator<Map<String, Object>> beforeSubmit = value -> value;

  public EditDialog(AppContext app, Component owner, String title, JComponent fields,
      Operation operation, Supplier<Map<String, Object>> values, Consumer<Object> onSaved) {
    super(app, title, "Dữ liệu chỉ được ghi khi bấm Lưu và backend xác nhận.");
    this.fields = fields;
    original = snapshot(fields);
    body.add(Ui.scroll(fields), BorderLayout.CENTER);
    javax.swing.JButton save = Ui.button("Lưu", () -> {
      if (submitting || app.jobs() != 0 || completed) { return; }
      Map<String, Object> payload = beforeSubmit.apply(values.get());
      if (payload == null) { return; }
      submitting = true;
      String bearer = app.token();
      app.run(this, () -> app.gateway().call(operation, bearer, payload), result -> {
        submitting = false;
        completed = true;
        dispose();
        onSaved.accept(result);
      }, error -> { submitting = false; Ui.error(this, error); });
    });
    save.setName("editorSave");
    javax.swing.JButton cancel = Ui.button("Hủy thao tác", this::cancel);
    cancel.setName("editorCancel");
    body.add(Ui.toolbar(save, cancel), BorderLayout.SOUTH);
    defaultAction(save);
  }

  /** Null means user returned to editing; no network request has been made. */
  public EditDialog beforeSubmit(java.util.function.UnaryOperator<Map<String, Object>> hook) {
    beforeSubmit = Objects.requireNonNull(hook);
    return this;
  }

  public void onCancel(Runnable action) { cancelled = action; }

  private void cancel() {
    if (app.jobs() > 0 || hasUnsavedChanges() && !Ui.confirm(this,
        "Bỏ các thay đổi chưa lưu của biểu mẫu này?")) { return; }
    completed = true;
    if (cancelled != null) { cancelled.run(); }
    else { dispose(); }
  }

  @Override public boolean hasUnsavedChanges() { return !completed && !Objects.equals(original, snapshot(fields)); }

  @Override public boolean canLeave() {
    if (app.session() != null && app.session().mustChange() && !completed) {
      Ui.message(this, "Cần đổi mật khẩu hoặc hủy để đăng xuất.");
      return false;
    }
    return !hasUnsavedChanges() || Ui.confirm(this,
        "Biểu mẫu chưa lưu. Giữ bản nháp và chuyển màn hình?");
  }

  private static List<Object> snapshot(Component component) {
    List<Object> values = new ArrayList<>();
    if (component instanceof JPasswordField password) {
      char[] secret = password.getPassword();
      values.add(secret.length > 0); // Never retain a second plaintext password copy.
      java.util.Arrays.fill(secret, '\0');
    } else if (component instanceof JTextComponent text) { values.add(text.getText()); }
    else if (component instanceof JComboBox<?> combo) { values.add(combo.getSelectedItem()); return values; }
    else if (component instanceof JSpinner spinner) { values.add(spinner.getValue()); return values; }
    else if (component instanceof AbstractButton button) { values.add(button.isSelected()); }
    if (component instanceof Container container) {
      for (Component child : container.getComponents()) { values.addAll(snapshot(child)); }
    }
    return values;
  }
}
