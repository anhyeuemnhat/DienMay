package vn.dienmay.client.ui;

import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.text.Normalizer;
import java.util.Locale;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPasswordField;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

/** Paint-only placeholders: never insert example text into a user's document or API payload. */
public final class GuidedInput {
  private GuidedInput() { }

  public static void install(JTextComponent field, String hint) {
    if (field instanceof JPasswordField || !field.isEditable()) { field.setToolTipText(hint); return; }
    if (field.getClientProperty("guidedHint") != null) { return; }
    field.putClientProperty("guidedHint", hint);
    field.getAccessibleContext().setAccessibleDescription(hint);
    field.setToolTipText(hint);
    Border original = field.getBorder();
    field.setBorder(new PromptBorder(original, field, hint));
    field.getDocument().addDocumentListener(new DocumentListener() {
      @Override public void insertUpdate(DocumentEvent event) { field.repaint(); }
      @Override public void removeUpdate(DocumentEvent event) { field.repaint(); }
      @Override public void changedUpdate(DocumentEvent event) { field.repaint(); }
    });
    field.addFocusListener(new FocusAdapter() {
      @Override public void focusGained(FocusEvent event) { field.repaint(); }
      @Override public void focusLost(FocusEvent event) { field.repaint(); }
    });
  }

  public static void apply(String label, Component component) {
    if (component instanceof javax.swing.JScrollPane scroll) {
      apply(label, scroll.getViewport().getView());
      return;
    }
    String normalized = fold(label);
    String hint;
    if (normalized.contains("sku")) { hint =
        "Mã nội bộ duy nhất; giữ nguyên số 0 đầu"; }
    else if (normalized.contains("ma vach")) { hint =
        "Quét mã trên bao bì; chưa xác minh thì để trống"; }
    else if (normalized.contains("model")) { hint = "Model đúng theo nhà sản xuất"; }
    else if (normalized.contains("dien thoai")) { hint =
        "Số được cung cấp hợp lệ; không rõ thì để trống"; }
    else if (normalized.contains("gia") || normalized.contains("tien")) { hint =
        "Số tiền VND không âm; không nhập dấu phân cách hàng nghìn"; }
    else if (normalized.contains("ngay")) { hint = "Nhập ngày theo dd/MM/yyyy"; }
    else if (normalized.contains("nguon") || normalized.contains("https")) { hint =
        "HTTPS của nguồn chính hãng / có thẩm quyền; không kèm bí mật"; }
    else if (normalized.contains("ten")) { hint =
        "Nhập tên chính thức / thông tin được cung cấp"; }
    else if (normalized.contains("chung tu")) { hint =
        "Số chứng từ hoặc hồ sơ thực tế để đối soát"; }
    else { hint = label.replace("*", "").trim(); }
    if (component instanceof JTextComponent text) { install(text, hint); }
    else if (component instanceof JComboBox<?> combo) { autocomplete(combo); }
    if (component instanceof JComponent control) { control.getAccessibleContext().setAccessibleName(label); }
  }

  public static void autocomplete(JComboBox<?> combo) {
    combo.setEditable(false);
    javax.swing.DefaultListCellRenderer renderer = new javax.swing.DefaultListCellRenderer();
    renderer.putClientProperty("html.disable", Boolean.TRUE);
    combo.setRenderer(renderer);
    combo.setToolTipText("Gõ vài chữ để chọn trong danh sách. Không tự tạo giá trị mới.");
    combo.setKeySelectionManager(new JComboBox.KeySelectionManager() {
      private long last;
      private String prefix = "";
      @Override public int selectionForKey(char key, javax.swing.ComboBoxModel<?> model) {
        long now = System.nanoTime();
        if (now - last > 1_000_000_000L) { prefix = ""; }
        last = now;
        prefix += fold(String.valueOf(key));
        for (int i = 0; i < model.getSize(); i++) {
          if (fold(String.valueOf(model.getElementAt(i))).startsWith(prefix)) { return i; }
        }
        return -1;
      }
    });
  }

  private static String fold(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
        .replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
  }

  @SuppressWarnings("serial")
  private static final class PromptBorder extends AbstractBorder {
    private final Border original;
    private final JTextComponent field;
    private final String hint;
    private PromptBorder(Border original, JTextComponent field, String hint) {
      this.original = original; this.field = field; this.hint = hint;
    }
    @Override public Insets getBorderInsets(Component c) {
      return original == null ? new Insets(5, 7, 5, 7) : original.getBorderInsets(c);
    }
    @Override public Insets getBorderInsets(Component c, Insets insets) {
      Insets value = getBorderInsets(c);
      insets.set(value.top, value.left, value.bottom, value.right);
      return insets;
    }
    @Override public void paintBorder(Component c, Graphics graphics, int x, int y, int width, int height) {
      if (original != null) { original.paintBorder(c, graphics, x, y, width, height); }
      if (field.getDocument().getLength() != 0 || field.hasFocus()) { return; }
      Graphics g = graphics.create();
      try {
        Insets insets = getBorderInsets(c);
        g.clipRect(x + insets.left, y + insets.top, Math.max(0, width - insets.left - insets.right),
            Math.max(0, height - insets.top - insets.bottom));
        g.setColor(Ui.MUTED);
        g.setFont(field.getFont().deriveFont(Font.ITALIC));
        g.drawString(hint, x + insets.left, y + insets.top + g.getFontMetrics().getAscent());
      } finally { g.dispose(); }
    }
  }
}
