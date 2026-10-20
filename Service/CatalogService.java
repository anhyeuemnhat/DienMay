package Service;
import Code.*;
import ImplementationDB.*;
import java.sql.*;
import java.util.*;
import static ImplementationDB.Jdbc.*;
public final class CatalogService {
    private final SQL db; private final AuthService auth; private final ProductDao dao=new ProductDao();
    public CatalogService(SQL db,AuthService auth){this.db=db;this.auth=auth;}
    public List<Item> search(AuthService.Session session,String text)throws SQLException{return db.read(c->{auth.require(c,session,Role.values());return dao.search(c,Validation.optional(text,180,"Từ khóa"));});}
    public Item find(AuthService.Session session,String code)throws SQLException{return db.read(c->{auth.require(c,session,Role.values());return dao.code(c,Validation.required(code,128,"SKU/mã vạch"));});}
    public Item get(AuthService.Session session,long id)throws SQLException{return db.read(c->{auth.require(c,session,Role.values());return dao.get(c,id,false);});}
    public List<String> serials(AuthService.Session session,long id,String location)throws SQLException{return db.read(c->{auth.require(c,session,Role.values());return dao.availableSerials(c,id,location);});}
    public long save(AuthService.Session session,Item value)throws SQLException {
        String sku=Validation.required(value.sku(),64,"SKU"),barcode=Validation.optional(value.barcode(),128,"Mã vạch"),name=Validation.required(value.name(),180,"Tên sản phẩm");
        String model=Validation.optional(value.model(),100,"Model"),desc=Validation.optional(value.description(),1000,"Mô tả");
        if(value.warrantyMonths()<0||value.warrantyMonths()>120||value.reorderLevel()<0)throw new IllegalArgumentException("Tháng bảo hành (0–120) hoặc mức tồn tối thiểu không hợp lệ.");
        Money.exact(value.costPrice());Money.exact(value.sellingPrice());
        return db.transaction(c->{
            auth.require(c,session,Role.MANAGER);long id=value.id();
            if(id==0) {
                id=insert(c,"INSERT dm.Products(SKU,Barcode,Name,CategoryId,BrandId,Model,Description,CostPrice,SellingPrice,WarrantyMonths,TrackSerial,Enabled,ReorderLevel) OUTPUT INSERTED.Id VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",sku,barcode.isBlank()?null:barcode,name,value.categoryId(),value.brandId(),model,desc,value.costPrice(),value.sellingPrice(),value.warrantyMonths(),value.trackSerial(),value.enabled(),value.reorderLevel());
                update(c,"INSERT dm.Stock(ProductId,Location,Quantity) VALUES(?,'KHO',0),(?,'QUAY',0)",id,id);
            } else {
                Item old=dao.get(c,id,true);
                if(old.revision()!=value.revision())throw new IllegalArgumentException("Sản phẩm vừa được sửa ở nơi khác. Tải lại trước khi lưu.");
                if(old.trackSerial()!=value.trackSerial()) {
                    try(PreparedStatement s=statement(c,"SELECT TOP (1) 1 FROM dm.StockMovements WHERE ProductId=?",id);ResultSet r=s.executeQuery()) {
                        if(r.next())throw new IllegalArgumentException("Không đổi cách quản lý Serial sau khi đã phát sinh kho. Hãy tạo SKU mới.");
                    }
                }
                update(c,"UPDATE dm.Products SET SKU=?,Barcode=?,Name=?,CategoryId=?,BrandId=?,Model=?,Description=?,CostPrice=?,SellingPrice=?,WarrantyMonths=?,TrackSerial=?,Enabled=?,ReorderLevel=?,Revision=Revision+1 WHERE Id=?",sku,barcode.isBlank()?null:barcode,name,value.categoryId(),value.brandId(),model,desc,value.costPrice(),value.sellingPrice(),value.warrantyMonths(),value.trackSerial(),value.enabled(),value.reorderLevel(),id);
                update(c,"DELETE FROM dm.ProductCodes WHERE ProductId=?",id); // index entries only, never transaction history
            }
            update(c,"INSERT dm.ProductCodes(Code,ProductId) VALUES(?,?)",sku,id);
            if(!barcode.isBlank()&&!barcode.equalsIgnoreCase(sku))update(c,"INSERT dm.ProductCodes(Code,ProductId) VALUES(?,?)",barcode,id);
            return id;
        });
    }
    public record Lookup(long id,String name){public String toString(){return name;}}
    private static String table(String type){return switch(type){case "category"->"dm.Categories";case "brand"->"dm.Brands";case "vendor"->"dm.Vendors";default->throw new IllegalArgumentException("Loại danh mục không hợp lệ.");};}
    public List<Lookup> lookups(AuthService.Session session,String type)throws SQLException{
        return db.read(c->{auth.require(c,session,type.equals("vendor")?new Role[]{Role.MANAGER,Role.WAREHOUSE}:Role.values());List<Lookup> values=new ArrayList<>();try(PreparedStatement s=statement(c,"SELECT Id,Name FROM "+table(type)+" ORDER BY Name");ResultSet r=s.executeQuery()){while(r.next())values.add(new Lookup(r.getLong(1),r.getString(2)));}return values;});
    }
    public long saveLookup(AuthService.Session session,String type,Long id,String name)throws SQLException{
        String valid=Validation.required(name,100,"Tên");if(type.equals("vendor"))throw new IllegalArgumentException("Dùng chức năng nhà cung cấp để lưu.");
        return db.transaction(c->{auth.require(c,session,Role.MANAGER);if(id==null)return insert(c,"INSERT "+table(type)+"(Name) OUTPUT INSERTED.Id VALUES(?)",valid);if(update(c,"UPDATE "+table(type)+" SET Name=? WHERE Id=?",valid,id)!=1)throw new IllegalArgumentException("Không tìm thấy danh mục.");return id;});
    }
}
