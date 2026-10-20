package GUI;
import Code.*;
public final class HistoryUI {
    private HistoryUI(){}
    public static void stock(){RowsFrame f=new RowsFrame("Lịch sử nhập / xuất / chuyển kho — tìm theo SKU");App.guard(f,Role.MANAGER,Role.WAREHOUSE);Runnable refresh=()->{String sku=f.search.getText();Ui.db(f,s->App.system().getWarehouse().inventory().history(s,sku),f::rows);};f.findAction(refresh);f.toolbar.add(Ui.button("Sắp hết hàng",()->Ui.db(f,s->App.system().reports.lowStock(s),f::rows)));f.setVisible(true);refresh.run();}
    public static void serial(){RowsFrame f=new RowsFrame("Lịch sử từng Serial / IMEI — nhập mã đầy đủ");App.guard(f,Role.MANAGER,Role.WAREHOUSE);Runnable refresh=()->{String serial=f.search.getText();Ui.db(f,s->App.system().getWarehouse().inventory().unitHistory(s,serial),f::rows);};f.findAction(refresh);f.setVisible(true);}
}
