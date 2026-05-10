package com.notebook.lumen.content.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "note_merge_idempotency_keys")
public class NoteMergeIdempotencyKey {
  @Id private UUID id;
  private UUID workspaceId;
  private UUID userId;
  private UUID noteId;
  private String idempotencyKey;
  private String requestHash;
  @Enumerated(EnumType.STRING)
  private Status status;
  private String resultEtag;
  private Integer resultVersion;
  private UUID resultNoteId;
  private Instant createdAt;
  private Instant completedAt;

  protected NoteMergeIdempotencyKey() {}

  public NoteMergeIdempotencyKey(
      UUID id,
      UUID workspaceId,
      UUID userId,
      UUID noteId,
      String idempotencyKey,
      String requestHash,
      Instant createdAt) {
    this.id = id;
    this.workspaceId = workspaceId;
    this.userId = userId;
    this.noteId = noteId;
    this.idempotencyKey = idempotencyKey;
    this.requestHash = requestHash;
    this.status = Status.IN_PROGRESS;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getNoteId() {
    return noteId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public Status getStatus() {
    return status;
  }

  public String getResultEtag() {
    return resultEtag;
  }

  public Integer getResultVersion() {
    return resultVersion;
  }

  public UUID getResultNoteId() {
    return resultNoteId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void markCompleted(String resultEtag, int resultVersion, UUID resultNoteId, Instant now) {
    this.status = Status.COMPLETED;
    this.resultEtag = resultEtag;
    this.resultVersion = resultVersion;
    this.resultNoteId = resultNoteId;
    this.completedAt = now;
  }

  public void markFailed(Instant now) {
    this.status = Status.FAILED;
    this.completedAt = now;
  }

  public enum Status {
    IN_PROGRESS,
    COMPLETED,
    FAILED
  }
}
