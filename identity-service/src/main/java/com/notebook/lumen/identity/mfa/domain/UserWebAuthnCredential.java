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
}
