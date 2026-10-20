package ImplementationDB;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
public final class Jdbc {
    private Jdbc() {}
    public static PreparedStatement statement(Connection c,String sql,Object... args) throws SQLException {
        PreparedStatement s=c.prepareStatement(sql); s.setQueryTimeout(20);
        try { bind(s,args); return s; } catch(SQLException | RuntimeException e) { s.close(); throw e; }
    }
    public static void bind(PreparedStatement s,Object... args) throws SQLException {
        for(int i=0;i<args.length;i++) {
            Object a=args[i];
            if(a instanceof LocalDate) s.setDate(i+1,java.sql.Date.valueOf((LocalDate)a));
            else if(a instanceof Instant) s.setTimestamp(i+1,Timestamp.valueOf(LocalDateTime.ofInstant((Instant)a,ZoneOffset.UTC)));
            else if(a instanceof UUID) s.setString(i+1,a.toString());
            else if(a instanceof String) s.setNString(i+1,(String)a);
            else if(a instanceof BigDecimal) s.setBigDecimal(i+1,(BigDecimal)a);
            else s.setObject(i+1,a);
        }
    }
    public static int update(Connection c,String sql,Object...args) throws SQLException { try(PreparedStatement s=statement(c,sql,args)) { return s.executeUpdate(); } }
    public static long insert(Connection c,String sql,Object...args) throws SQLException {
        try(PreparedStatement s=statement(c,sql,args); ResultSet r=s.executeQuery()) {
            if(!r.next()) throw new SQLException("Không nhận được mã dữ liệu sau khi lưu."); return r.getLong(1);
        }
    }
    public static void lock(Connection c,String resource) throws SQLException {
        try(PreparedStatement s=statement(c,"DECLARE @r int; EXEC @r=sys.sp_getapplock @Resource=?, @LockMode='Exclusive', @LockOwner='Transaction', @LockTimeout=10000; SELECT @r",resource); ResultSet r=s.executeQuery()) {
            if(!r.next() || r.getInt(1)<0) throw new SQLException("Giao dịch đang được xử lý. Giữ nguyên yêu cầu và thử lại.","40001");
        }
    }
    public static Instant now(Connection c) throws SQLException {
        try(PreparedStatement s=statement(c,"SELECT SYSUTCDATETIME()"); ResultSet r=s.executeQuery()) { r.next(); return instant(r,1); }
    }
    public static Instant instant(ResultSet r,int col) throws SQLException { Timestamp t=r.getTimestamp(col); return t==null?null:t.toLocalDateTime().toInstant(ZoneOffset.UTC); }
    public static List<LinkedHashMap<String,Object>> rows(Connection c,String sql,Object...args) throws SQLException {
        List<LinkedHashMap<String,Object>> list=new ArrayList<>();
        try(PreparedStatement s=statement(c,sql,args); ResultSet r=s.executeQuery()) {
            ResultSetMetaData m=r.getMetaData();
            while(r.next()) { LinkedHashMap<String,Object> row=new LinkedHashMap<>(); for(int i=1;i<=m.getColumnCount();i++) row.put(m.getColumnLabel(i),r.getObject(i)); list.add(row); }
        } return list;
    }
}
