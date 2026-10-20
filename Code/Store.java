package Code;
import Service.*;
import ImplementationDB.SQL;
/** Store facade retained from the original design; no cached master data or paid-sale ArrayList. */
public final class Store {
    private final SalesService sales;
    private final CatalogService catalog;
    public Store(SQL db,AuthService auth){sales=new SalesService(db,auth);catalog=new CatalogService(db,auth);}
    public SalesService sales(){return sales;}
    public CatalogService catalog(){return catalog;}
}
