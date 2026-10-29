package vn.dienmay.client.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Window;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import vn.dienmay.client.AppContext;
import vn.dienmay.common.api.Operation;

/** A real asynchronous editor that keeps entered values visible after server-side validation errors. */
@SuppressWarnings("serial") // View state is never Java-serialized.
public final class EditDialog extends JDialog {
  public EditDialog(AppContext app, Component owner, String title, JComponent fields,
      Operation operation, Supplier<Map<String, Object>> values, Consumer<Object> onSaved) {
    super(owner instanceof Window w ? w : owner == null ? null : SwingUtilities.getWindowAncestor(owner),
        title + " — Điện Máy Đỏ", Dialog.ModalityType.APPLICATION_MODAL);
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(new java.awt.event.WindowAdapter() {
      @Override
      public void windowClosing(java.awt.event.WindowEvent event) {
        if (app.jobs() == 0) {
          dispose();
        }
      }
    });
    setLayout(new BorderLayout(10, 10));
    add(Ui.scroll(fields), BorderLayout.CENTER);
    add(Ui.toolbar(Ui.button("Lưu", () -> app.request(this, operation, values.get(), result -> {
      dispose();
      onSaved.accept(result);
    })), Ui.button("Hủy", () -> { if (app.jobs() == 0) { dispose(); } })), BorderLayout.SOUTH);
    Ui.decorateDialog(this, title);
    Ui.fonts(this);
    Ui.fitDialog(this);
  }
}
