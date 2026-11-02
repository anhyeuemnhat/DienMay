package vn.dienmay.client.ui;

import java.awt.Component;
import java.math.BigDecimal;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import vn.dienmay.common.model.Item;
import vn.dienmay.common.model.Money;
import vn.dienmay.common.model.PricePolicy;

/** Explicit action labels, safe default is Return; closing the window never accepts. */
public final class PriceConfirmations {
  private PriceConfirmations() {}
  public static boolean confirmProduct(Component owner, Item item) {
    return confirm(owner, "Xác nhận giá", item.sku() + " / " + item.name(),
        item.costPrice(), item.sellingPrice(), "Xác nhận lưu");
  }
  public static boolean confirmReceipt(Component owner, Item item, BigDecimal unitCost) {
    return confirm(owner, "Cảnh báo giá vốn nhập hàng", item.sku() + " / " + item.name(),
        unitCost, item.sellingPrice(), "Xác nhận nhập hàng");
  }
  private static boolean confirm(Component owner, String title, String product, BigDecimal cost,
      BigDecimal selling, String proceed) {
    String back = "Quay lại chỉnh sửa";
    String message = product + "\n\nGiá bán đang thấp hơn giá nhập. Sản phẩm có nguy cơ bán lỗ. "
        + "Vui lòng kiểm tra trước khi lưu.\n\nGiá nhập: " + Money.format(cost)
        + "\nGiá bán: " + Money.format(selling) + "\nChênh lệch (bán - nhập): "
        + Money.format(PricePolicy.difference(cost, selling)) + "\nTỷ lệ so với giá nhập: "
        + PricePolicy.percentage(cost, selling).toPlainString() + "%";
    JTextArea details = new JTextArea(message, 12, 46);
    details.setEditable(false);
    details.setLineWrap(true);
    details.setWrapStyleWord(true);
    JOptionPane pane = new JOptionPane(Ui.scroll(details), JOptionPane.WARNING_MESSAGE,
        JOptionPane.DEFAULT_OPTION, null, new Object[] {back, proceed}, back);
    JDialog dialog = pane.createDialog(owner, title);
    dialog.setName("priceConfirmationDialog");
    Ui.decorateDialog(dialog, title);
    Ui.fitDialog(dialog);
    dialog.setVisible(true);
    boolean accepted = proceed.equals(pane.getValue());
    dialog.dispose();
    return accepted;
  }
}
