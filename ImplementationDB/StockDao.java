package ImplementationDB;
import Code.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import static ImplementationDB.Jdbc.*;
public final class StockDao {
    public void add(Connection c,long product,String location,int qty)throws SQLException {
        if(qty<=0)throw new IllegalArgumentException("Số lượng cộng kho phải lớn hơn 0.");
        if(update(c,"UPDATE dm.Stock SET Quantity=Quantity+? WHERE ProductId=? AND Location=? AND Quantity<=2147483647-?",qty,product,location,qty)!=1)throw new IllegalArgumentException("Không thể tăng tồn kho: thiếu dòng tồn hoặc vượt giới hạn số lượng.");
    }
    public void subtract(Connection c,long product,String location,int qty)throws SQLException {
        if(qty<=0)throw new IllegalArgumentException("Số lượng trừ kho phải lớn hơn 0.");
        if(update(c,"UPDATE dm.Stock SET Quantity=Quantity-? WHERE ProductId=? AND Location=? AND Quantity>=?",qty,product,location,qty)!=1)throw new IllegalArgumentException("Không đủ tồn tại "+location+". Dữ liệu có thể vừa được cập nhật bởi nhân viên khác.");
    }
    public long movement(Connection c,UUID request,long product,String location,int delta,String kind,String reference,long employee,Long invoice,Long vendor,BigDecimal cost)throws SQLException {
        return insert(c,"INSERT dm.StockMovements(RequestId,ProductId,Location,Delta,Kind,Reference,EmployeeId,InvoiceId,VendorId,UnitCost) OUTPUT INSERTED.Id VALUES(?,?,?,?,?,?,?,?,?,?,?)",request,product,location,delta,kind,reference,employee,invoice,vendor,cost);
    }
    public long sellUnit(Connection c,long product,String serial)throws SQLException {
        try(PreparedStatement s=statement(c,"UPDATE dm.ProductUnits SET Status='SOLD' OUTPUT INSERTED.Id WHERE ProductId=? AND Serial=? AND Status='IN_STOCK' AND Location='QUAY'",product,serial);ResultSet r=s.executeQuery()){
            if(!r.next())throw new IllegalArgumentException("Serial/IMEI không còn ở quầy hoặc đã bán: "+serial);return r.getLong(1);
        }
    }
    public long transferUnit(Connection c,long product,String serial,String from,String to)throws SQLException {
        try(PreparedStatement s=statement(c,"UPDATE dm.ProductUnits SET Location=? OUTPUT INSERTED.Id WHERE ProductId=? AND Serial=? AND Status='IN_STOCK' AND Location=?",to,product,serial,from);ResultSet r=s.executeQuery()){
            if(!r.next())throw new IllegalArgumentException("Serial/IMEI không nằm tại nguồn chuyển: "+serial);return r.getLong(1);
        }
    }
    public void attachUnit(Connection c,long movement,long unit)throws SQLException {update(c,"INSERT dm.StockMovementUnits(MovementId,UnitId) VALUES(?,?)",movement,unit);}
    public static void checkSerials(Item item,int qty,List<String> serials) {
        if(item.trackSerial()?serials.size()!=qty:!serials.isEmpty())throw new IllegalArgumentException("Thiết bị quản lý Serial cần đủ một mã cho mỗi chiếc; sản phẩm thường không nhập Serial.");
        if(new HashSet<>(serials).size()!=serials.size())throw new IllegalArgumentException("Serial/IMEI bị trùng trong yêu cầu.");
    }
}
