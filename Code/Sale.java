package Code;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
/** Immutable persisted invoice, reconstructed from SQL Server for display/reprint. */
public record Sale(long id,String code,Instant soldAt,String cashier,String customer,String phone,
                   String payment,BigDecimal subtotal,BigDecimal discount,BigDecimal total,List<Line> items) {
    public Sale {items=List.copyOf(items);}
    public record Line(String sku,String name,int quantity,BigDecimal price,int warrantyMonths,String serials) {}
    public String generateReceipt(String storeName,String address,String storePhone) {
        StringBuilder b=new StringBuilder(storeName).append('\n').append(address).append('\n').append(storePhone).append("\n\nHÓA ĐƠN BÁN HÀNG: ").append(code).append('\n');
        b.append("Ngày: ").append(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(Validation.ZONE).format(soldAt)).append("\nNhân viên: ").append(cashier).append("\nKhách hàng: ").append(customer).append("  ").append(phone).append("\n\n");
        for(Line l:items) {
            b.append(l.sku()).append(" — ").append(l.name()).append('\n').append("  ").append(l.quantity()).append(" × ").append(Money.format(l.price())).append(" = ").append(Money.format(l.price().multiply(BigDecimal.valueOf(l.quantity())))).append('\n');
            if(!l.serials().isBlank()) b.append("  Serial/IMEI: ").append(l.serials()).append('\n');
            b.append("  Bảo hành: ").append(l.warrantyMonths()).append(" tháng\n");
        }
        b.append("\nTạm tính: ").append(Money.format(subtotal)).append("\nGiảm giá: ").append(Money.format(discount)).append("\nTỔNG TIỀN: ").append(Money.format(total)).append("\nThanh toán: ").append("CASH".equals(payment)?"Tiền mặt":"Chuyển khoản");
        return b.append("\n\nCảm ơn quý khách!\nChứng từ nội bộ, không thay thế hóa đơn điện tử thuế.\n").toString();
    }
}
