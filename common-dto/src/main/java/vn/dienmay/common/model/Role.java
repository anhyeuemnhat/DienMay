package vn.dienmay.common.model;

/** Roles retain the baseline database values; labels are presentation only. */
public enum Role {
  MANAGER("Quản lý"),
  CASHIER("Thu ngân"),
  WAREHOUSE("Nhân viên kho");

  private final String label;

  Role(String label) {
    this.label = label;
  }

  @Override
  public String toString() {
    return label;
  }
}
