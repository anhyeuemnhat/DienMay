package Service;
import Code.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
/** One OS file lock per employee/action; durable request identity, never Java deserialization. */
public final class PendingStore implements AutoCloseable {
    private final Path file;
    private final FileChannel channel;
    private final FileLock lock;
    public PendingStore(Path directory,long employee,String kind)throws IOException {
        if(employee<=0||!Set.of("sale","receive","transfer").contains(kind))throw new IllegalArgumentException("Tên lưu yêu cầu không hợp lệ.");
        Files.createDirectories(directory); restrict(directory,true);
        file=directory.resolve(employee+"-"+kind+".properties");
        Path lockFile=directory.resolve(employee+"-"+kind+".lck");
        channel=FileChannel.open(lockFile,StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        FileLock acquired;
        try{acquired=channel.tryLock();}catch(OverlappingFileLockException e){channel.close();throw new IOException("Chức năng này đã mở cho tài khoản trong một cửa sổ khác.",e);}catch(IOException e){channel.close();throw e;}
        if(acquired==null){channel.close();throw new IOException("Chức năng này đang mở ở tiến trình khác. Đóng cửa sổ cũ trước.");}
        lock=acquired;restrict(lockFile,false);
    }
    public Path path(){return file;}
    private static void restrict(Path path,boolean directory)throws IOException {
        try{Files.setPosixFilePermissions(path,PosixFilePermissions.fromString(directory?"rwx------":"rw-------"));}catch(UnsupportedOperationException ignored){/* Windows ACL is controlled by the user's profile. */}
    }
    private Properties read()throws IOException {
        Properties p=new Properties();if(Files.exists(file)){try(Reader r=Files.newBufferedReader(file,StandardCharsets.UTF_8)){p.load(r);}}return p;
    }
    private void write(Properties p)throws IOException {
        Properties existing=read();
        if(!existing.isEmpty()&&(!Objects.equals(existing.getProperty("id"),p.getProperty("id")) || !Objects.equals(existing.getProperty("fingerprint"),p.getProperty("fingerprint"))))throw new IOException("Còn yêu cầu chưa đối soát. Không ghi đè bằng giao dịch mới.");
        Path temp=Files.createTempFile(file.getParent(),"pending-",".tmp");
        try{
            try(Writer w=Files.newBufferedWriter(temp,StandardCharsets.UTF_8)){p.store(w,"DienMay pending transaction - do not edit while application is running");}
            restrict(temp,false);
            try(FileChannel f=FileChannel.open(temp,StandardOpenOption.WRITE)){f.force(true);}
            try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    private static String required(Properties p,String k){String s=p.getProperty(k);if(s==null)throw new IllegalArgumentException("Thiếu trường "+k);return s;}
    private static void set(Properties p,String k,Object v){p.setProperty(k,v==null?"":String.valueOf(v));}
    public void save(SaleRequest r)throws IOException {
        Properties p=new Properties();set(p,"type","sale");set(p,"fingerprint",r.fingerprint());set(p,"id",r.requestId());set(p,"customer",r.customerId());set(p,"payment",r.payment());set(p,"manual",r.manualDiscount());set(p,"promotion",r.promotionCode());set(p,"discount",r.expectedDiscount());set(p,"lines",r.lines().size());
        for(int i=0;i<r.lines().size();i++){ItemQuantity l=r.lines().get(i);String k="line."+i+".";set(p,k+"product",l.productId());set(p,k+"sku",l.sku());set(p,k+"name",l.name());set(p,k+"price",l.expectedPrice());set(p,k+"qty",l.quantity());writeSerials(p,k,l.serials());}write(p);
    }
    private static void writeSerials(Properties p,String prefix,List<String> serials){set(p,prefix+"serialCount",serials.size());for(int i=0;i<serials.size();i++)set(p,prefix+"serial."+i,serials.get(i));}
    private static List<String> readSerials(Properties p,String prefix){int n=Integer.parseInt(required(p,prefix+"serialCount"));if(n<0||n>100000)throw new IllegalArgumentException("Số serial không hợp lệ.");List<String> list=new ArrayList<>();for(int i=0;i<n;i++)list.add(Validation.serial(required(p,prefix+"serial."+i)));return list;}
    public SaleRequest sale()throws IOException {
        Properties p=read();if(p.isEmpty())return null;
        try{
            if(!required(p,"type").equals("sale"))throw new IllegalArgumentException("Sai loại yêu cầu.");
            int count=Integer.parseInt(required(p,"lines"));if(count<1||count>100)throw new IllegalArgumentException("Số dòng không hợp lệ.");
            List<ItemQuantity> lines=new ArrayList<>();for(int i=0;i<count;i++){String k="line."+i+".";lines.add(new ItemQuantity(Long.parseLong(required(p,k+"product")),required(p,k+"sku"),required(p,k+"name"),Money.of(required(p,k+"price")),Integer.parseInt(required(p,k+"qty")),readSerials(p,k)));}
            String customer=required(p,"customer");
            SaleRequest request=new SaleRequest(UUID.fromString(required(p,"id")),customer.isBlank()?null:Long.valueOf(customer),required(p,"payment"),Money.of(required(p,"manual")),required(p,"promotion"),Money.of(required(p,"discount")),lines);
            verifyFingerprint(p,request.fingerprint());return request;
        }catch(RuntimeException e){throw new IOException("Tệp yêu cầu chờ bị lỗi; không xóa tệp. Cần kiểm tra: "+file,e);}
    }
    public void save(InventoryService.Request r)throws IOException {
        Properties p=new Properties();set(p,"type","stock");set(p,"fingerprint",r.fingerprint());set(p,"id",r.id());set(p,"product",r.productId());set(p,"qty",r.quantity());set(p,"reference",r.reference());set(p,"vendor",r.vendorId());set(p,"cost",r.unitCost());set(p,"from",r.from());set(p,"to",r.to());writeSerials(p,"",r.serials());write(p);
    }
    public InventoryService.Request inventory()throws IOException {
        Properties p=read();if(p.isEmpty())return null;
        try{if(!required(p,"type").equals("stock"))throw new IllegalArgumentException("Sai loại yêu cầu.");String vendor=required(p,"vendor"),from=required(p,"from");InventoryService.Request request=new InventoryService.Request(UUID.fromString(required(p,"id")),Long.parseLong(required(p,"product")),Integer.parseInt(required(p,"qty")),readSerials(p,""),required(p,"reference"),vendor.isBlank()?null:Long.valueOf(vendor),Money.of(required(p,"cost")),from.isBlank()?null:from,required(p,"to"));verifyFingerprint(p,request.fingerprint());return request;}
        catch(RuntimeException e){throw new IOException("Tệp yêu cầu kho chờ bị lỗi; không xóa tệp: "+file,e);}
    }
    private static void verifyFingerprint(Properties p,String actual){
        if(!required(p,"fingerprint").equals(actual))throw new IllegalArgumentException("Nội dung yêu cầu chờ đã thay đổi hoặc bị hỏng.");
    }
    public void clear()throws IOException{Files.deleteIfExists(file);}
    public void close()throws IOException{try{if(lock.isValid())lock.release();}finally{channel.close();}}
}
