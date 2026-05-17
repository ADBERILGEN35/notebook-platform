package com.notebook.lumen.gateway.admin.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlatformRetentionMetricsPublisherTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final PlatformRetentionMetricsPublisher publisher =
      new PlatformRetentionMetricsPublisher(registry);

  private static Map<String, Object> target(
      String targetKey, String service, String status, boolean blocked, List<String> warnings) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("targetKey", targetKey);
    row.put("service", service);
    row.put("status", status);
    row.put("blockedByLegalHold", blocked);
    row.put("warnings", new ArrayList<>(warnings));
    return row;
  }

  private static Map<String, Object> planWith(List<Map<String, Object>> targets) {
    Map<String, Object> plan = new LinkedHashMap<>();
    plan.put("targets", new ArrayList<>(targets));
    return plan;
  }

  private double counter(String name, String... tags) {
    var c = registry.find(name).tags(tags).counter();
    return c == null ? 0d : c.count();
  }

  @Test
  void emitsReadySummaryAndTargetStatusMetrics() {
    Map<String, Object> plan =
        planWith(
            List.of(
                target(
                    "content.note_versions", "content-service", "DRY_RUN_READY", false, List.of()),
                target("content.comments", "content-service", "DRY_RUN_READY", false, List.of())));
    AdminPlatformRetentionProxyService.applyServiceSummaries(plan);

    publisher.publish(plan);

    assertThat(counter("platform_retention_plan_generated_total", "result", "success"))
        .isEqualTo(1d);
    assertThat(
            counter(
                "platform_retention_service_summary_total",
                "service",
                "content-service",
                "status",
                "READY"))
        .isEqualTo(1d);
    assertThat(
            counter(
                "platform_retention_service_targets_total",
                "service",
                "content-service",
                "target_status",
                "DRY_RUN_READY"))
        .isEqualTo(2d);
  }

  @Test
  void emitsUnavailableSummaryFromPlanWarning() {
    Map<String, Object> plan =
        planWith(
            List.of(
                target(
                    "content.note_versions",
                    "content-service",
                    "INVENTORY_ONLY",
                    false,
                    List.of())));
    plan.put("warnings", new ArrayList<>(List.of("CONTENT_RETENTION_SERVICE_UNAVAILABLE")));
    AdminPlatformRetentionProxyService.applyServiceSummaries(plan);

    publisher.publish(plan);

    assertThat(
            counter(
                "platform_retention_service_summary_total",
                "service",
                "content-service",
                "status",
                "UNAVAILABLE"))
        .isEqualTo(1d);
    assertThat(
            counter(
                "platform_retention_service_warnings_total",
                "service",
                "content-service",
                "warning_code",
                "SERVICE_UNAVAILABLE"))
        .isEqualTo(1d);
  }

  @Test
  void emitsBlockedAndCappedAndKnownWarningMetrics() {
    Map<String, Object> plan =
        planWith(
            List.of(
                target(
                    "notification.digest_items_terminal",
                    "notification-service",
                    "DRY_RUN_READY",
                    true,
                    List.of(
                        "NOTIFICATION_RETENTION_QUERY_CAPPED",
                        "NOTIFICATION_RETENTION_LEGAL_HOLD_BLOCKED"))));
    AdminPlatformRetentionProxyService.applyServiceSummaries(plan);

    publisher.publish(plan);

    assertThat(
            counter(
                "platform_retention_service_blocked_targets_total",
                "service",
                "notification-service"))
        .isEqualTo(1d);
    assertThat(
            counter(
                "platform_retention_service_capped_targets_total",
                "service",
                "notification-service"))
        .isEqualTo(1d);
    assertThat(
            counter(
                "platform_retention_service_warnings_total",
                "service",
                "notification-service",
                "warning_code",
                "COUNT_CAPPED"))
        .isEqualTo(1d);
    assertThat(
            counter(
                "platform_retention_service_warnings_total",
                "service",
                "notification-service",
                "warning_code",
                "LEGAL_HOLD_BLOCKED"))
        .isEqualTo(1d);
  }

  @Test
  void unknownWarningAndUnknownLabelsCollapseToBoundedFallback() {
    Map<String, Object> plan =
        planWith(
            List.of(
                target(
                    "weird.thing",
                    "weird-service",
                    "SOME_NEW_STATUS",
                    false,
                    List.of("Inventory only."))));
    AdminPlatformRetentionProxyService.applyServiceSummaries(plan);

    publisher.publish(plan);

    // Unknown service collapses to "unknown"; computeServiceStatus still yields a bounded
    // status (PARTIAL here). Free-text target status / warning collapse to bounded fallbacks.
    assertThat(
            counter(
                "platform_retention_service_summary_total",
                "service",
                "unknown",
                "status",
                "PARTIAL"))
        .isEqualTo(1d);
    assertThat(
            counter(
                "platform_retention_service_targets_total",
                "service",
                "unknown",
                "target_status",
                "unknown"))
        .isEqualTo(1d);
    assertThat(
            counter(
                "platform_retention_service_warnings_total",
                "service",
                "unknown",
                "warning_code",
                "UNKNOWN_WARNING"))
        .isEqualTo(1d);
  }

  @Test
  void boundedHelpersMapKnownAndUnknownValues() {
    assertThat(PlatformRetentionMetricsPublisher.boundedService("identity-service"))
        .isEqualTo("identity-service");
    assertThat(PlatformRetentionMetricsPublisher.boundedService("nope")).isEqualTo("unknown");
    assertThat(PlatformRetentionMetricsPublisher.boundedStatus("BLOCKED_BY_HOLD"))
        .isEqualTo("BLOCKED_BY_HOLD");
    assertThat(PlatformRetentionMetricsPublisher.boundedStatus("nope")).isEqualTo("unknown");
    assertThat(PlatformRetentionMetricsPublisher.boundedTargetStatus("PURGE_READY"))
        .isEqualTo("PURGE_READY");
    assertThat(PlatformRetentionMetricsPublisher.boundedTargetStatus("nope")).isEqualTo("unknown");
    assertThat(PlatformRetentionMetricsPublisher.boundedWarning("CONTENT_RETENTION_COUNT_FAILED"))
        .isEqualTo("COUNT_FAILED");
    assertThat(
            PlatformRetentionMetricsPublisher.boundedWarning(
                "PLATFORM_RETENTION_CONTENT_PLAN_INCLUDED"))
        .isEqualTo("PLAN_INCLUDED");
    assertThat(PlatformRetentionMetricsPublisher.boundedWarning("some free text"))
        .isEqualTo("UNKNOWN_WARNING");
  }

  @Test
  void doesNotThrowOnNullOrEmptyPlanAndDoesNotMutateContract() {
    assertThatCode(() -> publisher.publish(null)).doesNotThrowAnyException();
    assertThatCode(() -> publisher.publish(new LinkedHashMap<>())).doesNotThrowAnyException();

    Map<String, Object> plan =
        planWith(
            List.of(
                target(
                    "content.note_versions",
                    "content-service",
                    "DRY_RUN_READY",
                    false,
                    List.of())));
    AdminPlatformRetentionProxyService.applyServiceSummaries(plan);
    var keysBefore = new java.util.LinkedHashSet<>(plan.keySet());

    publisher.publish(plan);

    assertThat(plan.keySet()).isEqualTo(keysBefore);
  }

  @Test
  void recordsBoundedFailureMetricOnly() {
    publisher.recordPlanGenerationFailure();

    assertThat(counter("platform_retention_plan_generated_total", "result", "error")).isEqualTo(1d);
    assertThat(counter("platform_retention_plan_generated_total", "result", "success"))
        .isEqualTo(0d);
    assertThat(registry.find("platform_retention_service_summary_total").counter()).isNull();
  }
}
