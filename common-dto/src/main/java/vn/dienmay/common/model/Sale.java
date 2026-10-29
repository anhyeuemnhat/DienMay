package vn.dienmay.common.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Immutable invoice reconstructed from persisted data, not from a client draft. */
public record Sale(
    long id, String code, Instant soldAt, String cashier, String customer, String phone,
    String payment, BigDecimal subtotal, BigDecimal discount, BigDecimal total, List<Line> items) {

  public Sale {
    items = List.copyOf(items);
  }

  public record Line(
      String sku, String name, int quantity, BigDecimal price, int warrantyMonths, String serials) {}

  public String generateReceipt(String storeName, String address, String storePhone) {
    StringBuilder b = new StringBuilder(storeName).append('\n').append(address).append('\n')
        .append(storePhone).append("\n\nHOA DON BAN HANG: ").append(code).append('\n');
    b.append("Ngay: ").append(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        .withZone(Validation.ZONE).format(soldAt)).append("\nNhan vien: ").append(cashier)
        .append("\nKhach hang: ").append(customer).append("  ").append(phone).append("\n\n");
    for (Line line : items) {
      b.append(line.sku()).append(" - ").append(line.name()).append('\n').append("  ")
          .append(line.quantity()).append(" x ").append(Money.format(line.price())).append(" = ")
          .append(Money.format(line.price().multiply(BigDecimal.valueOf(line.quantity()))))
          .append('\n');
      if (!line.serials().isBlank()) {
        b.append("  Serial/IMEI: ").append(line.serials()).append('\n');
      }
      b.append("  Bao hanh: ").append(line.warrantyMonths()).append(" thang\n");
    }
    b.append("\nTam tinh: ").append(Money.format(subtotal))
        .append("\nGiam gia: ").append(Money.format(discount))
        .append("\nTONG TIEN: ").append(Money.format(total))
        .append("\nThanh toan: ").append("CASH".equals(payment) ? "Tien mat" : "Chuyen khoan");
    return b.append("\n\nCam on quy khach!\nChung tu noi bo, khong thay the hoa don dien tu thue.\n")
        .toString();
  }
}
