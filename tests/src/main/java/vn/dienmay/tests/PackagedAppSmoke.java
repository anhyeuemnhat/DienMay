package vn.dienmay.tests;

import static vn.dienmay.tests.TestKit.check;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/** Launches the unmodified production entry point with java -jar from an unrelated working directory. */
public final class PackagedAppSmoke {
  private PackagedAppSmoke() {}

  public static void main(String[] args) throws Exception {
    Path jar = Path.of(args.length > 0 ? args[0] : "dist/client-swing.jar").toAbsolutePath();
    var screen = Toolkit.getDefaultToolkit().getScreenSize();
    Path output = Path.of(args.length > 1 ? args[1] : "docs/evidence/packaged",
        screen.width + "x" + screen.height).toAbsolutePath();
    Files.createDirectories(output);
    Path log = output.resolve("java-jar-startup.log");
    Path isolated = Files.createTempDirectory("dm-jar-working-directory-");
    String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    Process process = new ProcessBuilder(java, "-Dfile.encoding=UTF-8", "-jar", jar.toString())
        .directory(isolated.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    try {
      Robot robot = new Robot();
      java.awt.image.BufferedImage screenshot = null;
      boolean painted = false;
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
      while (System.nanoTime() < deadline) {
        check(process.isAlive(), "JAR exited: " + Files.readString(log));
        screenshot = robot.createScreenCapture(new Rectangle(screen));
        Color left = new Color(screenshot.getRGB(20, screen.height / 2));
        painted = left.getRed() > left.getGreen() + 30;
        if (painted) { break; }
        Thread.sleep(250);
      }
      ImageIO.write(screenshot, "png", output.resolve("00-login-from-java-jar.png").toFile());
      check(painted, "Login did not paint the expected packaged background within 20 seconds");
      check(Files.readString(log).isBlank(), "Unexpected startup output: " + Files.readString(log));
      System.out.println("PASS | java -jar from an unrelated working directory: " + jar);
      System.out.println("PASS | No startup exceptions; original application login shown; JAR resource loaded");
      System.out.println("SCOPE | Cold startup only. Authentication requires a configured real backend.");
    } finally {
      process.destroy();
      if (!process.waitFor(2, TimeUnit.SECONDS)) {
        process.destroyForcibly();
      }
      Files.deleteIfExists(isolated);
    }
  }
}
