package tests;
import Code.*;import Service.*;import ImplementationDB.*;import GUI.Ui;
import java.lang.reflect.*;import java.math.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.sql.*;import java.time.*;import java.util.*;import java.util.concurrent.atomic.AtomicInteger;import java.util.regex.*;
import static tests.TestKit.*;
/** OFFLINE tests: pure logic, file recovery, and JDBC boundary behavior with explicit test doubles.
 * These tests do NOT assert that SQL Server queries/locking or physical peripherals work. */
public final class UnitTests {
    public static Item item(long id,String barcode,boolean serial,int counter){return new Item(id,"SKU-"+id,barcode,"Điện thoại mẫu",1,1,"Điện thoại","Hãng mẫu","M1","Mô tả",Money.of("60"),Money.of("100.25"),12,serial,true,5,0,counter,1);}
    private static SaleRequest request(UUID id,List<ItemQuantity> lines){return new SaleRequest(id,null,"CASH",Money.ZERO,"",Money.ZERO,lines);}
    private static ItemQuantity line(long id,int qty,String...serials){return new ItemQuantity(id,"SKU-"+id,"Tên sản phẩm",Money.of("100.25"),qty,List.of(serials));}
    public static void main(String[] args)throws Exception {
        TestKit t=new TestKit();
        t.test("money: decimal addition is exact",()->equal(new BigDecimal("0.30"),Money.of("0.10").add(Money.of("0.20"))));
        t.test("money: scale normalized to 2",()->equal(new BigDecimal("100.00"),Money.of("100")));
        t.test("money: reject negatives",()->throwsType(IllegalArgumentException.class,()->Money.of("-1")));
        t.test("money: reject excess precision",()->throwsType(IllegalArgumentException.class,()->Money.of("1.001")));
        t.test("money: reject malformed input",()->throwsType(IllegalArgumentException.class,()->Money.of("1,000")));
        t.test("money: DECIMAL(19,2) boundary",()->{equal(new BigDecimal("99999999999999999.99"),Money.of("99999999999999999.99"));throwsType(IllegalArgumentException.class,()->Money.of("100000000000000000"));});
        t.test("money: discount rounds HALF_UP once",()->equal(new BigDecimal("0.01"),Money.discount(Money.of("0.05"),Money.of("10"))));
        t.test("money: discount cannot exceed subtotal",()->throwsType(IllegalArgumentException.class,()->Money.total(Money.of("10"),Money.of("10.01"))));
        t.test("money: percent boundaries",()->{equal(Money.of("100"),Money.discount(Money.of("100"),Money.of("100")));throwsType(IllegalArgumentException.class,()->Money.discount(Money.of("100"),Money.of("100.01")));});
        t.test("money: Vietnamese format retains cents",()->check(Money.format(Money.of("1234.56")).contains("1.234,56"),"Unexpected vi-VN format"));
        t.test("cart: barcode stays a string including leading zero",()->equal("000123",item(1,"000123",false,10).barcode()));
        t.test("cart: two scans merge into quantity 2",()->{Cart c=new Cart();c.add(item(1,"000123",false,10),1,List.of());c.add(item(1,"000123",false,10),1,List.of());equal(1,c.lines().size());equal(2,c.lines().get(0).quantity());equal(Money.of("200.50"),c.subtotal());});
        t.test("cart: overstock is rejected without mutating draft",()->{Cart c=new Cart();c.add(item(1,"0",false,1),1,List.of());throwsType(IllegalArgumentException.class,()->c.add(item(1,"0",false,1),1,List.of()));equal(1,c.lines().get(0).quantity());});
        t.test("cart: rejects zero quantity",()->throwsType(IllegalArgumentException.class,()->new Cart().add(item(1,"0",false,1),0,List.of())));
        t.test("cart: rejects negative quantity",()->throwsType(IllegalArgumentException.class,()->line(1,-1)));
        t.test("cart: serial count must equal quantity",()->throwsType(IllegalArgumentException.class,()->new Cart().add(item(1,"0",true,2),2,List.of("s1"))));
        t.test("cart: duplicate serial rejected after normalization",()->throwsType(IllegalArgumentException.class,()->line(1,2,"aBc","ABC")));
        t.test("cart: serial products keep distinct devices in one line",()->{Cart c=new Cart();c.add(item(1,"0",true,2),1,List.of("s1"));c.add(item(1,"0",true,2),1,List.of("s2"));equal(List.of("S1","S2"),c.lines().get(0).serials());});
        t.test("cart: ordinary product cannot carry serials",()->throwsType(IllegalArgumentException.class,()->new Cart().add(item(1,"0",false,2),1,List.of("s1"))));
        t.test("cart: clear/removal recomputes total",()->{Cart c=new Cart();c.add(item(1,"0",false,2),2,List.of());c.remove(1);equal(Money.ZERO,c.subtotal());});
        t.test("serial input: tabs, newlines, commas and zero prefixes",()->equal(List.of("001","002","ABC","DEF"),Validation.serials("001\n002,abc\tdef")));
        t.test("request: empty cart rejected",()->throwsType(IllegalArgumentException.class,()->request(UUID.randomUUID(),List.of())));
        t.test("request: duplicate product lines rejected",()->throwsType(IllegalArgumentException.class,()->request(UUID.randomUUID(),List.of(line(1,1),line(1,1)))));
        t.test("request: stable canonical hash independent of line order",()->{UUID id=UUID.randomUUID();equal(request(id,List.of(line(1,1),line(2,1))).fingerprint(),request(id,List.of(line(2,1),line(1,1))).fingerprint());});
        t.test("request: hash changes when quantity changes",()->check(!request(UUID.randomUUID(),List.of(line(1,1))).fingerprint().equals(request(UUID.randomUUID(),List.of(line(1,2))).fingerprint()),"Payload hash did not change"));
        t.test("request: disallow manual plus promotion stacking",()->throwsType(IllegalArgumentException.class,()->new SaleRequest(UUID.randomUUID(),null,"CASH",Money.of("1"),"TEST",Money.of("1"),List.of(line(1,1)))));
        t.test("request: disallow unknown payment method",()->throwsType(IllegalArgumentException.class,()->new SaleRequest(UUID.randomUUID(),null,"CARD",Money.ZERO,"",Money.ZERO,List.of(line(1,1)))));
        t.test("request: immutable line list",()->{List<ItemQuantity> list=new ArrayList<>(List.of(line(1,1)));SaleRequest r=request(UUID.randomUUID(),list);list.clear();equal(1,r.lines().size());});
        t.test("inventory request: source and destination must differ",()->throwsType(IllegalArgumentException.class,()->new InventoryService.Request(UUID.randomUUID(),1,1,List.of(),"test",null,Money.ZERO,"KHO","KHO")));
        t.test("inventory request: mandatory reason/document",()->throwsType(IllegalArgumentException.class,()->new InventoryService.Request(UUID.randomUUID(),1,1,List.of(),"",1L,Money.ZERO,null,"KHO")));
        t.test("inventory request: duplicate serial rejected",()->throwsType(IllegalArgumentException.class,()->new InventoryService.Request(UUID.randomUUID(),1,2,List.of("s1","S1"),"test",1L,Money.ZERO,null,"KHO")));
        t.test("warranty: 12 months from leap day",()->equal(LocalDate.of(2025,2,28),WarrantyRules.end(LocalDate.of(2024,2,29),12)));
        t.test("warranty: end-of-month rule",()->equal(LocalDate.of(2025,2,28),WarrantyRules.end(LocalDate.of(2025,1,31),1)));
        t.test("warranty: expires after inclusive end date",()->{LocalDate a=LocalDate.of(2025,1,1),b=LocalDate.of(2026,1,1);check(WarrantyRules.active(a,b,b),"End date should be covered");check(!WarrantyRules.active(a,b,b.plusDays(1)),"Day after should be expired");});
        t.test("warranty: invalid months rejected",()->{throwsType(IllegalArgumentException.class,()->WarrantyRules.end(LocalDate.now(),0));throwsType(IllegalArgumentException.class,()->WarrantyRules.end(LocalDate.now(),121));});
        t.test("claims: allowed forward states only",()->{check(WarrantyRules.canTransition("RECEIVED","PROCESSING"),"Missing transition");check(WarrantyRules.canTransition("PROCESSING","COMPLETED"),"Missing completion");check(!WarrantyRules.canTransition("COMPLETED","PROCESSING"),"Reopening must be refused");check(!WarrantyRules.canTransition("RECEIVED","COMPLETED"),"Skipping processing must be refused");});
        t.test("reports: local midnight converted to UTC",()->equal(Instant.parse("2026-10-18T17:00:00Z"),Validation.start(LocalDate.of(2026,10,19))));
        t.test("dates: strict dd/MM/yyyy",()->{equal(LocalDate.of(2026,10,19),Ui.date(new javax.swing.JTextField("19/10/2026")));throwsType(IllegalArgumentException.class,()->Ui.date(new javax.swing.JTextField("31/02/2026")));});
        t.test("reports: reversed range rejected",()->throwsType(IllegalArgumentException.class,()->Validation.dates(LocalDate.of(2026,2,1),LocalDate.of(2026,1,1))));
        t.test("auth: salted PBKDF2 verifies and rejects wrong password",()->{char[] pw="DienMay-test!2026".toCharArray();String a=Passwords.hash(pw),b=Passwords.hash(pw);check(!a.equals(b),"Salt must be random");check(Passwords.verify(pw,a),"Correct password rejected");check(!Passwords.verify("incorrect!2026".toCharArray(),a),"Wrong password accepted");Arrays.fill(pw,'\0');});
        t.test("auth: short passwords rejected",()->throwsType(IllegalArgumentException.class,()->Passwords.hash("short".toCharArray())));
        t.test("auth: malformed hash rejected",()->{check(!Passwords.verify("long enough secret".toCharArray(),"plain-text"),"Plain text accepted");check(!Passwords.verify("long enough secret".toCharArray(),"pbkdf2_sha256$1$AAAA$AAAA"),"Unsafe work factor accepted");});
        t.test("auth: role predicate matrix",()->{for(Role actual:Role.values())for(Role allowed:Role.values())equal(actual==allowed,AuthService.roleAllowed(actual,allowed));});
        t.test("auth: missing session denied before database query",()->throwsType(SecurityException.class,()->new AuthService(new SQL(()->{throw new SQLException("not used");})).require(null,null,Role.MANAGER)));
        t.test("auth: forged session object is not an authenticated token",()->{Constructor<AuthService.Session> ctor=AuthService.Session.class.getDeclaredConstructor(long.class,String.class,Role.class,int.class,boolean.class);ctor.setAccessible(true);AuthService.Session fake=ctor.newInstance(1,"fake",Role.MANAGER,1,false);throwsType(SecurityException.class,()->new AuthService(new SQL(()->null)).require(null,fake,Role.MANAGER));});
        t.test("demo: all seeded hashes match documented public passwords",()->{String sql=Files.readString(Path.of("database/002_demo.sql"));Matcher m=Pattern.compile("'((?:pbkdf2_sha256)\\$[^']+)'",Pattern.MULTILINE).matcher(sql);String[] secrets={"DienMay!Manager2026","DienMay!Cashier2026","DienMay!Warehouse2026"};int count=0;while(m.find()){check(count<3&&Passwords.verify(secrets[count].toCharArray(),m.group(1)),"Demo hash mismatch");count++;}equal(3,count);});
        t.test("pending sale: persistent UUID, Unicode and serial round-trip",()->{Path dir=Files.createTempDirectory("dm-sale-test");SaleRequest r=request(UUID.randomUUID(),List.of(line(1,1,"000001")));try(PendingStore store=new PendingStore(dir,1,"sale")){store.save(r);}try(PendingStore reopened=new PendingStore(dir,1,"sale")){equal(r,reopened.sale());equal(r.fingerprint(),reopened.sale().fingerprint());reopened.clear();equal(null,reopened.sale());}});
        t.test("pending sale: refuses overwriting an unresolved different request",()->{Path dir=Files.createTempDirectory("dm-sale-test");try(PendingStore store=new PendingStore(dir,1,"sale")){store.save(request(UUID.randomUUID(),List.of(line(1,1))));throwsType(java.io.IOException.class,()->store.save(request(UUID.randomUUID(),List.of(line(1,1)))));}});
        t.test("pending: OS file lock prevents two windows sharing an employee action",()->{Path dir=Files.createTempDirectory("dm-lock-test");try(PendingStore store=new PendingStore(dir,1,"sale")){throwsType(java.io.IOException.class,()->new PendingStore(dir,1,"sale"));}});
        t.test("pending inventory: round-trip and stable hash",()->{Path dir=Files.createTempDirectory("dm-stock-test");InventoryService.Request r=new InventoryService.Request(UUID.randomUUID(),1,2,List.of("0001","0002"),"Phiếu nhập mẫu",3L,Money.of("60"),null,"KHO");try(PendingStore store=new PendingStore(dir,1,"receive")){store.save(r);equal(r,store.inventory());equal(r.fingerprint(),store.inventory().fingerprint());}});
        t.test("pending: malformed file is never silently discarded",()->{Path dir=Files.createTempDirectory("dm-corrupt-test");try(PendingStore store=new PendingStore(dir,1,"sale")){Files.writeString(store.path(),"type=sale\nid=bad\n");throwsType(java.io.IOException.class,store::sale);check(Files.exists(store.path()),"Corrupt evidence removed");}});
        t.test("pending sale: same UUID cannot silently replace a different payload",()->{
            Path dir=Files.createTempDirectory("dm-payload-test");UUID id=UUID.randomUUID();
            try(PendingStore store=new PendingStore(dir,1,"sale")){
                store.save(request(id,List.of(line(1,1))));
                throwsType(java.io.IOException.class,()->store.save(request(id,List.of(line(1,2)))));
                equal(1,store.sale().lines().get(0).quantity());
            }
        });
        t.test("pending sale: detects tampered quantity and preserves evidence",()->{
            Path dir=Files.createTempDirectory("dm-payload-test");
            try(PendingStore store=new PendingStore(dir,1,"sale")){
                store.save(request(UUID.randomUUID(),List.of(line(1,1))));
                String saved=Files.readString(store.path(),StandardCharsets.UTF_8);
                check(saved.contains("line.0.qty=1"),"Fixture field missing");
                Files.writeString(store.path(),saved.replace("line.0.qty=1","line.0.qty=2"),StandardCharsets.UTF_8);
                throwsType(java.io.IOException.class,store::sale);check(Files.exists(store.path()),"Evidence removed");
            }
        });
        t.test("pending inventory: detects tampered destination",()->{
            Path dir=Files.createTempDirectory("dm-payload-test");
            InventoryService.Request r=new InventoryService.Request(UUID.randomUUID(),1,1,List.of(),"Test",1L,Money.of("60"),null,"KHO");
            try(PendingStore store=new PendingStore(dir,1,"receive")){
                store.save(r);String saved=Files.readString(store.path(),StandardCharsets.UTF_8);
                check(saved.contains("to=KHO"),"Fixture destination missing");
                Files.writeString(store.path(),saved.replace("to=KHO","to=QUAY"),StandardCharsets.UTF_8);
                throwsType(java.io.IOException.class,store::inventory);
            }
        });
        t.test("receipt: persisted snapshot totals and Vietnamese content",()->{Sale sale=new Sale(7,"HD-7",Instant.parse("2026-10-19T01:00:00Z"),"Thu ngân","Khách lẻ","","CASH",Money.of("200.50"),Money.of("0.50"),Money.of("200"),List.of(new Sale.Line("SKU","Điện thoại",2,Money.of("100.25"),12,"0001, 0002")));String receipt=sale.generateReceipt("Điện Máy","Hà Nội","0900000000");check(receipt.contains("HD-7")&&receipt.contains("19/10/2026 08:00")&&receipt.contains("Điện thoại")&&receipt.contains("0001, 0002")&&receipt.contains("200 ₫"),"Receipt fields missing");});
        t.test("JDBC boundary (double): commit and close on success",()->{FakeConnection f=new FakeConnection();int result=new SQL(f::connection).transaction(c->7);equal(7,result);equal(1,f.commits);equal(0,f.rollbacks);equal(1,f.closes);});
        t.test("JDBC boundary (double): SQL failure rolls back and closes",()->{FakeConnection f=new FakeConnection();SQLException expected=new SQLException("injected");SQLException actual=throwsType(SQLException.class,()->new SQL(f::connection).transaction(c->{throw expected;}));check(actual==expected,"Exception replaced");equal(0,f.commits);equal(1,f.rollbacks);equal(1,f.closes);});
        t.test("JDBC boundary (double): validation failure rolls back",()->{FakeConnection f=new FakeConnection();throwsType(IllegalArgumentException.class,()->new SQL(f::connection).transaction(c->{throw new IllegalArgumentException("injected");}));equal(1,f.rollbacks);equal(1,f.closes);});
        t.test("JDBC boundary (double): uncertain commit is not reported as success",()->{FakeConnection f=new FakeConnection();f.failCommit=true;throwsType(SQLException.class,()->new SQL(f::connection).transaction(c->true));equal(1,f.commits);equal(1,f.rollbacks);equal(1,f.closes);});
        t.test("JDBC boundary (double): rollback exception is preserved",()->{FakeConnection f=new FakeConnection();f.failRollback=true;SQLException ex=throwsType(SQLException.class,()->new SQL(f::connection).transaction(c->{throw new SQLException("original");}));equal(1,ex.getSuppressed().length);});
        t.test("JDBC boundary (double): read closes connection",()->{FakeConnection f=new FakeConnection();equal("ok",new SQL(f::connection).read(c->"ok"));equal(1,f.closes);});
        t.test("SQL search: LIKE metacharacters escaped, not concatenated",()->equal("%A~%~_~[~~%",ProductDao.like("A%_[~")));
        t.test("source check: active UI contains no raw JDBC or legacy RAM sales",()->{try(var files=Files.list(Path.of("GUI"))){for(Path f:files.filter(x->x.toString().endsWith(".java")).toList()){String s=Files.readString(f);check(!s.contains("DriverManager.getConnection")&&!s.contains("getSaleList().add"),"Legacy persistence left in "+f);}}});
        t.finish();
        System.out.println("NOT RUN | Real SQL Server integration, physical HID scanner and physical printer are outside this offline suite.");
    }
    /** Only exercises resource/transaction control flow, not SQL Server semantics. */
    private static final class FakeConnection implements InvocationHandler {
        int commits,rollbacks,closes;boolean failCommit,failRollback;
        Connection connection(){return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},this);}
        public Object invoke(Object proxy,Method method,Object[] args)throws Throwable {
            return switch(method.getName()){
                case "commit"->{commits++;if(failCommit)throw new SQLException("uncertain commit");yield null;}
                case "rollback"->{rollbacks++;if(failRollback)throw new SQLException("rollback failed");yield null;}
                case "close"->{closes++;yield null;}
                case "createStatement"->Proxy.newProxyInstance(Statement.class.getClassLoader(),new Class<?>[]{Statement.class},(p,m,a)->m.getReturnType()==boolean.class?false:m.getReturnType()==int.class?0:null);
                default->method.getReturnType()==boolean.class?false:method.getReturnType()==int.class?0:null;
            };
        }
    }
}
