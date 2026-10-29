package vn.dienmay.server;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import vn.dienmay.server.api.ApiServer;
import vn.dienmay.server.api.ApplicationApi;
import vn.dienmay.server.db.SqlDatabase;

/** Separate application-tier process; requires SQL Server configuration only on this host. */
public final class ServerMain {
  private ServerMain() {}

  public static void main(String[] args) throws Exception {
    Map<String, String> env = System.getenv();
    System.setProperty("sun.net.httpserver.maxReqTime", "30");
    System.setProperty("sun.net.httpserver.maxRspTime", "30");
    System.setProperty("sun.net.httpserver.maxConnections", "128");
    System.setProperty("sun.net.httpserver.maxReqHeaders", "50");
    String host = env.getOrDefault("DIENMAY_BIND", "127.0.0.1");
    int port = Integer.parseInt(env.getOrDefault("DIENMAY_PORT", "8080"));
    SqlDatabase database = new SqlDatabase();
    System.out.println("Database ready: " + database.checkConnection());
    ApiServer server = new ApiServer(new InetSocketAddress(host, port), tls(env),
        new ApplicationApi(new InventorySystem(database)));
    Runtime.getRuntime().addShutdownHook(new Thread(server::close, "dienmay-shutdown"));
    server.start();
    System.out.println("DienMay backend listening on " + host + ":" + server.port());
    new CountDownLatch(1).await();
  }

  private static SSLContext tls(Map<String, String> env) throws Exception {
    String file = env.get("DIENMAY_TLS_KEYSTORE");
    if (file == null || file.isBlank()) {
      return null;
    }
    String supplied = env.get("DIENMAY_TLS_PASSWORD");
    if (supplied == null || supplied.isEmpty()) {
      throw new IllegalArgumentException("DIENMAY_TLS_PASSWORD is required with a keystore");
    }
    char[] password = supplied.toCharArray();
    try (InputStream input = Files.newInputStream(Path.of(file))) {
      KeyStore keys = KeyStore.getInstance("PKCS12");
      keys.load(input, password);
      KeyManagerFactory manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      manager.init(keys, password);
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(manager.getKeyManagers(), null, null);
      return context;
    } finally {
      Arrays.fill(password, '\0');
    }
  }
}
