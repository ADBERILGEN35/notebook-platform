package com.notebook.lumen.identity.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "token_hash", nullable = false, length = 512)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at", nullable = true)
  private Instant revokedAt;

  @Column(name = "revoked_reason", nullable = true, length = 80)
  private String revokedReason;

  @Column(name = "revoked_by_user_id", nullable = true)
  private UUID revokedByUserId;

  @Column(name = "replaced_by_token_id", nullable = true)
  private UUID replacedByTokenId;

  @Column(name = "last_used_at", nullable = true)
  private Instant lastUsedAt;

  @Column(name = "replaced_at", nullable = true)
  private Instant replacedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "created_by_ip", nullable = true, length = 64)
  private String createdByIp;

  @Column(name = "user_agent", nullable = true, length = 512)
  private String userAgent;

  protected RefreshToken() {
    // JPA
  }

  public RefreshToken(
      UUID id,
      User user,
      String tokenHash,
      Instant expiresAt,
      Instant revokedAt,
      UUID replacedByTokenId,
      Instant createdAt,
      String createdByIp,
      String userAgent) {
    this.id = id;
    this.user = user;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.revokedAt = revokedAt;
    this.replacedByTokenId = replacedByTokenId;
    this.createdAt = createdAt;
    this.createdByIp = createdByIp;
    this.userAgent = userAgent;
  }

  public UUID getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public UUID getReplacedByTokenId() {
    return replacedByTokenId;
  }

  public String getRevokedReason() {
    return revokedReason;
  }

  public UUID getRevokedByUserId() {
    return revokedByUserId;
  }

  public Instant getLastUsedAt() {
    return lastUsedAt;
  }

  public Instant getReplacedAt() {
    return replacedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getCreatedByIp() {
    return createdByIp;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void markUsed(Instant lastUsedAt) {
    this.lastUsedAt = lastUsedAt;
  }

  public void revoke(Instant revokedAt, UUID replacedByTokenId) {
    revoke(revokedAt, replacedByTokenId, null, null);
  }

  public void revoke(
      Instant revokedAt, UUID replacedByTokenId, String revokedReason, UUID revokedByUserId) {
    this.revokedAt = revokedAt;
    this.replacedByTokenId = replacedByTokenId;
    this.revokedReason = revokedReason;
    this.revokedByUserId = revokedByUserId;
    if (replacedByTokenId != null) {
      this.replacedAt = revokedAt;
    }
  }
}
