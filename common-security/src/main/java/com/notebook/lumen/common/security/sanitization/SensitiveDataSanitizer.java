package com.notebook.lumen.common.security.sanitization;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class SensitiveDataSanitizer {
  public static final String MASK = "****";

  private static final List<Pattern> SENSITIVE_KEY_PATTERNS =
      List.of(
          Pattern.compile(".*password.*", Pattern.CASE_INSENSITIVE),
          Pattern.compile(".*token.*", Pattern.CASE_INSENSITIVE),
          Pattern.compile(".*secret.*", Pattern.CASE_INSENSITIVE),
          Pattern.compile(".*key.*", Pattern.CASE_INSENSITIVE),
          Pattern.compile(".*private.*", Pattern.CASE_INSENSITIVE),
          Pattern.compile(".*authorization.*", Pattern.CASE_INSENSITIVE),
          Pattern.compile(".*cookie.*", Pattern.CASE_INSENSITIVE));

  private SensitiveDataSanitizer() {}

  public static boolean isSensitiveKey(String key) {
    if (key == null || key.isBlank()) {
      return false;
    }
    String normalized = key.toLowerCase(Locale.ROOT);
    return SENSITIVE_KEY_PATTERNS.stream()
        .anyMatch(pattern -> pattern.matcher(normalized).matches());
  }

  public static Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return Map.of();
    }
    Map<String, Object> sanitized = new LinkedHashMap<>();
    metadata.forEach(
        (key, value) -> sanitized.put(key, isSensitiveKey(key) ? MASK : sanitizeValue(value)));
    return Map.copyOf(sanitized);
  }

  public static String validationMessageFor(String field, String message) {
    if (isSensitiveKey(field)) {
      return "Invalid sensitive value";
    }
    return message;
  }

  private static Object sanitizeValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> sanitized = new LinkedHashMap<>();
      map.forEach(
          (key, nestedValue) ->
              sanitized.put(
                  String.valueOf(key),
                  isSensitiveKey(String.valueOf(key)) ? MASK : sanitizeValue(nestedValue)));
      return Map.copyOf(sanitized);
    }
    if (value instanceof Collection<?> collection) {
      return collection.stream().map(SensitiveDataSanitizer::sanitizeValue).toList();
    }
    if (value instanceof Set<?> set) {
      return set.stream().map(SensitiveDataSanitizer::sanitizeValue).toList();
    }
    if (value != null && value.getClass().isArray()) {
      int length = Array.getLength(value);
      Object[] sanitized = new Object[length];
      for (int index = 0; index < length; index++) {
        sanitized[index] = sanitizeValue(Array.get(value, index));
      }
      return List.of(sanitized);
    }
    return value;
  }
}
