package GUI;
import Code.*;
import Service.*;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.Arrays;
public final class App {
    private static volatile AuthService.Session session;
    private static volatile long generation;
    private static MakeSaleUI pos;
    private App(){}
    public static InventorySystem system(){return SingletonPattern.getInstance().getSystem();}
    public static AuthService.Session session(){return session;}
    public static long epoch(){return generation;}
    public static Path pendingDirectory(){String profile=SaleRequest.digest(system().db.setting("db.url","unconfigured")).substring(0,16);return Path.of(System.getProperty("dienmay.stateDir",Path.of(System.getProperty("user.home"),".dienmay").toString()),profile);}
    public static void main(String[] args){
        try{system();}catch(RuntimeException e){SwingUtilities.invokeLater(()->Ui.error(null,e));return;}
        SwingUtilities.invokeLater(()->{try{UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());}catch(Exception ignored){/* Swing default is a usable fallback. */}new MainUI().setVisible(true);});
    }
    public static void enter(AuthService.Session authenticated){
        session=authenticated;generation++;
        // Keep the login window displayable during the first-login password change:
        // a SwingWorker needs a live AWT event loop to deliver its completion callback.
        if(session.mustChange()){changePassword(true);return;}
        home();
    }
    private static void home(){
        JFrame next=switch(session.role()){case MANAGER->new StoreManagerMain();case CASHIER->new CashierMain();case WAREHOUSE->new WarehouseManagerMain();};
        // Show the replacement first; only then close the login/old work windows.
        // Otherwise the last displayable Swing window can disappear before async work finishes.
        next.setVisible(true);
        for(Window w:Window.getWindows())if(w!=next)w.dispose();
    }
    public static boolean guard(JFrame frame,Role...roles){
        if(session==null||!AuthService.roleAllowed(session.role(),roles)){frame.dispose();throw new SecurityException("Vui lòng đăng nhập với vai trò phù hợp.");}return true;
    }
    public static void logout(){
        if(Ui.jobs()>0){Ui.message(null,"Đang xử lý dữ liệu. Hãy hoàn tất hoặc đối soát giao dịch trước khi đăng xuất.");return;}
        if(!Ui.confirm(null,"Đăng xuất và đóng các cửa sổ? Giỏ chưa thanh toán sẽ không được giữ; yêu cầu chờ vẫn được lưu để đối soát."))return;
        system().auth.logout(session);session=null;generation++;pos=null;
        for(Window w:Window.getWindows())w.dispose();new MainUI().setVisible(true);
    }
    public static void openPos(){
        if(pos!=null&&pos.isDisplayable()){pos.toFront();return;}
        pos=new MakeSaleUI();pos.setVisible(true);
    }
    public static void changePassword(boolean required){
        // Do not dispose POS/stock windows (and release their request-file locks)
        // while another worker may still commit or clear the pending request.
        if(!required && Ui.jobs()>0){
            Ui.message(null,"Đang xử lý dữ liệu. Hãy chờ hoàn tất trước khi đổi mật khẩu.");
            return;
        }
        JPasswordField old=new JPasswordField(24),next=new JPasswordField(24),repeat=new JPasswordField(24);
        JPanel p=Ui.fields("Mật khẩu hiện tại",old,"Mật khẩu mới (ít nhất 12 ký tự)",next,"Nhập lại mật khẩu mới",repeat);
        if(!Ui.edit(null,required?"Bắt buộc đổi mật khẩu ban đầu":"Đổi mật khẩu",p)){
            if(required){
                system().auth.logout(session);session=null;generation++;
                // The original login window was intentionally preserved.
                // Reuse it, rather than creating a duplicate frame.
                boolean found=false;
                for(Window w:Window.getWindows())if(w instanceof MainUI){w.setEnabled(true);w.setVisible(true);found=true;}
                if(!found)new MainUI().setVisible(true);
            }
            return;
        }
        char[] a=old.getPassword(),b=next.getPassword(),c=repeat.getPassword();
        if(!Arrays.equals(b,c)){Arrays.fill(a,'\0');Arrays.fill(b,'\0');Arrays.fill(c,'\0');Ui.message(null,"Mật khẩu nhập lại không khớp.");changePassword(required);return;}
        Arrays.fill(c,'\0');AuthService.Session original=session;
        // Prevent another login while the password change is committed asynchronously.
        if(required)for(Window w:Window.getWindows())if(w instanceof MainUI)w.setEnabled(false);
        Ui.async(null,()->{try{return system().auth.changePassword(original,a,b);}finally{Arrays.fill(a,'\0');Arrays.fill(b,'\0');}},newSession->{
            session=newSession;generation++;pos=null;
            home(); // Open first; home() disposes the old login only after the new UI is visible.
            Ui.message(null,"Đã đổi mật khẩu.");
        },error->{
            if(required)for(Window w:Window.getWindows())if(w instanceof MainUI)w.setEnabled(true);
            Ui.error(null,error);
            if(required)changePassword(true);
        });
    }
    private static JMenuItem item(String title,Runnable action){JMenuItem i=new JMenuItem(title);i.addActionListener(e->Ui.safe(action));return i;}
    public static void menu(JFrame f){
        JMenuBar bar=new JMenuBar();JMenu work=new JMenu("Nghiệp vụ"),account=new JMenu("Tài khoản");Role role=session.role();
        work.add(item("Sản phẩm / tồn kho",()->new ProductsUI().setVisible(true)));
        if(role!=Role.WAREHOUSE){work.add(item("Bán hàng",App::openPos));work.add(item("Tra cứu hóa đơn",()->new ReceiptsUI().setVisible(true)));work.add(item("Khách hàng",PeopleUI::customers));work.add(item("Bảo hành",()->new WarrantyUI().setVisible(true)));}
        if(role!=Role.CASHIER){work.add(item("Nhập hàng",()->new StockReceiveUI().setVisible(true)));work.add(item("Chuyển kho / quầy",()->new TransferfromWarehouse().setVisible(true)));work.add(item("Nhà cung cấp",PeopleUI::vendors));work.add(item("Lịch sử kho",HistoryUI::stock));work.add(item("Lịch sử Serial/IMEI",HistoryUI::serial));}
        if(role==Role.MANAGER){work.add(item("Nhân viên / tài khoản",PeopleUI::employees));work.add(item("Danh mục",()->PeopleUI.lookups("category")));work.add(item("Thương hiệu",()->PeopleUI.lookups("brand")));work.add(item("Khuyến mãi",PeopleUI::promotions));work.add(item("Báo cáo",()->new ReportsUI().setVisible(true)));}
        account.add(item("Đổi mật khẩu",()->changePassword(false)));account.add(item("Đăng xuất",App::logout));bar.add(work);bar.add(account);bar.add(new JLabel("  "+session.name()+" — "+role));f.setJMenuBar(bar);
        Dimension size=f.getSize();f.setPreferredSize(new Dimension(size.width,size.height+bar.getPreferredSize().height));f.pack();f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);f.addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosing(java.awt.event.WindowEvent e){logout();}});
    }
}
