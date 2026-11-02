package vn.dienmay.client.state;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import vn.dienmay.common.model.InventoryRequest;
import vn.dienmay.common.model.ItemQuantity;
import vn.dienmay.common.model.Money;
import vn.dienmay.common.model.SaleRequest;
import vn.dienmay.common.model.Validation;

/** Retains the upstream durable properties protocol and one OS lock per employee/action. */
public final class PendingStore implements AutoCloseable {
  private final Path file;
  private final String kind;
  private final FileChannel channel;
  private final FileLock lock;

  public PendingStore(Path directory, long employee, String kind) throws IOException {
    if (employee <= 0 || !Set.of("sale", "receive", "transfer", "delete", "reversal").contains(kind)) {
      throw new IllegalArgumentException("Ten luu yeu cau khong hop le.");
    }
    this.kind = kind;
    Files.createDirectories(directory);
    restrict(directory, true);
    file = directory.resolve(employee + "-" + kind + ".properties");
    Path lockFile = directory.resolve(employee + "-" + kind + ".lck");
    channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    FileLock acquired;
    try {
      acquired = channel.tryLock();
      restrict(lockFile, false);
    } catch (OverlappingFileLockException | IOException ex) {
      channel.close();
      throw new IOException("Chuc nang nay da mo o cua so/tien trinh khac.", ex);
    }
    if (acquired == null) {
      channel.close();
      throw new IOException("Chuc nang nay dang mo o tien trinh khac. Dong cua so cu truoc.");
    }
    lock = acquired;
  }

  public Path path() {
    return file;
  }

  private static void restrict(Path path, boolean directory) throws IOException {
    try {
      Files.setPosixFilePermissions(path,
          PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
    } catch (UnsupportedOperationException ignored) {
      // On Windows the user's profile ACL controls access; verify it during deployment.
    }
  }

  private Properties read() throws IOException {
    Properties properties = new Properties();
    if (Files.exists(file)) {
      if (Files.size(file) > 16 * 1024 * 1024) {
        throw new IOException("Tep yeu cau qua lon. Khong xoa tep; lien he quan tri.");
      }
      try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
        properties.load(reader);
      }
    }
    return properties;
  }

  private void write(Properties properties) throws IOException {
    Properties existing = read();
    if (!existing.isEmpty() && (!Objects.equals(existing.getProperty("id"), properties.getProperty("id"))
        || !Objects.equals(existing.getProperty("fingerprint"), properties.getProperty("fingerprint")))) {
      throw new IOException("Con yeu cau chua doi soat. Khong ghi de bang giao dich moi.");
    }
    Path temporary = Files.createTempFile(file.getParent(), "pending-", ".tmp");
    try {
      restrict(temporary, false);
      try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
        properties.store(writer, "DienMay pending transaction - do not edit");
      }
      try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
        output.force(true);
      }
      try {
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ex) {
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static String required(Properties properties, String key) {
    String value = properties.getProperty(key);
    if (value == null) {
      throw new IllegalArgumentException("Thieu truong " + key);
    }
    return value;
  }

  private static void set(Properties properties, String key, Object value) {
    properties.setProperty(key, value == null ? "" : String.valueOf(value));
  }

  public void save(SaleRequest request) throws IOException {
    Properties p = new Properties();
    set(p, "type", "sale");
    set(p, "fingerprint", request.fingerprint());
    set(p, "id", request.requestId());
    set(p, "customer", request.customerId());
    set(p, "payment", request.payment());
    set(p, "manual", request.manualDiscount());
    set(p, "promotion", request.promotionCode());
    set(p, "discount", request.expectedDiscount());
    set(p, "lines", request.lines().size());
    for (int i = 0; i < request.lines().size(); i++) {
      ItemQuantity line = request.lines().get(i);
      String key = "line." + i + ".";
      set(p, key + "product", line.productId());
      set(p, key + "sku", line.sku());
      set(p, key + "name", line.name());
      set(p, key + "price", line.expectedPrice());
      set(p, key + "qty", line.quantity());
      writeSerials(p, key, line.serials());
    }
    write(p);
  }

  private static void writeSerials(Properties properties, String prefix, List<String> serials) {
    set(properties, prefix + "serialCount", serials.size());
    for (int i = 0; i < serials.size(); i++) {
      set(properties, prefix + "serial." + i, serials.get(i));
    }
  }

  private static List<String> readSerials(Properties properties, String prefix) {
    int count = Integer.parseInt(required(properties, prefix + "serialCount"));
    if (count < 0 || count > 100000) {
      throw new IllegalArgumentException("So serial khong hop le.");
    }
    List<String> serials = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      serials.add(Validation.serial(required(properties, prefix + "serial." + i)));
    }
    return serials;
  }

