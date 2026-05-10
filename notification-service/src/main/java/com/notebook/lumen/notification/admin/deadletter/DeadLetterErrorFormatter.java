package com.notebook.lumen.notification.admin.deadletter;

final class DeadLetterErrorFormatter {

  private DeadLetterErrorFormatter() {}

  static String lastErrorCode(String lastError) {
    if (lastError == null || lastError.isBlank()) {
      return "UNKNOWN";
    }
    String t = lastError.trim();
    int colon = t.indexOf(':');
    if (colon > 1 && colon <= 72) {
      String code = t.substring(0, colon).trim();
      if (code.matches("[A-Za-z0-9_\\-]+") && code.length() <= 64) {
        return code.toUpperCase().replace('-', '_');
      }
    }
    return "UNKNOWN";
  }

  static String lastErrorSummary(String lastError) {
    if (lastError == null || lastError.isBlank()) {
      return "";
    }
    String t = lastError.trim().replaceAll("\\p{Cntrl}", " ");
    int colon = t.indexOf(':');
    if (colon > 0 && colon < t.length() - 1) {
      t = t.substring(colon + 1).trim();
    }
    if (t.length() > 240) {
      return t.substring(0, 237) + "...";
    }
    return t;
  }
}
