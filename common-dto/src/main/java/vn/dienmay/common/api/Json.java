package vn.dienmay.common.api;

import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bounded JSON codec for the explicit API data types. Never deserializes Java objects or class names. */
public final class Json {
  private static final int MAX_DEPTH = 48;
  private static final int MAX_TEXT = 8 * 1024 * 1024;

  private Json() {}

  public static Object parse(String text) {
    if (text == null || text.length() > MAX_TEXT) {
      throw new IllegalArgumentException("JSON body is missing or too large");
    }
    Parser parser = new Parser(text);
    Object value = parser.value(0);
    parser.space();
    if (parser.pos != text.length()) {
      throw new IllegalArgumentException("Unexpected trailing JSON data");
    }
    return value;
  }

  public static String stringify(Object value) {
    StringBuilder result = new StringBuilder();
    write(value, result, 0);
    if (result.length() > MAX_TEXT) {
      throw new IllegalArgumentException("JSON response is too large");
    }
    return result.toString();
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> object(Object value) {
    if (!(value instanceof Map<?, ?> map) || map.keySet().stream().anyMatch(k -> !(k instanceof String))) {
      throw new IllegalArgumentException("Expected a JSON object");
    }
    return (Map<String, Object>) map;
  }

  public static <T> T convert(Object value, Class<T> type) {
    return type.cast(convertValue(value, type));
  }

  public static <T> List<T> list(Object value, Class<T> type) {
    if (!(value instanceof List<?> values)) {
      throw new IllegalArgumentException("Expected a JSON array");
    }
    return values.stream().map(v -> convert(v, type)).toList();
  }

  private static Object convertValue(Object value, Type target) {
    if (target instanceof ParameterizedType parameterized) {
      if (parameterized.getRawType() == List.class) {
        if (!(value instanceof List<?> list)) {
          throw new IllegalArgumentException("Expected a JSON array");
        }
        return list.stream().map(v -> convertValue(v, parameterized.getActualTypeArguments()[0])).toList();
      }
      throw new IllegalArgumentException("Unsupported parameterized API type");
    }
    Class<?> type = (Class<?>) target;
    if (value == null) {
      if (type.isPrimitive()) {
        throw new IllegalArgumentException("Missing required numeric/boolean field");
      }
      return null;
    }
    if (type == Object.class || type.isInstance(value)) {
      return value;
    }
    if (type == long.class || type == Long.class) {
      return number(value).longValueExact();
    }
    if (type == int.class || type == Integer.class) {
      return number(value).intValueExact();
    }
    if (type == BigDecimal.class) {
      return number(value);
    }
    if (type == boolean.class || type == Boolean.class) {
      if (!(value instanceof Boolean)) {
        throw new IllegalArgumentException("Expected a boolean");
      }
      return value;
    }
    if (type == String.class) {
      throw new IllegalArgumentException("Expected a string");
    }
    if (type == UUID.class) {
      return UUID.fromString(convert(value, String.class));
    }
    if (type == LocalDate.class) {
      return LocalDate.parse(convert(value, String.class));
    }
    if (type == Instant.class) {
      return Instant.parse(convert(value, String.class));
    }
    if (type.isEnum()) {
      String name = convert(value, String.class);
      for (Object constant : type.getEnumConstants()) {
        if (((Enum<?>) constant).name().equals(name)) {
          return constant;
        }
      }
      throw new IllegalArgumentException("Unknown enum value: " + name);
    }
    if (type.isRecord() && type.getPackageName().startsWith("vn.dienmay.")) {
      Map<String, Object> fields = object(value);
      // Explicit migration for pre-v3 durable stock requests; no arbitrary missing fields accepted.
      if (type == vn.dienmay.common.model.InventoryRequest.class && !fields.containsKey("identifierType")) {
        fields = new LinkedHashMap<>(fields);
        fields.put("identifierType", "SERIAL");
      }
      if (type == vn.dienmay.common.model.InventoryRequest.class && !fields.containsKey("priceConfirmation")) {
        fields = new LinkedHashMap<>(fields);
        fields.put("priceConfirmation", null); // v4 pending files retain their original fingerprint.
      }
      RecordComponent[] components = type.getRecordComponents();
      if (fields.size() != components.length) {
        throw new IllegalArgumentException("Unexpected or missing fields for " + type.getSimpleName());
      }
      Class<?>[] types = new Class<?>[components.length];
      Object[] arguments = new Object[components.length];
      for (int i = 0; i < components.length; i++) {
        RecordComponent component = components[i];
        if (!fields.containsKey(component.getName())) {
          throw new IllegalArgumentException("Missing field: " + component.getName());
        }
        types[i] = component.getType();
        arguments[i] = convertValue(fields.get(component.getName()), component.getGenericType());
      }
      try {
        Constructor<?> constructor = type.getDeclaredConstructor(types);
        return constructor.newInstance(arguments);
      } catch (ReflectiveOperationException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof IllegalArgumentException validation) {
          throw validation;
        }
        throw new IllegalArgumentException("Invalid " + type.getSimpleName(), ex);
      }
    }
    throw new IllegalArgumentException("Unsupported API data type: " + type.getSimpleName());
  }

