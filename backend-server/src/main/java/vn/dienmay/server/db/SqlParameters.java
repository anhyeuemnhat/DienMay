package vn.dienmay.server.db;

/** Counts JDBC placeholders outside T-SqlDatabase literals, quoted identifiers and comments. */
public final class SqlParameters {
  private SqlParameters() {}

  public static int count(String sql) {
    int count = 0;
    char quote = 0;
    boolean lineComment = false;
    int blockDepth = 0;
    for (int i = 0; i < sql.length(); i++) {
      char c = sql.charAt(i);
      char next = i + 1 < sql.length() ? sql.charAt(i + 1) : 0;
      if (lineComment) {
        if (c == '\n' || c == '\r') {
          lineComment = false;
        }
      } else if (blockDepth > 0) {
        if (c == '/' && next == '*') {
          blockDepth++;
          i++;
        } else if (c == '*' && next == '/') {
          blockDepth--;
          i++;
        }
      } else if (quote != 0) {
        if (c == quote) {
          if (next == quote) {
            i++;
          } else {
            quote = 0;
          }
        }
      } else if (c == '-' && next == '-') {
        lineComment = true;
        i++;
      } else if (c == '/' && next == '*') {
        blockDepth = 1;
        i++;
      } else if (c == '\'' || c == '"' || c == '[') {
        quote = c == '[' ? ']' : c;
      } else if (c == '?') {
        count++;
      }
    }
    if (quote != 0 || blockDepth != 0) {
      throw new IllegalArgumentException("Unterminated SqlDatabase literal or comment");
    }
    return count;
  }
}
