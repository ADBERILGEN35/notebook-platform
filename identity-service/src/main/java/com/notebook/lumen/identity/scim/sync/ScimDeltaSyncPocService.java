package com.notebook.lumen.identity.scim.sync;

import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.sync.ScimDeltaStrategyResolver.ScimDeltaStrategyPlan;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.CheckpointResponse;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.DeltaReadinessResponse;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.DryRunPocRequest;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.DryRunPocResponse;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.LastCheckpointSummary;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.SyncRunResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Provider-specific delta sync POC (Faz 115–116). Diagnostic only — no IdP mutation, no deprovision from
 * missing delta users. Remote fetch disabled by default (Faz 116).
 */
@Service
public class ScimDeltaSyncPocService {

  private final ScimProperties properties;
  private final ScimDeltaStrategyResolver strategyResolver;
  private final ScimDeltaFetchDiagnosticsService fetchDiagnosticsService;
  private final ScimSyncCheckpointRepository checkpointRepository;
  private final ScimSyncRunRepository runRepository;
  private final ScimSyncDiagnosticsService diagnosticsService;
  private final AuditService auditService;

  public ScimDeltaSyncPocService(
      ScimProperties properties,
      ScimDeltaStrategyResolver strategyResolver,
      ScimDeltaFetchDiagnosticsService fetchDiagnosticsService,
      ScimSyncCheckpointRepository checkpointRepository,
      ScimSyncRunRepository runRepository,
      ScimSyncDiagnosticsService diagnosticsService,
      AuditService auditService) {
    this.properties = properties;
    this.strategyResolver = strategyResolver;
    this.fetchDiagnosticsService = fetchDiagnosticsService;
    this.checkpointRepository = checkpointRepository;
    this.runRepository = runRepository;
    this.diagnosticsService = diagnosticsService;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public DeltaReadinessResponse deltaReadiness(HttpServletRequest request) {
    ScimDeltaStrategyPlan plan = strategyResolver.resolve(properties);
    Set<String> warnings = new LinkedHashSet<>(plan.warnings());
    LastCheckpointSummary checkpoint = resolveLastCheckpoint(new ArrayList<>(warnings));
    String lastDryRunStatus = resolveLastDryRunStatus();
    ScimDeltaRateLimitDiagnostics rate =
        fetchDiagnosticsService.evaluateReadiness(new ArrayList<>(warnings));
    mergeRateWarnings(warnings, rate);
    SyncRunResponse lastRun = resolveLastRunWithRateMetadata();

    auditService.record(
        "SCIM_DELTA_READINESS_VIEWED",
        null,
        "SCIM_DELTA_POC",
        null,
        request,
        Map.of(
            "provider", plan.providerType(),
            "selectedStrategy", plan.selectedStrategy().name(),
            "deltaPocEnabled", plan.deltaPocEnabled(),
            "remoteFetchEnabled", rate.remoteFetchEnabled(),
            "remoteFetchConfigured", rate.remoteFetchConfigured()));

    return buildReadiness(plan, checkpoint, lastDryRunStatus, rate, lastRun, List.copyOf(warnings));
  }

  @Transactional
  public DryRunPocResponse executeDryRun(DryRunPocRequest body, HttpServletRequest request) {
    if (!properties.deltaProviderPocEnabled()) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "SCIM delta provider POC is disabled");
    }
    if (!properties.deltaDryRunOnly()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "SCIM delta dry-run only mode is required for POC");
    }

    ScimDeltaStrategyPlan plan = strategyResolver.resolve(properties);
    Set<String> warnings = new LinkedHashSet<>(plan.warnings());
    ScimDeltaRateLimitDiagnostics rate =
        fetchDiagnosticsService.evaluateDryRun(body, new ArrayList<>(warnings));
    mergeRateWarnings(warnings, rate);

    ScimResourceType resourceType =
        body == null || body.resourceType() == null ? ScimResourceType.USER : body.resourceType();

    String errorCode = fetchDiagnosticsService.runErrorCode(rate);
    long processed = rate.fetchedResourceCount();
    long skipped = processed > 0 ? 0L : 1L;
    Map<String, Long> counts =
        Map.of("processed", processed, "skipped", skipped, "deprovisioned", 0L);
    SyncRunResponse run =
        diagnosticsService.createDiagnosticRun(
            new ScimSyncDiagnosticsDtos.DiagnosticRunRequest(
                resourceType,
                ScimSyncMode.DELTA,
                counts,
                errorCode,
                fetchDiagnosticsService.runErrorSummary(rate)),
            request);

    CheckpointResponse checkpoint =
        diagnosticsService.ensureCheckpoint(resourceType, ScimSyncMode.DELTA, null, request);

    warnings.addAll(strategyResolver.warningsForCheckpoint(checkpoint.lastSuccessfulSyncAt()));

    auditService.record(
        "SCIM_DELTA_DRY_RUN_EXECUTED",
        null,
        "SCIM_DELTA_POC",
        run.id(),
        request,
        Map.of(
            "provider", plan.providerType(),
            "resourceType", resourceType.name(),
            "selectedStrategy", plan.selectedStrategy().name(),
            "deprovisionedCount", 0,
            "providerErrorClass",
            rate.providerErrorClass().name(),
            "retryAfterSeconds",
            rate.retryAfterSeconds() == null ? 0 : rate.retryAfterSeconds()));

    return buildDryRunResponse(plan, run, checkpoint, rate, List.copyOf(warnings));
  }

  private DeltaReadinessResponse buildReadiness(
      ScimDeltaStrategyPlan plan,
      LastCheckpointSummary checkpoint,
      String lastDryRunStatus,
      ScimDeltaRateLimitDiagnostics rate,
      SyncRunResponse lastRun,
      List<String> warnings) {
    boolean retryObserved = rate.retryAfterObserved();
    Integer retrySeconds = rate.retryAfterSeconds();
    Boolean retryCapped = rate.retryAfterCapped();
    Instant nextAttempt = rate.nextRecommendedAttemptAt();
    String providerErrorClass = rate.providerErrorClass().name();

    if (lastRun != null && lastRun.retryAfterSeconds() != null) {
      retryObserved = true;
      retrySeconds = lastRun.retryAfterSeconds();
      retryCapped = lastRun.retryAfterCapped();
      nextAttempt = lastRun.nextRecommendedAttemptAt();
      if (lastRun.providerErrorClass() != null) {
        providerErrorClass = lastRun.providerErrorClass();
      }
    }

    return new DeltaReadinessResponse(
        plan.providerType(),
        plan.deltaPocEnabled(),
        plan.dryRunOnly(),
        plan.selectedStrategy().name(),
        plan.deltaSource(),
        plan.supportsFiltering(),
        plan.supportsPagination(),
        plan.supportsPatch(),
        plan.supportsRetryAfter(),
        plan.capabilityAligned(),
        plan.deprovisionSemantics(),
        checkpoint,
        lastDryRunStatus,
        rate.remoteFetchEnabled(),
        rate.remoteFetchConfigured(),
        rate.remoteFetchAttempted(),
        rate.fetchedResourceCount(),
        rate.pageObserved(),
        rate.nextCursorPresent(),
        rate.rateLimitAware(),
        retryObserved,
        retrySeconds,
        retryCapped,
        nextAttempt,
        providerErrorClass,
        rate.backoffBaseSeconds(),
        rate.httpTimeoutMs(),
        warnings);
  }

  private DryRunPocResponse buildDryRunResponse(
      ScimDeltaStrategyPlan plan,
      SyncRunResponse run,
      CheckpointResponse checkpoint,
      ScimDeltaRateLimitDiagnostics rate,
      List<String> warnings) {
    return new DryRunPocResponse(
        plan.providerType(),
        plan.dryRunOnly(),
        plan.selectedStrategy().name(),
        run.status().name(),
        run,
        checkpoint,
        rate.remoteFetchEnabled(),
        rate.remoteFetchConfigured(),
        rate.remoteFetchAttempted(),
        rate.fetchedResourceCount(),
        rate.pageObserved(),
        rate.nextCursorPresent(),
        rate.rateLimitAware(),
        rate.retryAfterObserved(),
        rate.retryAfterSeconds(),
        rate.retryAfterCapped(),
        rate.nextRecommendedAttemptAt(),
        rate.providerErrorClass().name(),
        rate.backoffBaseSeconds(),
        rate.httpTimeoutMs(),
        warnings);
  }

  private static void mergeRateWarnings(Set<String> warnings, ScimDeltaRateLimitDiagnostics rate) {
    warnings.addAll(rate.warnings());
  }

  private LastCheckpointSummary resolveLastCheckpoint(List<String> warnings) {
    return checkpointRepository
        .findByProviderAndResourceType(
            ScimDeltaProviderKind.fromConfig(properties.providerType()).configValue(),
            ScimResourceType.USER)
        .map(
            c -> {
              warnings.addAll(strategyResolver.warningsForCheckpoint(c.getLastSuccessfulSyncAt()));
              return new LastCheckpointSummary(
                  c.getResourceType(),
                  c.getCheckpointToken() != null && !c.getCheckpointToken().isBlank(),
                  c.getStatus(),
                  c.getLastSuccessfulSyncAt());
            })
        .orElse(
            new LastCheckpointSummary(
                ScimResourceType.USER, false, ScimSyncCheckpointStatus.IDLE, null));
  }

  private String resolveLastDryRunStatus() {
    return runRepository
        .search(
            ScimDeltaProviderKind.fromConfig(properties.providerType()).configValue(),
            null,
            null,
            null,
            null,
            org.springframework.data.domain.PageRequest.of(
                0, 1, org.springframework.data.domain.Sort.by("startedAt").descending()))
        .stream()
        .findFirst()
        .map(r -> r.getStatus().name())
        .orElse("NONE");
  }

  private SyncRunResponse resolveLastRunWithRateMetadata() {
    return runRepository
        .search(
            ScimDeltaProviderKind.fromConfig(properties.providerType()).configValue(),
            null,
            null,
            null,
            null,
            org.springframework.data.domain.PageRequest.of(
                0, 1, org.springframework.data.domain.Sort.by("startedAt").descending()))
        .stream()
        .findFirst()
        .map(diagnosticsService::toRunResponse)
        .orElse(null);
  }
}
