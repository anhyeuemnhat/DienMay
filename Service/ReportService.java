package Service;
import Code.*;import ImplementationDB.*;
import java.sql.*;import java.time.*;import java.util.*;
import static ImplementationDB.Jdbc.*;
public final class ReportService {
    private final SQL db;private final AuthService auth;
    public ReportService(SQL db,AuthService auth){this.db=db;this.auth=auth;}
    public List<LinkedHashMap<String,Object>> report(AuthService.Session s,String type,LocalDate from,LocalDate to)throws SQLException {
        Validation.dates(from,to);
        return db.read(c->{auth.require(c,s,Role.MANAGER);
            String filter=" WHERE i.SoldAt>=? AND i.SoldAt<? ";
            String sql=switch(type){
                case "summary"->"SELECT COUNT_BIG(*) AS [Số hóa đơn],COALESCE(SUM(i.Subtotal),0) AS [Tiền hàng],COALESCE(SUM(i.Discount),0) AS [Giảm giá],COALESCE(SUM(i.Total),0) AS [Doanh thu thuần] FROM dm.Invoices i"+filter;
                case "employee"->"SELECT e.UserName AS [Tài khoản],e.FullName AS [Nhân viên],COUNT_BIG(*) AS [Số hóa đơn],SUM(i.Total) AS [Doanh thu thuần] FROM dm.Invoices i JOIN dm.Employees e ON e.Id=i.EmployeeId"+filter+"GROUP BY e.UserName,e.FullName ORDER BY SUM(i.Total) DESC";
                case "best"->"SELECT TOP (30) d.SKU,MAX(d.ProductName) AS [Sản phẩm],SUM(CONVERT(bigint,d.Quantity)) AS [Số lượng bán],SUM(d.UnitPrice*d.Quantity) AS [Tiền hàng trước giảm giá] FROM dm.Invoices i JOIN dm.InvoiceDetails d ON d.InvoiceId=i.Id"+filter+"GROUP BY d.ProductId,d.SKU ORDER BY SUM(CONVERT(bigint,d.Quantity)) DESC";
                default->throw new IllegalArgumentException("Loại báo cáo không hợp lệ.");
            };
            return rows(c,sql,Validation.start(from),Validation.start(to.plusDays(1)));
        });
    }
    public List<LinkedHashMap<String,Object>> lowStock(AuthService.Session s)throws SQLException{return db.read(c->{auth.require(c,s,Role.MANAGER,Role.WAREHOUSE);return rows(c,"SELECT p.Id AS [Mã],p.SKU,p.Name AS [Sản phẩm],SUM(CONVERT(bigint,s.Quantity)) AS [Tổng tồn],p.ReorderLevel AS [Mức cảnh báo] FROM dm.Products p JOIN dm.Stock s ON s.ProductId=p.Id WHERE p.Enabled=1 GROUP BY p.Id,p.SKU,p.Name,p.ReorderLevel HAVING SUM(CONVERT(bigint,s.Quantity))<=p.ReorderLevel ORDER BY SUM(CONVERT(bigint,s.Quantity))");});}
}
