package vn.dienmay.client.ui;

import vn.dienmay.client.AppContext;

/** Role-specific entry screen backed by the shared functional navigation. */
@SuppressWarnings("serial") // View state is never Java-serialized.
public final class StoreManagerMain extends DashboardFrame {
  public StoreManagerMain(AppContext app) {
    super(app, "Quản lý cửa hàng");
  }
}
