package vn.dienmay.common.api;

/** Transport-safe failure, without SqlDatabase statements, credentials or server stack traces. */
public final class ApiException extends Exception {
  private static final long serialVersionUID = 1L;
  private final int status;
  private final String code;
  private final String correlationId;

  public ApiException(int status, String code, String message, String correlationId) {
    super(message);
    this.status = status;
    this.code = code;
    this.correlationId = correlationId;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  public String correlationId() {
    return correlationId;
  }
}
