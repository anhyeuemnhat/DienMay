package ImplementationDB;
import Code.*;
import java.sql.*;
import java.util.*;
import static ImplementationDB.Jdbc.*;
public final class InvoiceDao {
    public Sale load(Connection c,long id)throws SQLException {
        Sale header;
        try(PreparedStatement s=statement(c,"SELECT i.Id,i.Code,i.SoldAt,e.FullName,i.CustomerName,i.CustomerPhone,i.PaymentMethod,i.Subtotal,i.Discount,i.Total FROM dm.Invoices i JOIN dm.Employees e ON e.Id=i.EmployeeId WHERE i.Id=?",id);
            ResultSet r=s.executeQuery()) {
            if(!r.next())throw new IllegalArgumentException("Không tìm thấy hóa đơn.");
            header=new Sale(r.getLong(1),r.getString(2),instant(r,3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getBigDecimal(8),r.getBigDecimal(9),r.getBigDecimal(10),List.of());
        }
        // Close the header cursor before issuing another query on this connection.
        return new Sale(header.id(),header.code(),header.soldAt(),header.cashier(),header.customer(),header.phone(),header.payment(),header.subtotal(),header.discount(),header.total(),lines(c,id));
    }
    private List<Sale.Line> lines(Connection c,long invoice)throws SQLException {
        List<Sale.Line> list=new ArrayList<>();
        String sql="SELECT d.Id,d.SKU,d.ProductName,d.Quantity,d.UnitPrice,d.WarrantyMonths,u.Serial FROM dm.InvoiceDetails d LEFT JOIN dm.InvoiceUnits iu ON iu.DetailId=d.Id LEFT JOIN dm.ProductUnits u ON u.Id=iu.UnitId WHERE d.InvoiceId=? ORDER BY d.Id,u.Serial";
        // Group on detail primary key once; joining serials must not duplicate sold quantities.
        try(PreparedStatement s=statement(c,sql,invoice);ResultSet r=s.executeQuery()) {
            long previous=-1; Sale.Line current=null; List<String> serials=new ArrayList<>();
            while(r.next()){
                long id=r.getLong(1);
                if(previous!=id){
                    if(current!=null)list.add(withSerials(current,serials));serials.clear();
                    current=new Sale.Line(r.getString(2),r.getString(3),r.getInt(4),r.getBigDecimal(5),r.getInt(6),"");previous=id;
                }
                if(r.getString(7)!=null)serials.add(r.getString(7));
            }
            if(current!=null)list.add(withSerials(current,serials));
        }return list;
    }
    private Sale.Line withSerials(Sale.Line l,List<String> serials){return new Sale.Line(l.sku(),l.name(),l.quantity(),l.price(),l.warrantyMonths(),String.join(", ",serials));}
}
