package com.notebook.lumen.identity.admin.rbac;

import com.notebook.lumen.identity.shared.exception.IdentityRuntimeException;
import org.springframework.http.HttpStatus;

public final class AdminRbacVisibilityDisabledException extends IdentityRuntimeException {
  public AdminRbacVisibilityDisabledException() {
    super(
        "ADMIN_RBAC_VISIBILITY_DISABLED",
        HttpStatus.NOT_FOUND,
        "Admin RBAC visibility API is disabled on this deployment");
  }
}
