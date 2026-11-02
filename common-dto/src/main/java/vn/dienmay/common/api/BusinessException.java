package vn.dienmay.common.api;

/** Explicit, transport-safe business rejection; no SQL text or secrets in the message. */
public final class BusinessException extends IllegalArgumentException {
  private static final long serialVersionUID = 1L;
  private final int status;
  private final String code;
  public BusinessException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }
  public int status() { return status; }
  public String code() { return code; }
}
