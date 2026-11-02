package vn.dienmay.tests;

import static vn.dienmay.tests.TestKit.check;
import static vn.dienmay.tests.TestKit.equal;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import vn.dienmay.client.AppContext;
import vn.dienmay.client.api.HttpApiClient;
import vn.dienmay.client.ui.EditDialog;
import vn.dienmay.client.ui.Navigation;
import vn.dienmay.common.api.ApiSession;
import vn.dienmay.common.api.BusinessException;
import vn.dienmay.common.api.Data;
import vn.dienmay.common.api.Json;
import vn.dienmay.common.api.Operation;
import vn.dienmay.common.model.DeletionPreview;
import vn.dienmay.common.model.DeletionRequest;
import vn.dienmay.common.model.InventoryRequest;
import vn.dienmay.common.model.Item;
import vn.dienmay.common.model.Money;
import vn.dienmay.common.model.PriceConfirmation;
import vn.dienmay.common.model.ReversalPreview;
import vn.dienmay.common.model.ReversalRequest;
import vn.dienmay.common.model.Role;
import vn.dienmay.server.api.ApiHandler;
import vn.dienmay.server.api.ApiServer;

/** Real Swing modal actions and real HTTP against TEST-ONLY responses, never SQL acceptance. */
public final class ControlledUiTests {
  private ControlledUiTests() {}
  private static final String BACK = "Quay l\u1ea1i ch\u1ec9nh s\u1eeda";
  private static final String SAVE = "X\u00e1c nh\u1eadn l\u01b0u";
  private static final String RECEIVE = "X\u00e1c nh\u1eadn nh\u1eadp h\u00e0ng";
  private static final String DELETE = "X\u00e1c nh\u1eadn x\u00f3a v\u0129nh vi\u1ec5n";
  private static final String REVERSE = "X\u00e1c nh\u1eadn h\u1ee7y/\u0111\u1ea3o phi\u1ebfu";

