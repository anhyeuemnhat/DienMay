package vn.dienmay.server.api;

import java.util.Map;
import vn.dienmay.common.api.Operation;

/** Transport/application boundary. Production uses ApplicationApi; tests inject a bounded fixture. */
@FunctionalInterface
public interface ApiHandler {
  Object execute(Operation operation, String bearer, Map<String, Object> input) throws Exception;
}
