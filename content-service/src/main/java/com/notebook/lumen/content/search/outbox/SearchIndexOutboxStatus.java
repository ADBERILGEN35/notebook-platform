package com.notebook.lumen.content.search.outbox;

public enum SearchIndexOutboxStatus {
  PENDING,
  PROCESSING,
  PROCESSED,
  FAILED,
  CANCELLED
}
