package com.notebook.lumen.notification.admin.platformretention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.notification.admin.platformretention.NotificationPlatformRetentionCountRepository.CountResult;
import com.notebook.lumen.notification.admin.platformretention.NotificationPlatformRetentionDtos.NotificationPlatformRetentionTargetView;
import com.notebook.lumen.notification.audit.AuditService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.dao.PermissionDeniedDataAccessException;

class NotificationPlatformRetentionPlanServiceTest {

  private static final Instant NOW = Instant.parse("2026-05-16T00:00:00Z");

  private final NotificationPlatformRetentionCountRepository countRepository =
      mock(NotificationPlatformRetentionCountRepository.class);
  private final AuditService auditService = mock(AuditService.class);

  private NotificationPlatformRetentionPlanService service(boolean enabled, int cap) {
    return new NotificationPlatformRetentionPlanService(
        new NotificationPlatformRetentionProperties(enabled, cap, 90, 7, 90, 90, 90, 90),
        countRepository,
        auditService,
        new SimpleMeterRegistry());
  }

  private void stubAllCounts(long value, boolean capped) {
    CountResult r = new CountResult(value, capped);
    when(countRepository.countAnalyticsHourlyBefore(any(), anyInt())).thenReturn(r);
    when(countRepository.countFanoutSentBefore(any(), anyInt())).thenReturn(r);
    when(countRepository.countFanoutDeadBefore(any(), anyInt())).thenReturn(r);
    when(countRepository.countDeadLetterRequeueBefore(any(), anyInt())).thenReturn(r);
    when(countRepository.countDigestTerminalBefore(any(), anyInt())).thenReturn(r);
    when(countRepository.countEmailTerminalBefore(any(), anyInt())).thenReturn(r);
  }

  @Test
  void dryRunDisabled_returnsEmptyWithWarning() {
    var plan = service(false, 100_000).buildPlan(Optional.empty(), Set.of(), Optional.of(NOW));

    assertThat(plan.service()).isEqualTo("notification-service");
    assertThat(plan.dryRun()).isTrue();
    assertThat(plan.targets()).isEmpty();
    assertThat(plan.warnings())
        .containsExactly(NotificationPlatformRetentionPlanService.WARN_DRY_RUN_DISABLED);
    verify(auditService)
        .record(
            eq(NotificationPlatformRetentionPlanService.EVT_PLAN_GENERATED),
            eq(NotificationPlatformRetentionPlanService.AGGREGATE),
            any(),
            any());
  }

  @Test
  void allTargetsCounted_eligibleAndPurgeableSet() {
    stubAllCounts(42, false);

    var plan = service(true, 100_000).buildPlan(Optional.empty(), Set.of(), Optional.of(NOW));

    assertThat(plan.targets()).hasSize(6);
    assertThat(plan.targets())
        .allMatch(t -> t.status() == NotificationPlatformRetentionTargetStatus.DRY_RUN_READY)
        .allMatch(t -> !t.blockedByLegalHold())
        .allMatch(t -> t.eligibleCount() != null && t.eligibleCount() == 42)
        .allMatch(t -> t.purgeableCount() == 42)
        .allMatch(t -> t.cutoff() != null && t.defaultRetentionDays() != null)
        .allMatch(t -> t.warnings().isEmpty());
  }

  @Test
  void queryCap_emitsCappedWarningAndAudit() {
    stubAllCounts(2, true);

    var plan =
        service(true, 2)
            .buildPlan(
                Optional.of(NotificationPlatformRetentionTargetKey.NOTIFICATION_ANALYTICS_HOURLY),
                Set.of(),
                Optional.of(NOW));

    assertThat(plan.targets()).hasSize(1);
    NotificationPlatformRetentionTargetView view = plan.targets().get(0);
    assertThat(view.eligibleCount()).isEqualTo(2);
    assertThat(view.warnings())
        .contains(NotificationPlatformRetentionPlanService.WARN_QUERY_CAPPED);
    verify(auditService, atLeastOnce())
        .record(
            eq(NotificationPlatformRetentionPlanService.EVT_COUNT_CAPPED),
            eq(NotificationPlatformRetentionPlanService.AGGREGATE),
            any(),
            any());
  }

