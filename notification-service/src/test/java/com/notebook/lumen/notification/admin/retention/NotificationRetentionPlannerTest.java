package com.notebook.lumen.notification.admin.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.notebook.lumen.notification.admin.legalhold.NotificationLegalHoldBlockEvaluator;
import com.notebook.lumen.notification.analytics.NotificationAnalyticsProperties;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationRetentionPlannerTest {

  @Mock private NotificationAnalyticsProperties analyticsProperties;
  @Mock private NotificationRetentionProperties retentionProperties;
  @Mock private NotificationProperties notificationProperties;
  @Mock private NotificationRetentionJdbcRepository jdbc;
  @Mock private NotificationLegalHoldBlockEvaluator legalHoldBlockEvaluator;

  @Test
  void planIncludesAllTargets() {
    when(analyticsProperties.retentionDays()).thenReturn(90);
    when(notificationProperties.fanout())
        .thenReturn(
            new NotificationProperties.Fanout(true, true, true, 5, 100, 10, 5, 300, 60, 24, 30));
    when(retentionProperties.deadLetterRequeueRequestRetentionDays()).thenReturn(90);
    when(retentionProperties.digestSentRetentionDays()).thenReturn(90);
    when(retentionProperties.emailTerminalRetentionDays()).thenReturn(90);
    when(jdbc.countAnalyticsHourlyEligible(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new NotificationRetentionJdbcRepository.CountMin(0, null));
    when(jdbc.countFanoutSentEligible(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new NotificationRetentionJdbcRepository.CountMin(0, null));
    when(jdbc.countFanoutDeadEligible(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new NotificationRetentionJdbcRepository.CountMin(0, null));
    when(jdbc.countDeadLetterRequeueEligible(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new NotificationRetentionJdbcRepository.CountMin(0, null));
    when(jdbc.countDigestTerminalEligible(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(new NotificationRetentionJdbcRepository.CountMin(0, null));
    when(jdbc.countEmailTerminalEligible(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new NotificationRetentionJdbcRepository.CountMin(0, null));
    when(legalHoldBlockEvaluator.loadActiveHolds()).thenReturn(List.of());
    when(legalHoldBlockEvaluator.evaluate(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            inv ->
                new NotificationLegalHoldBlockEvaluator.HoldBlockResult(
                    false, List.of(), List.of()));

    var planner =
        new NotificationRetentionPlanner(
            analyticsProperties,
            retentionProperties,
            notificationProperties,
            jdbc,
            legalHoldBlockEvaluator);
    var p = planner.plan(Instant.parse("2026-06-01T12:00:00Z"), true);
    assertThat(p.targets()).hasSize(6);
    assertThat(p.warnings()).isNotEmpty();
  }
}
