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

  public UserMfaSettings(
      UUID userId,
      boolean webauthnEnabled,
      boolean backupCodesEnabled,
      boolean mfaRequired,
      Instant createdAt,
      Instant updatedAt) {
    this.userId = userId;
    this.webauthnEnabled = webauthnEnabled;
    this.backupCodesEnabled = backupCodesEnabled;
    this.mfaRequired = mfaRequired;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public UUID getUserId() {
    return userId;
  }

  public boolean isWebauthnEnabled() {
    return webauthnEnabled;
  }

  public boolean isBackupCodesEnabled() {
    return backupCodesEnabled;
  }

  public boolean isMfaRequired() {
    return mfaRequired;
  }

  public void setWebauthnEnabled(boolean webauthnEnabled) {
    this.webauthnEnabled = webauthnEnabled;
    this.updatedAt = Instant.now();
  }

  public void setBackupCodesEnabled(boolean backupCodesEnabled) {
    this.backupCodesEnabled = backupCodesEnabled;
    this.updatedAt = Instant.now();
  }
}
