package com.notebook.lumen.identity.admin.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.identity.audit.AuditService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PlatformRetentionGovernanceServiceTest {

  @Test
  void registryReturnsInventoryTargets() {
    var service = service(repository(), new PlatformRetentionProperties(true, true));

    var targets = service.targets(mock(HttpServletRequest.class));

    assertThat(targets.targets()).isNotEmpty();
    assertThat(targets.targets()).anyMatch(t -> t.targetKey().equals("content.note_versions"));
    assertThat(targets.targets()).allMatch(t -> !t.destructivePurgeSupported());
  }

  @Test
  void registryMarksContentDryRunReadyTargets() {
    var service = service(repository(), new PlatformRetentionProperties(true, true));

    var targets = service.targets(mock(HttpServletRequest.class));

    assertThat(targets.targets())
        .filteredOn(
            t ->
                t.targetKey().equals("content.note_versions")
                    || t.targetKey().equals("content.comments")
                    || t.targetKey().equals("content.search_documents"))
        .hasSize(3)
        .allMatch(t -> t.status() == RetentionTargetStatus.DRY_RUN_READY);

    assertThat(targets.targets())
        .filteredOn(
            t ->
                t.targetKey().equals("content.workspaces")
                    || t.targetKey().equals("content.notebooks")
                    || t.targetKey().equals("content.notes")
                    || t.targetKey().equals("content.attachments_media"))
        .hasSize(4)
        .allMatch(t -> t.status() == RetentionTargetStatus.INVENTORY_ONLY);
  }

  @Test
  void registryContentTargetsForbidPurge() {
    var service = service(repository(), new PlatformRetentionProperties(true, true));

    var targets = service.targets(mock(HttpServletRequest.class));

    assertThat(targets.targets())
        .filteredOn(t -> t.targetKey().startsWith("content."))
        .isNotEmpty()
        .allMatch(t -> !t.destructivePurgeSupported())
        .allMatch(PlatformRetentionDtos.TargetResponse::legalHoldSupported);
  }

  @Test
  void registryDryRunReadyContentTargetsSupportDryRun() {
    var service = service(repository(), new PlatformRetentionProperties(true, true));

    var targets = service.targets(mock(HttpServletRequest.class));

    assertThat(targets.targets())
        .filteredOn(t -> t.status() == RetentionTargetStatus.DRY_RUN_READY)
        .filteredOn(t -> t.targetKey().startsWith("content."))
        .hasSize(3)
        .allMatch(PlatformRetentionDtos.TargetResponse::dryRunSupported);
  }

  @Test
  void registryIncludesNotificationDryRunReadyTargets() {
    var service = service(repository(), new PlatformRetentionProperties(true, true));

    var targets = service.targets(mock(HttpServletRequest.class));

    assertThat(targets.targets())
        .filteredOn(t -> t.targetKey().startsWith("notification."))
        .hasSize(6)
        .allMatch(t -> t.status() == RetentionTargetStatus.DRY_RUN_READY)
        .allMatch(t -> !t.destructivePurgeSupported())
        .allMatch(PlatformRetentionDtos.TargetResponse::dryRunSupported)
        .allMatch(PlatformRetentionDtos.TargetResponse::legalHoldSupported);
    assertThat(targets.targets())
        .extracting(PlatformRetentionDtos.TargetResponse::targetKey)
        .contains(
            "notification.analytics_hourly",
            "notification.fanout_outbox_sent",
            "notification.fanout_outbox_dead",
            "notification.dead_letter_requeue_requests",
            "notification.digest_items_terminal",
            "notification.email_notifications_terminal");
  }

  @Test
  void planEmitsDryRunReadyStatusForContentVersions() {
    var service = service(repository(), new PlatformRetentionProperties(true, true));

    var plan = service.plan("content.note_versions", true, mock(HttpServletRequest.class));

    assertThat(plan.targets()).hasSize(1);
    assertThat(plan.targets().get(0).status()).isEqualTo(RetentionTargetStatus.DRY_RUN_READY);
    assertThat(plan.targets().get(0).blockedByLegalHold()).isFalse();
  }

  @Test
  void activeAllPlatformHoldBlocksLegalHoldTargets() {
    PlatformLegalHoldRepository repository = repository();
    when(repository.findByStatusOrderByCreatedAtDesc(PlatformLegalHoldStatus.ACTIVE))
        .thenReturn(
            List.of(
                new PlatformLegalHold(
                    UUID.randomUUID(),
                    "case-all",
                    PlatformLegalHoldScope.ALL_PLATFORM,
                    null,
                    "active legal hold",
                    UUID.randomUUID(),
                    Instant.now(),
                    null)));
    var service = service(repository, new PlatformRetentionProperties(true, true));

    var plan = service.plan(null, true, mock(HttpServletRequest.class));

    assertThat(plan.dryRun()).isTrue();
    assertThat(plan.targets())
        .anyMatch(t -> t.blockedByLegalHold() && t.activeHoldKeys().contains("case-all"));
    assertThat(plan.targets()).allMatch(t -> t.purgeableCount() == 0);
  }

  @Test
  void domainHoldBlocksOnlyMatchingDomain() {
    PlatformLegalHoldRepository repository = repository();
    when(repository.findByStatusOrderByCreatedAtDesc(PlatformLegalHoldStatus.ACTIVE))
        .thenReturn(
            List.of(
                new PlatformLegalHold(
                    UUID.randomUUID(),
                    "content-hold",
                    PlatformLegalHoldScope.CONTENT,
                    null,
                    "content legal hold",
                    UUID.randomUUID(),
                    Instant.now(),
                    null)));
    var service = service(repository, new PlatformRetentionProperties(true, true));

    var plan = service.plan(null, true, mock(HttpServletRequest.class));

    assertThat(plan.targets())
        .filteredOn(
            t -> t.targetKey().startsWith("content.") || t.targetKey().startsWith("search."))
        .allMatch(PlatformRetentionDtos.PlanTargetResponse::blockedByLegalHold);
    assertThat(plan.targets())
        .filteredOn(t -> t.targetKey().startsWith("identity."))
        .noneMatch(PlatformRetentionDtos.PlanTargetResponse::blockedByLegalHold);
  }

  @Test
  void expiredHoldStillBlocksWithWarning() {
    PlatformLegalHoldRepository repository = repository();
    when(repository.findByStatusOrderByCreatedAtDesc(PlatformLegalHoldStatus.ACTIVE))
        .thenReturn(
            List.of(
                new PlatformLegalHold(
                    UUID.randomUUID(),
                    "expired-hold",
                    PlatformLegalHoldScope.AUDIT,
                    null,
                    "expired active hold",
                    UUID.randomUUID(),
                    Instant.now().minusSeconds(3600),
                    Instant.now().minusSeconds(60))));
    var service = service(repository, new PlatformRetentionProperties(true, true));

    var auditTargets = service.plan("audit.events", true, mock(HttpServletRequest.class)).targets();

    assertThat(auditTargets).hasSize(1);
    assertThat(auditTargets.get(0).blockedByLegalHold()).isTrue();
    assertThat(auditTargets.get(0).warnings()).anyMatch(w -> w.contains("passed expiresAt"));
  }

  @Test
  void createAndReleaseRequireReason() {
    var service = service(repository(), new PlatformRetentionProperties(true, true));
    UUID actor = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                service.createLegalHold(
                    new PlatformRetentionDtos.LegalHoldCreateRequest(
                        "case", PlatformLegalHoldScope.ALL_PLATFORM, null, "short", null),
                    actor,
                    mock(HttpServletRequest.class)))
        .isInstanceOf(ResponseStatusException.class);

    assertThatThrownBy(
            () ->
                service.releaseLegalHold(
                    UUID.randomUUID(),
                    new PlatformRetentionDtos.LegalHoldReleaseRequest("short"),
                    actor,
                    mock(HttpServletRequest.class)))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void releaseHoldRemovesFutureBlockWhenRepositoryNoLongerReturnsActiveHold() {
    PlatformLegalHoldRepository repository = repository();
    PlatformLegalHold hold =
        new PlatformLegalHold(
            UUID.randomUUID(),
            "release-case",
            PlatformLegalHoldScope.ALL_PLATFORM,
            null,
            "active legal hold",
            UUID.randomUUID(),
            Instant.now(),
            null);
    when(repository.findById(hold.getId())).thenReturn(Optional.of(hold));
    when(repository.save(any(PlatformLegalHold.class))).thenAnswer(inv -> inv.getArgument(0));
    when(repository.findByStatusOrderByCreatedAtDesc(PlatformLegalHoldStatus.ACTIVE))
        .thenReturn(List.of());
    AuditService auditService = mock(AuditService.class);
    var service =
        new PlatformRetentionGovernanceService(
            new RetentionTargetRegistry(),
            repository,
            new PlatformRetentionProperties(true, true),
            auditService,
            new SimpleMeterRegistry());

    service.releaseLegalHold(
        hold.getId(),
        new PlatformRetentionDtos.LegalHoldReleaseRequest("release after review"),
        UUID.randomUUID(),
        mock(HttpServletRequest.class));

    assertThat(service.plan(null, true, mock(HttpServletRequest.class)).targets())
        .noneMatch(PlatformRetentionDtos.PlanTargetResponse::blockedByLegalHold);
    verify(auditService, atLeastOnce()).record(any(), any(), any(), any(), any(), any());
  }

  private static PlatformRetentionGovernanceService service(
      PlatformLegalHoldRepository repository, PlatformRetentionProperties properties) {
    return new PlatformRetentionGovernanceService(
        new RetentionTargetRegistry(),
        repository,
        properties,
        mock(AuditService.class),
        new SimpleMeterRegistry());
  }

  private static PlatformLegalHoldRepository repository() {
    PlatformLegalHoldRepository repository = mock(PlatformLegalHoldRepository.class);
    when(repository.findByStatusOrderByCreatedAtDesc(PlatformLegalHoldStatus.ACTIVE))
        .thenReturn(List.of());
    return repository;
  }
}
