package com.notebook.lumen.content.admin.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.notebook.lumen.content.admin.retention.ContentRetentionCountRepository.CountResult;
import com.notebook.lumen.content.admin.retention.ContentRetentionPlanDtos.ContentRetentionPlanResponse;
import com.notebook.lumen.content.admin.retention.ContentRetentionPlanDtos.ContentRetentionTargetView;
import com.notebook.lumen.content.audit.AuditService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContentRetentionPlanServiceTest {

  private ContentRetentionCountRepository countRepository;
  private AuditService auditService;
  private SimpleMeterRegistry meterRegistry;
  private ContentRetentionProperties properties;
  private final Instant now = Instant.parse("2026-05-13T10:00:00Z");

  @BeforeEach
  void setUp() {
    countRepository = mock(ContentRetentionCountRepository.class);
    auditService = mock(AuditService.class);
    meterRegistry = new SimpleMeterRegistry();
    properties = new ContentRetentionProperties(true, 100_000, 365, 365, 90, false);
  }

  @Test
  void disabled_returnsEmptyTargetsWithWarning() {
    ContentRetentionProperties disabled =
        new ContentRetentionProperties(false, 100_000, 365, 365, 90, false);
    ContentRetentionPlanService service = service(disabled);

    ContentRetentionPlanResponse response =
        service.buildPlan(Optional.empty(), Set.of(), Optional.of(now));

    assertThat(response.dryRun()).isTrue();
    assertThat(response.targets()).isEmpty();
    assertThat(response.warnings()).containsExactly("CONTENT_RETENTION_DRY_RUN_DISABLED");
  }

  @Test
  void dryRunReadyTargets_returnEligibleAndPurgeableCounts() {
    when(countRepository.countNoteVersionsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(1200, false));
    when(countRepository.countCommentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(700, false));
    when(countRepository.countSearchDocumentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(300, false));
    ContentRetentionPlanService service = service(properties);

    ContentRetentionPlanResponse response =
        service.buildPlan(Optional.empty(), Set.of(), Optional.of(now));

    assertThat(response.targets()).hasSize(4);
    ContentRetentionTargetView versions = byKey(response, "content.note_versions");
    assertThat(versions.eligibleCount()).isEqualTo(1200);
    assertThat(versions.purgeableCount()).isEqualTo(1200);
    assertThat(versions.blockedByLegalHold()).isFalse();
    assertThat(versions.status()).isEqualTo(ContentRetentionTargetStatus.DRY_RUN_READY);
    ContentRetentionTargetView notes = byKey(response, "content.notes");
    assertThat(notes.eligibleCount()).isNull();
    assertThat(notes.warnings()).contains("CONTENT_RETENTION_TARGET_INVENTORY_ONLY");
  }

  @Test
  void fullyBlockingHold_zeroesPurgeableAndFlagsBlocked() {
    when(countRepository.countNoteVersionsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(1200, false));
    when(countRepository.countCommentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(700, false));
    when(countRepository.countSearchDocumentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(300, false));
    ContentRetentionPlanService service = service(properties);

    ContentRetentionPlanResponse response =
        service.buildPlan(
            Optional.empty(),
            EnumSet.of(ContentRetentionLegalHoldScope.ALL_PLATFORM),
            Optional.of(now));

    ContentRetentionTargetView versions = byKey(response, "content.note_versions");
    assertThat(versions.purgeableCount()).isZero();
    assertThat(versions.blockedByLegalHold()).isTrue();
    assertThat(versions.warnings()).contains("CONTENT_RETENTION_LEGAL_HOLD_BLOCKED");
  }

  @Test
  void contentScopeHold_blocksContentTargets() {
    when(countRepository.countNoteVersionsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(50, false));
    when(countRepository.countCommentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(0, false));
    when(countRepository.countSearchDocumentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(0, false));
    ContentRetentionPlanService service = service(properties);

    ContentRetentionPlanResponse response =
        service.buildPlan(
            Optional.empty(), EnumSet.of(ContentRetentionLegalHoldScope.CONTENT), Optional.of(now));

    assertThat(byKey(response, "content.note_versions").purgeableCount()).isZero();
    assertThat(byKey(response, "content.note_versions").blockedByLegalHold()).isTrue();
  }

  @Test
  void partialMappingScopes_addWarningButDoNotBlockCount() {
    when(countRepository.countNoteVersionsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(10, false));
    when(countRepository.countCommentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(20, false));
    when(countRepository.countSearchDocumentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(30, false));
    ContentRetentionPlanService service = service(properties);

    ContentRetentionPlanResponse response =
        service.buildPlan(
            Optional.empty(),
            EnumSet.of(ContentRetentionLegalHoldScope.WORKSPACE),
            Optional.of(now));

    assertThat(response.warnings()).contains("CONTENT_RETENTION_PARTIAL_LEGAL_HOLD_MAPPING");
    ContentRetentionTargetView versions = byKey(response, "content.note_versions");
    assertThat(versions.purgeableCount()).isEqualTo(10);
    assertThat(versions.blockedByLegalHold()).isFalse();
    assertThat(versions.warnings()).contains("CONTENT_RETENTION_PARTIAL_LEGAL_HOLD_MAPPING");
  }

  @Test
  void cappedCount_emitsCapWarning() {
    when(countRepository.countNoteVersionsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(100_000, true));
    when(countRepository.countCommentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(0, false));
    when(countRepository.countSearchDocumentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(0, false));
    ContentRetentionPlanService service = service(properties);

    ContentRetentionPlanResponse response =
        service.buildPlan(Optional.empty(), Set.of(), Optional.of(now));

    assertThat(byKey(response, "content.note_versions").warnings())
        .contains("CONTENT_RETENTION_QUERY_CAPPED");
    assertThat(meterRegistry.find("content_retention_count_capped_total").counter()).isNotNull();
  }

  @Test
  void dbPermissionDenied_mapsToSafeWarning() {
    when(countRepository.countNoteVersionsBefore(any(Instant.class), anyInt()))
        .thenThrow(
            new org.springframework.dao.PermissionDeniedDataAccessException(
                "permission denied for table note_versions", null));
    when(countRepository.countCommentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(0, false));
    when(countRepository.countSearchDocumentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(0, false));
    ContentRetentionPlanService service = service(properties);

    ContentRetentionPlanResponse response =
        service.buildPlan(Optional.empty(), Set.of(), Optional.of(now));

    ContentRetentionTargetView versions = byKey(response, "content.note_versions");
    assertThat(versions.warnings()).contains("CONTENT_RETENTION_DB_PERMISSION_DENIED");
    assertThat(versions.warnings()).doesNotContain("CONTENT_RETENTION_COUNT_FAILED");
    assertThat(versions.eligibleCount()).isNull();
    assertThat(versions.purgeableCount()).isZero();
  }

  @Test
  void genericRuntimeException_keepsCountFailedWarning() {
    when(countRepository.countNoteVersionsBefore(any(Instant.class), anyInt()))
        .thenThrow(new IllegalStateException("transient failure"));
    when(countRepository.countCommentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(0, false));
    when(countRepository.countSearchDocumentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(0, false));
    ContentRetentionPlanService service = service(properties);

    ContentRetentionPlanResponse response =
        service.buildPlan(Optional.empty(), Set.of(), Optional.of(now));

    ContentRetentionTargetView versions = byKey(response, "content.note_versions");
    assertThat(versions.warnings()).contains("CONTENT_RETENTION_COUNT_FAILED");
    assertThat(versions.warnings()).doesNotContain("CONTENT_RETENTION_DB_PERMISSION_DENIED");
  }

  @Test
  void dbPermissionDenied_doesNotLeakRawMessage() {
    String sensitiveMessage = "permission denied: sensitive table notebook_secret leaked";
    when(countRepository.countNoteVersionsBefore(any(Instant.class), anyInt()))
        .thenThrow(
            new org.springframework.dao.PermissionDeniedDataAccessException(
                sensitiveMessage, null));
    when(countRepository.countCommentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(0, false));
    when(countRepository.countSearchDocumentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(0, false));
    ContentRetentionPlanService service = service(properties);

    ContentRetentionPlanResponse response =
        service.buildPlan(Optional.empty(), Set.of(), Optional.of(now));

    ContentRetentionTargetView versions = byKey(response, "content.note_versions");
    assertThat(versions.warnings()).noneMatch(w -> w.contains("notebook_secret"));
    assertThat(versions.warnings()).noneMatch(w -> w.contains("sensitive"));
  }

  @Test
  void sqlStateInsufficientPrivilege_mapsToPermissionDenied() {
    java.sql.SQLException sqlException = new java.sql.SQLException("permission denied", "42501");
    org.springframework.jdbc.UncategorizedSQLException wrapped =
        new org.springframework.jdbc.UncategorizedSQLException(
            "task", "SELECT count(*)", sqlException);
    when(countRepository.countNoteVersionsBefore(any(Instant.class), anyInt())).thenThrow(wrapped);
    when(countRepository.countCommentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(0, false));
    when(countRepository.countSearchDocumentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(0, false));
    ContentRetentionPlanService service = service(properties);

    ContentRetentionPlanResponse response =
        service.buildPlan(Optional.empty(), Set.of(), Optional.of(now));

    ContentRetentionTargetView versions = byKey(response, "content.note_versions");
    assertThat(versions.warnings()).contains("CONTENT_RETENTION_DB_PERMISSION_DENIED");
  }

  @Test
  void targetFilter_returnsOnlySelectedTarget() {
    when(countRepository.countCommentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(50, false));
    ContentRetentionPlanService service = service(properties);

    ContentRetentionPlanResponse response =
        service.buildPlan(
            Optional.of(ContentRetentionTargetKey.CONTENT_COMMENTS), Set.of(), Optional.of(now));

    assertThat(response.targets()).hasSize(1);
    assertThat(response.targets().get(0).targetKey()).isEqualTo("content.comments");
  }

  private ContentRetentionPlanService service(ContentRetentionProperties props) {
    return new ContentRetentionPlanService(props, countRepository, auditService, meterRegistry);
  }

  private static ContentRetentionTargetView byKey(
      ContentRetentionPlanResponse response, String key) {
    return response.targets().stream()
        .filter(t -> t.targetKey().equals(key))
        .findFirst()
        .orElseThrow();
  }
}
