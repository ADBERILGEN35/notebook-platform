package com.notebook.lumen.gateway.admin.audit;

import java.util.Locale;

enum AuditSource {
  IDENTITY("identity", "identity-service"),
  WORKSPACE("workspace", "workspace-service"),
  CONTENT("content", "content-service");

  private final String value;
  private final String audience;

  AuditSource(String value, String audience) {
    this.value = value;
    this.audience = audience;
  }

  String value() {
    return value;
  }

  String audience() {
    return audience;
  }

  static AuditSource fromValue(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    for (AuditSource source : values()) {
      if (source.value.equals(normalized)) {
        return source;
      }
    }
    return null;
  }
}
