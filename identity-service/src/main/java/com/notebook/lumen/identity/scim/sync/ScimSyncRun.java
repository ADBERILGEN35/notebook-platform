package com.notebook.lumen.identity.scim.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scim_sync_runs")
public class ScimSyncRun {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "provider", nullable = false, length = 80)
  private String provider;

  @Enumerated(EnumType.STRING)
  @Column(name = "resource_type", nullable = false, length = 20)
  private ScimResourceType resourceType;

  @Enumerated(EnumType.STRING)
  @Column(name = "sync_mode", nullable = false, length = 20)
  private ScimSyncMode syncMode;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ScimSyncRunStatus status;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "processed_count", nullable = false)
  private long processedCount;

  @Column(name = "created_count", nullable = false)
  private long createdCount;

  @Column(name = "updated_count", nullable = false)
  private long updatedCount;

  @Column(name = "deprovisioned_count", nullable = false)
  private long deprovisionedCount;

  @Column(name = "skipped_count", nullable = false)
  private long skippedCount;

  @Column(name = "error_count", nullable = false)
  private long errorCount;

  @Column(name = "last_error_code", length = 120)
  private String lastErrorCode;

  @Column(name = "last_error_summary", length = 512)
  private String lastErrorSummary;

  @Column(name = "request_id", length = 120)
  private String requestId;

  protected ScimSyncRun() {}

  public ScimSyncRun(
      UUID id,
      String provider,
      ScimResourceType resourceType,
      ScimSyncMode syncMode,
      String requestId,
      Instant startedAt) {
    this.id = id;
    this.provider = provider;
    this.resourceType = resourceType;
    this.syncMode = syncMode;
    this.status = ScimSyncRunStatus.STARTED;
    this.requestId = requestId;
    this.startedAt = startedAt;
  }

  public UUID getId() {
    return id;
  }

  public String getProvider() {
    return provider;
  }

  public ScimResourceType getResourceType() {
    return resourceType;
  }

  public ScimSyncMode getSyncMode() {
    return syncMode;
  }

  public ScimSyncRunStatus getStatus() {
    return status;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public long getProcessedCount() {
    return processedCount;
  }

  public long getCreatedCount() {
    return createdCount;
  }

  public long getUpdatedCount() {
    return updatedCount;
  }

  public long getDeprovisionedCount() {
    return deprovisionedCount;
  }

  public long getSkippedCount() {
    return skippedCount;
  }

  public long getErrorCount() {
    return errorCount;
  }

  public String getLastErrorCode() {
    return lastErrorCode;
  }

  public String getLastErrorSummary() {
    return lastErrorSummary;
  }

  public String getRequestId() {
    return requestId;
  }

  public void complete(
      long processed, long created, long updated, long deprovisioned, long skipped) {
    this.status = ScimSyncRunStatus.COMPLETED;
    this.completedAt = Instant.now();
    this.processedCount = processed;
    this.createdCount = created;
    this.updatedCount = updated;
    this.deprovisionedCount = deprovisioned;
    this.skippedCount = skipped;
  }

  public void fail(String errorCode, String errorSummary) {
    this.status = ScimSyncRunStatus.FAILED;
    this.completedAt = Instant.now();
    this.errorCount = Math.max(1, this.errorCount);
    this.lastErrorCode = errorCode;
    this.lastErrorSummary =
        errorSummary == null
            ? null
            : errorSummary.substring(0, Math.min(512, errorSummary.length()));
  }
}
