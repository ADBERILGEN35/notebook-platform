package com.notebook.lumen.workspace.admin.retention;

import java.util.Optional;

public enum WorkspaceRetentionLegalHoldScope {
  ALL_PLATFORM(true),
  WORKSPACE(true),
  CONTENT(false),
  NOTE(false),
  USER(false);

  private final boolean fullyBlocking;

  WorkspaceRetentionLegalHoldScope(boolean fullyBlocking) {
    this.fullyBlocking = fullyBlocking;
  }

  public boolean fullyBlocking() {
    return fullyBlocking;
  }

  public static Optional<WorkspaceRetentionLegalHoldScope> fromString(String raw) {
    if (raw == null) return Optional.empty();
    String trimmed = raw.trim().toUpperCase();
    for (WorkspaceRetentionLegalHoldScope value : values()) {
      if (value.name().equals(trimmed)) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }
}
