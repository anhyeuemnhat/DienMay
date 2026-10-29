package vn.dienmay.client.controller;

import java.io.IOException;
import java.nio.file.Path;
import vn.dienmay.client.api.ApiGateway;
import vn.dienmay.client.state.PendingStore;
import vn.dienmay.common.api.ApiLimits;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.api.Operation;
import vn.dienmay.common.model.Sale;
import vn.dienmay.common.model.SaleRequest;

/** Persists before sending and clears only after a confirmed commit or a durable server cancellation. */
public final class SaleController implements AutoCloseable {
  private final ApiGateway gateway;
  private final PendingStore store;
  private volatile SaleRequest pending;

  public SaleController(ApiGateway gateway, Path directory, long employee) throws IOException {
    this.gateway = gateway;
    store = new PendingStore(directory, employee, "sale");
    try {
      pending = store.sale();
    } catch (IOException ex) {
      store.close();
      throw ex;
    }
  }

  public SaleRequest pending() {
    return pending;
  }

  public synchronized Sale submit(String bearer, SaleRequest request) throws Exception {
    if (pending != null && (!pending.requestId().equals(request.requestId())
        || !pending.fingerprint().equals(request.fingerprint()))) {
      throw new IllegalStateException("Con yeu cau cho; doi soat truoc khi tao giao dich moi.");
    }
    ApiLimits.encodeRequest(Data.map("request", request));
    store.save(request);
    pending = request;
    Sale result = Json.convert(gateway.call(Operation.CHECKOUT, bearer,
        Data.map("request", request)), Sale.class);
    if (result == null) {
      throw new IOException("Backend khong tra ve hoa don. Giu yeu cau va doi soat.");
    }
    store.clear();
    pending = null;
    return result;
  }

  public synchronized Sale retry(String bearer) throws Exception {
    return submit(bearer, requirePending());
  }

  public synchronized Sale resolve(String bearer) throws Exception {
    Sale result = Json.convert(gateway.call(Operation.SALE_RESOLVE, bearer,
        Data.map("request", requirePending())), Sale.class);
    if (result != null) {
      store.clear();
      pending = null;
    }
    return result;
  }

  public synchronized boolean cancel(String bearer) throws Exception {
    Object result = gateway.call(Operation.SALE_CANCEL, bearer, Data.map("request", requirePending()));
    if (!(result instanceof Boolean cancelled)) {
      throw new IOException("Phan hoi huy khong hop le. Giu yeu cau cho.");
    }
    if (cancelled) {
      store.clear();
      pending = null;
    }
    return cancelled;
  }

  private SaleRequest requirePending() {
    if (pending == null) {
      throw new IllegalStateException("Khong co yeu cau cho.");
    }
    return pending;
  }

  @Override
  public synchronized void close() throws IOException {
    store.close();
  }
}
