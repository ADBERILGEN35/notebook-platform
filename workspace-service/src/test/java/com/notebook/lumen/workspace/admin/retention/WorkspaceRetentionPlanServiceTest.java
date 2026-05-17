package com.notebook.lumen.workspace.admin.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.notebook.lumen.workspace.admin.retention.WorkspaceRetentionCountRepository.CountResult;
import com.notebook.lumen.workspace.admin.retention.WorkspaceRetentionPlanDtos.WorkspaceRetentionPlanResponse;
import com.notebook.lumen.workspace.admin.retention.WorkspaceRetentionPlanDtos.WorkspaceRetentionTargetView;
import com.notebook.lumen.workspace.audit.AuditService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkspaceRetentionPlanServiceTest {

  private WorkspaceRetentionCountRepository countRepository;
  private AuditService auditService;
  private SimpleMeterRegistry meterRegistry;
  private WorkspaceRetentionProperties properties;
  private final Instant now = Instant.parse("2026-05-13T10:00:00Z");

  @BeforeEach
  void setUp() {
    countRepository = mock(WorkspaceRetentionCountRepository.class);
    auditService = mock(AuditService.class);
    meterRegistry = new SimpleMeterRegistry();
    properties = new WorkspaceRetentionProperties(true, 100_000, 90, 365);
  }

  @Test
  void disabled_returnsEmptyTargetsWithWarning() {
    WorkspaceRetentionProperties disabled =
        new WorkspaceRetentionProperties(false, 100_000, 90, 365);
    WorkspaceRetentionPlanService service = service(disabled);

    WorkspaceRetentionPlanResponse response =
        service.buildPlan(Optional.empty(), Set.of(), Optional.of(now));

    assertThat(response.targets()).isEmpty();
    assertThat(response.warnings()).containsExactly("WORKSPACE_RETENTION_DRY_RUN_DISABLED");
  }

  @Test
  void dryRunReadyTargets_returnCounts() {
    when(countRepository.countExpiredPendingInvitationsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(5, false));
    when(countRepository.countAuditEventsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(200, false));
    WorkspaceRetentionPlanService service = service(properties);

    WorkspaceRetentionPlanResponse response =
        service.buildPlan(Optional.empty(), Set.of(), Optional.of(now));

    assertThat(response.targets()).hasSize(7);
    WorkspaceRetentionTargetView invitations = byKey(response, "workspace.invitations_expired");
    assertThat(invitations.eligibleCount()).isEqualTo(5);
    assertThat(invitations.status()).isEqualTo(WorkspaceRetentionTargetStatus.DRY_RUN_READY);
    assertThat(byKey(response, "workspace.memberships").warnings())
        .contains("WORKSPACE_RETENTION_TARGET_INVENTORY_ONLY");
  }

  @Test
  void workspaceHoldScope_blocksPurgeable() {
    when(countRepository.countExpiredPendingInvitationsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(5, false));
    when(countRepository.countAuditEventsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(200, false));
    WorkspaceRetentionPlanService service = service(properties);

    WorkspaceRetentionPlanResponse response =
        service.buildPlan(
            Optional.empty(),
            EnumSet.of(WorkspaceRetentionLegalHoldScope.WORKSPACE),
            Optional.of(now));

    WorkspaceRetentionTargetView invitations = byKey(response, "workspace.invitations_expired");
    assertThat(invitations.purgeableCount()).isZero();
    assertThat(invitations.blockedByLegalHold()).isTrue();
  }

  private WorkspaceRetentionPlanService service(WorkspaceRetentionProperties props) {
    return new WorkspaceRetentionPlanService(props, countRepository, auditService, meterRegistry);
  }

  private static WorkspaceRetentionTargetView byKey(
      WorkspaceRetentionPlanResponse response, String key) {
    return response.targets().stream()
        .filter(t -> key.equals(t.targetKey()))
        .findFirst()
        .orElseThrow();
  }
}
