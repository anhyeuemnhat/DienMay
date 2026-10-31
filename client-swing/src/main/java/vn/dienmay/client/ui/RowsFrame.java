package vn.dienmay.client.ui;

import java.awt.BorderLayout;
import java.util.Map;
import javax.swing.JPanel;
import vn.dienmay.client.AppContext;
import vn.dienmay.common.api.Operation;

/** Shared work screen; the data table expands in both dimensions instead of using absolute bounds. */
@SuppressWarnings("serial") // View state is never Java-serialized.
public class RowsFrame extends WorkFrame {
  protected final RowsPanel rows = new RowsPanel();
  protected final JPanel actions = Ui.toolbar();

  protected RowsFrame(AppContext app, String title, String description) {
    super(app, title, description);
    rows.dataContext(app::environmentMode);
    body.add(actions, BorderLayout.NORTH);
    body.add(rows, BorderLayout.CENTER);
  }

  protected void load(Operation operation, Map<String, Object> parameters) {
    message("Đang tải dữ liệu từ API...");
    app.request(this, operation, parameters, result -> {
      rows.setRows(result);
      message("Đã tải. Bảng có giới hạn 500/1.000 dòng theo API; thu hẹp từ khóa/khoảng ngày khi cần.");
    });
  }
}
