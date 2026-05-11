package com.notebook.lumen.identity.admin.rbac.overrides;

import com.notebook.lumen.identity.shared.exception.IdentityRuntimeException;
import org.springframework.http.HttpStatus;

/** Operational errors for RBAC override reload (no raw manifest in message). */
public final class AdminRbacOverridesReloadException extends IdentityRuntimeException {

  public AdminRbacOverridesReloadException(String errorCode, HttpStatus status, String message) {
    super(errorCode, status, message);
  }

  public static AdminRbacOverridesReloadException reloadDisabled() {
    return new AdminRbacOverridesReloadException(
        "ADMIN_RBAC_OVERRIDES_RELOAD_DISABLED",
        HttpStatus.FORBIDDEN,
        "RBAC override reload is disabled by configuration.");
  }

  public static AdminRbacOverridesReloadException reasonRequired() {
    return new AdminRbacOverridesReloadException(
        "ADMIN_RBAC_OVERRIDES_REASON_REQUIRED",
        HttpStatus.BAD_REQUEST,
        "A non-empty reason (at least 10 characters) is required.");
  }

  public static AdminRbacOverridesReloadException fileUnavailable() {
    return new AdminRbacOverridesReloadException(
        "ADMIN_RBAC_OVERRIDES_FILE_UNAVAILABLE",
        HttpStatus.SERVICE_UNAVAILABLE,
        "Override manifest file is not available.");
  }

  public static AdminRbacOverridesReloadException invalidManifest(String safeSummary) {
    return new AdminRbacOverridesReloadException(
        "ADMIN_RBAC_OVERRIDES_INVALID_MANIFEST",
        HttpStatus.UNPROCESSABLE_ENTITY,
        safeSummary == null || safeSummary.isBlank()
            ? "Override manifest failed validation."
            : safeSummary);
  }
}
