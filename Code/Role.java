package Code;
public enum Role {
    MANAGER("Quản lý"), CASHIER("Thu ngân"), WAREHOUSE("Nhân viên kho");
    private final String label; Role(String label){this.label=label;} public String toString(){return label;}
}
