package com.notebook.lumen.identity.breakglass;

public enum BreakGlassRevocationSource {
  ADMIN_REVOKE,
  ROTATION,
  EMERGENCY_DISABLE,
  REVIEW_REJECT;

  public String wire() {
    return name();
  }

  public static BreakGlassRevocationSource from(String raw) {
    if (raw == null || raw.isBlank()) {
      return ADMIN_REVOKE;
    }
    try {
      return valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return ADMIN_REVOKE;
    }
  }
}
