package Code;
import java.time.LocalDate;
import java.util.*;
public final class WarrantyRules {
    private WarrantyRules(){}
    public static LocalDate end(LocalDate start,int months){if(months<1||months>120)throw new IllegalArgumentException("Thời hạn bảo hành phải từ 1 đến 120 tháng.");return start.plusMonths(months);}
    public static boolean active(LocalDate start,LocalDate end,LocalDate today){return !today.isBefore(start)&&!today.isAfter(end);}
    public static boolean canTransition(String old,String next){return (old.equals("RECEIVED")&&Set.of("PROCESSING","REJECTED").contains(next)) || (old.equals("PROCESSING")&&Set.of("COMPLETED","REJECTED").contains(next));}
    public static String label(String status){return switch(status){case "RECEIVED"->"Đã tiếp nhận";case "PROCESSING"->"Đang xử lý";case "COMPLETED"->"Hoàn tất";case "REJECTED"->"Từ chối";default->status;};}
}
