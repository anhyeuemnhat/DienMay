package GUI;
import Code.*;import Service.*;
import javax.swing.*;import java.awt.*;
public final class StockReceiveUI extends JFrame {
    public StockReceiveUI(){
        super("Nhập hàng vào kho");App.guard(this,Role.MANAGER,Role.WAREHOUSE);
        JTextField sku=Ui.text(""),qty=Ui.text("1"),cost=Ui.text("0"),reference=Ui.text("");
        JTextArea serials=new JTextArea(7,28);JComboBox<CatalogService.Lookup> vendors=new JComboBox<>();
        JLabel hint=new JLabel("Mỗi thiết bị theo Serial cần một mã. Có thể dán danh sách cách nhau bằng dòng mới.");
        JButton save=new JButton("Ghi phiếu nhập");
        StockController controller=new StockController(this,true,()->{
            CatalogService.Lookup vendor=(CatalogService.Lookup)vendors.getSelectedItem();if(vendor==null)throw new IllegalArgumentException("Chọn nhà cung cấp.");
            return new StockController.Input(sku.getText(),Integer.parseInt(qty.getText().trim()),Validation.serials(serials.getText()),reference.getText(),vendor.id(),Money.of(cost.getText()),null,"KHO");
        },save);
        save.addActionListener(e->Ui.safe(controller::submit));
        JPanel main=new JPanel(new BorderLayout());main.add(hint,BorderLayout.NORTH);main.add(Ui.fields("SKU / mã vạch",sku,"Số lượng",qty,"Đơn giá nhập (VND)",cost,"Nhà cung cấp",vendors,"Số chứng từ / lý do",reference,"Serial / IMEI",new JScrollPane(serials)));main.add(save,BorderLayout.SOUTH);add(main);add(controller.footer(),BorderLayout.SOUTH);
        setSize(810,520);setLocationRelativeTo(null);Ui.decorate(this);
        Ui.db(this,s->App.system().getStore().catalog().lookups(s,"vendor"),items->{for(CatalogService.Lookup v:items)vendors.addItem(v);});
    }
}
