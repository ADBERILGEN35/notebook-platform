package com.notebook.lumen.identity.breakglass;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "identity.break-glass")
public record BreakGlassProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("static-token") String credentialMode,
    @DefaultValue("false") boolean staticTokenEnabled,
    @DefaultValue("false") boolean webauthnEnabled,
    @DefaultValue("false") boolean offlineSignedEnabled,
    @DefaultValue("disabled") String approvalMode,
    @DefaultValue("true") boolean eventLogEnabled,
    @DefaultValue("60") int reviewRequiredWithinMinutes,
    @DefaultValue("true") boolean notifySecurityAdmins,
    @DefaultValue("false") boolean reviewApiEnabled,
    @DefaultValue("false") boolean revocationEnabled,
    @DefaultValue("true") boolean revokeOnReject,
    @DefaultValue("24") int denylistRetentionHours,
    @DefaultValue("30") int revocationCacheTtlSeconds,
    @DefaultValue("false") boolean revocationCleanupEnabled,
    /** Expected token hash (recommended format: sha256:hex). */
    @DefaultValue("") String tokenHash,
    @DefaultValue("") String offlinePublicKeyPath,
    @DefaultValue("notebook-break-glass-offline") String offlineAllowedIssuer,
    @DefaultValue("identity-service") String offlineRequiredAudience,
    @DefaultValue("300") int offlineMaxAssertionTtlSeconds,
    @DefaultValue("15") int sessionTtlMinutes,
    @DefaultValue("true") boolean requireMfa,
    @DefaultValue("true") boolean requireReason,
    @DefaultValue("1") int maxActiveSessions,
    @DefaultValue("true") boolean notifyOnUse,
    @DefaultValue("5") int staticTokenMaxFailuresPerWindow,
    @DefaultValue("15") int staticTokenLockoutMinutes,
    @DefaultValue("true") boolean staticTokenRotationRecommendedAfterUse,
    @DefaultValue("false") boolean rotationTrackingEnabled,
    @DefaultValue("false") boolean rotationApiEnabled,
    @DefaultValue("12") int tokenHashFingerprintLength,
    @DefaultValue("5") int rotationMaxOpenEvents) {

  public BreakGlassProperties {
    if (sessionTtlMinutes < 1) {
      sessionTtlMinutes = 15;
    }
    if (maxActiveSessions < 1) {
      maxActiveSessions = 1;
    }
    if (offlineMaxAssertionTtlSeconds < 30) {
      offlineMaxAssertionTtlSeconds = 300;
    }
    if (reviewRequiredWithinMinutes < 5) {
      reviewRequiredWithinMinutes = 60;
    }
    if (denylistRetentionHours < 1) {
      denylistRetentionHours = 24;
    }
    if (revocationCacheTtlSeconds < 1) {
      revocationCacheTtlSeconds = 30;
    }
    if (staticTokenMaxFailuresPerWindow < 1) {
      staticTokenMaxFailuresPerWindow = 5;
    }
    if (staticTokenLockoutMinutes < 1) {
      staticTokenLockoutMinutes = 15;
    }
    if (tokenHashFingerprintLength < 8) {
      tokenHashFingerprintLength = 12;
    }
    if (tokenHashFingerprintLength > 32) {
      tokenHashFingerprintLength = 32;
    }
    if (rotationMaxOpenEvents < 1) {
      rotationMaxOpenEvents = 5;
    }
    credentialMode =
        credentialMode == null || credentialMode.isBlank() ? "static-token" : credentialMode.trim();
    approvalMode =
        approvalMode == null || approvalMode.isBlank() ? "disabled" : approvalMode.trim();
    tokenHash = tokenHash == null ? "" : tokenHash.trim();
    offlinePublicKeyPath = offlinePublicKeyPath == null ? "" : offlinePublicKeyPath.trim();
    offlineAllowedIssuer =
        offlineAllowedIssuer == null ? "notebook-break-glass-offline" : offlineAllowedIssuer.trim();
    offlineRequiredAudience =
        offlineRequiredAudience == null ? "identity-service" : offlineRequiredAudience.trim();
  }
}
