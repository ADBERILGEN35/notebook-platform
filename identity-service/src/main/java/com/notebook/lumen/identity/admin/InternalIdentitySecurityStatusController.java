package com.notebook.lumen.identity.admin;

import com.notebook.lumen.identity.audit.AuditAdminAuthorizer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/admin/status")
public class InternalIdentitySecurityStatusController {
  private final InternalAdminStatusProperties statusProperties;
  private final AuditAdminAuthorizer authorizer;
  private final IdentitySecurityStatusService statusService;

  public InternalIdentitySecurityStatusController(
      InternalAdminStatusProperties statusProperties,
      AuditAdminAuthorizer authorizer,
      IdentitySecurityStatusService statusService) {
    this.statusProperties = statusProperties;
    this.authorizer = authorizer;
    this.statusService = statusService;
  }

  @GetMapping(path = "/identity-security", produces = MediaType.APPLICATION_JSON_VALUE)
  public IdentitySecurityStatusResponse identitySecurity(
      @RequestHeader(value = AuditAdminAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization) {
    if (!statusProperties.enabled()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Internal admin status is disabled");
    }
    authorizer.authorize(serviceAuthorization, AuditAdminAuthorizer.ADMIN_STATUS_SCOPE);
    return statusService.build();
  }
}
