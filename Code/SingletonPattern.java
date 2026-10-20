package Code;
/** Legacy access point retained; initialization is explicitly warmed off the EDT by App. */
public final class SingletonPattern {
    private static final class Holder { static final SingletonPattern INSTANCE=new SingletonPattern(); }
    private final InventorySystem system=new InventorySystem();
    private SingletonPattern(){}
    public static SingletonPattern getInstance(){return Holder.INSTANCE;}
    public InventorySystem getSystem(){return system;}
}
