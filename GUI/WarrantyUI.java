package GUI;
import Code.*;import javax.swing.*;import java.util.*;
public final class WarrantyUI extends RowsFrame {
    public WarrantyUI(){
        super("Bảo hành — tìm mã BH, hóa đơn, Serial/IMEI hoặc điện thoại");App.guard(this,Role.MANAGER,Role.CASHIER);findAction(this::refresh);
        toolbar.add(Ui.button("Tiếp nhận",this::receive));toolbar.add(Ui.button("Phiếu của bảo hành này",()->claims(selectedId())));toolbar.add(Ui.button("Tất cả phiếu",()->claims(null)));refresh();
    }
    private void refresh(){String text=search.getText();Ui.db(this,s->App.system().warranties.search(s,text),this::rows);}
    private void receive(){long warranty=selectedId();String expiry=String.valueOf(selected().get("Hiệu lực"));JTextArea fault=new JTextArea(5,30);fault.setLineWrap(true);fault.setWrapStyleWord(true);if(!Ui.edit(this,"Tiếp nhận — "+expiry+" (hết hạn vẫn có thể tiếp nhận)",Ui.fields("Mô tả lỗi",new JScrollPane(fault))))return;String text=fault.getText();Ui.db(this,s->App.system().warranties.receive(s,warranty,text),id->{Ui.message(this,"Đã tạo phiếu tiếp nhận "+id);claims(warranty);});}
    private void claims(Long warranty){
        RowsFrame f=new RowsFrame("Phiếu bảo hành"+(warranty==null?"":" — mã nội bộ "+warranty));f.search.setVisible(false);
        Runnable refresh=()->Ui.db(f,s->App.system().warranties.claims(s,warranty),f::rows);f.findAction(refresh);
        f.toolbar.add(Ui.button("Lịch sử trạng thái",()->{long claim=f.selectedId();RowsFrame h=new RowsFrame("Lịch sử phiếu "+claim);h.toolbar.setVisible(false);h.setVisible(true);Ui.db(h,s->App.system().warranties.history(s,claim),h::rows);}));
        if(App.session().role()==Role.MANAGER)f.toolbar.add(Ui.button("Cập nhật trạng thái",()->{
            Map<String,Object> row=f.selected();long claim=f.selectedId();String previous=String.valueOf(row.get("Trạng thái"));
            String[] next=previous.equals("RECEIVED")?new String[]{"PROCESSING","REJECTED"}:previous.equals("PROCESSING")?new String[]{"COMPLETED","REJECTED"}:new String[0];
            if(next.length==0){Ui.message(f,"Phiếu đã đóng; lịch sử được giữ nguyên.");return;}
            JComboBox<String> states=new JComboBox<>(next);states.setRenderer(new DefaultListCellRenderer(){public java.awt.Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){return super.getListCellRendererComponent(list,WarrantyRules.label(String.valueOf(value)),index,selected,focus);}});
            JTextArea notes=new JTextArea(4,30);if(!Ui.edit(f,"Cập nhật phiếu "+claim,Ui.fields("Trạng thái mới",states,"Ghi chú xử lý",new JScrollPane(notes))))return;
            String target=(String)states.getSelectedItem(),message=notes.getText();if(!Ui.confirm(f,"Xác nhận đổi trạng thái thành "+WarrantyRules.label(target)+"?"))return;
            Ui.db(f,s->{App.system().warranties.transition(s,claim,previous,target,message);return true;},ok->{Ui.message(f,"Đã cập nhật và lưu lịch sử.");refresh.run();});
        }));f.setVisible(true);refresh.run();
    }
}
