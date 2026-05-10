package com.notebook.lumen.notification.admin.legalhold;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notification_legal_holds")
public class NotificationLegalHoldEntity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "hold_key", nullable = false, length = 200, unique = true)
  private String holdKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "scope", nullable = false, length = 64)
  private LegalHoldScope scope;

  @Column(name = "reason", nullable = false, columnDefinition = "text")
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private LegalHoldStatus status;

  @Column(name = "created_by_user_id", nullable = false)
  private UUID createdByUserId;

  @Column(name = "created_by_email", length = 320)
  private String createdByEmail;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "released_by_user_id")
  private UUID releasedByUserId;

  @Column(name = "released_at")
  private Instant releasedAt;

  @Column(name = "release_reason", columnDefinition = "text")
  private String releaseReason;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> metadata;

  protected NotificationLegalHoldEntity() {}

  public NotificationLegalHoldEntity(
      UUID id,
      String holdKey,
      LegalHoldScope scope,
      String reason,
      UUID createdByUserId,
      String createdByEmail,
      Instant createdAt,
      Instant expiresAt,
      Map<String, Object> metadata) {
    this.id = id;
    this.holdKey = holdKey;
    this.scope = scope;
    this.reason = reason;
    this.status = LegalHoldStatus.ACTIVE;
    this.createdByUserId = createdByUserId;
    this.createdByEmail = createdByEmail;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
    this.metadata = metadata == null ? Map.of() : metadata;
  }

  public UUID getId() {
    return id;
  }

  public String getHoldKey() {
    return holdKey;
  }

  public LegalHoldScope getScope() {
    return scope;
  }

  public String getReason() {
    return reason;
  }

  public LegalHoldStatus getStatus() {
    return status;
  }

  public UUID getCreatedByUserId() {
    return createdByUserId;
  }

  public String getCreatedByEmail() {
    return createdByEmail;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getReleasedAt() {
    return releasedAt;
  }

  public UUID getReleasedByUserId() {
    return releasedByUserId;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public void release(UUID releasedByUserId, String releaseReason, Instant now) {
    this.status = LegalHoldStatus.RELEASED;
    this.releasedByUserId = releasedByUserId;
    this.releaseReason = releaseReason;
    this.releasedAt = now;
  }
}
