package com.notebook.lumen.identity.mfa.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_webauthn_credentials")
public class UserWebAuthnCredential {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "credential_id", nullable = false)
  private String credentialId;

  @Column(name = "public_key_cose", nullable = false)
  private String publicKeyCose;

  @Column(name = "sign_count")
  private Long signCount;

  @Column(name = "attestation_type")
  private String attestationType;

  @Column(name = "aaguid")
  private String aaguid;

  @Column(name = "name")
  private String name;

  @Column(name = "last_used_at")
  private Instant lastUsedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  protected UserWebAuthnCredential() {}

  public UserWebAuthnCredential(
      UUID id,
      UUID userId,
      String credentialId,
      String publicKeyCose,
      Long signCount,
      String attestationType,
      String aaguid,
      String name,
      Instant lastUsedAt,
      Instant createdAt,
      Instant revokedAt) {
    this.id = id;
    this.userId = userId;
    this.credentialId = credentialId;
    this.publicKeyCose = publicKeyCose;
    this.signCount = signCount;
    this.attestationType = attestationType;
    this.aaguid = aaguid;
    this.name = name;
    this.lastUsedAt = lastUsedAt;
    this.createdAt = createdAt;
    this.revokedAt = revokedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getCredentialId() {
    return credentialId;
  }

  public String getPublicKeyCose() {
    return publicKeyCose;
  }

  public Long getSignCount() {
    return signCount;
  }

  public String getName() {
    return name;
  }

  public Instant getLastUsedAt() {
    return lastUsedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void markUsed(Long signCount) {
    this.signCount = signCount;
    this.lastUsedAt = Instant.now();
  }

  public void revoke() {
    this.revokedAt = Instant.now();
  }
}
