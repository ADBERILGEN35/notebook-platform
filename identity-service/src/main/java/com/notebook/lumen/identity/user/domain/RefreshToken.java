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

  @Column(name = "replaced_by_token_id", nullable = true)
  private UUID replacedByTokenId;

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

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getCreatedByIp() {
    return createdByIp;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void revoke(Instant revokedAt, UUID replacedByTokenId) {
    this.revokedAt = revokedAt;
    this.replacedByTokenId = replacedByTokenId;
  }
}
