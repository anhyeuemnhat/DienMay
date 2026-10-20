package GUI;
import Code.*;import javax.swing.*;import java.time.*;
public final class ReportsUI extends RowsFrame {
    private final JTextField from=Ui.text(Ui.DATE.format(LocalDate.now(Validation.ZONE))),to=Ui.text(Ui.DATE.format(LocalDate.now(Validation.ZONE)));
    private final JComboBox<String> type=new JComboBox<>(new String[]{"Tổng doanh thu","Theo nhân viên","Sản phẩm bán chạy","Sắp hết hàng"});
    public ReportsUI(){
        super("Báo cáo từ SQL Server — ngày theo múi giờ Việt Nam");App.guard(this,Role.MANAGER);search.setVisible(false);from.setColumns(9);to.setColumns(9);toolbar.add(new JLabel("Từ:"));toolbar.add(from);toolbar.add(new JLabel("Đến:"));toolbar.add(to);toolbar.add(type);
        toolbar.add(Ui.button("Hôm nay",()->{LocalDate day=LocalDate.now(Validation.ZONE);from.setText(Ui.DATE.format(day));to.setText(Ui.DATE.format(day));refresh();}));
        toolbar.add(Ui.button("Tháng này",()->{LocalDate day=LocalDate.now(Validation.ZONE);from.setText(Ui.DATE.format(day.withDayOfMonth(1)));to.setText(Ui.DATE.format(day.withDayOfMonth(day.lengthOfMonth())));refresh();}));findAction(this::refresh);refresh();
    }
    private void refresh(){LocalDate a=Ui.date(from),b=Ui.date(to);int index=type.getSelectedIndex();if(index==3)Ui.db(this,s->App.system().reports.lowStock(s),this::rows);else{String key=new String[]{"summary","employee","best"}[index];Ui.db(this,s->App.system().reports.report(s,key,a,b),this::rows);}}
}
