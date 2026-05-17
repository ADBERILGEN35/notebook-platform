package com.notebook.lumen.identity.scim.sync;

import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.CheckpointResponse;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.CompatibilityStatusResponse;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.DiagnosticRunRequest;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.ScimProviderStatus;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.SyncRunPageResponse;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.SyncRunResponse;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScimSyncDiagnosticsService {
  private final ScimProperties properties;
  private final ScimSyncCheckpointRepository checkpointRepository;
  private final ScimSyncRunRepository runRepository;
  private final AuditService auditService;
  private final MeterRegistry meterRegistry;

  public ScimSyncDiagnosticsService(
      ScimProperties properties,
      ScimSyncCheckpointRepository checkpointRepository,
      ScimSyncRunRepository runRepository,
      AuditService auditService,
      MeterRegistry meterRegistry) {
    this.properties = properties;
    this.checkpointRepository = checkpointRepository;
    this.runRepository = runRepository;
    this.auditService = auditService;
    this.meterRegistry = meterRegistry;
  }

  @Transactional(readOnly = true)
  public CompatibilityStatusResponse compatibilityStatus(HttpServletRequest request) {
    List<CheckpointResponse> checkpoints =
        checkpointRepository.findAllByOrderByProviderAscResourceTypeAsc().stream()
            .map(this::toCheckpoint)
            .toList();
    SyncRunResponse lastRun =
        runRepository
            .search(
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 1, Sort.by("startedAt").descending()))
            .stream()
            .findFirst()
            .map(this::toRun)
            .orElse(null);
    String lastStatus = lastRun == null ? "IDLE" : lastRun.status().name();
    auditService.record(
        "SCIM_COMPATIBILITY_STATUS_VIEWED",
        null,
        "SCIM_DIAGNOSTICS",
        null,
        request,
        Map.of("provider", provider(), "deltaSyncMode", deltaMode()));
    return new CompatibilityStatusResponse(
        new ScimProviderStatus(
            provider(),
            properties.deltaSyncEnabled(),
            deltaMode(),
            properties.providerSupportsBulk(),
            properties.providerSupportsFiltering(),
            properties.providerSupportsPatch(),
            properties.providerSupportsNestedGroups(),
            properties.providerRateLimitAware(),
            Math.max(1, properties.providerMaxPageSize()),
            lastStatus,
            warnings()),
        checkpoints,
        lastRun);
  }

  @Transactional(readOnly = true)
  public SyncRunPageResponse syncRuns(
      String provider,
      ScimResourceType resourceType,
      ScimSyncRunStatus status,
      Instant from,
      Instant to,
      int page,
      int size) {
    int safePage = Math.max(0, page);
    int safeSize = Math.max(1, Math.min(size, 100));
    var result =
        runRepository.search(
            blankToNull(provider),
            resourceType,
            status,
            from,
            to,
            PageRequest.of(safePage, safeSize, Sort.by("startedAt").descending()));
    return new SyncRunPageResponse(
        result.stream().map(this::toRun).toList(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public List<CheckpointResponse> checkpoints() {
    return checkpointRepository.findAllByOrderByProviderAscResourceTypeAsc().stream()
        .map(this::toCheckpoint)
        .toList();
  }

  @Transactional
  public SyncRunResponse createDiagnosticRun(
      DiagnosticRunRequest request, HttpServletRequest httpRequest) {
    ScimResourceType resourceType =
        request.resourceType() == null ? ScimResourceType.USER : request.resourceType();
    ScimSyncMode mode = request.syncMode() == null ? ScimSyncMode.DELTA : request.syncMode();
    ScimSyncRun run =
        new ScimSyncRun(
            UUID.randomUUID(),
            provider(),
            resourceType,
            mode,
            requestId(httpRequest),
            Instant.now());
    runRepository.save(run);
    auditService.record(
        "SCIM_SYNC_RUN_STARTED",
        null,
        "SCIM_SYNC_RUN",
        run.getId(),
        httpRequest,
        Map.of(
            "provider", provider(), "resourceType", resourceType.name(), "syncMode", mode.name()));
    var counts = request.counts() == null ? Map.<String, Long>of() : request.counts();
    if (request.errorCode() != null && !request.errorCode().isBlank()) {
      run.fail(request.errorCode(), request.errorSummary());
      auditService.record(
          "SCIM_SYNC_RUN_FAILED",
          null,
          "SCIM_SYNC_RUN",
          run.getId(),
          httpRequest,
          Map.of(
              "provider",
              provider(),
              "resourceType",
              resourceType.name(),
              "syncMode",
              mode.name(),
              "errorCode",
              request.errorCode()));
      meterRegistry
          .counter(
              "scim_sync_runs_total",
              "provider",
              provider(),
              "resourceType",
              resourceType.name(),
              "mode",
              mode.name(),
              "result",
              "FAILED")
          .increment();
      meterRegistry
          .counter("scim_sync_errors_total", "errorCode", safeTag(request.errorCode()))
          .increment();
    } else {
      run.complete(
          counts.getOrDefault("processed", 0L),
          counts.getOrDefault("created", 0L),
          counts.getOrDefault("updated", 0L),
          counts.getOrDefault("deprovisioned", 0L),
          counts.getOrDefault("skipped", 0L));
      auditService.record(
          "SCIM_SYNC_RUN_COMPLETED",
          null,
          "SCIM_SYNC_RUN",
          run.getId(),
          httpRequest,
          Map.of(
              "provider", provider(),
              "resourceType", resourceType.name(),
              "syncMode", mode.name(),
              "processedCount", run.getProcessedCount(),
              "createdCount", run.getCreatedCount(),
              "updatedCount", run.getUpdatedCount(),
              "deprovisionedCount", run.getDeprovisionedCount(),
              "skippedCount", run.getSkippedCount()));
      meterRegistry
          .counter(
              "scim_sync_runs_total",
              "provider",
              provider(),
              "resourceType",
              resourceType.name(),
              "mode",
              mode.name(),
              "result",
              "COMPLETED")
          .increment();
      incrementProcessed(resourceType, "processed", run.getProcessedCount());
      incrementProcessed(resourceType, "created", run.getCreatedCount());
      incrementProcessed(resourceType, "updated", run.getUpdatedCount());
      incrementProcessed(resourceType, "deprovisioned", run.getDeprovisionedCount());
      incrementProcessed(resourceType, "skipped", run.getSkippedCount());
    }
    return toRun(runRepository.save(run));
  }

  @Transactional
  public CheckpointResponse ensureCheckpoint(
      ScimResourceType resourceType, ScimSyncMode mode, String token, HttpServletRequest request) {
    ScimResourceType safeType = resourceType == null ? ScimResourceType.USER : resourceType;
    ScimSyncMode safeMode = mode == null ? ScimSyncMode.DELTA : mode;
    ScimSyncCheckpoint checkpoint =
        checkpointRepository
            .findByProviderAndResourceType(provider(), safeType)
            .orElseGet(
                () -> {
                  ScimSyncCheckpoint created =
                      new ScimSyncCheckpoint(
                          UUID.randomUUID(),
                          provider(),
                          safeType,
                          safeMode,
                          token,
                          ScimSyncCheckpointStatus.IDLE,
                          Instant.now());
                  auditService.record(
                      "SCIM_SYNC_CHECKPOINT_CREATED",
                      null,
                      "SCIM_SYNC_CHECKPOINT",
                      created.getId(),
                      request,
                      Map.of(
                          "provider",
                          provider(),
                          "resourceType",
                          safeType.name(),
                          "syncMode",
                          safeMode.name()));
                  return created;
                });
    checkpoint.markAttempt(safeMode, token);
    checkpoint.markSuccess(token);
    ScimSyncCheckpoint saved = checkpointRepository.save(checkpoint);
    auditService.record(
        "SCIM_SYNC_CHECKPOINT_UPDATED",
        null,
        "SCIM_SYNC_CHECKPOINT",
        saved.getId(),
        request,
        Map.of(
            "provider", provider(), "resourceType", safeType.name(), "syncMode", safeMode.name()));
    return toCheckpoint(saved);
  }

  private List<String> warnings() {
    List<String> out = new ArrayList<>();
    if (!properties.enabled()) out.add("SCIM_DISABLED");
    if (properties.enabled() && !properties.authConfigured()) out.add("SCIM_TOKEN_MISSING");
    if (properties.deltaSyncEnabled() && "disabled".equals(deltaMode()))
      out.add("DELTA_ENABLED_WITH_DISABLED_MODE");
    if (!properties.providerSupportsFiltering()) out.add("DELTA_LAST_MODIFIED_FILTER_UNAVAILABLE");
    if (!properties.providerRateLimitAware()) out.add("RATE_LIMIT_AWARENESS_DISABLED");
    if (properties.providerSupportsNestedGroups() && !properties.groupNestingEnabled())
      out.add("PROVIDER_NESTED_GROUPS_BUT_LOCAL_DISABLED");
    return out;
  }

  private CheckpointResponse toCheckpoint(ScimSyncCheckpoint c) {
    return new CheckpointResponse(
        c.getId(),
        c.getProvider(),
        c.getResourceType(),
        c.getSyncMode(),
        c.getCheckpointToken() != null && !c.getCheckpointToken().isBlank(),
        c.getLastSuccessfulSyncAt(),
        c.getLastAttemptAt(),
        c.getStatus(),
        c.getUpdatedAt());
  }

  public SyncRunResponse toRunResponse(ScimSyncRun r) {
    return toRun(r);
  }

  private SyncRunResponse toRun(ScimSyncRun r) {
    var metadata = ScimDeltaRunMetadataCodec.decode(r.getLastErrorSummary());
    String providerErrorClass = r.getLastErrorCode();
    Integer retryAfterSeconds = null;
    Boolean retryAfterCapped = null;
    Instant nextRecommendedAttemptAt = null;
    if (metadata.isPresent()) {
      retryAfterSeconds = metadata.get().retryAfterSeconds();
      retryAfterCapped = metadata.get().retryAfterCapped();
      nextRecommendedAttemptAt = metadata.get().nextRecommendedAttemptAt();
    }
    return new SyncRunResponse(
        r.getId(),
        r.getProvider(),
        r.getResourceType(),
        r.getSyncMode(),
        r.getStatus(),
        r.getStartedAt(),
        r.getCompletedAt(),
        r.getProcessedCount(),
        r.getCreatedCount(),
        r.getUpdatedCount(),
        r.getDeprovisionedCount(),
        r.getSkippedCount(),
        r.getErrorCount(),
        r.getLastErrorCode(),
        r.getLastErrorSummary(),
        r.getRequestId(),
        providerErrorClass,
        retryAfterSeconds,
        retryAfterCapped,
        nextRecommendedAttemptAt);
  }

  private String provider() {
    return Optional.ofNullable(properties.providerType())
        .filter(v -> !v.isBlank())
        .orElse("generic");
  }

  private String deltaMode() {
    String value = Optional.ofNullable(properties.deltaSyncMode()).orElse("disabled").toLowerCase();
    return switch (value) {
      case "diagnostic", "manual" -> value;
      default -> "disabled";
    };
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static String requestId(HttpServletRequest request) {
    if (request == null) return null;
    Object attr = request.getAttribute("requestId");
    if (attr != null) return attr.toString();
    return request.getHeader("X-Request-Id");
  }

  private void incrementProcessed(ScimResourceType resourceType, String result, long amount) {
    if (amount <= 0) return;
    meterRegistry
        .counter("scim_sync_processed_total", "resourceType", resourceType.name(), "result", result)
        .increment(amount);
  }

  private static String safeTag(String value) {
    return value == null || value.isBlank() ? "unknown" : value;
  }
}
