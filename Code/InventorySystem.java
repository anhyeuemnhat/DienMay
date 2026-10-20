package Code;
import Service.*;
import ImplementationDB.SQL;
/** Dependency graph only. Opening a Swing window never preloads the database. */
public final class InventorySystem {
    public final SQL db;
    public final AuthService auth;
    public final PeopleService people;
    public final PromotionService promotions;
    public final WarrantyService warranties;
    public final ReportService reports;
    private final Store store;
    private final Warehouse warehouse;
    public InventorySystem(){this(new SQL());}
    public InventorySystem(SQL db){this.db=db;auth=new AuthService(db);store=new Store(db,auth);warehouse=new Warehouse(db,auth);people=new PeopleService(db,auth);promotions=new PromotionService(db,auth);warranties=new WarrantyService(db,auth);reports=new ReportService(db,auth);}
    public Store getStore(){return store;}
    public Warehouse getWarehouse(){return warehouse;}
}
