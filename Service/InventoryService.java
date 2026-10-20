package Service;
import Code.*;
import ImplementationDB.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import static ImplementationDB.Jdbc.*;
public final class InventoryService {
    public record Request(UUID id,long productId,int quantity,List<String> serials,String reference,Long vendorId,BigDecimal unitCost,String from,String to) {
        public Request {
            Objects.requireNonNull(id);
            if(productId<=0||quantity<=0||quantity>100000)throw new IllegalArgumentException("Sản phẩm hoặc số lượng (1–100.000) không hợp lệ.");
            serials=serials.stream().map(Validation::serial).sorted().toList();
            if(new HashSet<>(serials).size()!=serials.size())throw new IllegalArgumentException("Serial bị trùng.");
            reference=Validation.required(reference,200,"Số chứng từ/lý do");unitCost=Money.exact(unitCost);
            if(!Set.of("KHO","QUAY").contains(to))throw new IllegalArgumentException("Vị trí đích không hợp lệ.");
            if(from!=null&&(!Set.of("KHO","QUAY").contains(from)||from.equals(to)))throw new IllegalArgumentException("Nguồn và đích phải khác nhau.");
        }
        public String fingerprint(){StringBuilder b=new StringBuilder();for(String value:List.of(""+productId,""+quantity,reference,""+vendorId,unitCost.toPlainString(),""+from,to))SaleRequest.token(b,value);serials.forEach(s->SaleRequest.token(b,s));return SaleRequest.digest(b.toString());}
    }
    private final SQL db;private final AuthService auth;private final ProductDao products=new ProductDao();private final StockDao stock=new StockDao();
    public InventoryService(SQL db,AuthService auth){this.db=db;this.auth=auth;}
    public void receive(AuthService.Session s,Request r)throws SQLException {
        if(r.from()!=null||!r.to().equals("KHO")||r.vendorId()==null)throw new IllegalArgumentException("Nhập hàng cần nhà cung cấp và vị trí KHO.");
        execute(s,r,true);
    }
    public void transfer(AuthService.Session s,Request r)throws SQLException {
        if(r.from()==null)throw new IllegalArgumentException("Cần vị trí nguồn chuyển.");execute(s,r,false);
    }
    private void execute(AuthService.Session session,Request r,boolean receiving)throws SQLException {
        db.transaction(c->{
            auth.require(c,session,Role.MANAGER,Role.WAREHOUSE);lock(c,"dm:inventory:"+r.id());
            try(PreparedStatement q=statement(c,"SELECT RequestHash,EmployeeId FROM dm.InventoryRequests WHERE RequestId=?",r.id());ResultSet found=q.executeQuery()){
                if(found.next()){
                    if(!found.getString(1).equals(r.fingerprint())||found.getLong(2)!=session.employeeId())throw new IllegalArgumentException("Mã yêu cầu đã dùng cho nội dung khác.");return null;
                }
            }
            Item item=products.get(c,r.productId(),true);StockDao.checkSerials(item,r.quantity(),r.serials());
            if(receiving){
                if(!item.enabled())throw new IllegalArgumentException("Sản phẩm đã ngừng kinh doanh.");
                try(PreparedStatement q=statement(c,"SELECT Id FROM dm.Vendors WITH (HOLDLOCK) WHERE Id=? AND Enabled=1",r.vendorId());ResultSet found=q.executeQuery()){if(!found.next())throw new IllegalArgumentException("Nhà cung cấp không hoạt động.");}
                stock.add(c,item.id(),"KHO",r.quantity());
                long m=stock.movement(c,r.id(),item.id(),"KHO",r.quantity(),"RECEIVE",r.reference(),session.employeeId(),null,r.vendorId(),r.unitCost());
                for(String serial:r.serials()) {
                    long unit=insert(c,"INSERT dm.ProductUnits(ProductId,Serial,Location) OUTPUT INSERTED.Id VALUES(?,?,'KHO')",item.id(),serial);stock.attachUnit(c,m,unit);
                }
                update(c,"UPDATE dm.Products SET CostPrice=?,Revision=Revision+1 WHERE Id=?",r.unitCost(),item.id());
            }else{
                stock.subtract(c,item.id(),r.from(),r.quantity());stock.add(c,item.id(),r.to(),r.quantity());
                long out=stock.movement(c,r.id(),item.id(),r.from(),-r.quantity(),"TRANSFER",r.reference(),session.employeeId(),null,null,null);
                long in=stock.movement(c,r.id(),item.id(),r.to(),r.quantity(),"TRANSFER",r.reference(),session.employeeId(),null,null,null);
                for(String serial:r.serials()){long unit=stock.transferUnit(c,item.id(),serial,r.from(),r.to());stock.attachUnit(c,out,unit);stock.attachUnit(c,in,unit);}
            }
            update(c,"INSERT dm.InventoryRequests(RequestId,RequestHash,EmployeeId) VALUES(?,?,?)",r.id(),r.fingerprint(),session.employeeId());
            return null;
        });
    }
    public boolean resolve(AuthService.Session session,Request r)throws SQLException {
        return db.transaction(c->{auth.require(c,session,Role.MANAGER,Role.WAREHOUSE);lock(c,"dm:inventory:"+r.id());try(PreparedStatement s=statement(c,"SELECT RequestHash,EmployeeId FROM dm.InventoryRequests WHERE RequestId=?",r.id());ResultSet rs=s.executeQuery()){if(!rs.next())return false;if(!rs.getString(1).equals(r.fingerprint())||rs.getLong(2)!=session.employeeId())throw new SecurityException("Yêu cầu không thuộc phiên này.");return true;}});
    }
    public List<LinkedHashMap<String,Object>> history(AuthService.Session s,String sku)throws SQLException {
        return db.read(c->{auth.require(c,s,Role.MANAGER,Role.WAREHOUSE);return rows(c,"SELECT TOP (1000) m.Id AS [Mã],m.CreatedAt AS [Thời gian UTC],p.SKU,m.Location AS [Vị trí],m.Delta AS [Biến động],m.Kind AS [Loại],m.Reference AS [Chứng từ],e.FullName AS [Nhân viên],m.UnitCost AS [Giá nhập],m.RequestId AS [Mã yêu cầu] FROM dm.StockMovements m JOIN dm.Products p ON p.Id=m.ProductId JOIN dm.Employees e ON e.Id=m.EmployeeId WHERE p.SKU LIKE ? ESCAPE '~' ORDER BY m.Id DESC",ProductDao.like(sku));});
    }
    public List<LinkedHashMap<String,Object>> unitHistory(AuthService.Session s,String serial)throws SQLException {
        return db.read(c->{auth.require(c,s,Role.MANAGER,Role.WAREHOUSE);return rows(c,"SELECT u.Serial,p.SKU,u.Status AS [Hiện trạng],m.CreatedAt AS [Thời gian UTC],m.Kind AS [Loại],m.Location AS [Vị trí],m.Delta AS [Biến động chứng từ],m.Reference AS [Chứng từ] FROM dm.ProductUnits u JOIN dm.Products p ON p.Id=u.ProductId LEFT JOIN dm.StockMovementUnits mu ON mu.UnitId=u.Id LEFT JOIN dm.StockMovements m ON m.Id=mu.MovementId WHERE u.Serial=? ORDER BY m.Id",Validation.serial(serial));});
    }
}
