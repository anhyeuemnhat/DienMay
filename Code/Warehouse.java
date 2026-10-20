package Code;
import Service.*;
import ImplementationDB.SQL;
public final class Warehouse {
    private final InventoryService inventory;
    public Warehouse(SQL db,AuthService auth){inventory=new InventoryService(db,auth);}
    public InventoryService inventory(){return inventory;}
}
