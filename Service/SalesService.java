package Service;
import Code.*;
import ImplementationDB.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import static ImplementationDB.Jdbc.*;
public final class SalesService {
    @FunctionalInterface public interface FailurePoint {void hit(String phase)throws SQLException;}
    public record Quote(BigDecimal subtotal,BigDecimal discount,BigDecimal total){}
    private final SQL db;private final AuthService auth;private final FailurePoint failures;
    private final ProductDao products=new ProductDao();private final StockDao stock=new StockDao();private final InvoiceDao invoices=new InvoiceDao();
    public SalesService(SQL db,AuthService auth){this(db,auth,phase->{});}
    /** Injection is used by integration tests only, never selected via a production config flag. */
    public SalesService(SQL db,AuthService auth,FailurePoint failures){this.db=db;this.auth=auth;this.failures=failures;}
    public Quote quote(AuthService.Session session,List<ItemQuantity> lines,String code,BigDecimal manual)throws SQLException {
        if(lines.isEmpty())throw new IllegalArgumentException("Giỏ hàng rỗng.");
        return db.read(c->{auth.require(c,session,Role.MANAGER,Role.CASHIER);BigDecimal sub=SaleRequest.subtotal(lines);BigDecimal discount=PromotionService.discount(c,Validation.optional(code,50,"Mã khuyến mãi").toUpperCase(Locale.ROOT),Money.exact(manual),sub,now(c),false);return new Quote(sub,discount,Money.total(sub,discount));});
    }
    public Sale checkout(AuthService.Session session,SaleRequest request)throws SQLException {
        return db.transaction(c->{
            auth.require(c,session,Role.MANAGER,Role.CASHIER);
            lock(c,"dm:sale:"+request.requestId());
            Long existing=findRequest(c,session,request);if(existing!=null)return invoices.load(c,existing);
            List<ItemQuantity> lines=request.lines().stream().sorted(Comparator.comparingLong(ItemQuantity::productId)).toList();
            Map<Long,Item> current=new LinkedHashMap<>();
            for(ItemQuantity l:lines){
                Item item=products.get(c,l.productId(),true);
                if(!item.enabled())throw new IllegalArgumentException("Sản phẩm đã ngừng kinh doanh: "+item.sku());
                if(item.sellingPrice().compareTo(l.expectedPrice())!=0)throw new IllegalArgumentException("Giá đã thay đổi: "+item.sku()+". Tải lại giỏ hàng và xác nhận lại.");
                StockDao.checkSerials(item,l.quantity(),l.serials());current.put(item.id(),item);
            }
            Instant timestamp=now(c);LocalDate saleDate=timestamp.atZone(Validation.ZONE).toLocalDate();
            BigDecimal subtotal=SaleRequest.subtotal(lines),discount=PromotionService.discount(c,request.promotionCode(),request.manualDiscount(),subtotal,timestamp,true);
            if(discount.compareTo(request.expectedDiscount())!=0)throw new IllegalArgumentException("Khuyến mãi đã thay đổi. Cần xác nhận lại tổng tiền.");
            String customer="Khách lẻ",phone="";
            if(request.customerId()!=null){try(PreparedStatement q=statement(c,"SELECT FullName,Phone FROM dm.Customers WITH (HOLDLOCK) WHERE Id=?",request.customerId());ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Không tìm thấy khách hàng.");customer=r.getString(1);phone=r.getString(2);}}
            long invoice=insert(c,"INSERT dm.Invoices(RequestId,RequestHash,SoldAt,EmployeeId,CustomerId,CustomerName,CustomerPhone,Subtotal,Discount,Total,PaymentMethod,PromotionCode) OUTPUT INSERTED.Id VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",request.requestId(),request.fingerprint(),timestamp,session.employeeId(),request.customerId(),customer,phone,subtotal,discount,Money.total(subtotal,discount),request.payment(),request.promotionCode().isBlank()?null:request.promotionCode());
            failures.hit("AFTER_INVOICE");
            for(ItemQuantity l:lines){
                Item item=current.get(l.productId());
                long detail=insert(c,"INSERT dm.InvoiceDetails(InvoiceId,ProductId,SKU,ProductName,UnitPrice,Quantity,WarrantyMonths) OUTPUT INSERTED.Id VALUES(?,?,?,?,?,?,?)",invoice,item.id(),item.sku(),item.name(),item.sellingPrice(),l.quantity(),item.warrantyMonths());
                stock.subtract(c,item.id(),"QUAY",l.quantity());
                long movement=stock.movement(c,request.requestId(),item.id(),"QUAY",-l.quantity(),"SALE","HD-"+invoice,session.employeeId(),invoice,null,null);
                failures.hit("AFTER_STOCK");
                if(item.trackSerial()){
                    for(String serial:l.serials().stream().sorted().toList()){
                        long unit=stock.sellUnit(c,item.id(),serial);
                        update(c,"INSERT dm.InvoiceUnits(DetailId,UnitId) VALUES(?,?)",detail,unit);stock.attachUnit(c,movement,unit);
                        createWarranty(c,detail,unit,saleDate,item.warrantyMonths(),1);
                    }
                }else createWarranty(c,detail,null,saleDate,item.warrantyMonths(),l.quantity());
            }
            failures.hit("BEFORE_COMMIT");
            return invoices.load(c,invoice);
        });
    }
    private void createWarranty(Connection c,long detail,Long unit,LocalDate start,int months,int qty)throws SQLException {
        if(months==0)return;
        update(c,"INSERT dm.Warranties(DetailId,UnitId,StartDate,EndDate,WarrantyMonths,CoveredQuantity) VALUES(?,?,?,?,?,?)",detail,unit,start,WarrantyRules.end(start,months),months,qty);
    }
    private Long findRequest(Connection c,AuthService.Session session,SaleRequest r)throws SQLException {
        try(PreparedStatement s=statement(c,"SELECT Id,RequestHash,EmployeeId FROM dm.Invoices WHERE RequestId=?",r.requestId());ResultSet found=s.executeQuery()){
            if(!found.next())return null;
            if(found.getLong(3)!=session.employeeId()||!found.getString(2).equals(r.fingerprint()))throw new SecurityException("Mã thanh toán đã tồn tại với nhân viên hoặc nội dung khác.");
            return found.getLong(1);
        }
    }
    /** Takes the same lock as checkout; absence is definitive after any in-flight request finishes. */
    public Sale resolve(AuthService.Session session,SaleRequest request)throws SQLException {
        return db.transaction(c->{auth.require(c,session,Role.MANAGER,Role.CASHIER);lock(c,"dm:sale:"+request.requestId());Long id=findRequest(c,session,request);return id==null?null:invoices.load(c,id);});
    }
    public Sale load(AuthService.Session s,long id)throws SQLException{return db.read(c->{auth.require(c,s,Role.MANAGER,Role.CASHIER);return invoices.load(c,id);});}
    public List<LinkedHashMap<String,Object>> search(AuthService.Session s,String text,LocalDate from,LocalDate to)throws SQLException {
        Validation.dates(from,to);String pattern=ProductDao.like(Validation.optional(text,180,"Từ khóa"));
        return db.read(c->{auth.require(c,s,Role.MANAGER,Role.CASHIER);return rows(c,"SELECT TOP (1000) i.Id AS [Mã],i.Code AS [Hóa đơn],i.SoldAt AS [Ngày bán UTC],i.CustomerName AS [Khách hàng],i.CustomerPhone AS [Điện thoại],e.FullName AS [Nhân viên],i.Total AS [Tổng tiền],i.PaymentMethod AS [Thanh toán] FROM dm.Invoices i JOIN dm.Employees e ON e.Id=i.EmployeeId WHERE i.SoldAt>=? AND i.SoldAt<? AND (i.Code LIKE ? ESCAPE '~' OR i.CustomerName LIKE ? ESCAPE '~' OR i.CustomerPhone LIKE ? ESCAPE '~' OR e.FullName LIKE ? ESCAPE '~') ORDER BY i.Id DESC",Validation.start(from),Validation.start(to.plusDays(1)),pattern,pattern,pattern,pattern);});
    }
}
