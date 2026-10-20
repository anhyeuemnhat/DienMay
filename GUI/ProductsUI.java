package GUI;
import Code.*;import Service.*;
import javax.swing.*;import java.util.*;import java.util.List;
public final class ProductsUI extends RowsFrame {
    private List<Item> products=List.of();
    private record Options(List<CatalogService.Lookup> categories,List<CatalogService.Lookup> brands){}
    public ProductsUI(){
        super("Sản phẩm điện máy / tồn kho");App.guard(this,Role.values());findAction(this::refresh);
        if(App.session().role()==Role.MANAGER){toolbar.add(Ui.button("Thêm sản phẩm",()->edit(null)));toolbar.add(Ui.button("Sửa / ngừng kinh doanh",()->edit(chosen())));}
        toolbar.add(Ui.button("Chi tiết / Serial",this::details));refresh();
    }
    private Item chosen(){long id=selectedId();return products.stream().filter(p->p.id()==id).findFirst().orElseThrow();}
    private void refresh(){String text=search.getText();Ui.db(this,s->App.system().getStore().catalog().search(s,text),values->{products=values;List<LinkedHashMap<String,Object>> data=new ArrayList<>();for(Item i:values){LinkedHashMap<String,Object> row=new LinkedHashMap<>();row.put("Mã",i.id());row.put("SKU",i.sku());row.put("Mã vạch",i.barcode());row.put("Tên",i.name());row.put("Danh mục",i.category());row.put("Thương hiệu",i.brand());row.put("Giá bán",i.sellingPrice());row.put("Kho",i.warehouseQuantity());row.put("Quầy",i.counterQuantity());row.put("BH tháng",i.warrantyMonths());row.put("Theo Serial",i.trackSerial());row.put("Kinh doanh",i.enabled());data.add(row);}rows(data);});}
    private void details(){Item i=chosen();Ui.db(this,s->App.system().getStore().catalog().serials(s,i.id(),"KHO"),serials->{String info=i.name()+"\nSKU: "+i.sku()+" | Barcode: "+i.barcode()+"\nModel: "+i.model()+"\nGiá nhập gần nhất: "+Money.format(i.costPrice())+"\n"+i.description()+"\nSerial tại kho: "+String.join(", ",serials);Ui.db(this,s->App.system().getStore().catalog().serials(s,i.id(),"QUAY"),counter->Ui.message(this,info+"\nSerial tại quầy: "+String.join(", ",counter)));});}
    private void edit(Item current){Ui.db(this,s->new Options(App.system().getStore().catalog().lookups(s,"category"),App.system().getStore().catalog().lookups(s,"brand")),options->{
        if(options.categories().isEmpty()||options.brands().isEmpty()){Ui.message(this,"Tạo danh mục và thương hiệu trong menu quản lý trước.");return;}
        JTextField sku=Ui.text(current==null?"":current.sku()),barcode=Ui.text(current==null?"":current.barcode()),name=Ui.text(current==null?"":current.name()),model=Ui.text(current==null?"":current.model()),cost=Ui.text(current==null?"0":current.costPrice().toPlainString()),price=Ui.text(current==null?"0":current.sellingPrice().toPlainString());
        JTextArea description=new JTextArea(current==null?"":current.description(),3,24);description.setLineWrap(true);description.setWrapStyleWord(true);
        JComboBox<CatalogService.Lookup> category=new JComboBox<>(options.categories().toArray(CatalogService.Lookup[]::new)),brand=new JComboBox<>(options.brands().toArray(CatalogService.Lookup[]::new));
        if(current!=null){options.categories().stream().filter(x->x.id()==current.categoryId()).findFirst().ifPresent(category::setSelectedItem);options.brands().stream().filter(x->x.id()==current.brandId()).findFirst().ifPresent(brand::setSelectedItem);}
        JSpinner warranty=new JSpinner(new SpinnerNumberModel(current==null?12:current.warrantyMonths(),0,120,1)),low=new JSpinner(new SpinnerNumberModel(current==null?5:current.reorderLevel(),0,100000,1));
        JCheckBox serial=new JCheckBox("Theo dõi từng thiết bị",current!=null&&current.trackSerial()),enabled=new JCheckBox("Đang kinh doanh",current==null||current.enabled());
        if(!Ui.edit(this,current==null?"Thêm sản phẩm":"Sửa sản phẩm — không xóa lịch sử",Ui.fields("SKU",sku,"Mã vạch (giữ số 0 đầu)",barcode,"Tên",name,"Danh mục",category,"Thương hiệu",brand,"Model",model,"Mô tả",new JScrollPane(description),"Giá nhập (VND)",cost,"Giá bán (VND)",price,"Bảo hành (tháng)",warranty,"Cảnh báo tồn ≤",low,"Serial / IMEI",serial,"Trạng thái",enabled)))return;
        CatalogService.Lookup c=(CatalogService.Lookup)category.getSelectedItem(),b=(CatalogService.Lookup)brand.getSelectedItem();
        Item item=new Item(current==null?0:current.id(),sku.getText(),barcode.getText(),name.getText(),c.id(),b.id(),c.name(),b.name(),model.getText(),description.getText(),Money.of(cost.getText()),Money.of(price.getText()),(Integer)warranty.getValue(),serial.isSelected(),enabled.isSelected(),(Integer)low.getValue(),0,0,current==null?0:current.revision());
        Ui.db(this,s->App.system().getStore().catalog().save(s,item),id->{Ui.message(this,"Đã lưu sản phẩm "+id);refresh();});
    });}
}
