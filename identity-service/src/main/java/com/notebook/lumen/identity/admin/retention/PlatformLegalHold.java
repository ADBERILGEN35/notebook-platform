package com.notebook.lumen.identity.admin.retention;

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
@Table(name = "platform_legal_holds")
public class PlatformLegalHold {
  @Id private UUID id;

  @Column(name = "hold_key", nullable = false, unique = true, length = 200)
  private String holdKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "scope", nullable = false, length = 40)
  private PlatformLegalHoldScope scope;

  @Column(name = "scope_ref_id")
  private UUID scopeRefId;

  @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private PlatformLegalHoldStatus status;

  @Column(name = "created_by_user_id", nullable = false)
  private UUID createdByUserId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "released_by_user_id")
  private UUID releasedByUserId;

  @Column(name = "released_at")
  private Instant releasedAt;

  @Column(name = "release_reason", columnDefinition = "TEXT")
  private String releaseReason;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected PlatformLegalHold() {
    // JPA
  }

  public PlatformLegalHold(
      UUID id,
      String holdKey,
      PlatformLegalHoldScope scope,
      UUID scopeRefId,
      String reason,
      UUID createdByUserId,
      Instant createdAt,
      Instant expiresAt) {
    this.id = id;
    this.holdKey = holdKey;
    this.scope = scope;
    this.scopeRefId = scopeRefId;
    this.reason = reason;
    this.status = PlatformLegalHoldStatus.ACTIVE;
    this.createdByUserId = createdByUserId;
    this.createdAt = createdAt;
    this.updatedAt = createdAt;
    this.expiresAt = expiresAt;
  }

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  public void release(UUID actorUserId, String releaseReason, Instant now) {
    this.status = PlatformLegalHoldStatus.RELEASED;
    this.releasedByUserId = actorUserId;
    this.releasedAt = now;
    this.releaseReason = releaseReason;
  }

  public UUID getId() {
    return id;
  }

  public String getHoldKey() {
    return holdKey;
  }

  public PlatformLegalHoldScope getScope() {
    return scope;
  }

  public UUID getScopeRefId() {
    return scopeRefId;
  }

  public PlatformLegalHoldStatus getStatus() {
    return status;
  }

  public UUID getCreatedByUserId() {
    return createdByUserId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public UUID getReleasedByUserId() {
    return releasedByUserId;
  }

  public Instant getReleasedAt() {
    return releasedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
