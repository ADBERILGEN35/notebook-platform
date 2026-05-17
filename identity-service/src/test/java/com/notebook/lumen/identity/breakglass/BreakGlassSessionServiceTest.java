package com.notebook.lumen.identity.breakglass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.identity.audit.AuditService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BreakGlassSessionServiceTest {

  @Mock private BreakGlassAccessEventRepository eventRepository;
  @Mock private BreakGlassAccessEventService eventService;
  @Mock private AuditService auditService;
  @Mock private BreakGlassRevocationMetrics metrics;

  private BreakGlassProperties props;
  private BreakGlassSessionService service;

  @BeforeEach
  void setUp() {
    props =
        new BreakGlassProperties(
            true,
            "static-token",
            true,
            false,
            false,
            "disabled",
            true,
            60,
            true,
            false,
            true,
            true,
            24,
            30,
            false,
            "",
            "",
            "iss",
            "aud",
            300,
            15,
            true,
            true,
            1,
            true,
            5,
            15,
            true,
            false,
            false,
            12,
            5);
    service =
        new BreakGlassSessionService(eventRepository, eventService, props, auditService, metrics);
  }

  @Test
  void listActiveSessions_masksJtiAndNeverIncludesToken() {
    BreakGlassAccessEvent event = activeEvent("full-jti-value-12345678");
    when(eventRepository.findByExpiresAtAfterAndTokenJtiIsNotNull(any()))
        .thenReturn(List.of(event));

    BreakGlassSessionDtos.SessionListResponse response =
        service.listActiveSessions(UUID.randomUUID(), null);

    assertThat(response.items()).hasSize(1);
    assertThat(response.items().getFirst().jtiMasked()).doesNotContain("full-jti-value");
    assertThat(response.items().getFirst().jtiMasked()).contains("…");
    assertThat(response.activeCount()).isEqualTo(1);
    verify(auditService)
        .record(
            eq("BREAK_GLASS_SESSION_LIST_VIEWED"), any(), eq("BREAK_GLASS"), any(), any(), any());
  }

  @Test
  void revokeByJti_requiresReason() {
    assertThatThrownBy(
            () ->
                service.revokeByJti(
                    "session-1",
                    UUID.randomUUID(),
                    "short",
                    BreakGlassRevocationSource.ADMIN_REVOKE,
                    null))
        .isInstanceOf(BreakGlassException.class)
        .extracting(e -> ((BreakGlassException) e).getErrorCode())
        .isEqualTo("BREAK_GLASS_REVOKE_REASON_REQUIRED");
  }

  @Test
  void revokeByJti_idempotentWhenAlreadyRevoked() {
    BreakGlassAccessEvent event = activeEvent("jti-abc");
    when(eventRepository.findByTokenJti("jti-abc")).thenReturn(Optional.of(event));
    when(eventService.revokeToken(eq(event.getId()), any(), any(), any(), any()))
        .thenReturn(new BreakGlassReviewDtos.RevokeTokenResponse(false, false, true));

    BreakGlassSessionDtos.RevokeSessionResponse response =
        service.revokeByJti(
            "jti-abc",
            UUID.randomUUID(),
            "Valid revoke reason here",
            BreakGlassRevocationSource.ADMIN_REVOKE,
            null);

    assertThat(response.alreadyRevoked()).isTrue();
    assertThat(response.revoked()).isFalse();
  }

  @Test
  void revokeAllActive_onlyRevokesActiveSessions() {
    BreakGlassAccessEvent active = activeEvent("jti-active");
    BreakGlassAccessEvent expired = expiredEvent("jti-expired");
    when(eventRepository.findByExpiresAtAfterAndTokenJtiIsNotNull(any()))
        .thenReturn(List.of(active, expired));
    when(eventService.revokeToken(eq(active.getId()), any(), any(), any(), any()))
        .thenReturn(new BreakGlassReviewDtos.RevokeTokenResponse(true, false, false));

    BreakGlassSessionDtos.RevokeAllActiveResponse response =
        service.revokeAllActive(
            UUID.randomUUID(),
            "Emergency revoke all active sessions",
            BreakGlassRevocationSource.EMERGENCY_DISABLE,
            null);

    assertThat(response.revokedCount()).isEqualTo(1);
    verify(auditService)
        .record(
            eq("BREAK_GLASS_ALL_ACTIVE_SESSIONS_REVOKED"),
            any(),
            eq("BREAK_GLASS"),
            any(),
            any(),
            any());
  }

  @Test
  void maskJti_neverReturnsFullValue() {
    assertThat(BreakGlassAccessEventService.maskJti("abcdefghijklmnop")).isEqualTo("abcd…mnop");
    assertThat(BreakGlassAccessEventService.maskJti("")).isEmpty();
  }

  private static BreakGlassAccessEvent expiredEvent(String jti) {
    Instant now = Instant.now();
    return new BreakGlassAccessEvent(
        UUID.randomUUID(),
        "session-expired",
        "static-token",
        "hash",
        "summary",
        "actor",
        BreakGlassAccessEventStatus.ISSUED,
        now.minusSeconds(120),
        now.minusSeconds(60),
        "",
        "",
        false,
        false,
        jti,
        now,
        now);
  }

  private static BreakGlassAccessEvent activeEvent(String jti) {
    Instant now = Instant.now();
    return new BreakGlassAccessEvent(
        UUID.randomUUID(),
        "session-" + UUID.randomUUID(),
        "static-token",
        "hash",
        "summary",
        "actor",
        BreakGlassAccessEventStatus.ISSUED,
        now.minusSeconds(30),
        now.plusSeconds(600),
        "",
        "",
        false,
        false,
        jti,
        now,
        now);
  }
}
