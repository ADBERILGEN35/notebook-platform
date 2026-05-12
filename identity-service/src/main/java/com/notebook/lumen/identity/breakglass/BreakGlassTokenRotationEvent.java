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
@Table(name = "break_glass_token_rotation_events")
public class BreakGlassTokenRotationEvent {
  @Id private UUID id;

  @Column(name = "rotation_key", nullable = false, unique = true, length = 128)
  private String rotationKey;

  @Column(name = "credential_mode", nullable = false, length = 64)
  private String credentialMode;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private BreakGlassTokenRotationEventStatus status;

  @Column(name = "triggered_by_event_id")
  private UUID triggeredByEventId;

  @Column(name = "triggered_by_session_id", length = 128)
  private String triggeredBySessionId;

  @Column(name = "old_token_hash_fingerprint", length = 64)
  private String oldTokenHashFingerprint;

  @Column(name = "new_token_hash_fingerprint", length = 64)
  private String newTokenHashFingerprint;

  @Column(name = "required_at", nullable = false)
  private Instant requiredAt;

  @Column(name = "acknowledged_at")
  private Instant acknowledgedAt;

  @Column(name = "acknowledged_by_user_id")
  private UUID acknowledgedByUserId;

  @Column(name = "verified_at")
  private Instant verifiedAt;

  @Column(name = "verified_by_user_id")
  private UUID verifiedByUserId;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "reason")
  private String reason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected BreakGlassTokenRotationEvent() {}

  public BreakGlassTokenRotationEvent(
      UUID id,
      String rotationKey,
      String credentialMode,
      BreakGlassTokenRotationEventStatus status,
      UUID triggeredByEventId,
      String triggeredBySessionId,
      String oldTokenHashFingerprint,
      Instant requiredAt,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.rotationKey = rotationKey;
    this.credentialMode = credentialMode;
    this.status = status;
    this.triggeredByEventId = triggeredByEventId;
    this.triggeredBySessionId = triggeredBySessionId;
    this.oldTokenHashFingerprint = oldTokenHashFingerprint;
    this.requiredAt = requiredAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public UUID getId() {
    return id;
  }

  public String getRotationKey() {
    return rotationKey;
  }

  public String getCredentialMode() {
    return credentialMode;
  }

  public BreakGlassTokenRotationEventStatus getStatus() {
    return status;
  }

  public UUID getTriggeredByEventId() {
    return triggeredByEventId;
  }

  public String getTriggeredBySessionId() {
    return triggeredBySessionId;
  }

  public String getOldTokenHashFingerprint() {
    return oldTokenHashFingerprint;
  }

  public String getNewTokenHashFingerprint() {
    return newTokenHashFingerprint;
  }

  public Instant getRequiredAt() {
    return requiredAt;
  }

  public Instant getAcknowledgedAt() {
    return acknowledgedAt;
  }

  public UUID getAcknowledgedByUserId() {
    return acknowledgedByUserId;
  }

  public Instant getVerifiedAt() {
    return verifiedAt;
  }

  public UUID getVerifiedByUserId() {
    return verifiedByUserId;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public String getReason() {
    return reason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void markAcknowledged(UUID actorUserId, String reason) {
    this.status = BreakGlassTokenRotationEventStatus.ACKNOWLEDGED;
    this.acknowledgedAt = Instant.now();
    this.acknowledgedByUserId = actorUserId;
    this.reason = reason;
    this.updatedAt = Instant.now();
  }

  public void markVerified(UUID actorUserId, String newFingerprint, String reason) {
    this.status = BreakGlassTokenRotationEventStatus.VERIFIED;
    this.verifiedAt = Instant.now();
    this.verifiedByUserId = actorUserId;
    this.newTokenHashFingerprint = newFingerprint;
    if (reason != null && !reason.isBlank()) {
      this.reason = reason;
    }
    this.updatedAt = Instant.now();
  }

  public void markClosed(String reason) {
    this.status = BreakGlassTokenRotationEventStatus.CLOSED;
    this.closedAt = Instant.now();
    if (reason != null && !reason.isBlank()) {
      this.reason = reason;
    }
    this.updatedAt = Instant.now();
  }
}
