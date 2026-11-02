package vn.dienmay.client.ui;

import java.awt.Component;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;

/** Required reason and unambiguous final action. A window-close or Return never commits. */
public final class AdminConfirmations {
  private AdminConfirmations() {}
  public static String reason(Component owner, String title, String details, String proceed) {
    JTextArea snapshot = new JTextArea(details, 8, 46);
    snapshot.setEditable(false);
    snapshot.setLineWrap(true);
    snapshot.setWrapStyleWord(true);
    JTextArea reason = new JTextArea(4, 42);
    reason.setName("administrativeReason");
    reason.setLineWrap(true);
    reason.setWrapStyleWord(true);
    String cancel = "Quay lại";
    while (true) {
      JOptionPane pane = new JOptionPane(Ui.fields("Bản ghi", Ui.scroll(snapshot), "Lý do bắt buộc *",
          Ui.scroll(reason)),
          JOptionPane.WARNING_MESSAGE, JOptionPane.DEFAULT_OPTION, null, new Object[] {cancel, proceed}, cancel);
      JDialog dialog = pane.createDialog(owner, title);
      dialog.setName("administrativeConfirmationDialog");
      Ui.decorateDialog(dialog, title);
      Ui.fitDialog(dialog);
      dialog.setVisible(true);
      boolean confirmed = proceed.equals(pane.getValue());
      dialog.dispose();
      if (!confirmed) { return null; }
      try { return vn.dienmay.common.model.Validation.required(reason.getText(), 1000, "Lý do"); }
      catch (IllegalArgumentException ex) { Ui.error(owner, ex); }
    }
  }
}
