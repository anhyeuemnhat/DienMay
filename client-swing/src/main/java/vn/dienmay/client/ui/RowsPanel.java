package vn.dienmay.client.ui;

import java.awt.BorderLayout;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.model.Money;
import vn.dienmay.common.model.Validation;
import vn.dienmay.common.model.WarrantyRules;

/** Scrollable read-only table with safe selection, local filtering, CSV export and printing. */
@SuppressWarnings("serial") // View state is never Java-serialized.
public final class RowsPanel extends JPanel {
  private final DefaultTableModel model = new DefaultTableModel() {
    @Override
    public boolean isCellEditable(int row, int column) {
      return false;
    }
  };
  private final JTable table = new JTable(model);
  private final JTextField filter = new JTextField(22);
  private final JLabel count = new JLabel("0 dòng");
  private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
  private List<Map<String, Object>> rows = List.of();
  private List<String> keys = List.of();
  private java.util.function.Supplier<String> context = () -> "UNKNOWN";
  public void dataContext(java.util.function.Supplier<String> source) { context = source; }

  public RowsPanel() {
    super(new BorderLayout(6, 6));
    table.setName("dataTable");
    table.setRowHeight(30);
    table.setFillsViewportHeight(true);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    table.setRowSorter(sorter);
    setOpaque(true);
    setBackground(java.awt.Color.WHITE);
    TableStyling.install(table, (column, value) -> display(keys.isEmpty() ? "" : keys.get(column), value));
    filter.getDocument().addDocumentListener(new DocumentListener() {
      private void update() {
        String query = filter.getText().toLowerCase(Locale.ROOT);
        sorter.setRowFilter(query.isBlank() ? null : new RowFilter<DefaultTableModel, Integer>() {
          @Override public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
            for (int column = 0; column < entry.getValueCount(); column++) {
              if (String.valueOf(display(keys.get(column), entry.getValue(column)))
                  .toLowerCase(Locale.ROOT).contains(query)) { return true; }
            }
            return false;
          }
        });
        updateCount();
      }

      @Override
      public void insertUpdate(DocumentEvent event) {
        update();
      }

      @Override
      public void removeUpdate(DocumentEvent event) {
        update();
      }

      @Override
      public void changedUpdate(DocumentEvent event) {
        update();
      }
    });
    add(Ui.toolbar(new JLabel("Lọc kết quả:"), filter, count,
        Ui.button("Xuất CSV", this::export), Ui.button("In bảng", this::print)), BorderLayout.NORTH);
    add(Ui.scroll(table), BorderLayout.CENTER);
  }

  /** Initial headers remain useful when an API query returns no rows. */
  public void columns(String... columns) {
    if (rows.isEmpty()) {
      keys = List.of(columns);
      model.setColumnIdentifiers(keys.stream().map(RowsPanel::label).toArray());
      TableStyling.fitColumns(table);
    }
  }

  public JTable table() {
    return table;
  }

  public void setRows(Object result) {
    if (!(result instanceof List<?> list)) {
      throw new IllegalArgumentException("API không trả về danh sách.");
    }
    setData(list.stream().map(Json::object).toList());
  }

  public void setData(List<Map<String, Object>> result) {
    Object selectedId = table.getSelectedRow() < 0 ? null : selected().get("Id");
    List<? extends javax.swing.RowSorter.SortKey> order = List.copyOf(sorter.getSortKeys());
    List<String> previousKeys = keys;
    int[] widths = java.util.stream.IntStream.range(0, table.getColumnCount())
        .map(i -> table.getColumnModel().getColumn(i).getWidth()).toArray();
    java.awt.Point scroll = table.getParent() instanceof javax.swing.JViewport viewport ? viewport.getViewPosition() :
        null;
    List<Map<String, Object>> snapshot = new ArrayList<>();
    for (Map<String, Object> row : result) {
      snapshot.add(java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(row)));
    }
    rows = List.copyOf(snapshot);
    keys = rows.isEmpty() ? keys : List.copyOf(rows.get(0).keySet());
    Object[] headers = keys.stream().map(RowsPanel::label).toArray();
    Object[][] values = new Object[rows.size()][keys.size()];
    for (int row = 0; row < rows.size(); row++) {
      for (int column = 0; column < keys.size(); column++) {
        values[row][column] = rows.get(row).get(keys.get(column));
      }
    }
    model.setDataVector(values, headers);
    TableStyling.fitColumns(table);
    for (int column = 0; column < keys.size(); column++) {
      sorter.setComparator(column, (left, right) -> {
        if (left instanceof Number a && right instanceof Number b) {
          return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()));
        }
        return String.valueOf(left).compareToIgnoreCase(String.valueOf(right));
      });
    }
    if (keys.equals(previousKeys)) {
      sorter.setSortKeys(order);
      for (int i = 0; i < widths.length; i++) { table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]); }
    }
    if (selectedId != null) {
      for (int i = 0; i < rows.size(); i++) {
        if (java.util.Objects.equals(selectedId, rows.get(i).get("Id"))) {
          int view = table.convertRowIndexToView(i);
          if (view >= 0) { table.setRowSelectionInterval(view, view); }
          break;
        }
      }
    }
    if (scroll != null) {
      javax.swing.SwingUtilities.invokeLater(() -> {
        if (table.getParent() instanceof javax.swing.JViewport viewport) { viewport.setViewPosition(scroll); }
      });
    }
    updateCount();
  }

  private void updateCount() {
    count.setText(table.getRowCount() + " / " + model.getRowCount() + " dòng");
  }

  public Map<String, Object> selected() {
    int selected = table.getSelectedRow();
    if (selected < 0) {
      throw new IllegalArgumentException("Chọn một dòng trước khi thao tác.");
    }
    return rows.get(table.convertRowIndexToModel(selected));
  }

  public static Object display(String key, Object value) {
    if (value == null) {
      return "";
    }
    if (value instanceof Boolean bool) {
      return bool ? "Có" : "Không";
    }
    if (value instanceof BigDecimal number) {
      String lower = key.toLowerCase(Locale.ROOT);
      if (lower.contains("price") || lower.contains("total") || lower.contains("revenue")
          || lower.equals("discount") || lower.equals("gross") || lower.equals("grosssales") ||
              lower.equals("unitcost")) {
        return Money.format(number);
      }
      return number.stripTrailingZeros().toPlainString();
    }
    if (value instanceof LocalDate date) { return Ui.DATE.format(date); }
    if (value instanceof Instant instant) {
      return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(Validation.ZONE).format(instant);
    }
    if (value instanceof String text) {
      boolean dateField = java.util.Set.of("StartDate", "EndDate", "SoldAt", "CreatedAt", "ChangedAt", "ReceivedAt",
          "CompletedAt", "VerifiedAt", "CheckedOn").contains(key);
      try {
        if (dateField && text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T.*Z")) {
          return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(Validation.ZONE)
              .format(Instant.parse(text));
        }
        if (dateField && text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
          return Ui.DATE.format(LocalDate.parse(text));
        }
      } catch (java.time.DateTimeException ignored) {
        // Non-date text is displayed verbatim, never silently reinterpreted.
      }
      if (!java.util.Set.of("Role", "PaymentMethod", "Kind", "Location", "Status", "NewStatus", "OldStatus",
          "Validity", "VerificationStatus", "BarcodeStatus", "BarcodeType").contains(key)) { return text; }
      return switch (text) {
        case "PENDING" -> "Chờ xác minh";
        case "VERIFIED" -> "Đã đối chiếu";
        case "INVALID" -> "Không hợp lệ";
        case "DEMO" -> "Mô phỏng";
        case "NONE" -> "Không có mã";
        case "OTHER" -> "Loại mã khác";
        case "KHO" -> "Kho";
        case "QUAY" -> "Quầy";
        case "MANAGER" -> "Quản lý";
        case "CASHIER" -> "Thu ngân";
        case "WAREHOUSE" -> "Nhân viên kho";
        case "CASH" -> "Tiền mặt";
        case "TRANSFER" -> key.equals("Kind") ? "Chuyển kho" : "Chuyển khoản";
        case "RECEIVE" -> "Nhập hàng";
        case "SALE" -> "Bán hàng";
        case "OPENING" -> "Tồn đầu kỳ";
        case "IN_STOCK" -> "Trong kho";
        case "SOLD" -> "Đã bán";
        case "ACTIVE" -> "Còn hạn";
        case "EXPIRED" -> "Hết hạn";
        default -> WarrantyRules.label(text);
      };
    }
    return value;
  }

  public static String label(String key) {
    return switch (key) {
      case "Category", "CategoryName" -> "Loại sản phẩm";
      case "Brand", "BrandName" -> "Thương hiệu";
      case "Barcode" -> "Mã vạch";
      case "IdentifierType" -> "Loại mã thiết bị";
      case "IdentifierSource" -> "Chứng từ nguồn";
      case "BarcodeType" -> "Loại mã vạch";
      case "BarcodeStatus" -> "Đối chiếu mã vạch";
      case "BarcodeSource" -> "Nguồn mã vạch";
      case "BarcodeNote" -> "Căn cứ mã vạch";
      case "Model" -> "Model chính hãng";
      case "Description", "Specifications" -> "Mô tả / thông số";
      case "TrackSerial" -> "Theo dõi Serial";
      case "Serial" -> "Serial / IMEI";
      case "VerificationStatus" -> "Đối chiếu danh tính";
      case "VerificationSource", "SourceUrl" -> "Nguồn đối chiếu";
      case "VerificationNote" -> "Căn cứ đối chiếu";
      case "VerifiedAt", "CheckedOn" -> "Ngày đối chiếu";
      case "IsDemo" -> "Dữ liệu DEMO";
      case "Revision" -> "Phiên bản";
      case "GrossSales" -> "Tiền hàng trước giảm";
      case "EntityType" -> "Nhóm dữ liệu";
      case "EntityId" -> "Mã bản ghi";
      case "Action" -> "Thao tác";
      case "OldValue" -> "Trước thay đổi";
      case "NewValue" -> "Sau thay đổi";
      case "Reason" -> "Lý do";
      case "Id", "id" -> "Mã";
      case "Name", "name", "ProductName" -> "Tên";
      case "FullName", "CustomerName" -> "Họ tên";
      case "Phone", "CustomerPhone" -> "Điện thoại";
      case "Address" -> "Địa chỉ";
      case "UserName" -> "Tài khoản";
      case "EmployeeName" -> "Nhân viên";
      case "Role" -> "Vai trò";
      case "Enabled" -> "Hoạt động";
      case "MustChange" -> "Cần đổi mật khẩu";
      case "Code" -> "Số / mã";
      case "InvoiceCode" -> "Hóa đơn";
      case "WarrantyCode" -> "Bảo hành";
      case "StartDate" -> "Từ ngày";
      case "EndDate" -> "Đến ngày";
      case "PercentOff" -> "Giảm %";
      case "CostPrice", "UnitCost" -> "Giá nhập";
      case "SellingPrice", "UnitPrice" -> "Đơn giá";
      case "WarehouseQuantity" -> "Tồn kho";
      case "CounterQuantity" -> "Tồn quầy";
      case "Quantity", "CoveredQuantity" -> "Số lượng";
      case "WarrantyMonths" -> "Tháng BH";
      case "Total", "NetRevenue" -> "Tổng tiền";
      case "Subtotal", "Gross" -> "Tiền hàng";
      case "Discount" -> "Giảm giá";
      case "PaymentMethod" -> "Thanh toán";
      case "SoldAt", "CreatedAt", "ChangedAt" -> "Thời gian";
      case "ReceivedAt" -> "Tiếp nhận";
      case "CompletedAt" -> "Hoàn tất";
      case "Status", "NewStatus" -> "Trạng thái";
      case "OldStatus" -> "Trước";
      case "Validity" -> "Hiệu lực";
      case "Fault" -> "Mô tả lỗi";
      case "Notes" -> "Ghi chú";
      case "Location" -> "Vị trí";
      case "Delta" -> "Biến động";
      case "Kind" -> "Loại";
      case "Reference" -> "Chứng từ / lý do";
      case "RequestId" -> "Mã yêu cầu";
      case "TotalStock" -> "Tổng tồn";
      case "ReorderLevel" -> "Mức cảnh báo";
      case "InvoiceCount" -> "Số hóa đơn";
      default -> key;
    };
  }

  public static String csvCell(Object value) {
    String text = value == null ? "" : value.toString();
    if (!text.isEmpty() && "=+-@\t\r\n".indexOf(text.charAt(0)) >= 0) {
      text = "'" + text;
    }
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }

  private void export() {
    JFileChooser chooser = new JFileChooser();
    chooser.setSelectedFile(new java.io.File("dienmay-" + context.get().toLowerCase(Locale.ROOT) + "-export.csv"));
    if (Ui.saveFile(this, chooser) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    Path file = chooser.getSelectedFile().toPath();
    if (Files.exists(file) && !Ui.confirm(this, "Tệp đã tồn tại. Ghi đè?")) {
      return;
    }
    StringBuilder csv = new StringBuilder("﻿");
    csv.append(csvCell("Nguồn dữ liệu")).append(",");
    csv.append(String.join(",", keys.stream().map(RowsPanel::label).map(RowsPanel::csvCell).toList()))
        .append("\r\n");
    for (int view = 0; view < table.getRowCount(); view++) {
      Map<String, Object> row = rows.get(table.convertRowIndexToModel(view));
      csv.append(csvCell(context.get())).append(",");
      csv.append(String.join(",", keys.stream().map(row::get).map(RowsPanel::csvCell).toList()))
          .append("\r\n");
    }
    try {
      Files.writeString(file, csv, StandardCharsets.UTF_8);
      Ui.message(this, "Đã xuất CSV: " + file);
    } catch (IOException ex) {
      Ui.error(this, ex);
    }
  }

  private void print() {
    try {
      table.print(JTable.PrintMode.FIT_WIDTH, new MessageFormat("Điện Máy Đỏ / " + context.get()), new
          MessageFormat("Trang {0}"));
    } catch (java.awt.print.PrinterException ex) {
      Ui.error(this, ex);
    }
  }
}
