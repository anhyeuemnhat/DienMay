package Service;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
public final class Passwords {
    public static final int ITERATIONS=600_000;
    private static final SecureRandom RANDOM=new SecureRandom();
    private Passwords(){}
    public static void validate(char[] password){if(password==null||password.length<12||password.length>256)throw new IllegalArgumentException("Mật khẩu phải có từ 12 đến 256 ký tự.");}
    public static String hash(char[] password) {
        validate(password); byte[] salt=new byte[16]; RANDOM.nextBytes(salt);
        return "pbkdf2_sha256$"+ITERATIONS+"$"+Base64.getEncoder().encodeToString(salt)+"$"+Base64.getEncoder().encodeToString(derive(password,salt,ITERATIONS));
    }
    public static boolean verify(char[] password,String encoded) {
        if(password==null||password.length>256||encoded==null) return false;
        try {
            String[] p=encoded.split("\\$",-1);
            if(p.length!=4||!p[0].equals("pbkdf2_sha256"))return false;
            int iterations=Integer.parseInt(p[1]); if(iterations<600_000||iterations>2_000_000)return false;
            byte[] salt=Base64.getDecoder().decode(p[2]), expected=Base64.getDecoder().decode(p[3]);
            if(salt.length<16||salt.length>64||expected.length!=32)return false;
            return MessageDigest.isEqual(expected,derive(password,salt,iterations));
        } catch(IllegalArgumentException e){return false;}
    }
    private static byte[] derive(char[] password,byte[] salt,int iterations) {
        PBEKeySpec spec=new PBEKeySpec(password,salt,iterations,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
        catch(GeneralSecurityException e){throw new IllegalStateException("Java không hỗ trợ PBKDF2-HMAC-SHA256.",e);}
        finally{spec.clearPassword();}
    }
}
