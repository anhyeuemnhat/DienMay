package Code;
import java.time.*;
import java.util.*;
public final class Validation {
    public static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private Validation(){}
    public static String required(String s,int max,String label) {
        s=s==null?"":s.trim(); if(s.isEmpty()||s.length()>max) throw new IllegalArgumentException(label+" phải có từ 1 đến "+max+" ký tự."); return s;
    }
    public static String optional(String s,int max,String label) {
        s=s==null?"":s.trim(); if(s.length()>max) throw new IllegalArgumentException(label+" quá dài (tối đa "+max+")."); return s;
    }
    public static String serial(String s){return required(s,128,"Serial/IMEI").toUpperCase(Locale.ROOT);}
    public static List<String> serials(String text){return text==null||text.isBlank()?List.of():Arrays.stream(text.split("[,;\\s]+",-1)).filter(x->!x.isBlank()).map(Validation::serial).toList();}
    public static Instant start(LocalDate d){return d.atStartOfDay(ZONE).toInstant();}
    public static void dates(LocalDate from,LocalDate to){if(from==null||to==null||from.isAfter(to)) throw new IllegalArgumentException("Khoảng ngày không hợp lệ.");}
}
