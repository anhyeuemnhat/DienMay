package Code;
import java.math.BigDecimal;
/** A model/SKU, not an individual serialized device. Replaces legacy float prices. */
public record Item(long id,String sku,String barcode,String name,long categoryId,long brandId,
                   String category,String brand,String model,String description,BigDecimal costPrice,
                   BigDecimal sellingPrice,int warrantyMonths,boolean trackSerial,boolean enabled,
                   int reorderLevel,int warehouseQuantity,int counterQuantity,int revision) {
    public String toString(){return sku+" — "+name;}
}
