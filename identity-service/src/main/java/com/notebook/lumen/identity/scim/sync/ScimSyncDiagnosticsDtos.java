package com.notebook.lumen.identity.scim.sync;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ScimSyncDiagnosticsDtos {
  private ScimSyncDiagnosticsDtos() {}

  public record CompatibilityStatusResponse(
      ScimProviderStatus scimProvider,
      List<CheckpointResponse> checkpoints,
      SyncRunResponse lastRun) {}

  public record ScimProviderStatus(
      String type,
      boolean deltaSyncEnabled,
      String deltaSyncMode,
      boolean bulkSupported,
      boolean filteringSupported,
      boolean patchSupported,
      boolean nestedGroupsSupported,
      boolean rateLimitAware,
      int maxPageSize,
      String lastSyncStatus,
      List<String> warnings) {}

  public record CheckpointResponse(
      UUID id,
      String provider,
      ScimResourceType resourceType,
      ScimSyncMode syncMode,
      boolean checkpointPresent,
      Instant lastSuccessfulSyncAt,
      Instant lastAttemptAt,
      ScimSyncCheckpointStatus status,
      Instant updatedAt) {}

  public record SyncRunResponse(
      UUID id,
      String provider,
      ScimResourceType resourceType,
      ScimSyncMode syncMode,
      ScimSyncRunStatus status,
      Instant startedAt,
      Instant completedAt,
      long processedCount,
      long createdCount,
      long updatedCount,
      long deprovisionedCount,
      long skippedCount,
      long errorCount,
      String lastErrorCode,
      String lastErrorSummary,
      String requestId,
      String providerErrorClass,
      Integer retryAfterSeconds,
      Boolean retryAfterCapped,
      Instant nextRecommendedAttemptAt) {}

  public record SyncRunPageResponse(
      List<SyncRunResponse> items, int page, int size, long totalElements, int totalPages) {}

  public record DiagnosticRunRequest(
      ScimResourceType resourceType,
      ScimSyncMode syncMode,
      Map<String, Long> counts,
      String errorCode,
      String errorSummary) {}

  public record DeltaReadinessResponse(
      String providerType,
      boolean deltaPocEnabled,
      boolean dryRunOnly,
      String selectedStrategy,
      String deltaSource,
      boolean supportsFiltering,
      boolean supportsPagination,
      boolean supportsPatch,
      boolean supportsRetryAfter,
      boolean capabilityAligned,
      String deprovisionSemantics,
      LastCheckpointSummary lastCheckpoint,
      String lastDryRunStatus,
      boolean remoteFetchEnabled,
      boolean remoteFetchConfigured,
      boolean remoteFetchAttempted,
      int fetchedResourceCount,
      int pageObserved,
      boolean nextCursorPresent,
      boolean rateLimitAware,
      boolean retryAfterObserved,
      Integer retryAfterSeconds,
      Boolean retryAfterCapped,
      Instant nextRecommendedAttemptAt,
      String providerErrorClass,
      int backoffBaseSeconds,
      int httpTimeoutMs,
      List<String> warnings) {}

  public record LastCheckpointSummary(
      ScimResourceType resourceType,
      boolean checkpointPresent,
      ScimSyncCheckpointStatus status,
      Instant lastSuccessfulSyncAt) {}

  public record DryRunPocRequest(
      ScimResourceType resourceType,
      boolean observedRetryAfter,
      Integer retryAfterSeconds,
      Integer simulatedHttpStatus,
      String simulatedRetryAfter,
      Boolean simulatedTimeout,
      Boolean simulatedBadResponse) {}

  public record DryRunPocResponse(
      String providerType,
      boolean dryRunOnly,
      String selectedStrategy,
      String lastDryRunStatus,
      SyncRunResponse run,
      CheckpointResponse checkpoint,
      boolean remoteFetchEnabled,
      boolean remoteFetchConfigured,
      boolean remoteFetchAttempted,
      int fetchedResourceCount,
      int pageObserved,
      boolean nextCursorPresent,
      boolean rateLimitAware,
      boolean retryAfterObserved,
      Integer retryAfterSeconds,
      Boolean retryAfterCapped,
      Instant nextRecommendedAttemptAt,
      String providerErrorClass,
      int backoffBaseSeconds,
      int httpTimeoutMs,
      List<String> warnings) {}
}
