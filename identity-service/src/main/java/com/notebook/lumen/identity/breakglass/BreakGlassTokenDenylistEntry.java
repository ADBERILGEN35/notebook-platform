package com.notebook.lumen.identity.breakglass;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "break_glass_token_denylist")
public class BreakGlassTokenDenylistEntry {
  @Id private UUID id;

  @Column(name = "jti", nullable = false, unique = true, length = 128)
  private String jti;

  @Column(name = "session_id", nullable = false, length = 128)
  private String sessionId;

  @Column(name = "event_id")
  private UUID eventId;

  @Column(name = "revoked_by_user_id")
  private UUID revokedByUserId;

  @Column(name = "revoked_at", nullable = false)
  private Instant revokedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "reason", nullable = false)
  private String reason;

  @Column(name = "source", nullable = false, length = 64)
  private String source;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected BreakGlassTokenDenylistEntry() {}

  public BreakGlassTokenDenylistEntry(
      UUID id,
      String jti,
      String sessionId,
      UUID eventId,
      UUID revokedByUserId,
      Instant revokedAt,
      Instant expiresAt,
      String reason,
      String source,
      Instant createdAt) {
    this.id = id;
    this.jti = jti;
    this.sessionId = sessionId;
    this.eventId = eventId;
    this.revokedByUserId = revokedByUserId;
    this.revokedAt = revokedAt;
    this.expiresAt = expiresAt;
    this.reason = reason;
    this.source = source;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getJti() {
    return jti;
  }

  public String getSessionId() {
    return sessionId;
  }

  public UUID getEventId() {
    return eventId;
  }

  public UUID getRevokedByUserId() {
    return revokedByUserId;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public String getReason() {
    return reason;
  }

  public String getSource() {
    return source;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