  public SaleRequest sale() throws IOException {
    Properties p = read();
    if (p.isEmpty()) {
      return null;
    }
    try {
      if (!required(p, "type").equals("sale")) {
        throw new IllegalArgumentException("Sai loai yeu cau.");
      }
      int count = Integer.parseInt(required(p, "lines"));
      if (count < 1 || count > 100) {
        throw new IllegalArgumentException("So dong khong hop le.");
      }
      List<ItemQuantity> lines = new ArrayList<>();
      for (int i = 0; i < count; i++) {
        String key = "line." + i + ".";
        lines.add(new ItemQuantity(Long.parseLong(required(p, key + "product")),
            required(p, key + "sku"), required(p, key + "name"),
            Money.of(required(p, key + "price")), Integer.parseInt(required(p, key + "qty")),
            readSerials(p, key)));
      }
      String customer = required(p, "customer");
      SaleRequest request = new SaleRequest(UUID.fromString(required(p, "id")),
          customer.isBlank() ? null : Long.valueOf(customer), required(p, "payment"),
          Money.of(required(p, "manual")), required(p, "promotion"),
          Money.of(required(p, "discount")), lines);
      verifyFingerprint(p, request.fingerprint());
      return request;
    } catch (RuntimeException ex) {
      throw new IOException("Tep yeu cau cho bi loi; KHONG xoa tep: " + file, ex);
    }
  }

  public void save(InventoryRequest request) throws IOException {
    Properties p = new Properties();
    set(p, "type", "stock");
    set(p, "fingerprint", request.fingerprint());
    set(p, "id", request.id());
    set(p, "product", request.productId());
    set(p, "qty", request.quantity());
    set(p, "reference", request.reference());
    set(p, "vendor", request.vendorId());
    set(p, "cost", request.unitCost());
    set(p, "from", request.from());
    set(p, "to", request.to());
    set(p, "identifierType", request.identifierType());
    set(p, "priceConfirmation", vn.dienmay.common.api.Json.stringify(request.priceConfirmation()));
    writeSerials(p, "", request.serials());
    write(p);
  }

  public InventoryRequest inventory() throws IOException {
    Properties p = read();
    if (p.isEmpty()) {
      return null;
    }
    try {
      if (!required(p, "type").equals("stock")) {
        throw new IllegalArgumentException("Sai loai yeu cau.");
      }
      String vendor = required(p, "vendor");
      String from = required(p, "from");
      InventoryRequest request = new InventoryRequest(UUID.fromString(required(p, "id")),
          Long.parseLong(required(p, "product")), Integer.parseInt(required(p, "qty")),
          readSerials(p, ""), required(p, "reference"), vendor.isBlank() ? null : Long.valueOf(vendor),
          Money.of(required(p, "cost")), from.isBlank() ? null : from, required(p, "to"),
          p.getProperty("identifierType", "SERIAL"), vn.dienmay.common.api.Json.convert(
              vn.dienmay.common.api.Json.parse(p.getProperty("priceConfirmation", "null")),
              vn.dienmay.common.model.PriceConfirmation.class));
      verifyFingerprint(p, request.fingerprint());
      return request;
    } catch (RuntimeException ex) {
      throw new IOException("Tep yeu cau kho bi loi; KHONG xoa tep: " + file, ex);
    }
  }

  private static void verifyFingerprint(Properties properties, String actual) {
    if (!required(properties, "fingerprint").equals(actual)) {
      throw new IllegalArgumentException("Noi dung yeu cau cho da thay doi hoac bi hong.");
    }
  }

  public void save(vn.dienmay.common.model.GuardedRequest request) throws IOException {
    boolean matches = kind.equals("delete") && request instanceof vn.dienmay.common.model.DeletionRequest
        || kind.equals("reversal") && request instanceof vn.dienmay.common.model.ReversalRequest;
    if (!matches) { throw new IllegalArgumentException("Wrong durable administrative request kind."); }
    Properties p = new Properties();
    set(p, "type", "guarded-" + kind);
    set(p, "id", request.id());
    set(p, "fingerprint", request.fingerprint());
    set(p, "payload", vn.dienmay.common.api.Json.stringify(request));
    write(p);
  }

  public <T extends vn.dienmay.common.model.GuardedRequest> T guarded(Class<T> type) throws IOException {
    Properties p = read();
    if (p.isEmpty()) { return null; }
    try {
      if (!required(p, "type").equals("guarded-" + kind)) {
        throw new IllegalArgumentException("Wrong request kind.");
      }
      T result = vn.dienmay.common.api.Json.convert(vn.dienmay.common.api.Json.parse(required(p, "payload")), type);
      if (!result.id().toString().equals(required(p, "id"))) {
        throw new IllegalArgumentException("Request identifier was changed.");
      }
      verifyFingerprint(p, result.fingerprint());
      return result;
    } catch (RuntimeException ex) {
      throw new IOException("Tep yeu cau quan tri bi loi; KHONG xoa tep: " + file, ex);
    }
  }

  public void clear() throws IOException {
    Files.deleteIfExists(file);
  }

  @Override
  public void close() throws IOException {
    try {
      if (lock.isValid()) {
        lock.release();
      }
    } finally {
      channel.close();
    }
  }
}
