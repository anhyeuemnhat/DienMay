package GUI;
import Code.*;
import Service.AuthService;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.*;
import java.text.MessageFormat;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
public final class Ui {
    @FunctionalInterface public interface DbJob<T>{T run(AuthService.Session session)throws Exception;}
    private static final AtomicInteger JOBS=new AtomicInteger();
    public static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private Ui(){}
    public static int jobs(){return JOBS.get();}
    public static JButton button(String text,Runnable action){JButton b=new JButton(text);b.addActionListener(e->safe(action));return b;}
    public static void safe(Runnable action){try{action.run();}catch(RuntimeException e){error(null,e);}}
    public static void message(Component owner,String message){JOptionPane.showMessageDialog(owner,message,"Điện Máy",JOptionPane.INFORMATION_MESSAGE);}
    public static boolean confirm(Component owner,String message){return JOptionPane.showConfirmDialog(owner,message,"Xác nhận",JOptionPane.OK_CANCEL_OPTION,JOptionPane.QUESTION_MESSAGE)==JOptionPane.OK_OPTION;}
    public static void error(Component owner,Throwable error){
        Throwable e=error;while(e instanceof ExecutionException||e instanceof CompletionException){if(e.getCause()==null)break;e=e.getCause();}
        String message=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
        if(e instanceof SQLException sql){
            if(sql.getErrorCode()==2601||sql.getErrorCode()==2627)message="Mã SKU/mã vạch, Serial/IMEI hoặc mã chứng từ đã tồn tại. Giao dịch bị từ chối; kiểm tra dữ liệu rồi đối soát yêu cầu.";
            else if(sql.getErrorCode()==547)message="Dữ liệu không thỏa ràng buộc liên kết hoặc giới hạn hợp lệ. Không tiếp tục tạo chứng từ mới khi chưa đối soát.";
            else message="Lỗi SQL Server: "+message+"\nVới yêu cầu thanh toán/nhập/chuyển kho: giữ nguyên mã yêu cầu và dùng Đối soát hoặc Thử lại.";
        }
        JOptionPane.showMessageDialog(owner,message,"Không thể thực hiện",JOptionPane.ERROR_MESSAGE);
    }
    public static <T> void async(Component owner,Callable<T> job,Consumer<T> success){async(owner,job,success,e->error(owner,e));}
    public static <T> void async(Component owner,Callable<T> job,Consumer<T> success,Consumer<Throwable> failed){
        JOBS.incrementAndGet();
        new SwingWorker<T,Void>(){
            protected T doInBackground()throws Exception{return job.call();}
            protected void done(){JOBS.decrementAndGet();try{success.accept(get());}catch(InterruptedException e){Thread.currentThread().interrupt();failed.accept(e);}catch(ExecutionException e){failed.accept(e.getCause());}catch(RuntimeException e){failed.accept(e);}}
        }.execute();
    }
    public static <T> void db(Component owner,DbJob<T> work,Consumer<T> success){db(owner,work,success,e->error(owner,e));}
    public static <T> void db(Component owner,DbJob<T> work,Consumer<T> success,Consumer<Throwable> failed){
        AuthService.Session session=App.session();long epoch=App.epoch();
        async(owner,()->work.run(session),result->{if(App.epoch()==epoch)success.accept(result);},error->{if(App.epoch()==epoch)failed.accept(error);});
    }
    public static void decorate(Window frame){
        fonts(frame);
        if(frame instanceof JFrame f){f.setIconImage(new ImageIcon(Ui.class.getResource("/Images/shop.png")).getImage());if(f.getJMenuBar()==null)f.setJMenuBar(new JMenuBar());}
    }
    private static void fonts(Component c){Font old=c.getFont();if(old!=null)c.setFont(new Font(Font.DIALOG,old.getStyle(),Math.max(13,Math.min(old.getSize(),26))));if(c instanceof Container p)for(Component child:p.getComponents())fonts(child);}
    public static JPanel fields(Object... values){
        JPanel p=new JPanel(new GridBagLayout());GridBagConstraints g=new GridBagConstraints();g.insets=new Insets(5,7,5,7);g.anchor=GridBagConstraints.WEST;
        for(int i=0;i<values.length;i+=2){g.gridy=i/2;g.gridx=0;g.weightx=0;g.fill=GridBagConstraints.NONE;p.add(new JLabel(String.valueOf(values[i])),g);g.gridx=1;g.weightx=1;g.fill=GridBagConstraints.HORIZONTAL;p.add((Component)values[i+1],g);}return p;
    }
    public static boolean edit(Component owner,String title,JPanel panel){return JOptionPane.showConfirmDialog(owner,panel,title,JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION;}
    public static JTextField text(String value){return new JTextField(value==null?"":value,24);}
    public static LocalDate date(JTextField field){try{return LocalDate.parse(field.getText().trim(),DATE);}catch(DateTimeParseException e){throw new IllegalArgumentException("Ngày không hợp lệ. Nhập dd/MM/yyyy.");}}
    public static void receipt(Sale sale){
        JFrame f=new JFrame("Hóa đơn "+sale.code());f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        JTextArea area=new JTextArea(sale.generateReceipt(App.system().db.setting("store.name","Cửa hàng Điện Máy"),App.system().db.setting("store.address",""),App.system().db.setting("store.phone","")));
        area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);area.setFont(new Font(Font.DIALOG,Font.PLAIN,14));area.setMargin(new Insets(18,18,18,18));
        f.add(new JScrollPane(area));JButton print=button("In / in lại hóa đơn",()->async(f,()->area.print(null,new MessageFormat("Trang {0}")),ok->{if(ok)message(f,"Đã gửi lệnh in.");}));f.add(print,BorderLayout.SOUTH);f.setSize(620,740);f.setLocationRelativeTo(null);decorate(f);f.setVisible(true);
    }
    public static DefaultTableModel model(){return new DefaultTableModel(){public boolean isCellEditable(int r,int c){return false;}};}
    public static void setupTable(JTable table){
        table.setAutoCreateRowSorter(true);table.setRowHeight(28);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(Object.class,new DefaultTableCellRenderer(){
            public Component getTableCellRendererComponent(JTable t,Object value,boolean selected,boolean focus,int row,int column){
                Object shown=value;String name=t.getColumnName(column).toLowerCase(Locale.ROOT);
                if(value instanceof Timestamp stamp)shown=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(Validation.ZONE).format(stamp.toLocalDateTime().toInstant(ZoneOffset.UTC));
                else if(value instanceof java.sql.Date d)shown=DATE.format(d.toLocalDate());
                else if(value instanceof BigDecimal money)shown=name.contains("%")?money.toPlainString():Money.format(money);
                else if(value instanceof Boolean b)shown=b?"Có":"Không";
                else if(value instanceof String str){shown=WarrantyRules.label(str);if(str.equals("CASH"))shown="Tiền mặt";if(str.equals("TRANSFER")&&!name.contains("loại"))shown="Chuyển khoản";if(str.equals("MANAGER"))shown="Quản lý";if(str.equals("CASHIER"))shown="Thu ngân";if(str.equals("WAREHOUSE"))shown="Nhân viên kho";if(str.equals("RECEIVE"))shown="Nhập hàng";if(str.equals("SALE"))shown="Bán hàng";if(str.equals("OPENING"))shown="Tồn đầu kỳ";if(str.equals("TRANSFER")&&name.contains("loại"))shown="Chuyển kho";if(str.equals("IN_STOCK"))shown="Trong kho";if(str.equals("SOLD"))shown="Đã bán";}
                return super.getTableCellRendererComponent(t,shown,selected,focus,row,column);
            }
        });
    }
}
