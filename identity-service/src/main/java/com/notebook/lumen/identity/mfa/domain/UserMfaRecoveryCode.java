package com.notebook.lumen.identity.mfa.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_mfa_recovery_codes")
public class UserMfaRecoveryCode {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "code_hash", nullable = false)
  private String codeHash;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected UserMfaRecoveryCode() {}

  public UserMfaRecoveryCode(
      UUID id, UUID userId, String codeHash, Instant usedAt, Instant revokedAt, Instant createdAt) {
    this.id = id;
    this.userId = userId;
    this.codeHash = codeHash;
    this.usedAt = usedAt;
    this.revokedAt = revokedAt;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getCodeHash() {
    return codeHash;
  }

  public Instant getUsedAt() {
    return usedAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public void markUsed() {
    this.usedAt = Instant.now();
  }

  public void revoke() {
    this.revokedAt = Instant.now();
  }
}
