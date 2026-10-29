package vn.dienmay.client.api;

import java.util.Map;
import vn.dienmay.common.api.Operation;

/** Injectable desktop port; contains no persistence credentials or server-side services. */
public interface ApiGateway {
  Object call(Operation operation, String bearer, Map<String, Object> parameters) throws Exception;

  String profile();
}
