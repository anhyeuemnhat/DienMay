package vn.dienmay.tools;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Offline, deliberately limited project convention checker and token-preserving whitespace formatter. */
public final class SourceStyle {
  private static final int WIDTH = 120;
  private static final Pattern PACKAGE = Pattern.compile("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*");
  private static final Pattern TYPE = Pattern.compile("[A-Z][A-Za-z0-9]*");
  private static final Pattern MEMBER = Pattern.compile("[a-z][A-Za-z0-9]*");
  private static final Pattern CONSTANT = Pattern.compile("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*");
  private static final List<String> ROOTS = List.of(
      "common-dto", "backend-server", "client-swing", "tests", "tools");

  private SourceStyle() {}

  public static void main(String[] args) throws Exception {
    String mode = args.length == 0 ? "check" : args[0];
    if (mode.equals("self-test")) {
      selfTest();
      return;
    }
    if (!List.of("check", "format").contains(mode)) {
      throw new IllegalArgumentException("Usage: SourceStyle check|format|self-test [project-root]");
    }
    Path root = Path.of(args.length > 1 ? args[1] : ".").toAbsolutePath().normalize();
    List<Path> sources = new ArrayList<>();
    for (String module : ROOTS) {
      Path source = root.resolve(module).resolve("src/main/java");
      try (var paths = Files.walk(source)) {
        sources.addAll(paths.filter(p -> p.toString().endsWith(".java")).sorted().toList());
      }
    }
    int changed = 0;
    List<String> errors = new ArrayList<>();
    for (Path source : sources) {
      String text = Files.readString(source, StandardCharsets.UTF_8);
      String formatted = format(text);
      if (!text.equals(formatted)) {
        if (mode.equals("format")) {
          Files.writeString(source, formatted, StandardCharsets.UTF_8);
          changed++;
        } else {
          errors.add(root.relativize(source) + ": formatting differs; run ant format");
        }
      }
      lexicalChecks(source, formatted, errors);
    }
    syntaxChecks(sources, errors);
    for (String error : errors) {
      System.out.println("STYLE ERROR | " + error);
    }
    System.out.println("STYLE | mode=" + mode + " files=" + sources.size()
        + " changed=" + changed + " errors=" + errors.size());
    System.out.println("SCOPE | Project subset, not full Google Java Style / Checkstyle certification.");
    if (!errors.isEmpty()) {
      throw new IllegalStateException("Source convention violations: " + errors.size());
    }
  }