  @Test
  void fullLegalHold_blocksAllTargets() {
    stubAllCounts(50, false);

    var plan =
        service(true, 100_000)
            .buildPlan(
                Optional.empty(),
                Set.of(NotificationPlatformRetentionLegalHoldScope.ALL_PLATFORM),
                Optional.of(NOW));

    assertThat(plan.targets())
        .hasSize(6)
        .allMatch(NotificationPlatformRetentionTargetView::blockedByLegalHold)
        .allMatch(t -> t.purgeableCount() == 0)
        .allMatch(
            t ->
                t.warnings()
                    .contains(NotificationPlatformRetentionPlanService.WARN_LEGAL_HOLD_BLOCKED));
  }

  @Test
  void targetSpecificLegalHold_blocksOnlyThatTargetAndWarnsPartial() {
    stubAllCounts(10, false);

    var plan =
        service(true, 100_000)
            .buildPlan(
                Optional.empty(),
                Set.of(NotificationPlatformRetentionLegalHoldScope.FANOUT_OUTBOX),
                Optional.of(NOW));

    assertThat(plan.warnings())
        .contains(NotificationPlatformRetentionPlanService.WARN_PARTIAL_LEGAL_HOLD_MAPPING);
    assertThat(plan.targets())
        .filteredOn(t -> t.targetKey().startsWith("notification.fanout_outbox"))
        .hasSize(2)
        .allMatch(NotificationPlatformRetentionTargetView::blockedByLegalHold)
        .allMatch(t -> t.purgeableCount() == 0);
    assertThat(plan.targets())
        .filteredOn(t -> !t.targetKey().startsWith("notification.fanout_outbox"))
        .allMatch(t -> !t.blockedByLegalHold())
        .allMatch(t -> t.purgeableCount() == 10);
  }

  @Test
  void dbPermissionDenied_mapsToSafeWarningWithoutRawSql() {
    when(countRepository.countAnalyticsHourlyBefore(any(), anyInt()))
        .thenThrow(
            new PermissionDeniedDataAccessException(
                "ERROR: permission denied for table notification_delivery_analytics_hourly", null));

    var plan =
        service(true, 100_000)
            .buildPlan(
                Optional.of(NotificationPlatformRetentionTargetKey.NOTIFICATION_ANALYTICS_HOURLY),
                Set.of(),
                Optional.of(NOW));

    NotificationPlatformRetentionTargetView view = plan.targets().get(0);
    assertThat(view.eligibleCount()).isNull();
    assertThat(view.warnings())
        .containsExactly(NotificationPlatformRetentionPlanService.WARN_DB_PERMISSION_DENIED);
    assertThat(view.warnings()).noneMatch(w -> w.contains("permission denied for table"));
    verify(auditService)
        .record(
            eq(NotificationPlatformRetentionPlanService.EVT_PLAN_FAILED),
            eq(NotificationPlatformRetentionPlanService.AGGREGATE),
            any(),
            any());
  }

  @Test
  void genericRuntimeException_keepsCountFailedWarning() {
    when(countRepository.countFanoutSentBefore(any(), anyInt()))
        .thenThrow(new RuntimeException("boom"));

    var plan =
        service(true, 100_000)
            .buildPlan(
                Optional.of(NotificationPlatformRetentionTargetKey.NOTIFICATION_FANOUT_OUTBOX_SENT),
                Set.of(),
                Optional.of(NOW));

    assertThat(plan.targets().get(0).warnings())
        .containsExactly(NotificationPlatformRetentionPlanService.WARN_COUNT_FAILED);
  }

  @Test
  void classifyDbFailure_detectsSqlState42501AndDefaultsToCountFailed() {
    Exception permissionDenied =
        new RuntimeException("wrapped", new SQLException("denied", "42501"));
    Exception generic = new IllegalStateException("unexpected");

    assertThat(NotificationPlatformRetentionPlanService.classifyDbFailure(permissionDenied))
        .isEqualTo(NotificationPlatformRetentionPlanService.WARN_DB_PERMISSION_DENIED);
    assertThat(NotificationPlatformRetentionPlanService.classifyDbFailure(generic))
        .isEqualTo(NotificationPlatformRetentionPlanService.WARN_COUNT_FAILED);
  }
}
