package vn.dienmay.client.controller;

import java.io.IOException;
import java.nio.file.Path;
import vn.dienmay.client.api.ApiGateway;
import vn.dienmay.client.state.PendingStore;
import vn.dienmay.common.api.ApiLimits;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.api.Operation;
import vn.dienmay.common.model.DeletionPreview;
import vn.dienmay.common.model.DeletionRequest;
import vn.dienmay.common.model.GuardedRequest;
import vn.dienmay.common.model.ReversalRequest;

/** Exclusive durable state for delete/reversal; no hidden retry, and unknown replies never clear intent. */
public final class GuardedActionController<T extends GuardedRequest> implements AutoCloseable {
  private final ApiGateway gateway;
  private final PendingStore store;
  private final Operation execute;
  private final Operation resolve;
  private final Operation cancel;
  private volatile T pending;
  public GuardedActionController(ApiGateway gateway, Path directory, long employee, Class<T> type) throws IOException {
    this.gateway = gateway;
    if (type != DeletionRequest.class && type != ReversalRequest.class) {
      throw new IllegalArgumentException("Unsupported administrative request type.");
    }
    boolean deleting = type == DeletionRequest.class;
    execute = deleting ? Operation.DELETE_RECORD : Operation.REVERSE_RECEIPT;
    resolve = deleting ? Operation.DELETE_RESOLVE : Operation.REVERSAL_RESOLVE;
    cancel = deleting ? Operation.DELETE_CANCEL : Operation.REVERSAL_CANCEL;
    store = new PendingStore(directory, employee, deleting ? "delete" : "reversal");
    try { pending = store.guarded(type); } catch (IOException ex) { store.close(); throw ex; }
  }
  public T pending() { return pending; }
  public synchronized Object submit(String bearer, T request) throws Exception {
    if (pending != null && (!pending.id().equals(request.id()) || !pending.fingerprint().equals(request.fingerprint())))
        {
      throw new IllegalStateException("Con yeu cau cho; khong tao yeu cau khac.");
    }
    ApiLimits.encodeRequest(Data.map("request", request));
    store.save(request);
    pending = request;
    Object result = gateway.call(execute, bearer, Data.map("request", request));
    if (execute == Operation.DELETE_RECORD) {
      DeletionPreview deleted;
      try { deleted = Json.convert(result, DeletionPreview.class); }
      catch (RuntimeException ex) { throw new IOException("Invalid delete response; reconcile request.", ex); }
      DeletionRequest intent = (DeletionRequest) request;
      if (deleted == null || !deleted.allowed() || deleted.entityId() != intent.entityId()
          || !deleted.type().equals(intent.type()) || deleted.revision() != intent.revision()) {
        throw new IOException("Unknown deletion result; reconcile request.");
      }
    } else {
      try {
        if (Json.number(result).longValueExact() <= 0) { throw new IllegalArgumentException("Missing movement."); }
      } catch (RuntimeException ex) { throw new IOException("Invalid reversal response; reconcile request.", ex); }
    }
    store.clear();
    pending = null;
    return result;
  }
  public synchronized boolean resolve(String bearer) throws Exception { return finish(bearer, resolve); }
  public synchronized boolean cancel(String bearer) throws Exception { return finish(bearer, cancel); }
  private boolean finish(String bearer, Operation operation) throws Exception {
    if (pending == null) { throw new IllegalStateException("No pending administrative request."); }
    Object result = gateway.call(operation, bearer, Data.map("request", pending));
    if (!(result instanceof Boolean done)) { throw new IOException("Invalid recovery response; request retained."); }
    if (done) { store.clear(); pending = null; }
    return done;
  }
  @Override public synchronized void close() throws IOException { store.close(); }
}
