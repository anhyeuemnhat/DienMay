package Code;
import java.math.BigDecimal;
import java.util.*;
public record ItemQuantity(long productId,String sku,String name,BigDecimal expectedPrice,int quantity,List<String> serials) {
    public ItemQuantity {
        if(productId<=0 || quantity<=0 || quantity>100000) throw new IllegalArgumentException("Số lượng phải từ 1 đến 100.000.");
        expectedPrice=Money.exact(expectedPrice); serials=serials.stream().map(Validation::serial).toList();
        if(new HashSet<>(serials).size()!=serials.size()) throw new IllegalArgumentException("Serial/IMEI bị trùng.");
    }
    public BigDecimal gross(){return Money.exact(expectedPrice.multiply(BigDecimal.valueOf(quantity)));}
}
