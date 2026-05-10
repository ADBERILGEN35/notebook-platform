package com.notebook.lumen.content.admin;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ContentAdminStatusService {
  private final boolean mergeAnalysisEnabled;
  private final boolean mergeApplyEnabled;
  private final String mergeSupportedVersions;
  private final boolean mergeIdempotencyEnabled;
  private final boolean mergeMetricsEnabled;
  private final boolean mergeAuditFailuresEnabled;

  public ContentAdminStatusService(
      @Value("${content.merge.analysis-enabled:false}") boolean mergeAnalysisEnabled,
      @Value("${content.merge.apply-enabled:false}") boolean mergeApplyEnabled,
      @Value("${content.merge.supported-versions:1}") String mergeSupportedVersions,
      @Value("${content.merge.idempotency-enabled:true}") boolean mergeIdempotencyEnabled,
      @Value("${content.merge.metrics-enabled:true}") boolean mergeMetricsEnabled,
      @Value("${content.merge.audit-failures-enabled:false}") boolean mergeAuditFailuresEnabled) {
    this.mergeAnalysisEnabled = mergeAnalysisEnabled;
    this.mergeApplyEnabled = mergeApplyEnabled;
    this.mergeSupportedVersions = mergeSupportedVersions;
    this.mergeIdempotencyEnabled = mergeIdempotencyEnabled;
    this.mergeMetricsEnabled = mergeMetricsEnabled;
    this.mergeAuditFailuresEnabled = mergeAuditFailuresEnabled;
  }

  public ContentAdminStatusResponse build() {
    List<Integer> supportedVersions =
        Arrays.stream(mergeSupportedVersions.split(","))
            .map(String::trim)
            .filter(part -> !part.isBlank())
            .map(Integer::parseInt)
            .toList();
    return new ContentAdminStatusResponse(
        new MergeStatus(
            mergeAnalysisEnabled,
            mergeApplyEnabled,
            supportedVersions,
            mergeIdempotencyEnabled,
            mergeMetricsEnabled,
            mergeAuditFailuresEnabled),
        new OfflineSyncRelatedStatus(mergeAnalysisEnabled || mergeApplyEnabled));
  }

  public record ContentAdminStatusResponse(MergeStatus merge, OfflineSyncRelatedStatus offlineSyncRelated) {}

  public record MergeStatus(
      boolean analysisEnabled,
      boolean applyEnabled,
      List<Integer> supportedVersions,
      boolean idempotencyEnabled,
      boolean metricsEnabled,
      boolean auditFailuresEnabled) {}

  public record OfflineSyncRelatedStatus(boolean semanticMergeAvailable) {}
}
