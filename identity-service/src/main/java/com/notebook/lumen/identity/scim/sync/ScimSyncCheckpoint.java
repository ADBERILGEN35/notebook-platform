package com.notebook.lumen.identity.scim.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scim_sync_checkpoints")
public class ScimSyncCheckpoint {
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

  @Column(name = "checkpoint_token", length = 1024)
  private String checkpointToken;

  @Column(name = "last_successful_sync_at")
  private Instant lastSuccessfulSyncAt;

  @Column(name = "last_attempt_at")
  private Instant lastAttemptAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ScimSyncCheckpointStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ScimSyncCheckpoint() {}

  public ScimSyncCheckpoint(
      UUID id,
      String provider,
      ScimResourceType resourceType,
      ScimSyncMode syncMode,
      String checkpointToken,
      ScimSyncCheckpointStatus status,
      Instant now) {
    this.id = id;
    this.provider = provider;
    this.resourceType = resourceType;
    this.syncMode = syncMode;
    this.checkpointToken = checkpointToken;
    this.status = status;
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
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

  public String getCheckpointToken() {
    return checkpointToken;
  }

  public Instant getLastSuccessfulSyncAt() {
    return lastSuccessfulSyncAt;
  }

  public Instant getLastAttemptAt() {
    return lastAttemptAt;
  }

  public ScimSyncCheckpointStatus getStatus() {
    return status;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void markAttempt(ScimSyncMode mode, String checkpointToken) {
    this.syncMode = mode;
    this.checkpointToken = checkpointToken;
    this.lastAttemptAt = Instant.now();
    this.status = ScimSyncCheckpointStatus.RUNNING;
  }

  public void markSuccess(String checkpointToken) {
    this.checkpointToken = checkpointToken;
    this.lastSuccessfulSyncAt = Instant.now();
    this.status = ScimSyncCheckpointStatus.IDLE;
  }

  public void markFailed() {
    this.status = ScimSyncCheckpointStatus.FAILED;
  }
}
