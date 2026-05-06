package com.notebook.lumen.content.search.outbox.application;

public record SearchIndexOutboxStatusView(
    long pendingCount, long processingCount, long failedCount, Long oldestPendingAgeSeconds) {}
