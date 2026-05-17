package com.notebook.lumen.identity.breakglass;

import com.notebook.lumen.identity.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BreakGlassSessionService {
  private final BreakGlassAccessEventRepository eventRepository;
  private final BreakGlassAccessEventService eventService;
  private final BreakGlassProperties props;
  private final AuditService auditService;
  private final BreakGlassRevocationMetrics metrics;

  public BreakGlassSessionService(
      BreakGlassAccessEventRepository eventRepository,
      BreakGlassAccessEventService eventService,
      BreakGlassProperties props,
      AuditService auditService,
      BreakGlassRevocationMetrics metrics) {
    this.eventRepository = eventRepository;
    this.eventService = eventService;
    this.props = props;
    this.auditService = auditService;
    this.metrics = metrics;
  }

  @Transactional(readOnly = true)
  public BreakGlassSessionDtos.SessionListResponse listActiveSessions(
      UUID actorUserId, HttpServletRequest request) {
    if (!props.revocationEnabled()) {
      throw new BreakGlassException(
          "BREAK_GLASS_TOKEN_REVOCATION_DISABLED",
          HttpStatus.FORBIDDEN,
          "Break-glass token revocation is disabled");
    }
    Instant now = Instant.now();
    List<BreakGlassSessionDtos.SessionItem> items = new ArrayList<>();
    long active = 0;
    for (BreakGlassAccessEvent event :
        eventRepository.findByExpiresAtAfterAndTokenJtiIsNotNull(now)) {
      String tokenStatus = tokenStatus(event, now);
      if ("ACTIVE".equals(tokenStatus)) {
        active++;
      }
      items.add(toSessionItem(event, tokenStatus, now));
    }
    metrics.setActiveSessions(active);
    auditService.record(
        "BREAK_GLASS_SESSION_LIST_VIEWED",
        actorUserId,
        "BREAK_GLASS",
        null,
        request,
        java.util.Map.of("activeCount", active, "listedCount", items.size()));
    return new BreakGlassSessionDtos.SessionListResponse(items, active);
  }

  @Transactional
  public BreakGlassSessionDtos.RevokeSessionResponse revokeByJti(
      String jtiRef,
      UUID actorUserId,
      String reason,
      BreakGlassRevocationSource source,
      HttpServletRequest request) {
    validateReason(reason);
    BreakGlassAccessEvent event = resolveEvent(jtiRef);
    BreakGlassReviewDtos.RevokeTokenResponse result =
        eventService.revokeToken(event.getId(), actorUserId, reason, source.wire(), request);
    String resultTag =
        result.alreadyRevoked()
            ? "already_revoked"
            : result.alreadyExpired() ? "already_expired" : result.revoked() ? "revoked" : "noop";
    metrics.recordRevocation(source.wire(), resultTag);
    if (result.revoked()) {
      auditService.record(
          "BREAK_GLASS_SESSION_REVOKED",
          actorUserId,
          "BREAK_GLASS",
          event.getId(),
          request,
          java.util.Map.of(
              "source",
              source.wire(),
              "sessionIdPrefix",
              shortSession(event.getSessionId()),
              "reasonPresent",
              true));
    }
    return new BreakGlassSessionDtos.RevokeSessionResponse(
        result.revoked(),
        result.alreadyExpired(),
        result.alreadyRevoked(),
        shortSession(event.getSessionId()));
  }

  @Transactional
  public BreakGlassSessionDtos.RevokeAllActiveResponse revokeAllActive(
      UUID actorUserId,
      String reason,
      BreakGlassRevocationSource source,
      HttpServletRequest request) {
    validateReason(reason);
    if (!props.revocationEnabled()) {
      auditDenied(actorUserId, "revocation_disabled", request);
      throw new BreakGlassException(
          "BREAK_GLASS_TOKEN_REVOCATION_DISABLED",
          HttpStatus.FORBIDDEN,
          "Break-glass token revocation is disabled");
    }
    Instant now = Instant.now();
    int revoked = 0;
    int alreadyRevoked = 0;
    int expired = 0;
    for (BreakGlassAccessEvent event :
        eventRepository.findByExpiresAtAfterAndTokenJtiIsNotNull(now)) {
      if (!"ACTIVE".equals(tokenStatus(event, now))) {
        if (event.getTokenRevokedAt() != null) {
          alreadyRevoked++;
        } else if (event.getExpiresAt().isBefore(now)) {
          expired++;
        }
        continue;
      }
      BreakGlassReviewDtos.RevokeTokenResponse result =
          eventService.revokeToken(event.getId(), actorUserId, reason, source.wire(), request);
      if (result.revoked()) {
        revoked++;
      } else if (result.alreadyRevoked()) {
        alreadyRevoked++;
      } else if (result.alreadyExpired()) {
        expired++;
      }
    }
    metrics.recordRevokeAll(revoked > 0 ? "revoked" : "noop");
    metrics.recordRevocation(source.wire(), "revoke_all");
    auditService.record(
        "BREAK_GLASS_ALL_ACTIVE_SESSIONS_REVOKED",
        actorUserId,
        "BREAK_GLASS",
        null,
        request,
        java.util.Map.of(
            "source",
            source.wire(),
            "revokedCount",
            revoked,
            "alreadyRevokedCount",
            alreadyRevoked,
            "expiredCount",
            expired,
            "reasonPresent",
            true));
    return new BreakGlassSessionDtos.RevokeAllActiveResponse(revoked, alreadyRevoked, expired);
  }

  private BreakGlassAccessEvent resolveEvent(String jtiRef) {
    if (jtiRef == null || jtiRef.isBlank()) {
      throw new BreakGlassException(
          "BREAK_GLASS_SESSION_NOT_FOUND", HttpStatus.NOT_FOUND, "Break-glass session not found");
    }
    String ref = jtiRef.trim();
    return eventRepository
        .findByTokenJti(ref)
        .or(() -> eventRepository.findBySessionId(ref))
        .orElseThrow(
            () ->
                new BreakGlassException(
                    "BREAK_GLASS_SESSION_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "Break-glass session not found"));
  }

  private static void validateReason(String reason) {
    String r = reason == null ? "" : reason.trim();
    if (r.length() < 10) {
      throw new BreakGlassException(
          "BREAK_GLASS_REVOKE_REASON_REQUIRED",
          HttpStatus.BAD_REQUEST,
          "Revocation reason must be at least 10 characters");
    }
  }

  private void auditDenied(UUID actorUserId, String reason, HttpServletRequest request) {
    auditService.record(
        "BREAK_GLASS_REVOKE_DENIED",
        actorUserId,
        "BREAK_GLASS",
        null,
        request,
        java.util.Map.of("reason", reason));
  }

  private static BreakGlassSessionDtos.SessionItem toSessionItem(
      BreakGlassAccessEvent event, String tokenStatus, Instant now) {
    String source = "";
    if (event.getTokenRevokedAt() != null) {
      source = BreakGlassRevocationSource.ADMIN_REVOKE.wire();
    }
    return new BreakGlassSessionDtos.SessionItem(
        event.getId(),
        shortSession(event.getSessionId()),
        event.getSessionId(),
        BreakGlassAccessEventService.maskJti(event.getTokenJti()),
        event.getMode(),
        event.getActorLabel(),
        event.getStatus().name(),
        tokenStatus,
        event.getIssuedAt(),
        event.getExpiresAt(),
        source);
  }

  private static String tokenStatus(BreakGlassAccessEvent event, Instant now) {
    if (event.getTokenRevokedAt() != null) {
      return "REVOKED";
    }
    if (event.getExpiresAt().isBefore(now)) {
      return "EXPIRED";
    }
    return event.getTokenJti() == null || event.getTokenJti().isBlank() ? "UNKNOWN" : "ACTIVE";
  }

  private static String shortSession(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return "";
    }
    return sessionId.length() <= 12 ? sessionId : sessionId.substring(0, 12);
  }
}
