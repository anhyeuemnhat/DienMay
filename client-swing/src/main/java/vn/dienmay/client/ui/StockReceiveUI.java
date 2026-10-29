package vn.dienmay.client.ui;

import vn.dienmay.client.AppContext;

/** Concrete stock work screen sharing validation, layout and durable request handling. */
@SuppressWarnings("serial") // View state is never Java-serialized.
public final class StockReceiveUI extends StockFrame {
  public StockReceiveUI(AppContext app) {
    super(app, true);
  }
}
