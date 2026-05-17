package com.notebook.lumen.gateway.admin.retention;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Emits bounded-cardinality Micrometer counters from the already-computed platform retention {@code
 * serviceSummaries} (Faz 104) and merged {@code targets} list. Aggregate-only: no note/comment
 * body, no recipient/user email, no workspace/note/user id, no legal-hold reason and no raw warning
 * message ever becomes a label. All label values are mapped onto fixed allowlists so cardinality
 * stays bounded regardless of registry/target growth. Metrics emission never mutates the plan and
 * never throws — a metrics failure must not fail plan generation.
 */
@Component
class PlatformRetentionMetricsPublisher {

  private static final Logger log =
      LoggerFactory.getLogger(PlatformRetentionMetricsPublisher.class);

  static final String SERVICE_UNKNOWN = "unknown";
  static final String STATUS_UNKNOWN = "unknown";
  static final String TARGET_STATUS_UNKNOWN = "unknown";
  static final String WARNING_UNKNOWN = "UNKNOWN_WARNING";

  private static final Set<String> KNOWN_SERVICES =
      Set.of(
          "content-service",
          "notification-service",
          "search-service",
          "workspace-service",
          "identity-service",
          "platform");
  private static final Set<String> KNOWN_STATUSES =
      Set.of(
          "ERROR",
          "UNAVAILABLE",
          "DISABLED",
          "BLOCKED_BY_HOLD",
          "PARTIAL",
          "READY",
          "INVENTORY_ONLY");
  private static final Set<String> KNOWN_TARGET_STATUSES =
      Set.of("DRY_RUN_READY", "INVENTORY_ONLY", "PURGE_READY", "DISABLED", "ERROR", "UNAVAILABLE");

  private final MeterRegistry meterRegistry;

  PlatformRetentionMetricsPublisher(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  /**
   * Bounded failure metric only; emitted when plan generation fails before any service summary
   * exists (spec Faz 105: "If a plan generation fails before summary exists, emit only bounded
   * failure metric.").
   */
  void recordPlanGenerationFailure() {
    try {
      counter("platform_retention_plan_generated_total", "result", "error").increment();
    } catch (RuntimeException e) {
      log.warn("platform retention failure metric emission failed: {}", e.toString());
    }
  }

  /**
   * Emits bounded counters derived from {@code serviceSummaries} and merged {@code targets}. Called
   * after {@link AdminPlatformRetentionProxyService#applyServiceSummaries(Map)}.
   */
  @SuppressWarnings("unchecked")
  void publish(Map<String, Object> plan) {
    try {
      counter("platform_retention_plan_generated_total", "result", "success").increment();
      if (plan == null) {
        return;
      }
      if (plan.get("serviceSummaries") instanceof List<?> summaries) {
        for (Object s : summaries) {
          if (!(s instanceof Map<?, ?> summaryRaw)) {
            continue;
          }
          publishSummary((Map<String, Object>) summaryRaw);
        }
      }
      if (plan.get("targets") instanceof List<?> targets) {
        for (Object t : targets) {
          if (!(t instanceof Map<?, ?> rowRaw)) {
            continue;
          }
          publishTargetStatus((Map<String, Object>) rowRaw);
        }
      }
    } catch (RuntimeException e) {
      log.warn("platform retention metrics emission failed: {}", e.toString());
    }
  }

  @SuppressWarnings("unchecked")
  private void publishSummary(Map<String, Object> summary) {
    String service = boundedService(str(summary.get("service")));
    String status = boundedStatus(str(summary.get("status")));
    counter("platform_retention_service_summary_total", "service", service, "status", status)
        .increment();
    double blocked = toCount(summary.get("blockedTargets"));
    if (blocked > 0) {
      counter("platform_retention_service_blocked_targets_total", "service", service)
          .increment(blocked);
    }
    double capped = toCount(summary.get("cappedTargets"));
    if (capped > 0) {
      counter("platform_retention_service_capped_targets_total", "service", service)
          .increment(capped);
    }
    if (summary.get("warnings") instanceof List<?> warnings) {
      for (Object w : warnings) {
        if (w == null) {
          continue;
        }
        counter(
                "platform_retention_service_warnings_total",
                "service",
                service,
                "warning_code",
                boundedWarning(w.toString()))
            .increment();
      }
    }
  }

  private void publishTargetStatus(Map<String, Object> row) {
    String targetKey = str(row.get("targetKey"));
    String serviceRaw = str(row.get("service"));
    String service =
        boundedService(
            serviceRaw.isBlank()
                ? AdminPlatformRetentionProxyService.serviceFromKey(targetKey)
                : serviceRaw);
    counter(
            "platform_retention_service_targets_total",
            "service",
            service,
            "target_status",
            boundedTargetStatus(str(row.get("status"))))
        .increment();
  }

  private Counter counter(String name, String... tags) {
    return Counter.builder(name).tags(tags).register(meterRegistry);
  }

  private static String str(Object v) {
    return v == null ? "" : v.toString();
  }

  private static double toCount(Object v) {
    return v instanceof Number n ? Math.max(0d, n.doubleValue()) : 0d;
  }

  static String boundedService(String value) {
    return KNOWN_SERVICES.contains(value) ? value : SERVICE_UNKNOWN;
  }

  static String boundedStatus(String value) {
    return KNOWN_STATUSES.contains(value) ? value : STATUS_UNKNOWN;
  }

  static String boundedTargetStatus(String value) {
    return KNOWN_TARGET_STATUSES.contains(value) ? value : TARGET_STATUS_UNKNOWN;
  }

  /**
   * Normalises a (possibly target-prefixed) warning code onto a small fixed label set. Free-text or
   * unrecognised warnings collapse to {@link #WARNING_UNKNOWN}, so {@code warning_code} cardinality
   * is bounded even if upstream services add new prefixed codes.
   */
  static String boundedWarning(String code) {
    if (code == null || code.isBlank()) {
      return WARNING_UNKNOWN;
    }
    if (code.endsWith("_SERVICE_UNAVAILABLE")) {
      return "SERVICE_UNAVAILABLE";
    }
    if (code.endsWith("_LEGAL_HOLD_BLOCKED")) {
      return "LEGAL_HOLD_BLOCKED";
    }
    if (code.endsWith("_QUERY_CAPPED") || code.endsWith("_COUNT_CAPPED")) {
      return "COUNT_CAPPED";
    }
    if (code.endsWith("_DRY_RUN_DISABLED")) {
      return "DRY_RUN_DISABLED";
    }
    if (code.endsWith("_COUNT_FAILED")
        || code.endsWith("_DRY_RUN_FAILED")
        || code.endsWith("_DB_PERMISSION_DENIED")) {
      return "COUNT_FAILED";
    }
    if (code.endsWith("_PARTIAL_LEGAL_HOLD_MAPPING")) {
      return "PARTIAL_LEGAL_HOLD_MAPPING";
    }
    if (code.endsWith("_PLAN_INCLUDED")) {
      return "PLAN_INCLUDED";
    }
    return WARNING_UNKNOWN;
  }
}
