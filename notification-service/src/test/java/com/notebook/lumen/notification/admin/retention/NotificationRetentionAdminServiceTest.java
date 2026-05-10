package com.notebook.lumen.notification.admin.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.notification.audit.AuditService;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationRetentionAdminServiceTest {

  @Mock private NotificationRetentionProperties retentionProperties;
  @Mock private NotificationRetentionPlanner planner;
  @Mock private NotificationRetentionPurgeExecutor purgeExecutor;
  @Mock private NotificationRetentionMetrics metrics;
  @Mock private AuditService auditService;

  @InjectMocks private NotificationRetentionAdminService service;

  @Test
  void dryRunDoesNotCallPurgeExecutor() {
    var snap =
        new RetentionAdminDtos.RetentionPlanResponse(
            Instant.parse("2026-05-10T00:00:00Z"), true, List.of(), List.of());
    when(planner.plan(any(), eq(true))).thenReturn(snap);
    var r = service.run(true, "ALL", null, null);
    assertThat(r.dryRun()).isTrue();
    assertThat(r.totalDeleted()).isZero();
    verify(purgeExecutor, never()).purge(any(), any(), anyInt());
  }

  @Test
  void destructiveWhenManualRunDisabled_throws() {
    var snap =
        new RetentionAdminDtos.RetentionPlanResponse(
            Instant.parse("2026-05-10T00:00:00Z"), false, List.of(), List.of());
    when(planner.plan(any(), eq(false))).thenReturn(snap);
    when(retentionProperties.manualRunEnabled()).thenReturn(false);
    assertThatThrownBy(() -> service.run(false, "ALL", "long enough reason here", "actor"))
        .isInstanceOf(NotificationException.class);
    verify(purgeExecutor, never()).purge(any(), any(), anyInt());
  }

  @Test
  void destructiveRunsPurge() {
    var snap =
        new RetentionAdminDtos.RetentionPlanResponse(
            Instant.parse("2026-05-10T00:00:00Z"), false, List.of(), List.of());
    when(retentionProperties.manualRunEnabled()).thenReturn(true);
    when(retentionProperties.maxDeletePerRun()).thenReturn(10_000);
    when(purgeExecutor.purge(any(), any(), eq(10_000)))
        .thenReturn(Map.of("notification_delivery_analytics_hourly", 2L));
    var after =
        new RetentionAdminDtos.RetentionPlanResponse(
            Instant.parse("2026-05-10T01:00:00Z"), false, List.of(), List.of());
    when(planner.plan(any(), eq(false))).thenReturn(snap, after);
    var r = service.run(false, "ALL", "long enough reason here", "actor");
    assertThat(r.totalDeleted()).isEqualTo(2L);
    verify(purgeExecutor).purge(any(), any(), eq(10_000));
  }
}
