package com.notebook.lumen.search.admin.retention;

import java.util.Optional;

public enum SearchRetentionLegalHoldScope {
  ALL_PLATFORM(true),
  CONTENT(true),
  WORKSPACE(false),
  NOTE(false),
  USER(false);

  private final boolean fullyBlocking;

  SearchRetentionLegalHoldScope(boolean fullyBlocking) {
    this.fullyBlocking = fullyBlocking;
  }

  public boolean fullyBlocking() {
    return fullyBlocking;
  }

  public static Optional<SearchRetentionLegalHoldScope> fromString(String raw) {
    if (raw == null) return Optional.empty();
    String trimmed = raw.trim().toUpperCase();
    for (SearchRetentionLegalHoldScope value : values()) {
      if (value.name().equals(trimmed)) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }
}
