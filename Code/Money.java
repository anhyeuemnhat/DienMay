package Code;
import java.math.*;
import java.text.NumberFormat;
import java.util.Locale;
public final class Money {
    public static final BigDecimal ZERO=new BigDecimal("0.00");
    private static final BigDecimal MAX=new BigDecimal("99999999999999999.99");
    private Money() {}
    public static BigDecimal of(String value) {
        try { return exact(new BigDecimal(value.trim())); } catch(NumberFormatException|ArithmeticException e) { throw new IllegalArgumentException("Số tiền không hợp lệ; nhập số không dấu phân cách, tối đa 2 chữ số thập phân."); }
    }
    public static BigDecimal exact(BigDecimal value) {
        if(value==null || value.signum()<0 || value.compareTo(MAX)>0) throw new IllegalArgumentException("Số tiền phải không âm và trong giới hạn DECIMAL(19,2).");
        return value.setScale(2,RoundingMode.UNNECESSARY);
    }
    public static BigDecimal discount(BigDecimal subtotal,BigDecimal percent) {
        if(percent.signum()<0 || percent.compareTo(new BigDecimal("100"))>0) throw new IllegalArgumentException("Giảm giá phải từ 0 đến 100%.");
        return exact(subtotal.multiply(percent).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP));
    }
    public static BigDecimal total(BigDecimal subtotal,BigDecimal discount) {
        subtotal=exact(subtotal); discount=exact(discount);
        if(discount.compareTo(subtotal)>0) throw new IllegalArgumentException("Giảm giá không được vượt tổng tiền.");
        return subtotal.subtract(discount);
    }
    public static String format(BigDecimal value) {
        NumberFormat f=NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")); f.setMaximumFractionDigits(2); f.setMinimumFractionDigits(value.stripTrailingZeros().scale()>0?2:0);
        return f.format(value)+" ₫";
    }
}
