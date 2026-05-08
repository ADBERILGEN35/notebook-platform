package com.notebook.lumen.identity.mfa.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_mfa_settings")
public class UserMfaSettings {
  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "webauthn_enabled", nullable = false)
  private boolean webauthnEnabled;

  @Column(name = "backup_codes_enabled", nullable = false)
  private boolean backupCodesEnabled;

  @Column(name = "mfa_required", nullable = false)
  private boolean mfaRequired;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserMfaSettings() {}
}
