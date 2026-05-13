package com.notebook.lumen.content.admin.retention;

import java.util.Optional;

public enum ContentRetentionLegalHoldScope {
  ALL_PLATFORM(true),
  CONTENT(true),
  WORKSPACE(false),
  NOTE(false),
  USER(false);

  private final boolean fullyBlocking;

  ContentRetentionLegalHoldScope(boolean fullyBlocking) {
    this.fullyBlocking = fullyBlocking;
  }

  public boolean fullyBlocking() {
    return fullyBlocking;
  }

  public static Optional<ContentRetentionLegalHoldScope> fromString(String raw) {
    if (raw == null) return Optional.empty();
    String trimmed = raw.trim().toUpperCase();
    for (ContentRetentionLegalHoldScope value : values()) {
      if (value.name().equals(trimmed)) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }
}