  /** Only whitespace outside literals is wrapped. Imports are sorted without removing any import. */
  static String format(String source) {
    String normal = source.replace("\r\n", "\n").replace('\r', '\n');
    List<String> lines = new ArrayList<>();
    boolean blockComment = false;
    for (String original : normal.split("\n", -1)) {
      String line = original.stripTrailing();
      int leading = 0;
      while (leading < line.length() && Character.isWhitespace(line.charAt(leading))) {
        leading++;
      }
      line = line.substring(0, leading).replace("\t", "  ") + line.substring(leading);
      boolean commentLine = blockComment || line.stripLeading().startsWith("//")
          || line.stripLeading().startsWith("/*") || line.stripLeading().startsWith("*");
      if (line.stripLeading().startsWith("/*") && !line.contains("*/")) {
        blockComment = true;
      }
      if (blockComment && line.contains("*/")) {
        blockComment = false;
      }
      if (!commentLine && !line.contains("\"\"\"")) {
        int indentation = line.length() - line.stripLeading().length();
        String continuation = " ".repeat(indentation + 4);
        while (line.length() > WIDTH) {
          int split = breakPosition(line);
          if (split < 0) {
            break;
          }
          lines.add(line.substring(0, split).stripTrailing());
          line = continuation + line.substring(split).stripLeading();
        }
      }
      lines.add(line);
    }
    int first = -1;
    int last = -1;
    List<String> imports = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      if (lines.get(i).startsWith("import ")) {
        if (first < 0) {
          first = i;
        }
        last = i;
        imports.add(lines.get(i));
      }
    }
    if (first >= 0) {
      boolean safe = true;
      for (int i = first; i <= last; i++) {
        safe &= lines.get(i).isBlank() || lines.get(i).startsWith("import ");
      }
      if (safe) {
        imports.sort(Comparator.comparing((String s) -> !s.startsWith("import static "))
            .thenComparing(Comparator.naturalOrder()));
        int staticCount = (int) imports.stream().filter(s -> s.startsWith("import static ")).count();
        if (staticCount > 0 && staticCount < imports.size()) {
          imports.add(staticCount, "");
        }
        lines.subList(first, last + 1).clear();
        lines.addAll(first, imports);
      }
    }
    while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
      lines.remove(lines.size() - 1);
    }
    return String.join("\n", lines) + "\n";
  }

  private static int breakPosition(String line) {
    boolean string = false;
    boolean character = false;
    boolean escaped = false;
    int candidate = -1;
    int start = line.length() - line.stripLeading().length();
    for (int i = start; i < line.length() && i <= WIDTH; i++) {
      char value = line.charAt(i);
      if (escaped) {
        escaped = false;
        continue;
      }
      if ((string || character) && value == '\\') {
        escaped = true;
        continue;
      }
      if (!character && value == '"') {
        string = !string;
      } else if (!string && value == '\'') {
        character = !character;
      } else if (!string && !character) {
        if (value == '/' && i + 1 < line.length()
            && (line.charAt(i + 1) == '/' || line.charAt(i + 1) == '*')) {
          break;
        }
        if (value == ' ' && i > start + 20) {
          candidate = i;
        }
      }
    }
    return candidate;
  }

  private static void lexicalChecks(Path file, String text, List<String> errors) {
    int number = 0;
    for (String line : text.split("\n")) {
      number++;
      String location = file.getFileName() + ":" + number;
      if (line.contains("\t")) {
        errors.add(location + " tab character");
      }
      int spaces = line.length() - line.stripLeading().length();
      if (spaces % 2 != 0 && !line.stripLeading().startsWith("*")) {
        errors.add(location + " indentation must be a multiple of two spaces");
      }
      if (line.length() > WIDTH) {
        errors.add(location + " exceeds " + WIDTH + " columns (" + line.length() + ")");
      }
      if (line.matches("import .*\\*;")) {
        errors.add(location + " wildcard import");
      }

    }
    if (file.toString().contains("client-swing")) {
      for (String forbidden : List.of("import java.sql.", "import vn.dienmay.server.",
          "com.microsoft.sqlserver", "jdbc:sqlserver", "DriverManager")) {
        if (text.contains(forbidden)) {
          errors.add(file + " client boundary violation: " + forbidden);
        }
      }
    }
  }

  private static void syntaxChecks(List<Path> paths, List<String> errors) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("A full JDK (not JRE) is required");
    }
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(
        diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
      JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics,
          List.of("--release", "17", "-proc:none"), null, manager.getJavaFileObjectsFromPaths(paths));
      for (CompilationUnitTree unit : task.parse()) {
        String file = unit.getSourceFile().getName().replace('\\', '/');
        String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
        if (!PACKAGE.matcher(packageName).matches()
            || !file.endsWith(packageName.replace('.', '/') + "/" + Path.of(file).getFileName())) {
          errors.add(file + " invalid package name or directory");
        }
        new TreeScanner<Void, Void>() {
          @Override
          public Void visitClass(ClassTree tree, Void unused) {
            String name = tree.getSimpleName().toString();
            if (!name.isEmpty() && !TYPE.matcher(name).matches()) {
              errors.add(file + " invalid type name: " + name);
            }
            return super.visitClass(tree, unused);
          }

          @Override
          public Void visitMethod(MethodTree tree, Void unused) {
            String name = tree.getName().toString();
            if (!name.equals("<init>") && !MEMBER.matcher(name).matches()) {
              errors.add(file + " invalid method name: " + name);
            }
            return super.visitMethod(tree, unused);
          }

          @Override
          public Void visitVariable(VariableTree tree, Void unused) {
            String name = tree.getName().toString();
            if (!MEMBER.matcher(name).matches() && !CONSTANT.matcher(name).matches()) {
              errors.add(file + " invalid variable name: " + name);
            }
            return super.visitVariable(tree, unused);
          }
        }.scan(unit, null);
      }
    }
    for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
      if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
        errors.add("Java parse error: " + diagnostic);
      }
    }
  }

  private static void selfTest() throws Exception {
    String input = "package sample;\r\nimport java.util.Map;\r\nimport java.util.List;\r\n"
        + "public class Sample {\r\n\tString value = \" spaces  preserved \";  \r\n}\r\n";
    String formatted = format(input);
    if (!format(formatted).equals(formatted) || formatted.contains("\r") || formatted.contains("\t")
        || formatted.indexOf("import java.util.List") > formatted.indexOf("import java.util.Map")
        || !formatted.contains(" spaces  preserved ")) {
      throw new AssertionError("Formatter must preserve literal content and be idempotent");
    }
    List<String> violations = new ArrayList<>();
    lexicalChecks(Path.of("Bad.java"), "   int bad;\nimport java.util.*;\n", violations);
    if (violations.size() < 2) {
      throw new AssertionError("Checker accepted known invalid indentation and wildcard import");
    }
    System.out.println("PASS | style self-test: idempotence, imports, literals, indentation and wildcard rejection");
  }
}
