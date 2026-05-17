package com.notebook.lumen.search.admin.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.notebook.lumen.search.admin.retention.SearchRetentionCountRepository.CountResult;
import com.notebook.lumen.search.admin.retention.SearchRetentionPlanDtos.SearchRetentionPlanResponse;
import com.notebook.lumen.search.admin.retention.SearchRetentionPlanDtos.SearchRetentionTargetView;
import com.notebook.lumen.search.index.application.SearchAuditService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SearchRetentionPlanServiceTest {

  private SearchRetentionCountRepository countRepository;
  private SearchAuditService auditService;
  private SimpleMeterRegistry meterRegistry;
  private SearchRetentionProperties properties;
  private final Instant now = Instant.parse("2026-05-13T10:00:00Z");

  @BeforeEach
  void setUp() {
    countRepository = mock(SearchRetentionCountRepository.class);
    auditService = mock(SearchAuditService.class);
    meterRegistry = new SimpleMeterRegistry();
    properties = new SearchRetentionProperties(true, 100_000, 90, 90);
  }

  @Test
  void disabled_returnsEmptyTargetsWithWarning() {
    SearchRetentionProperties disabled = new SearchRetentionProperties(false, 100_000, 90, 90);
    SearchRetentionPlanService service = service(disabled);

    SearchRetentionPlanResponse response =
        service.buildPlan(Optional.empty(), Set.of(), Optional.of(now));

    assertThat(response.dryRun()).isTrue();
    assertThat(response.targets()).isEmpty();
    assertThat(response.warnings()).containsExactly("SEARCH_RETENTION_DRY_RUN_DISABLED");
  }

  @Test
  void dryRunReadyTargets_returnEligibleAndPurgeableCounts() {
    when(countRepository.countArchivedDocumentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(100, false));
    when(countRepository.countTerminalReindexJobsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(12, false));
    SearchRetentionPlanService service = service(properties);

    SearchRetentionPlanResponse response =
        service.buildPlan(Optional.empty(), Set.of(), Optional.of(now));

    assertThat(response.targets()).hasSize(4);
    SearchRetentionTargetView stale = byKey(response, "search.documents_stale");
    assertThat(stale.eligibleCount()).isEqualTo(100);
    assertThat(stale.purgeableCount()).isEqualTo(100);
    assertThat(stale.status()).isEqualTo(SearchRetentionTargetStatus.DRY_RUN_READY);
    SearchRetentionTargetView active = byKey(response, "search.documents_active");
    assertThat(active.warnings()).contains("SEARCH_RETENTION_TARGET_INVENTORY_ONLY");
  }

  @Test
  void fullyBlockingHold_zeroesPurgeableAndFlagsBlocked() {
    when(countRepository.countArchivedDocumentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(100, false));
    when(countRepository.countTerminalReindexJobsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(12, false));
    SearchRetentionPlanService service = service(properties);

    SearchRetentionPlanResponse response =
        service.buildPlan(
            Optional.empty(),
            EnumSet.of(SearchRetentionLegalHoldScope.ALL_PLATFORM),
            Optional.of(now));

    SearchRetentionTargetView stale = byKey(response, "search.documents_stale");
    assertThat(stale.purgeableCount()).isZero();
    assertThat(stale.blockedByLegalHold()).isTrue();
    assertThat(stale.warnings()).contains("SEARCH_RETENTION_LEGAL_HOLD_BLOCKED");
  }

  @Test
  void queryCap_emitsCappedWarning() {
    when(countRepository.countArchivedDocumentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(100_000, true));
    when(countRepository.countTerminalReindexJobsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(0, false));
    SearchRetentionPlanService service = service(properties);

    SearchRetentionPlanResponse response =
        service.buildPlan(Optional.empty(), Set.of(), Optional.of(now));

    assertThat(byKey(response, "search.documents_stale").warnings())
        .contains("SEARCH_RETENTION_QUERY_CAPPED");
  }

  private SearchRetentionPlanService service(SearchRetentionProperties props) {
    return new SearchRetentionPlanService(props, countRepository, auditService, meterRegistry);
  }

  private static SearchRetentionTargetView byKey(SearchRetentionPlanResponse response, String key) {
    return response.targets().stream()
        .filter(t -> key.equals(t.targetKey()))
        .findFirst()
        .orElseThrow();
  }
}
