package Service;
import Code.*;import ImplementationDB.*;
import java.sql.*;import java.time.*;import java.util.*;
import static ImplementationDB.Jdbc.*;
public final class WarrantyService {
    private final SQL db;private final AuthService auth;
    public WarrantyService(SQL db,AuthService auth){this.db=db;this.auth=auth;}
    public List<LinkedHashMap<String,Object>> search(AuthService.Session s,String text)throws SQLException {
        String term=Validation.optional(text,180,"Từ khóa"),pattern=ProductDao.like(term);
        return db.read(c->{auth.require(c,s,Role.MANAGER,Role.CASHIER);LocalDate today=now(c).atZone(Validation.ZONE).toLocalDate();
            return rows(c,"SELECT TOP (1000) w.Id AS [Mã],w.Code AS [Bảo hành],i.Code AS [Hóa đơn],d.SKU,d.ProductName AS [Sản phẩm],COALESCE(u.Serial,N'') AS [Serial/IMEI],i.CustomerName AS [Khách hàng],i.CustomerPhone AS [Điện thoại],w.StartDate AS [Ngày mua],w.EndDate AS [Hết hạn],w.WarrantyMonths AS [Tháng BH],w.CoveredQuantity AS [Số lượng],CASE WHEN w.StartDate<=? AND w.EndDate>=? THEN N'Còn hạn' ELSE N'Hết hạn' END AS [Hiệu lực] FROM dm.Warranties w JOIN dm.InvoiceDetails d ON d.Id=w.DetailId JOIN dm.Invoices i ON i.Id=d.InvoiceId LEFT JOIN dm.ProductUnits u ON u.Id=w.UnitId WHERE w.Code LIKE ? ESCAPE '~' OR i.Code LIKE ? ESCAPE '~' OR u.Serial LIKE ? ESCAPE '~' OR i.CustomerPhone LIKE ? ESCAPE '~' ORDER BY w.Id DESC",today,today,pattern,pattern,pattern,pattern);
        });
    }
    public long receive(AuthService.Session session,long warranty,String fault)throws SQLException {
        String description=Validation.required(fault,1000,"Mô tả lỗi");
        return db.transaction(c->{auth.require(c,session,Role.MANAGER,Role.CASHIER);
            try(PreparedStatement s=statement(c,"SELECT Id FROM dm.Warranties WHERE Id=?",warranty);ResultSet r=s.executeQuery()){if(!r.next())throw new IllegalArgumentException("Không tìm thấy bảo hành.");}
            // Expiry is displayed, not an automatic rejection policy.
            long claim=insert(c,"INSERT dm.WarrantyClaims(WarrantyId,ReceivedBy,Fault) OUTPUT INSERTED.Id VALUES(?,?,?)",warranty,session.employeeId(),description);
            update(c,"INSERT dm.ClaimHistory(ClaimId,ChangedBy,OldStatus,NewStatus,Notes) VALUES(?,?,NULL,'RECEIVED',?)",claim,session.employeeId(),description);return claim;
        });
    }
    public List<LinkedHashMap<String,Object>> claims(AuthService.Session s,Long warranty)throws SQLException {
        return db.read(c->{auth.require(c,s,Role.MANAGER,Role.CASHIER);return rows(c,"SELECT TOP (1000) cl.Id AS [Mã],w.Code AS [Bảo hành],cl.ReceivedAt AS [Tiếp nhận UTC],cl.Fault AS [Mô tả lỗi],e.FullName AS [Nhân viên],cl.Status AS [Trạng thái],cl.Notes AS [Ghi chú],cl.CompletedAt AS [Hoàn tất UTC] FROM dm.WarrantyClaims cl JOIN dm.Warranties w ON w.Id=cl.WarrantyId JOIN dm.Employees e ON e.Id=cl.ReceivedBy WHERE (? IS NULL OR cl.WarrantyId=?) ORDER BY cl.Id DESC",warranty,warranty);});
    }
    public void transition(AuthService.Session session,long claim,String expected,String next,String notes)throws SQLException {
        if(!WarrantyRules.canTransition(expected,next))throw new IllegalArgumentException("Chuyển trạng thái không hợp lệ.");
        String message=Validation.required(notes,1000,"Ghi chú xử lý");
        db.transaction(c->{auth.require(c,session,Role.MANAGER);
            try(PreparedStatement s=statement(c,"SELECT Status FROM dm.WarrantyClaims WITH (UPDLOCK,HOLDLOCK) WHERE Id=?",claim);ResultSet r=s.executeQuery()){
                if(!r.next())throw new IllegalArgumentException("Không tìm thấy phiếu.");if(!r.getString(1).equals(expected))throw new IllegalArgumentException("Phiếu đã đổi trạng thái ở nơi khác. Hãy tải lại.");
            }
            update(c,"UPDATE dm.WarrantyClaims SET Status=?,Notes=?,CompletedAt=? WHERE Id=?",next,message,Set.of("COMPLETED","REJECTED").contains(next)?now(c):null,claim);
            update(c,"INSERT dm.ClaimHistory(ClaimId,ChangedBy,OldStatus,NewStatus,Notes) VALUES(?,?,?,?,?)",claim,session.employeeId(),expected,next,message);return null;
        });
    }
    public List<LinkedHashMap<String,Object>> history(AuthService.Session s,long claim)throws SQLException{return db.read(c->{auth.require(c,s,Role.MANAGER,Role.CASHIER);return rows(c,"SELECT h.Id AS [Mã],h.ChangedAt AS [Thời gian UTC],e.FullName AS [Nhân viên],h.OldStatus AS [Trước],h.NewStatus AS [Sau],h.Notes AS [Ghi chú] FROM dm.ClaimHistory h JOIN dm.Employees e ON e.Id=h.ChangedBy WHERE h.ClaimId=? ORDER BY h.Id",claim);});}
}
