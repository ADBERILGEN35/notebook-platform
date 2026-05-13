package com.notebook.lumen.identity.admin.retention;

import com.notebook.lumen.identity.admin.InternalAdminStatusProperties;
import com.notebook.lumen.identity.audit.AuditAdminAuthorizer;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/admin/retention/platform")
public class InternalPlatformRetentionController {
  private static final String ACTOR_HEADER = "X-Admin-Actor-User-Id";

  private final InternalAdminStatusProperties statusProperties;
  private final AuditAdminAuthorizer authorizer;
  private final PlatformRetentionProperties properties;
  private final PlatformRetentionGovernanceService service;

  public InternalPlatformRetentionController(
      InternalAdminStatusProperties statusProperties,
      AuditAdminAuthorizer authorizer,
      PlatformRetentionProperties properties,
      PlatformRetentionGovernanceService service) {
    this.statusProperties = statusProperties;
    this.authorizer = authorizer;
    this.properties = properties;
    this.service = service;
  }

  @GetMapping(path = "/targets", produces = MediaType.APPLICATION_JSON_VALUE)
  public PlatformRetentionDtos.TargetsResponse targets(
      @RequestHeader(value = AuditAdminAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      HttpServletRequest request) {
    authorize(serviceAuthorization, AuditAdminAuthorizer.PLATFORM_RETENTION_READ_SCOPE);
    return service.targets(request);
  }

  @GetMapping(path = "/plan", produces = MediaType.APPLICATION_JSON_VALUE)
  public PlatformRetentionDtos.PlanResponse plan(
      @RequestHeader(value = AuditAdminAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      @RequestParam(required = false) String target,
      @RequestParam(defaultValue = "true") boolean dryRun,
      HttpServletRequest request) {
    authorize(serviceAuthorization, AuditAdminAuthorizer.PLATFORM_RETENTION_READ_SCOPE);
    return service.plan(target, dryRun, request);
  }

  @GetMapping(path = "/legal-holds", produces = MediaType.APPLICATION_JSON_VALUE)
  public PlatformRetentionDtos.LegalHoldListResponse legalHolds(
      @RequestHeader(value = AuditAdminAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      @RequestParam(required = false) PlatformLegalHoldStatus status) {
    authorize(serviceAuthorization, AuditAdminAuthorizer.PLATFORM_RETENTION_READ_SCOPE);
    return service.legalHolds(Optional.ofNullable(status));
  }

  @PostMapping(
      path = "/legal-holds",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public PlatformRetentionDtos.LegalHoldResponse createLegalHold(
      @RequestHeader(value = AuditAdminAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      @RequestHeader(value = ACTOR_HEADER, required = false) String actorUserId,
      @RequestBody PlatformRetentionDtos.LegalHoldCreateRequest body,
      HttpServletRequest request) {
    authorize(serviceAuthorization, AuditAdminAuthorizer.PLATFORM_LEGAL_HOLD_WRITE_SCOPE);
    return service.createLegalHold(body, actor(actorUserId), request);
  }

  @PostMapping(
      path = "/legal-holds/{id}/release",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public PlatformRetentionDtos.LegalHoldResponse releaseLegalHold(
      @RequestHeader(value = AuditAdminAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      @RequestHeader(value = ACTOR_HEADER, required = false) String actorUserId,
      @PathVariable UUID id,
      @RequestBody PlatformRetentionDtos.LegalHoldReleaseRequest body,
      HttpServletRequest request) {
    authorize(serviceAuthorization, AuditAdminAuthorizer.PLATFORM_LEGAL_HOLD_WRITE_SCOPE);
    return service.releaseLegalHold(id, body, actor(actorUserId), request);
  }

  private void authorize(String serviceAuthorization, String scope) {
    if (!statusProperties.enabled() || !properties.enabled()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Platform retention is disabled");
    }
    authorizer.authorize(serviceAuthorization, scope);
  }

  private static UUID actor(String actorUserId) {
    if (actorUserId == null || actorUserId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin actor is required");
    }
    try {
      return UUID.fromString(actorUserId);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin actor must be a UUID");
    }
  }
}
