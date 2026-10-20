package Code;
import java.math.BigDecimal;
import java.util.*;
/** Only an editable draft lives in RAM; a cart is never a paid sale. */
public final class Cart {
    private final LinkedHashMap<Long,ItemQuantity> lines=new LinkedHashMap<>();
    public void add(Item p,int quantity,List<String> serials) {
        if(!p.enabled()) throw new IllegalArgumentException("Sản phẩm đã ngừng kinh doanh.");
        if(quantity<=0) throw new IllegalArgumentException("Số lượng phải lớn hơn 0.");
        List<String> normalized=serials.stream().map(Code.Validation::serial).toList();
        if(p.trackSerial() ? normalized.size()!=quantity : !normalized.isEmpty()) throw new IllegalArgumentException("Số Serial phải bằng số lượng đối với hàng theo Serial; hàng thường không nhập Serial.");
        ItemQuantity old=lines.get(p.id()); int next=Math.addExact(old==null?0:old.quantity(),quantity);
        if(next>p.counterQuantity()) throw new IllegalArgumentException("Không đủ tồn tại quầy. Cần chuyển hàng từ kho trước.");
        if(old!=null && old.expectedPrice().compareTo(p.sellingPrice())!=0) throw new IllegalArgumentException("Giá đã thay đổi. Xóa dòng và thêm lại để xác nhận giá mới.");
        List<String> all=new ArrayList<>(); if(old!=null) all.addAll(old.serials()); all.addAll(normalized);
        ItemQuantity line=new ItemQuantity(p.id(),p.sku(),p.name(),p.sellingPrice(),next,all);
        if(lines.size()>=100 && old==null) throw new IllegalArgumentException("Tối đa 100 dòng mỗi hóa đơn.");
        lines.put(p.id(),line);
    }
    public List<ItemQuantity> lines(){return List.copyOf(lines.values());}
    public void remove(long productId){lines.remove(productId);}
    public void clear(){lines.clear();}
    public BigDecimal subtotal(){return Money.exact(lines.values().stream().map(ItemQuantity::gross).reduce(Money.ZERO,BigDecimal::add));}
}
