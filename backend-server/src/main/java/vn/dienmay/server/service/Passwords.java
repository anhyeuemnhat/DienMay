package vn.dienmay.server.service;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Retains the baseline salted PBKDF2-SHA256 storage format and iteration count. */
public final class Passwords {
  public static final int ITERATIONS = 600_000;
  private static final SecureRandom RANDOM = new SecureRandom();

  private Passwords() {}

  public static void validate(char[] password) {
    if (password == null || password.length < 12 || password.length > 256) {
      throw new IllegalArgumentException("Mat khau phai co tu 12 den 256 ky tu.");
    }
  }

  public static String hash(char[] password) {
    validate(password);
    byte[] salt = new byte[16];
    RANDOM.nextBytes(salt);
    return "pbkdf2_sha256$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
        + "$" + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
  }

  public static boolean verify(char[] password, String encoded) {
    if (password == null || password.length > 256 || encoded == null) {
      return false;
    }
    try {
      String[] parts = encoded.split("\\$", -1);
      if (parts.length != 4 || !parts[0].equals("pbkdf2_sha256")) {
        return false;
      }
      int iterations = Integer.parseInt(parts[1]);
      if (iterations < ITERATIONS || iterations > 2_000_000) {
        return false;
      }
      byte[] salt = Base64.getDecoder().decode(parts[2]);
      byte[] expected = Base64.getDecoder().decode(parts[3]);
      if (salt.length < 16 || salt.length > 64 || expected.length != 32) {
        return false;
      }
      return MessageDigest.isEqual(expected, derive(password, salt, iterations));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static byte[] derive(char[] password, byte[] salt, int iterations) {
    PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
          .generateSecret(spec).getEncoded();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("PBKDF2-HMAC-SHA256 is required", e);
    } finally {
      spec.clearPassword();
    }
  }
}
