package com.notebook.lumen.identity.scim.sync;

import com.notebook.lumen.identity.admin.InternalAdminStatusProperties;
import com.notebook.lumen.identity.audit.AuditAdminAuthorizer;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.CheckpointResponse;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.CompatibilityStatusResponse;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.SyncRunPageResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.DeltaReadinessResponse;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.DryRunPocRequest;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.DryRunPocResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/admin/scim")
public class InternalScimDiagnosticsController {
  private final InternalAdminStatusProperties statusProperties;
  private final AuditAdminAuthorizer authorizer;
  private final ScimSyncDiagnosticsService service;
  private final ScimDeltaSyncPocService deltaPocService;

  public InternalScimDiagnosticsController(
      InternalAdminStatusProperties statusProperties,
      AuditAdminAuthorizer authorizer,
      ScimSyncDiagnosticsService service,
      ScimDeltaSyncPocService deltaPocService) {
    this.statusProperties = statusProperties;
    this.authorizer = authorizer;
    this.service = service;
    this.deltaPocService = deltaPocService;
  }

  @GetMapping(path = "/compatibility/status", produces = MediaType.APPLICATION_JSON_VALUE)
  public CompatibilityStatusResponse compatibilityStatus(
      @RequestHeader(value = AuditAdminAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      HttpServletRequest request) {
    authorize(serviceAuthorization);
    return service.compatibilityStatus(request);
  }

  @GetMapping(path = "/sync-runs", produces = MediaType.APPLICATION_JSON_VALUE)
  public SyncRunPageResponse syncRuns(
      @RequestHeader(value = AuditAdminAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      @RequestParam(required = false) String provider,
      @RequestParam(required = false) ScimResourceType resourceType,
      @RequestParam(required = false) ScimSyncRunStatus status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size) {
    authorize(serviceAuthorization);
    return service.syncRuns(provider, resourceType, status, from, to, page, size);
  }

  @GetMapping(path = "/sync-checkpoints", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<CheckpointResponse> syncCheckpoints(
      @RequestHeader(value = AuditAdminAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization) {
    authorize(serviceAuthorization);
    return service.checkpoints();
  }

  @GetMapping(path = "/delta/readiness", produces = MediaType.APPLICATION_JSON_VALUE)
  public DeltaReadinessResponse deltaReadiness(
      @RequestHeader(value = AuditAdminAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      HttpServletRequest request) {
    authorize(serviceAuthorization);
    return deltaPocService.deltaReadiness(request);
  }

  @PostMapping(path = "/delta/dry-run", produces = MediaType.APPLICATION_JSON_VALUE)
  public DryRunPocResponse deltaDryRun(
      @RequestHeader(value = AuditAdminAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      @RequestBody(required = false) DryRunPocRequest body,
      HttpServletRequest request) {
    authorize(serviceAuthorization);
    return deltaPocService.executeDryRun(body, request);
  }

  private void authorize(String serviceAuthorization) {
    if (!statusProperties.enabled()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Internal admin status is disabled");
    }
    authorizer.authorize(serviceAuthorization, AuditAdminAuthorizer.SCIM_DIAGNOSTICS_READ_SCOPE);
  }
}
