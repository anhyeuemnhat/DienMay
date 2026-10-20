package GUI;
import Code.*;import javax.swing.*;import java.time.*;
public final class ReceiptsUI extends RowsFrame {
    private final JTextField from=Ui.text(Ui.DATE.format(LocalDate.now(Validation.ZONE).withDayOfMonth(1))),to=Ui.text(Ui.DATE.format(LocalDate.now(Validation.ZONE)));
    public ReceiptsUI(){super("Tra cứu / in lại hóa đơn");App.guard(this,Role.MANAGER,Role.CASHIER);from.setColumns(9);to.setColumns(9);toolbar.add(new JLabel("Từ:"));toolbar.add(from);toolbar.add(new JLabel("Đến:"));toolbar.add(to);findAction(this::refresh);toolbar.add(Ui.button("Xem / in lại",()->{long id=selectedId();Ui.db(this,s->App.system().getStore().sales().load(s,id),Ui::receipt);}));refresh();}
    private void refresh(){String text=search.getText();LocalDate a=Ui.date(from),b=Ui.date(to);Ui.db(this,s->App.system().getStore().sales().search(s,text,a,b),this::rows);}
}
