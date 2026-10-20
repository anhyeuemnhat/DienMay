package Service;
import Code.*;
import ImplementationDB.*;
import java.sql.*;
import java.util.*;
import static ImplementationDB.Jdbc.*;
public final class PeopleService {
    private final SQL db;private final AuthService auth;
    public PeopleService(SQL db,AuthService auth){this.db=db;this.auth=auth;}
    public List<LinkedHashMap<String,Object>> customers(AuthService.Session s,String text)throws SQLException{return db.read(c->{auth.require(c,s,Role.MANAGER,Role.CASHIER);return rows(c,"SELECT TOP (500) Id AS [Mã],FullName AS [Họ tên],Phone AS [Điện thoại],Address AS [Địa chỉ] FROM dm.Customers WHERE FullName LIKE ? ESCAPE '~' OR Phone LIKE ? ESCAPE '~' ORDER BY Id DESC",ProductDao.like(text),ProductDao.like(text));});}
    public record CustomerSummary(long id,String name,String phone) {}
    public CustomerSummary customer(AuthService.Session session,long id)throws SQLException {
        if(id<=0)throw new IllegalArgumentException("Mã khách hàng phải là số dương.");
        return db.read(c->{
            auth.require(c,session,Role.MANAGER,Role.CASHIER);
            try(PreparedStatement q=statement(c,"SELECT Id,FullName,Phone FROM dm.Customers WHERE Id=?",id);ResultSet r=q.executeQuery()){
                if(!r.next())throw new IllegalArgumentException("Không tìm thấy khách hàng.");
                return new CustomerSummary(r.getLong(1),r.getString(2),r.getString(3));
            }
        });
    }
    public long saveCustomer(AuthService.Session s,Long id,String name,String phone,String address)throws SQLException{
        String n=Validation.required(name,120,"Họ tên"),p=Validation.optional(phone,30,"Điện thoại"),a=Validation.optional(address,300,"Địa chỉ");
        return db.transaction(c->{auth.require(c,s,Role.MANAGER,Role.CASHIER);if(id==null)return insert(c,"INSERT dm.Customers(FullName,Phone,Address) OUTPUT INSERTED.Id VALUES(?,?,?)",n,p,a);if(update(c,"UPDATE dm.Customers SET FullName=?,Phone=?,Address=? WHERE Id=?",n,p,a,id)!=1)throw new IllegalArgumentException("Không tìm thấy khách hàng.");return id;});
    }
    public List<LinkedHashMap<String,Object>> vendors(AuthService.Session s)throws SQLException{return db.read(c->{auth.require(c,s,Role.MANAGER,Role.WAREHOUSE);return rows(c,"SELECT Id AS [Mã],Name AS [Nhà cung cấp],Phone AS [Điện thoại],Address AS [Địa chỉ],Enabled AS [Hoạt động] FROM dm.Vendors ORDER BY Name");});}
    public long saveVendor(AuthService.Session s,Long id,String name,String phone,String address,boolean enabled)throws SQLException{
        String n=Validation.required(name,150,"Tên nhà cung cấp"),p=Validation.optional(phone,30,"Điện thoại"),a=Validation.optional(address,300,"Địa chỉ");
        return db.transaction(c->{auth.require(c,s,Role.MANAGER);if(id==null)return insert(c,"INSERT dm.Vendors(Name,Phone,Address,Enabled) OUTPUT INSERTED.Id VALUES(?,?,?,?)",n,p,a,enabled);if(update(c,"UPDATE dm.Vendors SET Name=?,Phone=?,Address=?,Enabled=? WHERE Id=?",n,p,a,enabled,id)!=1)throw new IllegalArgumentException("Không tìm thấy nhà cung cấp.");return id;});
    }
    public List<LinkedHashMap<String,Object>> employees(AuthService.Session s)throws SQLException{return db.read(c->{auth.require(c,s,Role.MANAGER);return rows(c,"SELECT Id AS [Mã],UserName AS [Tài khoản],FullName AS [Họ tên],Phone AS [Điện thoại],Role AS [Vai trò],Enabled AS [Hoạt động],MustChange AS [Cần đổi mật khẩu] FROM dm.Employees ORDER BY Id");});}
    public long saveEmployee(AuthService.Session session,Long id,String username,String name,String phone,Role role,boolean enabled,char[] password)throws SQLException{
        String u=Validation.required(username,64,"Tài khoản"),n=Validation.required(name,120,"Họ tên"),p=Validation.optional(phone,30,"Điện thoại");
        String hash=password!=null&&password.length>0?Passwords.hash(password):null;
        if(id==null&&hash==null)throw new IllegalArgumentException("Cần mật khẩu cho tài khoản mới.");
        Objects.requireNonNull(role);
        return db.transaction(c->{
            lock(c,"dm:employee-admin");auth.require(c,session,Role.MANAGER);
            if(id==null)return insert(c,"INSERT dm.Employees(UserName,FullName,Phone,PasswordHash,Role,Enabled,MustChange) OUTPUT INSERTED.Id VALUES(?,?,?,?,?,?,1)",u,n,p,hash,role.name(),enabled);
            if(id==session.employeeId()&&(!enabled||role!=Role.MANAGER))throw new IllegalArgumentException("Không tự khóa hoặc hạ quyền tài khoản đang dùng.");
            if(id==session.employeeId()&&hash!=null)throw new IllegalArgumentException("Dùng menu Đổi mật khẩu cho tài khoản đang đăng nhập.");
            if(!enabled||role!=Role.MANAGER){try(PreparedStatement q=statement(c,"SELECT COUNT(*) FROM dm.Employees WITH (UPDLOCK,HOLDLOCK) WHERE Role='MANAGER' AND Enabled=1 AND Id<>?",id);ResultSet r=q.executeQuery()){r.next();if(r.getInt(1)==0)throw new IllegalArgumentException("Phải giữ ít nhất một quản lý hoạt động.");}}
            if(update(c,"UPDATE dm.Employees SET UserName=?,FullName=?,Phone=?,Role=?,Enabled=?,AuthVersion=AuthVersion+1,FailedAttempts=0,LockUntil=NULL WHERE Id=?",u,n,p,role.name(),enabled,id)!=1)throw new IllegalArgumentException("Không tìm thấy nhân viên.");
            if(hash!=null)update(c,"UPDATE dm.Employees SET PasswordHash=?,MustChange=1 WHERE Id=?",hash,id);
            return id;
        });
    }
}
