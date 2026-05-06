package com.notebook.lumen.search.reindex.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "search_reindex_jobs")
public class SearchReindexJob {
  @Id private UUID id;
  private UUID workspaceId;
  private UUID notebookId;

  @Enumerated(EnumType.STRING)
  private SearchReindexJobStatus status;

  private String requestedByService;

  @Enumerated(EnumType.STRING)
  private SearchReindexMode mode;

  private long totalScanned;
  private long totalIndexed;
  private long totalFailed;
  private long totalArchivedOrphans;
  private String lastCursor;
  private Instant startedAt;
  private Instant completedAt;
  private Instant failedAt;
  private Instant cleanupStartedAt;
  private Instant cleanupCompletedAt;
  private boolean cleanupOrphansRequested;
  private boolean cleanupOrphansExecuted;
  private boolean dryRunCleanup;
  private long cleanupPreviewCount;
  private Instant cleanupPreviewGeneratedAt;
  private String lastError;
  private Instant createdAt;
  private Instant updatedAt;

  protected SearchReindexJob() {}

  public SearchReindexJob(
      UUID id,
      SearchReindexMode mode,
      UUID workspaceId,
      UUID notebookId,
      boolean cleanupOrphansRequested,
      boolean dryRunCleanup,
      String requestedByService,
      Instant now) {
    this.id = id;
    this.mode = mode;
    this.workspaceId = workspaceId;
    this.notebookId = notebookId;
    this.cleanupOrphansRequested = cleanupOrphansRequested;
    this.dryRunCleanup = dryRunCleanup;
    this.requestedByService = requestedByService;
    this.status = SearchReindexJobStatus.PENDING;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void start(Instant now) {
    this.status = SearchReindexJobStatus.RUNNING;
    this.startedAt = now;
    this.updatedAt = now;
  }

  public void recordBatch(long scanned, long indexed, long failed, String nextCursor, Instant now) {
    this.totalScanned += scanned;
    this.totalIndexed += indexed;
    this.totalFailed += failed;
    this.lastCursor = nextCursor;
    this.updatedAt = now;
  }

  public void complete(Instant now) {
    this.status = SearchReindexJobStatus.COMPLETED;
    this.completedAt = now;
    this.updatedAt = now;
  }

  public void startCleanup(Instant now) {
    this.cleanupStartedAt = now;
    this.updatedAt = now;
  }

  public void completeCleanup(long archivedOrphans, Instant now) {
    this.totalArchivedOrphans = archivedOrphans;
    this.cleanupOrphansExecuted = true;
    this.cleanupPreviewCount = archivedOrphans;
    this.cleanupPreviewGeneratedAt = now;
    this.cleanupCompletedAt = now;
    this.updatedAt = now;
  }

  public void skipCleanup(Instant now) {
    this.cleanupOrphansExecuted = false;
    this.cleanupCompletedAt = now;
    this.updatedAt = now;
  }

  public void completeDryRunCleanup(long orphanCount, Instant now) {
    this.cleanupOrphansExecuted = false;
    this.cleanupPreviewCount = orphanCount;
    this.cleanupPreviewGeneratedAt = now;
    this.cleanupCompletedAt = now;
    this.updatedAt = now;
  }

  public void fail(String error, Instant now) {
    this.status = SearchReindexJobStatus.FAILED;
    this.failedAt = now;
    this.lastError = truncate(error);
    this.updatedAt = now;
  }

  public void cancel(Instant now) {
    this.status = SearchReindexJobStatus.CANCELLED;
    this.completedAt = now;
    this.updatedAt = now;
  }

  public boolean terminal() {
    return status == SearchReindexJobStatus.COMPLETED
        || status == SearchReindexJobStatus.FAILED
        || status == SearchReindexJobStatus.CANCELLED;
  }

  private String truncate(String value) {
    if (value == null) {
      return null;
    }
    return value.length() <= 1000 ? value : value.substring(0, 1000);
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public UUID getNotebookId() {
    return notebookId;
  }

  public SearchReindexJobStatus getStatus() {
    return status;
  }

  public String getRequestedByService() {
    return requestedByService;
  }

  public SearchReindexMode getMode() {
    return mode;
  }

  public long getTotalScanned() {
    return totalScanned;
  }

  public long getTotalIndexed() {
    return totalIndexed;
  }

  public long getTotalFailed() {
    return totalFailed;
  }

  public long getTotalArchivedOrphans() {
    return totalArchivedOrphans;
  }

  public String getLastCursor() {
    return lastCursor;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public Instant getFailedAt() {
    return failedAt;
  }

  public Instant getCleanupStartedAt() {
    return cleanupStartedAt;
  }

  public Instant getCleanupCompletedAt() {
    return cleanupCompletedAt;
  }

  public boolean isCleanupOrphansRequested() {
    return cleanupOrphansRequested;
  }

  public boolean isCleanupOrphansExecuted() {
    return cleanupOrphansExecuted;
  }

  public boolean isDryRunCleanup() {
    return dryRunCleanup;
  }

  public long getCleanupPreviewCount() {
    return cleanupPreviewCount;
  }

  public Instant getCleanupPreviewGeneratedAt() {
    return cleanupPreviewGeneratedAt;
  }

  public String getLastError() {
    return lastError;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