  private static <T> T edt(Callable<T> work) throws Exception {
    FutureTask<T> task = new FutureTask<>(work);
    SwingUtilities.invokeAndWait(task);
    return task.get();
  }
  private static List<Component> all(Component root) {
    List<Component> result = new ArrayList<>(); result.add(root);
    if (root instanceof Container container) {
      for (Component child : container.getComponents()) { result.addAll(all(child)); }
    }
    return result;
  }
  private static <T extends Component> T find(Component root, Class<T> kind, String name) {
    return all(root).stream().filter(kind::isInstance).map(kind::cast)
        .filter(c -> name == null || name.equals(c.getName()) || c instanceof AbstractButton b &&
            name.equals(b.getText()))
        .findFirst().orElseThrow(() -> new AssertionError("Missing " + kind.getSimpleName() + ": " + name));
  }
  private static AbstractButton button(AppContext app, String name) { return find(app.shell().current(),
      AbstractButton.class, name); }
  private static void text(AppContext app, String name, String value) throws Exception {
    edt(() -> { find(app.shell().current(), JTextField.class, name).setText(value); return null; });
  }
  private static void idle(AppContext app) throws Exception {
    for (int n = 0; n < 300; n++) {
      if (edt(app::jobs) == 0) { Thread.sleep(70); if (edt(app::jobs) == 0) { return; } }
      Thread.sleep(25);
    }
    throw new AssertionError("Worker did not finish");
  }
  private record Step(String name, Consumer<JDialog> answer) {}
  private static Step choose(String name, Object answer) {
    return new Step(name, dialog -> find(dialog, JOptionPane.class, null).setValue(answer));
  }
  private static Step admin(Object answer, String reason, boolean reversal) {
    return new Step("administrativeConfirmationDialog", dialog -> {
      JTextArea field = find(dialog, JTextArea.class, "administrativeReason");
      field.setText(reason);
      if (reversal) {
        check(all(dialog).stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast)
            .anyMatch(a -> a.getText().contains("ch\u1ee9ng t\u1eeb \u0111\u1ea3o")), "Missing reversal warning");
      }
      find(dialog, JOptionPane.class, null).setValue(answer);
    });
  }
  /** Timer consumes exact modal sequence; unexpected dialogs fail rather than silently clicking through. */
  private static void act(AppContext app, Runnable action, Step... steps) throws Exception {
    Queue<Step> queue = new ArrayDeque<>(List.of(steps));
    AtomicReference<Throwable> failure = new AtomicReference<>();
    AtomicBoolean finished = new AtomicBoolean();
    javax.swing.Timer timer = edt(() -> {
      javax.swing.Timer t = new javax.swing.Timer(45, e -> {
        for (Window window : Window.getWindows()) {
          if (!(window instanceof JDialog dialog) || !dialog.isShowing()) { continue; }
          try {
            Step step = queue.poll();
            check(step != null, "Unexpected dialog: " + dialog.getTitle());
            if (step.name() != null) { equal(step.name(), dialog.getName()); }
            step.answer().accept(dialog);
          } catch (Throwable error) { failure.compareAndSet(null, error); dialog.dispose(); }
          break;
        }
      }); t.start(); return t;
    });
    SwingUtilities.invokeLater(() -> {
      try { action.run(); } catch (Throwable error) { failure.compareAndSet(null, error); }
      finally { finished.set(true); }
    });
    try {
      long deadline = System.nanoTime() + 15_000_000_000L;
      while (System.nanoTime() < deadline) {
        if (failure.get() != null) { throw new AssertionError("Modal workflow failed", failure.get()); }
        boolean ready = edt(() -> finished.get() && app.jobs() == 0 && queue.isEmpty()
            && java.util.Arrays.stream(Window.getWindows()).noneMatch(w -> w instanceof JDialog && w.isShowing()));
        if (ready) { Thread.sleep(130); if (edt(app::jobs) == 0) { return; } }
        Thread.sleep(25);
      }
      throw new AssertionError("Modal timeout; remaining=" + queue.size());
    } finally { edt(() -> { timer.stop(); for (Window w : Window.getWindows()) { if (w instanceof JDialog) {
        w.dispose(); } } return null; }); }
  }

  private static final class Fixture implements ApiHandler {
    final UiFixture base = new UiFixture();
    final Map<Operation, Integer> counts = new ConcurrentHashMap<>();
    final Map<Operation, Map<String, Object>> payloads = new ConcurrentHashMap<>();
    volatile boolean blocked;
    volatile boolean failSave;
    String token;
    int count(Operation operation) { return counts.getOrDefault(operation, 0); }
    @Override public Object execute(Operation operation, String bearer, Map<String, Object> p) throws Exception {
      counts.merge(operation, 1, Integer::sum); payloads.put(operation, p);
      if (operation == Operation.LOGIN) {
        ApiSession session = (ApiSession) base.execute(operation, bearer, p); token = session.token(); return session;
      }
      if (!bearer.equals(token) || !operation.allows(Role.MANAGER)) { throw new SecurityException("Fixture denied"); }
      return switch (operation) {
        case PRODUCT_SAVE -> {
          if (failSave) { throw new BusinessException(409, "REVISION_CONFLICT", "TEST fixture: revision changed"); }
          yield 1L;
        }
        case RECEIVE -> null;
        case DELETE_PREVIEW -> new DeletionPreview(Data.text(p, "type"), Data.id(p, "id"), 1,
            "SYNTHETIC", "Test record - not SQL data", !blocked, blocked ? "TEST dependency blocker" : "");
        case DELETE_RECORD -> {
          DeletionRequest r = Json.convert(p.get("request"), DeletionRequest.class);
          yield new DeletionPreview(r.type(), r.entityId(), r.revision(), "SYNTHETIC", "Test record", true, "");
        }
        case REVERSAL_PREVIEW -> new ReversalPreview(1, 1, 1, "SYNTHETIC", "Test receipt", 2, 2,
            Money.of("700000"), Money.of("600000"), !blocked, blocked ? "TEST movement blocker" : "");
        case REVERSE_RECEIPT -> 2L;
        default -> base.execute(operation, bearer, p);
      };
    }
  }
  @FunctionalInterface private interface Case { void run(AppContext app, Fixture fixture) throws Exception; }
  private static void application(Case body) throws Exception {
    Fixture fixture = new Fixture();
    try (ApiServer server = new ApiServer(new InetSocketAddress("127.0.0.1", 0), null, fixture)) {
      server.start();
      HttpApiClient gateway = new HttpApiClient(URI.create("http://127.0.0.1:" + server.port()));
      ApiSession session = Json.convert(gateway.call(Operation.LOGIN, "", Data.map("username", "MANAGER")),
          ApiSession.class);
      AppContext app = new AppContext(gateway);
      try { edt(() -> { app.acceptSession(session); return null; }); idle(app); body.run(app, fixture); }
      finally { edt(() -> { app.shell().dispose(); for (Window w : Window.getWindows()) { w.dispose(); } return null;
          }); }
    }
  }
  private static void open(AppContext app, String key) throws Exception {
    edt(() -> { Navigation.open(app, Navigation.destinations(app).stream().filter(d -> d.permission() ==
        (key.equals("receive") ? Operation.RECEIVE : Operation.PRODUCT_SEARCH)).findFirst().orElseThrow()); return null;
        });
    idle(app);
  }
  private static void product(AppContext app, String cost) throws Exception {
    open(app, "products");
    edt(() -> { find(app.shell().current(), JTable.class, null).setRowSelectionInterval(0, 0);
      button(app, "S\u1eeda d\u00f2ng ch\u1ecdn").doClick(); return null; }); idle(app);
    check(edt(() -> app.shell().current() instanceof EditDialog), "Expected real product editor");
    text(app, "productCost", cost);
  }
  private static void receipt(AppContext app) throws Exception {
    open(app, "receive"); text(app, "stockSku", "DEMO-24MR400-B");
    edt(() -> { button(app, "T\u00ecm s\u1ea3n ph\u1ea9m").doClick(); return null; }); idle(app);
    text(app, "stockCost", "2500000"); text(app, "stockReference", "TEST synthetic receipt");
  }
  private static void select(AppContext app, boolean receipt) throws Exception {
    open(app, receipt ? "receive" : "products");
    if (receipt) { edt(() -> { find(app.shell().current(), JTabbedPane.class, "receiveTabs").setSelectedIndex(1); return
        null; }); idle(app); }
    edt(() -> { find(app.shell().current(), JTable.class, null).setRowSelectionInterval(0, 0); return null; });
  }
  public static void main(String[] args) throws Exception {
    System.setProperty("dienmay.stateDir", Files.createTempDirectory("dm-modal-tests-").toString());
    edt(() -> { vn.dienmay.client.ui.Ui.installTheme(); return null; });
    TestKit tests = new TestKit();
    tests.test("Product below cost: Return keeps exact form, sends nothing", () -> application((app, f) -> {
      product(app, "1100000"); act(app, () -> button(app, "editorSave").doClick(), choose("priceConfirmationDialog",
          BACK));
      equal(0, f.count(Operation.PRODUCT_SAVE)); equal("1100000", edt(() -> find(app.shell().current(),
          JTextField.class, "productCost").getText()));
    }));
    tests.test("Product below cost: closing warning never accepts", () -> application((app, f) -> {
      product(app, "1100000"); act(app, () -> button(app, "editorSave").doClick(), new Step("priceConfirmationDialog", d
          -> d.dispatchEvent(new WindowEvent(d, WindowEvent.WINDOW_CLOSING))));
      equal(0, f.count(Operation.PRODUCT_SAVE));
    }));
    tests.test("Product below cost: one submit with exact tuple; repeated click ignored", () -> application((app, f) ->
        {
      product(app, "1100000"); AbstractButton save = edt(() -> button(app, "editorSave"));
      act(app, () -> { save.doClick(); save.doClick(); }, new Step("priceConfirmationDialog", d -> {
        String details = all(d).stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast)
            .map(JTextArea::getText).reduce("", String::concat);
        check(details.contains("-9.09%"), "Missing exact rounded loss percentage");
        find(d, JOptionPane.class, null).setValue(SAVE);
      }));
      equal(1, f.count(Operation.PRODUCT_SAVE));
      Map<String, Object> p = f.payloads.get(Operation.PRODUCT_SAVE);
      Item item = Json.convert(p.get("item"), Item.class);
      PriceConfirmation ack = Json.convert(p.get("priceConfirmation"), PriceConfirmation.class);
      check(ack.matches(item.id(), item.revision(), item.costPrice(), item.sellingPrice()),
          "Unbound price acknowledgment");
    }));
    for (String cost : List.of("600000", "1000000", "0")) {
      tests.test("Product no loss / cost=" + cost + ": no warning or acknowledgment", () -> application((app, f) -> {
        product(app, cost); act(app, () -> button(app, "editorSave").doClick());
        equal(1, f.count(Operation.PRODUCT_SAVE));
            check(!f.payloads.get(Operation.PRODUCT_SAVE).containsKey("priceConfirmation"),
            "Unnecessary acknowledgment");
      }));
    }
    tests.test("Product negative price: hard validation before soft confirmation", () -> application((app, f) -> {
      product(app, "1100000"); text(app, "productPrice", "-1");
      act(app, () -> button(app, "editorSave").doClick(), new Step(null, d -> {
        equal(JOptionPane.ERROR_MESSAGE, find(d, JOptionPane.class, null).getMessageType()); find(d, JOptionPane.class,
            null).setValue(JOptionPane.OK_OPTION);
      })); equal(0, f.count(Operation.PRODUCT_SAVE));
    }));
    tests.test("Product failed save retains form; no optimistic success or retry", () -> application((app, f) -> {
      f.failSave = true; product(app, "1100000"); act(app, () -> button(app, "editorSave").doClick(),
          choose("priceConfirmationDialog", SAVE), choose(null, JOptionPane.OK_OPTION));
      equal(1, f.count(Operation.PRODUCT_SAVE)); check(edt(() -> app.shell().current() instanceof EditDialog),
          "Form lost on error");
      check(edt(() -> button(app, "editorSave").isEnabled()), "Cannot correct failed form");
    }));
    tests.test("Receive below cost: Return makes no stock request", () -> application((app, f) -> {
      receipt(app); act(app, () -> button(app, "stockSubmit").doClick(), choose("priceConfirmationDialog", BACK));
      equal(0, f.count(Operation.RECEIVE)); equal("2500000", edt(() -> find(app.shell().current(), JTextField.class,
          "stockCost").getText()));
    }));
    tests.test("Receive: explicit two confirmations bind prices and submit once", () -> application((app, f) -> {
      receipt(app); act(app, () -> button(app, "stockSubmit").doClick(), choose("priceConfirmationDialog", RECEIVE),
          choose(null, JOptionPane.YES_OPTION), choose(null, JOptionPane.OK_OPTION));
      equal(1, f.count(Operation.RECEIVE)); InventoryRequest r =
          Json.convert(f.payloads.get(Operation.RECEIVE).get("request"), InventoryRequest.class);
      check(r.priceConfirmation().matches(2, 1, Money.of("2500000"), Money.of("2400000")), "Incorrect receive tuple");
      equal(0, f.count(Operation.PRODUCT_SAVE));
    }));
    tests.test("Receive: cancel final stock confirmation also sends nothing", () -> application((app, f) -> {
      receipt(app); act(app, () -> button(app, "stockSubmit").doClick(), choose("priceConfirmationDialog", RECEIVE),
          choose(null, JOptionPane.NO_OPTION)); equal(0, f.count(Operation.RECEIVE));
    }));
    tests.test("Delete: preview then cancel never commits", () -> application((app, f) -> {
      select(app, false); act(app, () -> button(app, "recordDelete").doClick(), admin("Quay l\u1ea1i", "", false));
      equal(1, f.count(Operation.DELETE_PREVIEW)); equal(0, f.count(Operation.DELETE_RECORD));
    }));
    tests.test("Delete: required reason, exact revision and single commit", () -> application((app, f) -> {
      select(app, false); act(app, () -> button(app, "recordDelete").doClick(), admin(DELETE,
          "TEST only: unused synthetic record", false));
      equal(1, f.count(Operation.DELETE_RECORD)); DeletionRequest r =
          Json.convert(f.payloads.get(Operation.DELETE_RECORD).get("request"), DeletionRequest.class);
      equal("product", r.type()); equal(1L, r.entityId()); equal(1, r.revision());
          check(r.reason().contains("TEST only"), "Missing reason");
    }));
    tests.test("Delete: dependency blocker prevents confirmation and commit", () -> application((app, f) -> {
      f.blocked = true; select(app, false); act(app, () -> button(app, "recordDelete").doClick(), choose(null,
          JOptionPane.OK_OPTION)); equal(0, f.count(Operation.DELETE_RECORD));
    }));
    tests.test("Delete: empty reason opens error, then cancel sends no delete", () -> application((app, f) -> {
      select(app, false); act(app, () -> button(app, "recordDelete").doClick(), admin(DELETE, "", false), choose(null,
          JOptionPane.OK_OPTION), admin("Quay l\u1ea1i", "", false)); equal(0, f.count(Operation.DELETE_RECORD));
    }));
    tests.test("Reversal: explicit posted warning, cancel makes no mutation", () -> application((app, f) -> {
      select(app, true); act(app, () -> button(app, "recordDelete").doClick(), admin("Quay l\u1ea1i", "", true));
          equal(0, f.count(Operation.REVERSE_RECEIPT)); equal(0, f.count(Operation.DELETE_RECORD));
    }));
    tests.test("Reversal: confirmation submits compensating API, never hard delete", () -> application((app, f) -> {
      select(app, true); act(app, () -> button(app, "recordDelete").doClick(), admin(REVERSE,
          "TEST only: undo synthetic receipt", true), choose(null, JOptionPane.OK_OPTION));
      equal(1, f.count(Operation.REVERSE_RECEIPT)); equal(0, f.count(Operation.DELETE_RECORD));
      ReversalRequest r = Json.convert(f.payloads.get(Operation.REVERSE_RECEIPT).get("request"), ReversalRequest.class);
          equal(1L, r.movementId()); equal(1, r.productRevision());
    }));
    tests.test("Reversal: backend preview blocker prevents commit", () -> application((app, f) -> {
      f.blocked = true; select(app, true); act(app, () -> button(app, "recordDelete").doClick(), choose(null,
          JOptionPane.OK_OPTION)); equal(0, f.count(Operation.REVERSE_RECEIPT));
    }));
    tests.test("Forced password screen retains all custom window controls", () -> application((app, f) -> {
      ApiSession old = app.session(); edt(() -> { app.acceptSession(new ApiSession(old.token(), old.employeeId(),
          old.name(), old.role(), true, Instant.now().plusSeconds(300))); return null; });
      for (String name : List.of("windowMinimize", "windowRestore", "windowClose")) { check(edt(() -> find(app.shell(),
          AbstractButton.class, name).isShowing()), "Missing forced-password chrome"); }
      check(edt(() -> app.shell().isUndecorated()), "Native decoration returned");
    }));
    tests.finish("Interactive modal / SYNTHETIC HTTP ONLY / " + java.awt.Toolkit.getDefaultToolkit().getScreenSize());
  }
}
