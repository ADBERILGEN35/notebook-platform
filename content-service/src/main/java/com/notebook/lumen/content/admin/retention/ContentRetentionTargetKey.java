package com.notebook.lumen.content.admin.retention;

import java.util.Optional;

public enum ContentRetentionTargetKey {
  CONTENT_NOTE_VERSIONS("content.note_versions", ContentRetentionTargetStatus.DRY_RUN_READY),
  CONTENT_COMMENTS("content.comments", ContentRetentionTargetStatus.DRY_RUN_READY),
  CONTENT_SEARCH_DOCUMENTS("content.search_documents", ContentRetentionTargetStatus.DRY_RUN_READY),
  CONTENT_NOTES("content.notes", ContentRetentionTargetStatus.INVENTORY_ONLY);

  private final String key;
  private final ContentRetentionTargetStatus defaultStatus;

  ContentRetentionTargetKey(String key, ContentRetentionTargetStatus defaultStatus) {
    this.key = key;
    this.defaultStatus = defaultStatus;
  }

  public String key() {
    return key;
  }

  public ContentRetentionTargetStatus defaultStatus() {
    return defaultStatus;
  }

  public static Optional<ContentRetentionTargetKey> fromKey(String raw) {
    if (raw == null) return Optional.empty();
    String trimmed = raw.trim();
    for (ContentRetentionTargetKey value : values()) {
      if (value.key.equals(trimmed)) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }
}
