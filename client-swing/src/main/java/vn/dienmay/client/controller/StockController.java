package vn.dienmay.client.controller;

import java.io.IOException;
import java.nio.file.Path;
import vn.dienmay.client.api.ApiGateway;
import vn.dienmay.client.state.PendingStore;
import vn.dienmay.common.api.ApiLimits;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Operation;
import vn.dienmay.common.model.InventoryRequest;

/** Receive/transfer workflow shared by both stock screens, with durable and exclusive pending state. */
public final class StockController implements AutoCloseable {
  private final ApiGateway gateway;
  private final PendingStore store;
  private final Operation operation;
  private volatile InventoryRequest pending;

  public StockController(ApiGateway gateway, Path directory, long employee, boolean receiving)
      throws IOException {
    this.gateway = gateway;
    operation = receiving ? Operation.RECEIVE : Operation.TRANSFER;
    store = new PendingStore(directory, employee, receiving ? "receive" : "transfer");
    try {
      pending = store.inventory();
    } catch (IOException ex) {
      store.close();
      throw ex;
    }
  }

  public InventoryRequest pending() {
    return pending;
  }

  public synchronized void submit(String bearer, InventoryRequest request) throws Exception {
    if (pending != null && (!pending.id().equals(request.id())
        || !pending.fingerprint().equals(request.fingerprint()))) {
      throw new IllegalStateException("Con yeu cau cho; khong tao chung tu moi.");
    }
    if ((operation == Operation.RECEIVE) != (request.from() == null)) {
      throw new IllegalArgumentException("Sai loai chung tu kho.");
    }
    ApiLimits.encodeRequest(Data.map("request", request));
    store.save(request);
    pending = request;
    Object result = gateway.call(operation, bearer, Data.map("request", request));
    if (result != null) {
      throw new IOException("Phan hoi ghi kho khong hop le. Giu yeu cau va doi soat.");
    }
    store.clear();
    pending = null;
  }

  public synchronized void retry(String bearer) throws Exception {
    submit(bearer, requirePending());
  }

  public synchronized boolean resolve(String bearer) throws Exception {
    Object result = gateway.call(Operation.STOCK_RESOLVE, bearer, Data.map("request", requirePending()));
    if (!(result instanceof Boolean committed)) {
      throw new IOException("Phan hoi doi soat khong hop le. Giu yeu cau cho.");
    }
    if (committed) {
      store.clear();
      pending = null;
    }
    return committed;
  }

  public synchronized boolean cancel(String bearer) throws Exception {
    Object result = gateway.call(Operation.STOCK_CANCEL, bearer, Data.map("request", requirePending()));
    if (!(result instanceof Boolean cancelled)) {
      throw new IOException("Phan hoi huy khong hop le. Giu yeu cau cho.");
    }
    if (cancelled) {
      store.clear();
      pending = null;
    }
    return cancelled;
  }

  private InventoryRequest requirePending() {
    if (pending == null) {
      throw new IllegalStateException("Khong co yeu cau kho cho.");
    }
    return pending;
  }

  @Override
  public synchronized void close() throws IOException {
    store.close();
  }
}
