package Service;
import Code.*;
import ImplementationDB.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static ImplementationDB.Jdbc.*;
/** Sessions cannot be constructed by UI callers; every service rechecks DB role/version/enabled. */
public final class AuthService {
    public static final class Session {
        private final UUID token=UUID.randomUUID();
        private final long employeeId; private final String name; private final Role role;
        private final int version; private final boolean mustChange;
        private Session(long id,String name,Role role,int version,boolean mustChange){this.employeeId=id;this.name=name;this.role=role;this.version=version;this.mustChange=mustChange;}
        public long employeeId(){return employeeId;} public String name(){return name;} public Role role(){return role;} public boolean mustChange(){return mustChange;}
    }
    private final SQL db;
    private final Map<UUID,Session> sessions=new ConcurrentHashMap<>();
    // Equal-cost verification for unknown usernames; not an actual account password.
    private static final String DUMMY=Passwords.hash("not-an-account-secret".toCharArray());
    public AuthService(SQL db){this.db=db;}
    public Session login(String username,char[] password) throws SQLException {
        String user=Validation.required(username,64,"Tên đăng nhập");
        Session result=db.transaction(c -> {
            record Account(long id,String name,String hash,Role role,int version,boolean mustChange,
                           boolean enabled,int failed,Instant lockUntil,Instant now) {}
            Account account;
            try(PreparedStatement s=statement(c,"SELECT Id,FullName,PasswordHash,Role,AuthVersion,MustChange,Enabled,FailedAttempts,LockUntil,SYSUTCDATETIME() FROM dm.Employees WITH (UPDLOCK,ROWLOCK) WHERE UserName=?",user);
                ResultSet r=s.executeQuery()) {
                if(!r.next()) {
                    Passwords.verify(password,DUMMY);
                    return null;
                }
                account=new Account(r.getLong(1),r.getString(2),r.getString(3),Role.valueOf(r.getString(4)),
                    r.getInt(5),r.getBoolean(6),r.getBoolean(7),r.getInt(8),instant(r,9),instant(r,10));
            }
            boolean correct=Passwords.verify(password,account.hash());
            if(!account.enabled() || (account.lockUntil()!=null && account.lockUntil().isAfter(account.now())))return null;
            if(!correct) {
                int count=account.lockUntil()!=null?1:account.failed()+1;
                update(c,"UPDATE dm.Employees SET FailedAttempts=?,LockUntil=? WHERE Id=?",count,
                    count>=5?account.now().plusSeconds(300):null,account.id());
                return null; // Commit the failed-attempt counter, not a successful session.
            }
            update(c,"UPDATE dm.Employees SET FailedAttempts=0,LockUntil=NULL WHERE Id=?",account.id());
            return new Session(account.id(),account.name(),account.role(),account.version(),account.mustChange());
        });
        if(result==null)throw new SecurityException("Tên đăng nhập hoặc mật khẩu không đúng, tài khoản bị khóa, hoặc tạm khóa 5 phút sau 5 lần sai.");
        sessions.put(result.token,result);return result;
    }
    public void logout(Session session){if(session!=null)sessions.remove(session.token);}
    public static boolean roleAllowed(Role actual,Role... roles){return Arrays.asList(roles).contains(actual);}
    public void require(Connection c,Session session,Role...roles) throws SQLException { check(c,session,false,roles); }
    private void check(Connection c,Session session,boolean changing,Role...roles) throws SQLException {
        if(session==null||sessions.get(session.token)!=session)throw new SecurityException("Phiên đăng nhập đã hết. Vui lòng đăng nhập lại.");
        // HOLDLOCK makes role/lock changes wait until the authorized transaction completes.
        try(PreparedStatement s=statement(c,"SELECT Role,Enabled,AuthVersion,MustChange FROM dm.Employees WITH (HOLDLOCK) WHERE Id=?",session.employeeId); ResultSet r=s.executeQuery()) {
            if(!r.next()||!r.getBoolean(2)||r.getInt(3)!=session.version)throw new SecurityException("Tài khoản hoặc quyền đã thay đổi. Hãy đăng nhập lại.");
            Role role=Role.valueOf(r.getString(1));
            if(!roleAllowed(role,roles))throw new SecurityException("Bạn không có quyền thực hiện chức năng này.");
            if(r.getBoolean(4)&&!changing)throw new SecurityException("Cần đổi mật khẩu ban đầu trước khi sử dụng.");
        }
    }
    public Session changePassword(Session session,char[] oldPassword,char[] newPassword) throws SQLException {
        Passwords.validate(newPassword);
        if(Arrays.equals(oldPassword,newPassword))throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu cũ.");
        String hash=Passwords.hash(newPassword);
        Session next=db.transaction(c -> {
            check(c,session,true,Role.values());
            try(PreparedStatement s=statement(c,"SELECT PasswordHash FROM dm.Employees WITH (UPDLOCK) WHERE Id=?",session.employeeId);ResultSet r=s.executeQuery()) {
                if(!r.next()||!Passwords.verify(oldPassword,r.getString(1)))throw new SecurityException("Mật khẩu hiện tại không đúng.");
            }
            update(c,"UPDATE dm.Employees SET PasswordHash=?,MustChange=0,AuthVersion=AuthVersion+1 WHERE Id=?",hash,session.employeeId);
            return new Session(session.employeeId,session.name,session.role,session.version+1,false);
        });
        logout(session);sessions.put(next.token,next);return next;
    }
}
