package vn.dienmay.tests;

import java.util.Objects;

/** Dependency-free runner. Every failed test is printed and makes the process exit unsuccessfully. */
public final class TestKit {
  @FunctionalInterface
  public interface Checked {
    void run() throws Exception;
  }

  private int passed;
  private int failed;

  public void test(String name, Checked action) {
    try {
      action.run();
      passed++;
      System.out.println("PASS | " + name);
    } catch (Throwable error) {
      failed++;
      System.out.println("FAIL | " + name + " | " + error);
      error.printStackTrace(System.out);
    }
  }

  public void finish(String suite) {
    System.out.println("RESULT | " + suite + " | passed=" + passed + " failed=" + failed);
    if (failed != 0) {
      throw new AssertionError("Test failures: " + failed);
    }
  }

  public static void check(boolean value, String message) {
    if (!value) {
      throw new AssertionError(message);
    }
  }

  public static void equal(Object expected, Object actual) {
    if (!Objects.equals(expected, actual)) {
      throw new AssertionError("Expected " + expected + " but got " + actual);
    }
  }

  public static <T extends Throwable> T throwsType(Class<T> type, Checked action) throws Exception {
    try {
      action.run();
    } catch (Throwable error) {
      if (type.isInstance(error)) {
        return type.cast(error);
      }
      throw new AssertionError("Expected " + type.getSimpleName() + " but got " + error, error);
    }
    throw new AssertionError("Expected " + type.getSimpleName() + "; no exception was thrown");
  }
}