  public static BigDecimal number(Object value) {
    if (value instanceof BigDecimal number) {
      return number;
    }
    if (value instanceof Byte || value instanceof Short || value instanceof Integer
        || value instanceof Long) {
      return BigDecimal.valueOf(((Number) value).longValue());
    }
    throw new IllegalArgumentException("Expected an exact JSON number");
  }

  private static void write(Object value, StringBuilder out, int depth) {
    if (depth > MAX_DEPTH || out.length() > MAX_TEXT) {
      throw new IllegalArgumentException("JSON complexity limit exceeded");
    }
    if (value == null) {
      out.append("null");
    } else if (value instanceof String || value instanceof UUID || value instanceof LocalDate
        || value instanceof Instant) {
      quote(value.toString(), out);
    } else if (value instanceof Enum<?> e) {
      quote(e.name(), out);
    } else if (value instanceof Boolean || value instanceof BigDecimal || value instanceof Integer
        || value instanceof Long || value instanceof Short || value instanceof Byte) {
      out.append(value);
    } else if (value instanceof Map<?, ?> map) {
      out.append('{');
      boolean comma = false;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (!(entry.getKey() instanceof String key)) {
          throw new IllegalArgumentException("JSON object keys must be strings");
        }
        if (comma) {
          out.append(',');
        }
        quote(key, out);
        out.append(':');
        write(entry.getValue(), out, depth + 1);
        comma = true;
      }
      out.append('}');
    } else if (value instanceof Collection<?> values) {
      out.append('[');
      boolean comma = false;
      for (Object element : values) {
        if (comma) {
          out.append(',');
        }
        write(element, out, depth + 1);
        comma = true;
      }
      out.append(']');
    } else if (value.getClass().isRecord() && value.getClass().getPackageName().startsWith("vn.dienmay.")) {
      Map<String, Object> fields = new LinkedHashMap<>();
      for (RecordComponent component : value.getClass().getRecordComponents()) {
        try {
          fields.put(component.getName(), component.getAccessor().invoke(value));
        } catch (ReflectiveOperationException ex) {
          throw new IllegalArgumentException("Cannot encode API record", ex);
        }
      }
      write(fields, out, depth + 1);
    } else {
      throw new IllegalArgumentException("Not an API value: " + value.getClass().getSimpleName());
    }
  }

  private static void quote(String value, StringBuilder out) {
    out.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        default -> {
          if (c < 32 || Character.isSurrogate(c)) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    out.append('"');
  }

  private static final class Parser {
    private final String text;
    private int pos;

    private Parser(String text) {
      this.text = text;
    }

    private void space() {
      while (pos < text.length() && " \r\n\t".indexOf(text.charAt(pos)) >= 0) {
        pos++;
      }
    }

    private char take() {
      if (pos >= text.length()) {
        throw new IllegalArgumentException("Unexpected end of JSON");
      }
      return text.charAt(pos++);
    }

    private void expect(char expected) {
      space();
      if (take() != expected) {
        throw new IllegalArgumentException("Invalid JSON delimiter");
      }
    }

    private boolean consume(char c) {
      space();
      if (pos < text.length() && text.charAt(pos) == c) {
        pos++;
        return true;
      }
      return false;
    }

    private Object value(int depth) {
      if (depth > MAX_DEPTH) {
        throw new IllegalArgumentException("JSON nesting limit exceeded");
      }
      space();
      if (pos >= text.length()) {
        throw new IllegalArgumentException("Missing JSON value");
      }
      return switch (text.charAt(pos)) {
        case '{' -> objectValue(depth + 1);
        case '[' -> arrayValue(depth + 1);
        case '"' -> string();
        case 't' -> literal("true", Boolean.TRUE);
        case 'f' -> literal("false", Boolean.FALSE);
        case 'n' -> literal("null", null);
        default -> numeric();
      };
    }

    private Object literal(String literal, Object value) {
      if (!text.startsWith(literal, pos)) {
        throw new IllegalArgumentException("Invalid JSON literal");
      }
      pos += literal.length();
      return value;
    }

    private Map<String, Object> objectValue(int depth) {
      expect('{');
      Map<String, Object> map = new LinkedHashMap<>();
      if (consume('}')) {
        return map;
      }
      do {
        space();
        String key = string();
        expect(':');
        if (map.containsKey(key)) {
          throw new IllegalArgumentException("Duplicate JSON key");
        }
        map.put(key, value(depth));
        if (map.size() > 100_000) {
          throw new IllegalArgumentException("Too many JSON fields");
        }
      } while (consume(','));
      expect('}');
      return map;
    }

    private List<Object> arrayValue(int depth) {
      expect('[');
      List<Object> list = new ArrayList<>();
      if (consume(']')) {
        return list;
      }
      do {
        list.add(value(depth));
        if (list.size() > 100_000) {
          throw new IllegalArgumentException("Too many JSON array items");
        }
      } while (consume(','));
      expect(']');
      return list;
    }

    private String string() {
      if (take() != '"') {
        throw new IllegalArgumentException("Expected a JSON string");
      }
      StringBuilder value = new StringBuilder();
      while (true) {
        char c = take();
        if (c == '"') {
          String result = value.toString();
          for (int i = 0; i < result.length(); i++) {
            char ch = result.charAt(i);
            if (Character.isHighSurrogate(ch)) {
              if (++i >= result.length() || !Character.isLowSurrogate(result.charAt(i))) {
                throw new IllegalArgumentException("Unpaired JSON surrogate");
              }
            } else if (Character.isLowSurrogate(ch)) {
              throw new IllegalArgumentException("Unpaired JSON surrogate");
            }
          }
          return result;
        }
        if (c < 32) {
          throw new IllegalArgumentException("Control character in JSON string");
        }
        if (c == '\\') {
          c = take();
          c = switch (c) {
            case '"', '\\', '/' -> c;
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'u' -> unicode();
            default -> throw new IllegalArgumentException("Invalid JSON escape");
          };
        }
        value.append(c);
      }
    }

    private char unicode() {
      int value = 0;
      for (int i = 0; i < 4; i++) {
        int digit = Character.digit(take(), 16);
        if (digit < 0) {
          throw new IllegalArgumentException("Invalid Unicode escape");
        }
        value = value * 16 + digit;
      }
      return (char) value;
    }

    private BigDecimal numeric() {
      int begin = pos;
      while (pos < text.length() && "-+0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
        pos++;
      }
      String number = text.substring(begin, pos);
      if (number.length() > 128
          || !number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
        throw new IllegalArgumentException("Invalid JSON number");
      }
      BigDecimal value = new BigDecimal(number);
      if (Math.abs((long) value.scale()) > 1000) {
        throw new IllegalArgumentException("JSON exponent limit exceeded");
      }
      return value;
    }
  }
}
