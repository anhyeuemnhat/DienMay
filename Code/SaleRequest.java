package Code;
import java.math.BigDecimal;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
/** Stable request identity and payload survive uncertain network/commit outcomes. */
public record SaleRequest(UUID requestId,Long customerId,String payment,BigDecimal manualDiscount,
                          String promotionCode,BigDecimal expectedDiscount,List<ItemQuantity> lines) {
    public SaleRequest {
        Objects.requireNonNull(requestId); lines=List.copyOf(lines);
        if(!Set.of("CASH","TRANSFER").contains(payment)) throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ.");
        if(customerId!=null&&customerId<=0) throw new IllegalArgumentException("Mã khách hàng không hợp lệ.");
        manualDiscount=Money.exact(manualDiscount); expectedDiscount=Money.exact(expectedDiscount);
        promotionCode=Validation.optional(promotionCode,50,"Mã khuyến mãi").toUpperCase(Locale.ROOT);
        if(!promotionCode.isEmpty()&&manualDiscount.signum()!=0) throw new IllegalArgumentException("Chỉ chọn giảm giá trực tiếp hoặc mã khuyến mãi, không cộng cả hai.");
        if(lines.isEmpty()||lines.size()>100) throw new IllegalArgumentException("Giỏ phải có từ 1 đến 100 dòng.");
        if(lines.stream().map(ItemQuantity::productId).distinct().count()!=lines.size()) throw new IllegalArgumentException("Trùng dòng sản phẩm.");
        Money.total(subtotal(lines),expectedDiscount);
    }
    public static BigDecimal subtotal(List<ItemQuantity> lines){return Money.exact(lines.stream().map(ItemQuantity::gross).reduce(Money.ZERO,BigDecimal::add));}
    public String fingerprint(){
        StringBuilder b=new StringBuilder(); token(b,String.valueOf(customerId));token(b,payment);token(b,manualDiscount.toPlainString());token(b,promotionCode);token(b,expectedDiscount.toPlainString());
        lines.stream().sorted(Comparator.comparingLong(ItemQuantity::productId)).forEach(l->{token(b,""+l.productId());token(b,""+l.quantity());token(b,l.expectedPrice().toPlainString()); l.serials().stream().sorted().forEach(s->token(b,s)); token(b,"END");});
        return digest(b.toString());
    }
    public static void token(StringBuilder b,String s){b.append(s.length()).append(':').append(s);}
    public static String digest(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
