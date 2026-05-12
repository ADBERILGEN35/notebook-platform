package com.notebook.lumen.gateway.admin;

import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import com.notebook.lumen.gateway.config.GatewayAdminProperties;
import com.notebook.lumen.gateway.config.GatewayAdminRbacProperties;
import com.notebook.lumen.gateway.config.GatewayAdminWriteProperties;
import com.notebook.lumen.gateway.error.ErrorCode;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class AdminAuthorizationService {
  private static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

  private final GatewayAdminProperties properties;
  private final GatewayAdminWriteProperties writeProperties;
  private final GatewayAdminRbacProperties rbacProperties;

  public AdminAuthorizationService(
      GatewayAdminProperties properties,
      GatewayAdminWriteProperties writeProperties,
      GatewayAdminRbacProperties rbacProperties) {
    this.properties = properties;
    this.writeProperties = writeProperties;
    this.rbacProperties = rbacProperties;
  }

  public boolean rbacEnforce() {
    return rbacProperties.enforce();
  }

  public boolean isAdmin(Jwt jwt) {
    if (jwt == null) {
      return false;
    }
    boolean requiresMfa = requiresMfa();
    if (hasPlatformAdminRole(jwt)) {
      if (requiresMfa && !hasVerifiedMfa(jwt)) {
        return false;
      }
      return true;
    }
    String userId = jwt.getSubject();
    if (userId != null && properties.allowedUserIdSet().contains(userId)) {
      return !requiresMfa || hasVerifiedMfa(jwt);
    }
    String email = jwt.getClaimAsString("email");
    boolean allowlisted =
        email != null
            && properties.allowedEmailSet().contains(email.toLowerCase(Locale.ROOT).trim());
    return allowlisted && (!requiresMfa || hasVerifiedMfa(jwt));
  }

  public boolean requiresMfa() {
    String mode = properties.effectiveMfaMode();
    return properties.requireMfa() || "enforce".equals(mode);
  }

  public boolean adminFeatureEnabled() {
    return properties.enabled() && properties.effectiveAudit().enabled();
  }

  public boolean enterpriseFeatureEnabled() {
    return properties.enabled() && properties.effectiveEnterprise().enabled();
  }

  public boolean adminWriteFeatureEnabled() {
    return enterpriseFeatureEnabled() && writeProperties.enabled();
  }

  /**
   * Legacy enterprise admin write: {@code PLATFORM_ADMIN} only; allowlists are not sufficient. MFA
   * when required by gateway policy.
   */
  public Optional<ErrorCode> enterpriseAdminWriteDenialReason(Jwt jwt) {
    if (jwt == null) {
      return Optional.of(ErrorCode.ADMIN_ACCESS_DENIED);
    }
    if (!hasPlatformAdminRole(jwt)) {
      return Optional.of(ErrorCode.ADMIN_ACCESS_DENIED);
    }
    if (adminWriteRequiresMfa() && !hasVerifiedMfa(jwt)) {
      return Optional.of(ErrorCode.ADMIN_WRITE_MFA_REQUIRED);
    }
    return Optional.empty();
  }

  public Optional<ErrorCode> ensureAdminPermission(Jwt jwt, String permission) {
    if (jwt == null) {
      return Optional.of(ErrorCode.ADMIN_ACCESS_DENIED);
    }
    if (!rbacProperties.enforce()) {
      if (!isAdmin(jwt)) {
        return Optional.of(
            requiresMfa() ? ErrorCode.ADMIN_MFA_REQUIRED : ErrorCode.ADMIN_ACCESS_DENIED);
      }
      return Optional.empty();
    }
    if (requiresMfa() && !hasVerifiedMfa(jwt)) {
      return Optional.of(ErrorCode.ADMIN_MFA_REQUIRED);
    }
    if (hasPlatformAdminRole(jwt)) {
      return Optional.empty();
    }
    if (hasNonEmptyPermissionsClaim(jwt)) {
      if (hasPermissionClaim(jwt, permission)) {
        return Optional.empty();
      }
      return Optional.of(ErrorCode.ADMIN_PERMISSION_REQUIRED);
    }
    // Fine-grained enforce mode: email/user allowlist is not a substitute for JWT permissions.
    if (hasPlatformAdminRole(jwt)) {
      return Optional.empty();
    }
    return Optional.of(ErrorCode.ADMIN_PERMISSION_REQUIRED);
  }

  public Optional<ErrorCode> ensureChangeRequestList(Jwt jwt) {
    if (!rbacProperties.enforce()) {
      return enterpriseAdminWriteDenialReason(jwt);
    }
    return ensureAdminPermission(jwt, PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_LIST);
  }

  public Optional<ErrorCode> ensureChangeRequestValidate(Jwt jwt, Optional<String> operationType) {
    if (!rbacProperties.enforce()) {
      return enterpriseAdminWriteDenialReason(jwt);
    }
    Optional<ErrorCode> base =
        ensureAdminPermission(jwt, PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_CREATE);
    if (base.isPresent()) {
      return base;
    }
    if (operationType.isEmpty() || operationType.get().isBlank()) {
      return Optional.empty();
    }
    return ensureOperationCreatePermission(jwt, operationType.get());
  }

  public Optional<ErrorCode> ensureChangeRequestCreate(Jwt jwt, String operationType) {
    if (!rbacProperties.enforce()) {
      return enterpriseAdminWriteDenialReason(jwt);
    }
    Optional<ErrorCode> base =
        ensureAdminPermission(jwt, PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_CREATE);
    if (base.isPresent()) {
      return base;
    }
    return ensureOperationCreatePermission(jwt, operationType);
  }

  public Optional<ErrorCode> ensureChangeRequestApprove(Jwt jwt) {
    if (!rbacProperties.enforce()) {
      return enterpriseAdminWriteDenialReason(jwt);
    }
    return ensureAdminPermission(jwt, PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_APPROVE);
  }

  public Optional<ErrorCode> ensureChangeRequestReject(Jwt jwt) {
    if (!rbacProperties.enforce()) {
      return enterpriseAdminWriteDenialReason(jwt);
    }
    return ensureAdminPermission(jwt, PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_REJECT);
  }

  /** Read-only dead-letter listing / dry-run (Faz 82). */
  public Optional<ErrorCode> ensureNotificationDeadLetterRead(Jwt jwt) {
    return ensureAdminPermission(
        jwt, PlatformAdminRbacConstants.PERM_NOTIFICATIONS_DEAD_LETTER_READ);
  }

  /**
   * Dead-letter requeue requires the dedicated permission plus the same admin-write MFA gate as
   * other high-impact mutations.
   */
  public Optional<ErrorCode> ensureNotificationRetentionRead(Jwt jwt) {
    return ensureAdminPermission(jwt, PlatformAdminRbacConstants.PERM_NOTIFICATIONS_RETENTION_READ);
  }

  /** Destructive retention purge: dedicated permission plus admin-write MFA gate (Faz 83). */
  public Optional<ErrorCode> ensureNotificationRetentionRun(Jwt jwt) {
    if (!rbacProperties.enforce()) {
      if (!isAdmin(jwt)) {
        return Optional.of(ErrorCode.ADMIN_ACCESS_DENIED);
      }
      if (adminWriteRequiresMfa() && !hasVerifiedMfa(jwt)) {
        return Optional.of(ErrorCode.ADMIN_WRITE_MFA_REQUIRED);
      }
      return Optional.empty();
    }
    Optional<ErrorCode> base =
        ensureAdminPermission(jwt, PlatformAdminRbacConstants.PERM_NOTIFICATIONS_RETENTION_RUN);
    if (base.isPresent()) {
      return base;
    }
    if (adminWriteRequiresMfa() && !hasVerifiedMfa(jwt)) {
      return Optional.of(ErrorCode.ADMIN_WRITE_MFA_REQUIRED);
    }
    return Optional.empty();
  }

  public Optional<ErrorCode> ensureNotificationDeadLetterRequeue(Jwt jwt) {
    if (!rbacProperties.enforce()) {
      if (!isAdmin(jwt)) {
        return Optional.of(ErrorCode.ADMIN_ACCESS_DENIED);
      }
      if (adminWriteRequiresMfa() && !hasVerifiedMfa(jwt)) {
        return Optional.of(ErrorCode.ADMIN_WRITE_MFA_REQUIRED);
      }
      return Optional.empty();
    }
    Optional<ErrorCode> base =
        ensureAdminPermission(
            jwt, PlatformAdminRbacConstants.PERM_NOTIFICATIONS_DEAD_LETTER_REQUEUE);
    if (base.isPresent()) {
      return base;
    }
    if (adminWriteRequiresMfa() && !hasVerifiedMfa(jwt)) {
      return Optional.of(ErrorCode.ADMIN_WRITE_MFA_REQUIRED);
    }
    return Optional.empty();
  }

  public Optional<ErrorCode> ensureNotificationLegalHoldRead(Jwt jwt) {
    return ensureAdminPermission(
        jwt, PlatformAdminRbacConstants.PERM_NOTIFICATIONS_LEGAL_HOLD_READ);
  }

  /** Create / release legal hold: dedicated permission plus admin-write MFA gate (Faz 84). */
  /** Faz 86: read-only admin RBAC directory (identity-sourced; no raw IdP claims). */
  public Optional<ErrorCode> ensureAdminRbacRead(Jwt jwt) {
    return ensureAdminPermission(jwt, PlatformAdminRbacConstants.PERM_RBAC_READ);
  }

  public Optional<ErrorCode> ensureBreakGlassRead(Jwt jwt) {
    return ensureAdminPermission(jwt, PlatformAdminRbacConstants.PERM_BREAK_GLASS_READ);
  }

  public Optional<ErrorCode> ensureBreakGlassReview(Jwt jwt) {
    Optional<ErrorCode> base = ensureAdminPermission(jwt, PlatformAdminRbacConstants.PERM_BREAK_GLASS_REVIEW);
    if (base.isPresent()) {
      return base;
    }
    if (adminWriteRequiresMfa() && !hasVerifiedMfa(jwt)) {
      return Optional.of(ErrorCode.ADMIN_WRITE_MFA_REQUIRED);
    }
    return Optional.empty();
  }

  public Optional<ErrorCode> ensureBreakGlassRevoke(Jwt jwt) {
    Optional<ErrorCode> base = ensureAdminPermission(jwt, PlatformAdminRbacConstants.PERM_BREAK_GLASS_REVOKE);
    if (base.isPresent()) {
      return base;
    }
    if (adminWriteRequiresMfa() && !hasVerifiedMfa(jwt)) {
      return Optional.of(ErrorCode.ADMIN_WRITE_MFA_REQUIRED);
    }
    return Optional.empty();
  }

  public Optional<ErrorCode> ensureBreakGlassRotationRead(Jwt jwt) {
    return ensureAdminPermission(jwt, PlatformAdminRbacConstants.PERM_BREAK_GLASS_ROTATION_READ);
  }

  public Optional<ErrorCode> ensureBreakGlassRotationManage(Jwt jwt) {
    Optional<ErrorCode> base =
        ensureAdminPermission(jwt, PlatformAdminRbacConstants.PERM_BREAK_GLASS_ROTATION_MANAGE);
    if (base.isPresent()) {
      return base;
    }
    if (adminWriteRequiresMfa() && !hasVerifiedMfa(jwt)) {
      return Optional.of(ErrorCode.ADMIN_WRITE_MFA_REQUIRED);
    }
    return Optional.empty();
  }

  /** Hot-reload mounted GitOps RBAC overrides (identity-service); admin-write MFA gate. */
  public Optional<ErrorCode> ensureAdminRbacOverridesReload(Jwt jwt) {
    if (!rbacProperties.enforce()) {
      if (!isAdmin(jwt)) {
        return Optional.of(ErrorCode.ADMIN_ACCESS_DENIED);
      }
      if (adminWriteRequiresMfa() && !hasVerifiedMfa(jwt)) {
        return Optional.of(ErrorCode.ADMIN_WRITE_MFA_REQUIRED);
      }
      return Optional.empty();
    }
    Optional<ErrorCode> base =
        ensureAdminPermission(jwt, PlatformAdminRbacConstants.PERM_RBAC_OVERRIDE_RELOAD);
    if (base.isPresent()) {
      return base;
    }
    if (adminWriteRequiresMfa() && !hasVerifiedMfa(jwt)) {
      return Optional.of(ErrorCode.ADMIN_WRITE_MFA_REQUIRED);
    }
    return Optional.empty();
  }

  public Optional<ErrorCode> ensureNotificationLegalHoldWrite(Jwt jwt) {
    if (!rbacProperties.enforce()) {
      if (!isAdmin(jwt)) {
        return Optional.of(ErrorCode.ADMIN_ACCESS_DENIED);
      }
      if (adminWriteRequiresMfa() && !hasVerifiedMfa(jwt)) {
        return Optional.of(ErrorCode.ADMIN_WRITE_MFA_REQUIRED);
      }
      return Optional.empty();
    }
    Optional<ErrorCode> base =
        ensureAdminPermission(jwt, PlatformAdminRbacConstants.PERM_NOTIFICATIONS_LEGAL_HOLD_WRITE);
    if (base.isPresent()) {
      return base;
    }
    if (adminWriteRequiresMfa() && !hasVerifiedMfa(jwt)) {
      return Optional.of(ErrorCode.ADMIN_WRITE_MFA_REQUIRED);
    }
    return Optional.empty();
  }

  public Optional<ErrorCode> ensureChangeRequestGitOpsDryRun(Jwt jwt) {
    if (!rbacProperties.enforce()) {
      return enterpriseAdminWriteDenialReason(jwt);
    }
    return ensureAdminPermission(
        jwt, PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_GITOPS_DRY_RUN);
  }

  /**
   * GitOps PR creation requires the dedicated permission plus the same admin-write MFA gate used
   * for other high-impact enterprise mutations.
   */
  public Optional<ErrorCode> ensureChangeRequestGitOpsCreatePr(Jwt jwt) {
    if (!rbacProperties.enforce()) {
      Optional<ErrorCode> base = enterpriseAdminWriteDenialReason(jwt);
      if (base.isPresent()) {
        return base;
      }
      if (adminWriteRequiresMfa() && !hasVerifiedMfa(jwt)) {
        return Optional.of(ErrorCode.ADMIN_WRITE_MFA_REQUIRED);
      }
      return Optional.empty();
    }
    Optional<ErrorCode> perm =
        ensureAdminPermission(jwt, PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_GITOPS_CREATE);
    if (perm.isPresent()) {
      return perm;
    }
    if (adminWriteRequiresMfa() && !hasVerifiedMfa(jwt)) {
      return Optional.of(ErrorCode.ADMIN_WRITE_MFA_REQUIRED);
    }
    return Optional.empty();
  }

  public Optional<ErrorCode> ensureChangeRequestCancel(Jwt jwt) {
    if (!rbacProperties.enforce()) {
      return enterpriseAdminWriteDenialReason(jwt);
    }
    if (jwt == null) {
      return Optional.of(ErrorCode.ADMIN_ACCESS_DENIED);
    }
    if (adminWriteRequiresMfa() && !hasVerifiedMfa(jwt)) {
      return Optional.of(ErrorCode.ADMIN_WRITE_MFA_REQUIRED);
    }
    if (hasPlatformAdminRole(jwt)) {
      return Optional.empty();
    }
    if (hasNonEmptyPermissionsClaim(jwt)) {
      if (hasPermissionClaim(jwt, PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_CANCEL)
          || hasPermissionClaim(jwt, PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_CREATE)) {
        return Optional.empty();
      }
      return Optional.of(ErrorCode.ADMIN_PERMISSION_REQUIRED);
    }
    if (hasPlatformAdminRole(jwt)) {
      return Optional.empty();
    }
    return Optional.of(ErrorCode.ADMIN_PERMISSION_REQUIRED);
  }

  public boolean mayCancelAnyPendingChangeRequest(Jwt jwt) {
    if (jwt == null || !rbacProperties.enforce()) {
      return false;
    }
    return hasPlatformAdminRole(jwt)
        || hasPermissionClaim(jwt, PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_CANCEL);
  }

  private Optional<ErrorCode> ensureOperationCreatePermission(Jwt jwt, String operationType) {
    Optional<String> required =
        GatewayAdminOperationPermissions.requiredCreatePermission(operationType);
    if (required.isEmpty()) {
      return Optional.of(ErrorCode.ADMIN_OPERATION_NOT_ALLOWED);
    }
    if (hasPlatformAdminRole(jwt)) {
      return Optional.empty();
    }
    if (hasPermissionClaim(jwt, required.get())) {
      return Optional.empty();
    }
    if (!hasNonEmptyPermissionsClaim(jwt) && hasPlatformAdminRole(jwt)) {
      return Optional.empty();
    }
    return Optional.of(ErrorCode.ADMIN_OPERATION_PERMISSION_REQUIRED);
  }

  private boolean adminWriteRequiresMfa() {
    return properties.requireMfa() || !"off".equals(properties.effectiveMfaMode());
  }

  private boolean allowlisted(Jwt jwt) {
    String userId = jwt.getSubject();
    if (userId != null && properties.allowedUserIdSet().contains(userId)) {
      return true;
    }
    String email = jwt.getClaimAsString("email");
    return email != null
        && properties.allowedEmailSet().contains(email.toLowerCase(Locale.ROOT).trim());
  }

  @SuppressWarnings("unchecked")
  private boolean hasNonEmptyPermissionsClaim(Jwt jwt) {
    Object p = jwt.getClaims().get("platform_permissions");
    if (p instanceof Collection<?> c) {
      return !c.isEmpty();
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private boolean hasPermissionClaim(Jwt jwt, String permission) {
    Object p = jwt.getClaims().get("platform_permissions");
    if (!(p instanceof Collection<?> c)) {
      return false;
    }
    for (Object o : c) {
      if (permission.equals(String.valueOf(o))) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private boolean hasPlatformAdminRole(Jwt jwt) {
    return hasRoleClaim(jwt.getClaims().get("platform_roles"))
        || hasRoleClaim(jwt.getClaims().get("roles"));
  }

  private boolean hasRoleClaim(Object roles) {
    if (roles instanceof Collection<?> collection) {
      return collection.stream().map(String::valueOf).anyMatch(this::isPlatformAdminRole);
    }
    if (roles instanceof String value) {
      for (String role : value.split(",")) {
        if (isPlatformAdminRole(role)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isPlatformAdminRole(String role) {
    if (role == null) {
      return false;
    }
    String normalized = role.trim().toUpperCase(Locale.ROOT);
    return PLATFORM_ADMIN.equals(normalized)
        || "ROLE_PLATFORM_ADMIN".equals(normalized)
        || "ADMIN".equals(normalized)
        || "ROLE_ADMIN".equals(normalized);
  }

  @SuppressWarnings("unchecked")
  private boolean hasVerifiedMfa(Jwt jwt) {
    Object mfaVerified = jwt.getClaims().get("mfa_verified");
    if (mfaVerified instanceof Boolean bool && bool) {
      return true;
    }
    Object amr = jwt.getClaims().get("amr");
    if (amr instanceof Collection<?> collection) {
      java.util.Set<String> accepted = properties.acceptedMfaMethods();
      return collection.stream()
          .map(String::valueOf)
          .map(value -> value.toLowerCase(Locale.ROOT))
          .anyMatch(accepted::contains);
    }
    return false;
  }
}
