package Service;
import Code.*;import ImplementationDB.*;
import java.math.BigDecimal;import java.sql.*;import java.time.*;import java.util.*;
import static ImplementationDB.Jdbc.*;
public final class PromotionService {
    private final SQL db;private final AuthService auth;
    public PromotionService(SQL db,AuthService auth){this.db=db;this.auth=auth;}
    public List<LinkedHashMap<String,Object>> list(AuthService.Session s)throws SQLException{return db.read(c->{auth.require(c,s,Role.MANAGER,Role.CASHIER);return rows(c,"SELECT Id AS [Mã],Code AS [Mã khuyến mãi],Name AS [Tên],StartDate AS [Từ ngày],EndDate AS [Đến ngày],PercentOff AS [Giảm %],Enabled AS [Hoạt động] FROM dm.Promotions ORDER BY Id DESC");});}
    public long save(AuthService.Session s,Long id,String code,String name,LocalDate from,LocalDate to,BigDecimal percent,boolean enabled)throws SQLException{
        String co=Validation.required(code,50,"Mã khuyến mãi").toUpperCase(Locale.ROOT),n=Validation.required(name,150,"Tên khuyến mãi");Validation.dates(from,to);Money.discount(Money.ZERO,percent);Money.exact(percent);
        return db.transaction(c->{auth.require(c,s,Role.MANAGER);if(id==null)return insert(c,"INSERT dm.Promotions(Code,Name,StartDate,EndDate,PercentOff,Enabled) OUTPUT INSERTED.Id VALUES(?,?,?,?,?,?)",co,n,from,to,percent,enabled);if(update(c,"UPDATE dm.Promotions SET Code=?,Name=?,StartDate=?,EndDate=?,PercentOff=?,Enabled=? WHERE Id=?",co,n,from,to,percent,enabled,id)!=1)throw new IllegalArgumentException("Không tìm thấy khuyến mãi.");return id;});
    }
    static BigDecimal discount(Connection c,String code,BigDecimal manual,BigDecimal subtotal,Instant now,boolean lock)throws SQLException {
        if(code==null||code.isBlank()){Money.total(subtotal,manual);return manual;}
        if(manual.signum()!=0)throw new IllegalArgumentException("Không cộng giảm trực tiếp với khuyến mãi.");
        LocalDate day=now.atZone(Validation.ZONE).toLocalDate();
        try(PreparedStatement s=statement(c,"SELECT PercentOff FROM dm.Promotions "+(lock?"WITH (HOLDLOCK) ":"")+"WHERE Code=? AND Enabled=1 AND StartDate<=? AND EndDate>=?",code,day,day);ResultSet r=s.executeQuery()){
            if(!r.next())throw new IllegalArgumentException("Mã khuyến mãi không tồn tại hoặc đã hết hiệu lực.");return Money.discount(subtotal,r.getBigDecimal(1));
        }
    }
}
