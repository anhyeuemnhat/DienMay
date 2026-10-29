package vn.dienmay.client.ui;

import vn.dienmay.client.AppContext;

/** Role-specific entry screen backed by the shared functional navigation. */
@SuppressWarnings("serial") // View state is never Java-serialized.
public final class CashierMain extends DashboardFrame {
  public CashierMain(AppContext app) {
    super(app, "Bàn làm việc thu ngân");
  }
}
