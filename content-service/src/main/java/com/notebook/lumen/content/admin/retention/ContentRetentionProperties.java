package com.notebook.lumen.content.admin.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "content.retention")
public record ContentRetentionProperties(
    boolean dryRunEnabled,
    int maxCountQueryLimit,
    int noteVersionRetentionDays,
    int commentRetentionDays,
    int searchDocumentRetentionDays,
    boolean includeArchivedNotesOnly) {

  public ContentRetentionProperties {
    if (maxCountQueryLimit <= 0) {
      maxCountQueryLimit = 100_000;
    }
    if (noteVersionRetentionDays <= 0) {
      noteVersionRetentionDays = 365;
    }
    if (commentRetentionDays <= 0) {
      commentRetentionDays = 365;
    }
    if (searchDocumentRetentionDays <= 0) {
      searchDocumentRetentionDays = 90;
    }
  }

  public int retentionDaysFor(ContentRetentionTargetKey target) {
    return switch (target) {
      case CONTENT_NOTE_VERSIONS -> noteVersionRetentionDays;
      case CONTENT_COMMENTS -> commentRetentionDays;
      case CONTENT_SEARCH_DOCUMENTS -> searchDocumentRetentionDays;
      case CONTENT_NOTES -> 0;
    };
  }
}
