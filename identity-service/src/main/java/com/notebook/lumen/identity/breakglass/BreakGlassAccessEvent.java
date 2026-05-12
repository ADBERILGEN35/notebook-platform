package com.notebook.lumen.identity.breakglass;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "break_glass_access_events")
public class BreakGlassAccessEvent {
  @Id private UUID id;

  @Column(name = "session_id", nullable = false, unique = true, length = 128)
  private String sessionId;

  @Column(name = "mode", nullable = false, length = 64)
  private String mode;

  @Column(name = "reason_hash", length = 128)
  private String reasonHash;

  @Column(name = "reason_summary", length = 256)
  private String reasonSummary;

  @Column(name = "actor_label", nullable = false, length = 128)
  private String actorLabel;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 64)
  private BreakGlassAccessEventStatus status;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "reviewed_by_user_id")
  private UUID reviewedByUserId;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "review_decision", length = 32)
  private String reviewDecision;

  @Column(name = "review_reason")
  private String reviewReason;

  @Column(name = "source_ip_hash", length = 128)
  private String sourceIpHash;

  @Column(name = "user_agent_hash", length = 128)
  private String userAgentHash;

  @Column(name = "rotation_required", nullable = false)
  private boolean rotationRequired;

  @Column(name = "notification_sent", nullable = false)
  private boolean notificationSent;

  @Column(name = "token_jti", length = 128)
  private String tokenJti;

  @Column(name = "token_revoked_at")
  private Instant tokenRevokedAt;

  @Column(name = "token_revoked_by_user_id")
  private UUID tokenRevokedByUserId;

  @Column(name = "token_revocation_reason")
  private String tokenRevocationReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected BreakGlassAccessEvent() {}

  public BreakGlassAccessEvent(
      UUID id,
      String sessionId,
      String mode,
      String reasonHash,
      String reasonSummary,
      String actorLabel,
      BreakGlassAccessEventStatus status,
      Instant issuedAt,
      Instant expiresAt,
      String sourceIpHash,
      String userAgentHash,
      boolean rotationRequired,
      boolean notificationSent,
      String tokenJti,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.sessionId = sessionId;
    this.mode = mode;
    this.reasonHash = reasonHash;
    this.reasonSummary = reasonSummary;
    this.actorLabel = actorLabel;
    this.status = status;
    this.issuedAt = issuedAt;
    this.expiresAt = expiresAt;
    this.sourceIpHash = sourceIpHash;
    this.userAgentHash = userAgentHash;
    this.rotationRequired = rotationRequired;
    this.notificationSent = notificationSent;
    this.tokenJti = tokenJti;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public UUID getId() {
    return id;
  }

  public String getSessionId() {
    return sessionId;
  }

  public String getMode() {
    return mode;
  }

  public String getReasonHash() {
    return reasonHash;
  }

  public String getReasonSummary() {
    return reasonSummary;
  }

  public String getActorLabel() {
    return actorLabel;
  }

  public BreakGlassAccessEventStatus getStatus() {
    return status;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public UUID getReviewedByUserId() {
    return reviewedByUserId;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public String getReviewDecision() {
    return reviewDecision;
  }

  public String getReviewReason() {
    return reviewReason;
  }

  public String getSourceIpHash() {
    return sourceIpHash;
  }

  public String getUserAgentHash() {
    return userAgentHash;
  }

  public boolean isRotationRequired() {
    return rotationRequired;
  }

  public boolean isNotificationSent() {
    return notificationSent;
  }

  public String getTokenJti() {
    return tokenJti;
  }

  public Instant getTokenRevokedAt() {
    return tokenRevokedAt;
  }

  public UUID getTokenRevokedByUserId() {
    return tokenRevokedByUserId;
  }

  public String getTokenRevocationReason() {
    return tokenRevocationReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void markReviewed(
      UUID reviewerId, String decision, String reason, BreakGlassAccessEventStatus nextStatus) {
    this.reviewedByUserId = reviewerId;
    this.reviewDecision = decision;
    this.reviewReason = reason;
    this.reviewedAt = Instant.now();
    this.status = nextStatus;
    this.updatedAt = Instant.now();
  }

  public void markTokenRevoked(UUID reviewerId, String reason) {
    this.tokenRevokedAt = Instant.now();
    this.tokenRevokedByUserId = reviewerId;
    this.tokenRevocationReason = reason;
    this.updatedAt = Instant.now();
  }
}
