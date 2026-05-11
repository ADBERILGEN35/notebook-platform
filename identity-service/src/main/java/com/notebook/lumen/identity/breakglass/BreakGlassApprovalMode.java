package com.notebook.lumen.identity.breakglass;

import java.util.Locale;

public enum BreakGlassApprovalMode {
  DISABLED("disabled"),
  POST_USE_REVIEW("post_use_review"),
  REQUIRED_BEFORE_ISSUE("required_before_issue");

  private final String wire;

  BreakGlassApprovalMode(String wire) {
    this.wire = wire;
  }

  public String wire() {
    return wire;
  }

  public static BreakGlassApprovalMode from(String raw) {
    if (raw == null || raw.isBlank()) {
      return DISABLED;
    }
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    for (BreakGlassApprovalMode value : values()) {
      if (value.wire.equals(normalized)) {
        return value;
      }
    }
    return DISABLED;
  }
}
