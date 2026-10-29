package vn.dienmay.common.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Editable draft with defensive snapshots; a draft is never a committed invoice. */
public final class Cart {
  private final LinkedHashMap<Long, ItemQuantity> lines = new LinkedHashMap<>();

  public void add(Item product, int quantity, List<String> serials) {
    if (!product.enabled()) {
      throw new IllegalArgumentException("San pham da ngung kinh doanh.");
    }
    if (quantity <= 0) {
      throw new IllegalArgumentException("So luong phai lon hon 0.");
    }
    List<String> normalized = serials.stream().map(Validation::serial).toList();
    if (product.trackSerial() ? normalized.size() != quantity : !normalized.isEmpty()) {
      throw new IllegalArgumentException("Hang theo Serial can mot ma cho moi chiec.");
    }
    ItemQuantity old = lines.get(product.id());
    int next = Math.addExact(old == null ? 0 : old.quantity(), quantity);
    if (next > product.counterQuantity()) {
      throw new IllegalArgumentException("Khong du ton tai quay. Can chuyen hang tu kho.");
    }
    if (old != null && old.expectedPrice().compareTo(product.sellingPrice()) != 0) {
      throw new IllegalArgumentException("Gia da thay doi. Xoa dong va them lai de xac nhan.");
    }
    List<String> all = new ArrayList<>();
    if (old != null) {
      all.addAll(old.serials());
    }
    all.addAll(normalized);
    ItemQuantity line = new ItemQuantity(
        product.id(), product.sku(), product.name(), product.sellingPrice(), next, all);
    if (lines.size() >= 100 && old == null) {
      throw new IllegalArgumentException("Toi da 100 dong moi hoa don.");
    }
    lines.put(product.id(), line);
  }

  public List<ItemQuantity> lines() {
    return List.copyOf(lines.values());
  }

  public void remove(long productId) {
    lines.remove(productId);
  }

  public void clear() {
    lines.clear();
  }

  public BigDecimal subtotal() {
    return SaleRequest.subtotal(lines());
  }
}
