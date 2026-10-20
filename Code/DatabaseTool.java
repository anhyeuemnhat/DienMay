package Code;
import ImplementationDB.*;import Service.Passwords;
import java.io.Console;import java.sql.*;import java.util.*;
import static ImplementationDB.Jdbc.*;
/** DBA-assisted initial bootstrap only; never used by the running employee UI. */
public final class DatabaseTool {
    public static void main(String[] args)throws Exception{
        if(args.length==0)throw new IllegalArgumentException("Dùng: check | bootstrap <username> | hash");
        SQL db=new SQL();
        if(args[0].equals("check")){System.out.println("SQL Server: "+db.checkConnection());return;}
        Console console=System.console();if(console==null)throw new IllegalStateException("Chạy trong terminal có Console để mật khẩu không bị hiển thị.");
        char[] password=console.readPassword("Mat khau (12-256 ky tu): "),again=console.readPassword("Nhap lai: ");
        if(password==null||again==null)throw new IllegalStateException("Đã hủy nhập mật khẩu.");
        try{
            if(!Arrays.equals(password,again))throw new IllegalArgumentException("Mật khẩu không khớp.");String hash=Passwords.hash(password);
            if(args[0].equals("hash")){System.out.println(hash);return;}
            if(!args[0].equals("bootstrap")||args.length!=2)throw new IllegalArgumentException("Dùng bootstrap <username> hoặc hash.");
            String username=Validation.required(args[1],64,"Tên tài khoản");
            long id=db.transaction(c->{lock(c,"dm:employee-admin");try(PreparedStatement s=statement(c,"SELECT COUNT(*) FROM dm.Employees WITH (UPDLOCK,HOLDLOCK)");ResultSet r=s.executeQuery()){r.next();if(r.getInt(1)>0)throw new IllegalArgumentException("Chỉ bootstrap khi bảng nhân viên trống.");}return insert(c,"INSERT dm.Employees(UserName,FullName,PasswordHash,Role,MustChange) OUTPUT INSERTED.Id VALUES(?,N'Quản lý ban đầu',?,'MANAGER',0)",username,hash);});
            System.out.println("Da tao quan ly ban dau, ID="+id);
        }finally{Arrays.fill(password,'\0');Arrays.fill(again,'\0');}
    }
}
