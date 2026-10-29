package vn.dienmay.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import vn.dienmay.client.AppContext;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.api.Operation;
import vn.dienmay.common.model.Item;
import vn.dienmay.common.model.Money;

/** Retains catalog CRUD, optimistic revision and serial lookup while removing in-process services. */
@SuppressWarnings("serial") // View state is never Java-serialized.
public final class ProductsUI extends RowsFrame {
  private final JTextField search = new JTextField(24);
  private List<Item> products = List.of();

  public ProductsUI(AppContext app) {
    super(app, "Sản phẩm / tồn kho", "Tra cứu SKU, mã vạch và tên sản phẩm");
    actions.add(new JLabel("SKU / mã vạch / tên:"));
    actions.add(search);
    actions.add(Ui.button("Tìm / tải lại", this::reload));
    search.addActionListener(event -> reload());
    if (Operation.PRODUCT_SAVE.allows(app.session().role())) {
      actions.add(Ui.button("Thêm sản phẩm", () -> edit(null)));
      actions.add(Ui.button("Sửa dòng chọn", () -> edit(selected())));
    }
    actions.add(Ui.button("Serial / IMEI", this::serials));
    reload();
  }

  private Item selected() {
    long id = Json.number(rows.selected().get("Id")).longValueExact();
    return products.stream().filter(item -> item.id() == id).findFirst().orElseThrow();
  }

  private void reload() {
    app.request(this, Operation.PRODUCT_SEARCH, Data.map("text", search.getText()), result -> {
      products = Json.list(result, Item.class);
      List<Map<String, Object>> data = new ArrayList<>();
      for (Item item : products) {
        data.add(Data.map("Id", item.id(), "SKU", item.sku(), "Barcode", item.barcode(),
            "Name", item.name(), "Category", item.category(), "Brand", item.brand(),
            "SellingPrice", item.sellingPrice(), "WarehouseQuantity", item.warehouseQuantity(),
            "CounterQuantity", item.counterQuantity(), "WarrantyMonths", item.warrantyMonths(),
            "TrackSerial", item.trackSerial(), "Enabled", item.enabled()));
      }
      rows.setData(data);
      message(products.size() + " sản phẩm • Tồn kho được kiểm tra lại khi ghi giao dịch.");
    });
  }

  private void serials() {
    Item item = selected();
    String bearer = app.token();
    app.run(this, () -> {
      List<Map<String, Object>> data = new ArrayList<>();
      for (String location : List.of("KHO", "QUAY")) {
        Object result = app.gateway().call(Operation.SERIALS, bearer, Data.map("id", item.id(), "location", location));
        for (String serial : Json.list(result, String.class)) {
          data.add(Data.map("SKU", item.sku(), "Location", location, "Serial", serial));
        }
      }
      return data;
    }, result -> {
      RowsFrame frame = new RowsFrame(app, "Serial / " + item.sku(), "Các thiết bị hiện còn trong kho/quầy");
      frame.rows.setData(result);
      frame.setVisible(true);
    });
  }

  private void edit(Item item) {
    String bearer = app.token();
    app.run(this, () -> List.of(
        Json.list(app.gateway().call(Operation.LOOKUPS, bearer, Data.map("type", "category")), Ui.Choice.class),
        Json.list(app.gateway().call(Operation.LOOKUPS, bearer, Data.map("type", "brand")), Ui.Choice.class)),
        lists -> showEditor(item, lists.get(0), lists.get(1)));
  }

  private void showEditor(Item item, List<Ui.Choice> categories, List<Ui.Choice> brands) {
    if (categories.isEmpty() || brands.isEmpty()) {
      throw new IllegalArgumentException("Tạo loại sản phẩm và thương hiệu trong Danh mục trước.");
    }
    JTextField sku = Ui.text(item == null ? "" : item.sku());
    JTextField barcode = Ui.text(item == null ? "" : item.barcode());
    JTextField name = Ui.text(item == null ? "" : item.name());
    JTextField model = Ui.text(item == null ? "" : item.model());
    JTextArea description = new JTextArea(item == null ? "" : item.description(), 3, 24);
    description.setLineWrap(true);
    JTextField cost = Ui.text(item == null ? "0" : item.costPrice().toPlainString());
    JTextField price = Ui.text(item == null ? "0" : item.sellingPrice().toPlainString());
    JComboBox<Ui.Choice> category = new JComboBox<>(categories.toArray(Ui.Choice[]::new));
    JComboBox<Ui.Choice> brand = new JComboBox<>(brands.toArray(Ui.Choice[]::new));
    if (item != null) {
      categories.stream().filter(value -> value.id() ==
          item.categoryId()).findFirst().ifPresent(category::setSelectedItem);
      brands.stream().filter(value -> value.id() == item.brandId()).findFirst().ifPresent(brand::setSelectedItem);
    }
    JSpinner warranty = new JSpinner(new SpinnerNumberModel(item == null ? 0 : item.warrantyMonths(), 0, 120, 1));
    JSpinner reorder = new JSpinner(new SpinnerNumberModel(item == null ? 5 : item.reorderLevel(), 0, 100000, 1));
    JCheckBox serial = new JCheckBox("Quản lý từng thiết bị", item != null && item.trackSerial());
    JCheckBox enabled = new JCheckBox("Đang kinh doanh", item == null || item.enabled());
    new EditDialog(app, this, item == null ? "Thêm sản phẩm" : "Sửa sản phẩm",
        Ui.fields("SKU *", sku, "Mã vạch", barcode, "Tên *", name,
            "Loại *", category, "Thương hiệu *", brand, "Model", model,
            "Mô tả", Ui.scroll(description), "Giá nhập", cost, "Giá bán", price,
            "Bảo hành (tháng)", warranty, "Tồn tối thiểu", reorder,
            "Serial / IMEI", serial, "Trạng thái", enabled), Operation.PRODUCT_SAVE,
        () -> Data.map("item", new Item(item == null ? 0 : item.id(), sku.getText(), barcode.getText(),
            name.getText(), ((Ui.Choice) category.getSelectedItem()).id(), ((Ui.Choice) brand.getSelectedItem()).id(),
            "", "", model.getText(), description.getText(), Money.of(cost.getText()), Money.of(price.getText()),
            Ui.integer(warranty), serial.isSelected(), enabled.isSelected(), Ui.integer(reorder),
            item == null ? 0 : item.warehouseQuantity(), item == null ? 0 : item.counterQuantity(),
            item == null ? 0 : item.revision())), result -> reload()).setVisible(true);
  }
}
