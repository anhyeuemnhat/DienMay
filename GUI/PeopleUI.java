package GUI;
import Code.*;import Service.*;
import javax.swing.*;import java.time.*;import java.math.BigDecimal;import java.util.*;import java.util.List;
public final class PeopleUI {
    private PeopleUI(){}
    private static String val(Map<String,Object> row,String key){return row==null?"":Objects.toString(row.get(key),"");}
    public static void customers(){
        RowsFrame f=new RowsFrame("Khách hàng");App.guard(f,Role.MANAGER,Role.CASHIER);
        Runnable refresh=()->{String text=f.search.getText();Ui.db(f,s->App.system().people.customers(s,text),f::rows);};f.findAction(refresh);
        f.toolbar.add(Ui.button("Thêm",()->customerEdit(f,null,refresh)));f.toolbar.add(Ui.button("Sửa",()->customerEdit(f,f.selected(),refresh)));f.setVisible(true);refresh.run();
    }
    private static void customerEdit(RowsFrame f,Map<String,Object> row,Runnable refresh){
        JTextField name=Ui.text(val(row,"Họ tên")),phone=Ui.text(val(row,"Điện thoại")),address=Ui.text(val(row,"Địa chỉ"));
        if(!Ui.edit(f,"Thông tin khách hàng",Ui.fields("Họ tên",name,"Điện thoại",phone,"Địa chỉ",address)))return;
        Long id=row==null?null:((Number)row.get("Mã")).longValue();String n=name.getText(),p=phone.getText(),a=address.getText();Ui.db(f,s->App.system().people.saveCustomer(s,id,n,p,a),saved->{Ui.message(f,"Mã khách hàng: "+saved+". Dùng mã này ở POS; để trống cho khách lẻ.");refresh.run();});
    }
    public static void vendors(){
        RowsFrame f=new RowsFrame("Nhà cung cấp");App.guard(f,Role.MANAGER,Role.WAREHOUSE);f.search.setVisible(false);
        Runnable refresh=()->Ui.db(f,s->App.system().people.vendors(s),f::rows);f.findAction(refresh);
        if(App.session().role()==Role.MANAGER){f.toolbar.add(Ui.button("Thêm",()->vendorEdit(f,null,refresh)));f.toolbar.add(Ui.button("Sửa",()->vendorEdit(f,f.selected(),refresh)));}f.setVisible(true);refresh.run();
    }
    private static void vendorEdit(RowsFrame f,Map<String,Object> row,Runnable refresh){
        JTextField name=Ui.text(val(row,"Nhà cung cấp")),phone=Ui.text(val(row,"Điện thoại")),address=Ui.text(val(row,"Địa chỉ"));JCheckBox enabled=new JCheckBox("Hoạt động",row==null||Boolean.TRUE.equals(row.get("Hoạt động")));
        if(!Ui.edit(f,"Nhà cung cấp",Ui.fields("Tên",name,"Điện thoại",phone,"Địa chỉ",address,"Trạng thái",enabled)))return;
        Long id=row==null?null:((Number)row.get("Mã")).longValue();String n=name.getText(),p=phone.getText(),a=address.getText();boolean on=enabled.isSelected();Ui.db(f,s->App.system().people.saveVendor(s,id,n,p,a,on),saved->{Ui.message(f,"Đã lưu nhà cung cấp "+saved);refresh.run();});
    }
    public static void employees(){
        RowsFrame f=new RowsFrame("Nhân viên / phân quyền / khóa tài khoản");App.guard(f,Role.MANAGER);f.search.setVisible(false);
        Runnable refresh=()->Ui.db(f,s->App.system().people.employees(s),f::rows);f.findAction(refresh);f.toolbar.add(Ui.button("Thêm",()->employeeEdit(f,null,refresh)));f.toolbar.add(Ui.button("Sửa / khóa / đặt lại mật khẩu",()->employeeEdit(f,f.selected(),refresh)));f.setVisible(true);refresh.run();
    }
    private static void employeeEdit(RowsFrame f,Map<String,Object> row,Runnable refresh){
        JTextField username=Ui.text(val(row,"Tài khoản")),name=Ui.text(val(row,"Họ tên")),phone=Ui.text(val(row,"Điện thoại"));JPasswordField password=new JPasswordField(24);
        JComboBox<Role> role=new JComboBox<>(Role.values());if(row!=null)role.setSelectedItem(Role.valueOf(val(row,"Vai trò")));
        JCheckBox enabled=new JCheckBox("Được đăng nhập",row==null||Boolean.TRUE.equals(row.get("Hoạt động")));
        if(!Ui.edit(f,"Tài khoản — mật khẩu mới yêu cầu đổi ở lần đăng nhập sau",Ui.fields("Tên đăng nhập",username,"Họ tên",name,"Điện thoại",phone,"Vai trò",role,"Trạng thái",enabled,"Mật khẩu mới (trống: giữ nguyên)",password)))return;
        if(!Ui.confirm(f,"Xác nhận thay đổi tài khoản / quyền truy cập?"))return;
        Long id=row==null?null:((Number)row.get("Mã")).longValue();String u=username.getText(),n=name.getText(),p=phone.getText();Role r=(Role)role.getSelectedItem();boolean on=enabled.isSelected();char[] secret=password.getPassword();
        Ui.db(f,s->{try{return App.system().people.saveEmployee(s,id,u,n,p,r,on,secret);}finally{Arrays.fill(secret,'\0');}},saved->{Ui.message(f,"Đã lưu nhân viên "+saved+". Phiên đăng nhập cũ của tài khoản sửa sẽ hết hiệu lực.");refresh.run();});
    }
    public static void lookups(String type){
        RowsFrame f=new RowsFrame(type.equals("category")?"Danh mục điện máy":"Thương hiệu");App.guard(f,Role.MANAGER);f.search.setVisible(false);
        Runnable refresh=()->Ui.db(f,s->App.system().getStore().catalog().lookups(s,type),values->{List<LinkedHashMap<String,Object>> rows=new ArrayList<>();for(CatalogService.Lookup v:values){LinkedHashMap<String,Object> row=new LinkedHashMap<>();row.put("Mã",v.id());row.put("Tên",v.name());rows.add(row);}f.rows(rows);});f.findAction(refresh);
        f.toolbar.add(Ui.button("Thêm",()->lookupEdit(f,type,null,refresh)));f.toolbar.add(Ui.button("Đổi tên",()->lookupEdit(f,type,f.selected(),refresh)));f.setVisible(true);refresh.run();
    }
    private static void lookupEdit(RowsFrame f,String type,Map<String,Object> row,Runnable refresh){String name=JOptionPane.showInputDialog(f,"Tên",val(row,"Tên"));if(name==null)return;Long id=row==null?null:((Number)row.get("Mã")).longValue();Ui.db(f,s->App.system().getStore().catalog().saveLookup(s,type,id,name),saved->{Ui.message(f,"Đã lưu.");refresh.run();});}
    public static void promotions(){
        RowsFrame f=new RowsFrame("Khuyến mãi cơ bản");App.guard(f,Role.MANAGER);f.search.setVisible(false);
        Runnable refresh=()->Ui.db(f,s->App.system().promotions.list(s),f::rows);f.findAction(refresh);f.toolbar.add(Ui.button("Thêm",()->promotionEdit(f,null,refresh)));f.toolbar.add(Ui.button("Sửa / ngừng áp dụng",()->promotionEdit(f,f.selected(),refresh)));f.setVisible(true);refresh.run();
    }
    private static void promotionEdit(RowsFrame f,Map<String,Object> row,Runnable refresh){
        LocalDate today=LocalDate.now(Validation.ZONE);JTextField code=Ui.text(val(row,"Mã khuyến mãi")),name=Ui.text(val(row,"Tên")),percent=Ui.text(row==null?"5":val(row,"Giảm %")),from=Ui.text(row==null?Ui.DATE.format(today):Ui.DATE.format(((java.sql.Date)row.get("Từ ngày")).toLocalDate())),to=Ui.text(row==null?Ui.DATE.format(today.plusMonths(1)):Ui.DATE.format(((java.sql.Date)row.get("Đến ngày")).toLocalDate()));
        JCheckBox enabled=new JCheckBox("Áp dụng",row==null||Boolean.TRUE.equals(row.get("Hoạt động")));
        if(!Ui.edit(f,"Khuyến mãi",Ui.fields("Mã",code,"Tên",name,"Bắt đầu dd/MM/yyyy",from,"Kết thúc dd/MM/yyyy",to,"Giảm %",percent,"Trạng thái",enabled)))return;
        Long id=row==null?null:((Number)row.get("Mã")).longValue();String c=code.getText(),n=name.getText();LocalDate a=Ui.date(from),b=Ui.date(to);BigDecimal rate=Money.of(percent.getText());boolean on=enabled.isSelected();Ui.db(f,s->App.system().promotions.save(s,id,c,n,a,b,rate,on),saved->{Ui.message(f,"Đã lưu khuyến mãi.");refresh.run();});
    }
}
