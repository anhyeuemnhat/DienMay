package GUI;
import Code.*;import Service.*;
import javax.swing.*;import java.awt.*;import java.math.BigDecimal;import java.util.*;import java.util.List;import java.util.function.Supplier;
/** Common transaction/recovery behavior; both legacy transfer form and new receiving form use it. */
public final class StockController {
    public record Input(String sku,int qty,List<String> serials,String reference,Long vendor,BigDecimal cost,String from,String to){}
    private final JFrame frame;private final boolean receiving;private final Supplier<Input> input;private final AbstractButton submit;
    private final PendingStore store;private volatile InventoryService.Request pending;private boolean busy;
    private final JLabel status=new JLabel();private final JButton retry,resolve,details;
    public StockController(JFrame frame,boolean receiving,Supplier<Input> input,AbstractButton submit){
        this.frame=frame;this.receiving=receiving;this.input=input;this.submit=submit;
        PendingStore opened=null;
        try{opened=new PendingStore(App.pendingDirectory(),App.session().employeeId(),receiving?"receive":"transfer");pending=opened.inventory();}
        catch(java.io.IOException e){if(opened!=null)try{opened.close();}catch(java.io.IOException close){e.addSuppressed(close);}frame.dispose();throw new IllegalStateException(e.getMessage(),e);}store=opened;
        retry=Ui.button("Thử lại cùng yêu cầu",()->execute(null));resolve=Ui.button("Đối soát",this::resolve);
        details=Ui.button("Chi tiết yêu cầu chờ",()->{if(pending!=null)Ui.message(frame,"Yêu cầu: "+pending.id()+"\nMã sản phẩm nội bộ: "+pending.productId()+"\nSố lượng: "+pending.quantity()+"\nNguồn: "+pending.from()+" → "+pending.to()+"\nChứng từ: "+pending.reference()+"\nSerial/IMEI: "+String.join(", ",pending.serials()));});
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);frame.addWindowListener(new java.awt.event.WindowAdapter(){
            public void windowClosing(java.awt.event.WindowEvent e){if(busy){Ui.message(frame,"Đang xử lý giao dịch; không đóng cửa sổ.");return;}if(pending!=null&&!Ui.confirm(frame,"Yêu cầu chờ sẽ được giữ để đối soát lần sau. Đóng cửa sổ?"))return;frame.dispose();}
            public void windowClosed(java.awt.event.WindowEvent e){try{store.close();}catch(java.io.IOException ex){Ui.error(null,ex);}}
        });render();
    }
    public JPanel footer(){JPanel p=new JPanel(new BorderLayout());JPanel b=new JPanel(new FlowLayout(FlowLayout.LEFT));b.add(retry);b.add(resolve);b.add(details);p.add(b);p.add(status,BorderLayout.SOUTH);return p;}
    private void render(){submit.setEnabled(!busy&&pending==null);retry.setEnabled(!busy&&pending!=null);resolve.setEnabled(!busy&&pending!=null);details.setEnabled(!busy&&pending!=null);status.setText(pending==null?(busy?"Đang ghi dữ liệu…":"Mọi biến động kho đều cần chứng từ hoặc lý do."):"Chưa đối soát: "+pending.id());}
    public void submit(){if(busy||pending!=null)return;Input values=input.get();if(!Ui.confirm(frame,"Xác nhận "+(receiving?"nhập":"chuyển")+" "+values.qty()+" sản phẩm?"))return;execute(values);}
    private void execute(Input values){
        if(busy||(pending==null&&values==null))return;busy=true;render();
        Ui.db(frame,s->{
            if(pending==null){Item item=App.system().getStore().catalog().find(s,values.sku());pending=new InventoryService.Request(UUID.randomUUID(),item.id(),values.qty(),values.serials(),values.reference(),values.vendor(),values.cost(),values.from(),values.to());}
            store.save(pending);
            if(receiving)App.system().getWarehouse().inventory().receive(s,pending);else App.system().getWarehouse().inventory().transfer(s,pending);
            store.clear();return pending.id();
        },id->{busy=false;pending=null;render();Ui.message(frame,"Đã ghi giao dịch kho: "+id);},error->{busy=false;render();Ui.error(frame,error);});
    }
    private void resolve(){
        if(busy||pending==null)return;busy=true;render();InventoryService.Request request=pending;
        Ui.db(frame,s->{boolean saved=App.system().getWarehouse().inventory().resolve(s,request);if(saved)store.clear();return saved;},saved->{busy=false;if(saved){pending=null;Ui.message(frame,"SQL Server xác nhận giao dịch đã được ghi. Không nhập/chuyển lại.");}else if(Ui.confirm(frame,"SQL Server xác nhận yêu cầu chưa được ghi. Hủy yêu cầu chờ để sửa thông tin?")){try{store.clear();pending=null;}catch(java.io.IOException e){Ui.error(frame,e);}}render();},error->{busy=false;render();Ui.error(frame,error);});
    }
}
