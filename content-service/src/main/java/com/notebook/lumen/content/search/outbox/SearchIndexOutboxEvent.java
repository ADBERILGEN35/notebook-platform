package com.notebook.lumen.content.search.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "search_index_outbox")
public class SearchIndexOutboxEvent {
  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  private SearchIndexOutboxEventType eventType;

  @Enumerated(EnumType.STRING)
  private SearchIndexOutboxStatus status;

  private UUID workspaceId;
  private UUID notebookId;
  private UUID noteId;
  private Integer sourceVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String payload;

  private String idempotencyKey;
  private int attemptCount;
  private Instant nextAttemptAt;
  private String lastError;
  private Instant lockedAt;
  private String lockedBy;
  private Instant lockExpiresAt;
  private Instant processedAt;
  private Instant failedAt;
  private Instant createdAt;
  private Instant updatedAt;

  protected SearchIndexOutboxEvent() {}

  public SearchIndexOutboxEvent(
      UUID id,
      SearchIndexOutboxEventType eventType,
      UUID workspaceId,
      UUID notebookId,
      UUID noteId,
      Integer sourceVersion,
      String payload,
      String idempotencyKey,
      Instant now) {
    this.id = id;
    this.eventType = eventType;
    this.status = SearchIndexOutboxStatus.PENDING;
    this.workspaceId = workspaceId;
    this.notebookId = notebookId;
    this.noteId = noteId;
    this.sourceVersion = sourceVersion;
    this.payload = payload;
    this.idempotencyKey = idempotencyKey;
    this.attemptCount = 0;
    this.nextAttemptAt = now;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void markProcessing(String lockedBy, Instant now, Instant lockExpiresAt) {
    this.status = SearchIndexOutboxStatus.PROCESSING;
    this.lockedAt = now;
    this.lockedBy = lockedBy;
    this.lockExpiresAt = lockExpiresAt;
    this.updatedAt = now;
  }

  public void markProcessed(Instant now) {
    this.status = SearchIndexOutboxStatus.PROCESSED;
    this.processedAt = now;
    this.lastError = null;
    clearLock();
    this.updatedAt = now;
  }

  public void markRetry(String error, Instant nextAttemptAt, Instant now) {
    this.status = SearchIndexOutboxStatus.PENDING;
    this.attemptCount++;
    this.nextAttemptAt = nextAttemptAt;
    this.lastError = truncate(error);
    clearLock();
    this.updatedAt = now;
  }

  public void markFailed(String error, Instant now) {
    this.status = SearchIndexOutboxStatus.FAILED;
    this.attemptCount++;
    this.nextAttemptAt = null;
    this.lastError = truncate(error);
    this.failedAt = now;
    clearLock();
    this.updatedAt = now;
  }

  public void requeue(Instant now) {
    this.status = SearchIndexOutboxStatus.PENDING;
    this.attemptCount = 0;
    this.nextAttemptAt = now;
    this.lastError = null;
    clearLock();
    this.failedAt = null;
    this.updatedAt = now;
  }

  private void clearLock() {
    this.lockedAt = null;
    this.lockedBy = null;
    this.lockExpiresAt = null;
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

  public SearchIndexOutboxEventType getEventType() {
    return eventType;
  }

  public SearchIndexOutboxStatus getStatus() {
    return status;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public UUID getNotebookId() {
    return notebookId;
  }

  public UUID getNoteId() {
    return noteId;
  }

  public Integer getSourceVersion() {
    return sourceVersion;
  }

  public String getPayload() {
    return payload;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getLastError() {
    return lastError;
  }

  public Instant getLockedAt() {
    return lockedAt;
  }

  public String getLockedBy() {
    return lockedBy;
  }

  public Instant getLockExpiresAt() {
    return lockExpiresAt;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }

  public Instant getFailedAt() {
    return failedAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
