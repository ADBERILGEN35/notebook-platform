package com.notebook.lumen.identity.breakglass;

import com.notebook.lumen.identity.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BreakGlassAccessEventService {
  private final BreakGlassAccessEventRepository repository;
  private final BreakGlassTokenDenylistRepository denylistRepository;
  private final AuditService auditService;
  private final BreakGlassProperties props;

  public BreakGlassAccessEventService(
      BreakGlassAccessEventRepository repository,
      BreakGlassTokenDenylistRepository denylistRepository,
      AuditService auditService,
      BreakGlassProperties props) {
    this.repository = repository;
    this.denylistRepository = denylistRepository;
    this.auditService = auditService;
    this.props = props;
  }

  @Transactional
  public BreakGlassAccessEvent createIssuedEvent(
      String sessionId,
      String mode,
      String actorLabel,
      String reason,
      Instant issuedAt,
      Instant expiresAt,
      boolean rotationRequired,
      String tokenJti,
      HttpServletRequest request) {
    Instant now = Instant.now();
    BreakGlassAccessEventStatus status =
        switch (BreakGlassApprovalMode.from(props.approvalMode())) {
          case POST_USE_REVIEW -> BreakGlassAccessEventStatus.PENDING_REVIEW;
          default -> BreakGlassAccessEventStatus.ISSUED;
        };
    BreakGlassAccessEvent event =
        new BreakGlassAccessEvent(
            UUID.randomUUID(),
            sessionId,
            mode,
            hash(reason),
            summarizeReason(reason),
            actorLabel,
            status,
            issuedAt,
            expiresAt,
            hash(request == null ? null : request.getRemoteAddr()),
            hash(request == null ? null : request.getHeader("User-Agent")),
            rotationRequired,
            false,
            tokenJti,
            now,
            now);
    repository.save(event);
    auditService.record(
        "BREAK_GLASS_EVENT_CREATED",
        null,
        "BREAK_GLASS",
        event.getId(),
        request,
        Map.of(
            "mode",
            mode,
            "status",
            status.name(),
            "reasonPresent",
            reason != null && !reason.isBlank()));
    return event;
  }

  @Transactional(readOnly = true)
  public BreakGlassReviewDtos.EventListResponse list(
      String status, String mode, Instant from, Instant to, int page, int size) {
    Instant start = from == null ? Instant.now().minus(7, ChronoUnit.DAYS) : from;
    Instant end = to == null ? Instant.now().plus(1, ChronoUnit.MINUTES) : to;
    var pageable =
        PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), 200),
            Sort.by(Sort.Direction.DESC, "issuedAt"));
    var rows =
        (status == null || status.isBlank())
            ? repository.findByIssuedAtBetween(start, end, pageable)
            : repository.findByStatusAndIssuedAtBetween(
                BreakGlassAccessEventStatus.valueOf(status.trim().toUpperCase()),
                start,
                end,
                pageable);
    var items =
        rows.stream()
            .filter(e -> mode == null || mode.isBlank() || mode.equalsIgnoreCase(e.getMode()))
            .map(
                e ->
                    new BreakGlassReviewDtos.EventItem(
                        e.getId(),
                        shortSession(e.getSessionId()),
                        e.getMode(),
                        e.getActorLabel(),
                        e.getStatus().name(),
                        tokenStatus(e),
                        e.isRotationRequired(),
                        e.getIssuedAt(),
                        e.getExpiresAt(),
                        e.getReviewedAt()))
            .toList();
    long overdue =
        repository.countByStatusAndIssuedAtBefore(
            BreakGlassAccessEventStatus.PENDING_REVIEW,
            Instant.now().minus(props.reviewRequiredWithinMinutes(), ChronoUnit.MINUTES));
    return new BreakGlassReviewDtos.EventListResponse(items, rows.getTotalElements(), overdue);
  }

  @Transactional(readOnly = true)
  public long pendingReviewCount() {
    return repository.countByStatus(BreakGlassAccessEventStatus.PENDING_REVIEW);
  }

  @Transactional(readOnly = true)
  public long overdueReviewCount() {
    return repository.countByStatusAndIssuedAtBefore(
        BreakGlassAccessEventStatus.PENDING_REVIEW,
        Instant.now().minus(props.reviewRequiredWithinMinutes(), ChronoUnit.MINUTES));
  }

  @Transactional(readOnly = true)
  public BreakGlassReviewDtos.EventDetailResponse detail(UUID id) {
    BreakGlassAccessEvent e = getOrThrow(id);
    return new BreakGlassReviewDtos.EventDetailResponse(
        e.getId(),
        shortSession(e.getSessionId()),
        e.getMode(),
        e.getActorLabel(),
        e.getStatus().name(),
        tokenStatus(e),
        e.isRotationRequired(),
        e.getIssuedAt(),
        e.getExpiresAt(),
        e.getReasonSummary(),
        e.getReviewedAt(),
        e.getReviewedByUserId(),
        e.getReviewDecision(),
        e.getReviewReason(),
        e.getTokenRevokedAt());
  }

  @Transactional
  public BreakGlassReviewDtos.EventDetailResponse review(
      UUID id,
      UUID reviewerId,
      BreakGlassReviewDtos.ReviewRequest body,
      HttpServletRequest request) {
    if (!props.reviewApiEnabled()) {
      throw new BreakGlassException(
          "BREAK_GLASS_REVIEW_API_DISABLED",
          HttpStatus.FORBIDDEN,
          "Break-glass review API disabled");
    }
    BreakGlassAccessEvent e = getOrThrow(id);
    if (e.getStatus() != BreakGlassAccessEventStatus.PENDING_REVIEW) {
      throw new BreakGlassException(
          "BREAK_GLASS_EVENT_NOT_REVIEWABLE",
          HttpStatus.CONFLICT,
          "Break-glass event is not pending review");
    }
    String decision =
        body == null || body.decision() == null ? "" : body.decision().trim().toUpperCase();
    BreakGlassAccessEventStatus next =
        switch (decision) {
          case "APPROVE" -> BreakGlassAccessEventStatus.REVIEW_APPROVED;
          case "REJECT" -> BreakGlassAccessEventStatus.REVIEW_REJECTED;
          case "CLOSE" -> BreakGlassAccessEventStatus.CLOSED;
          default ->
              throw new BreakGlassException(
                  "BREAK_GLASS_EVENT_NOT_REVIEWABLE",
                  HttpStatus.BAD_REQUEST,
                  "Invalid review decision");
        };
    e.markReviewed(reviewerId, decision, body.reason(), next);
    repository.save(e);
    if ("REJECT".equals(decision) && props.revokeOnReject()) {
      revokeToken(id, reviewerId, "Rejected review: " + body.reason(), "REVIEW_REJECT", request);
    }
    auditService.record(
        "BREAK_GLASS_REVIEW_" + next.name(),
        reviewerId,
        "BREAK_GLASS",
        e.getId(),
        request,
        Map.of("mode", e.getMode(), "status", next.name(), "reviewReasonPresent", true));
    return detail(id);
  }

  @Transactional
  public BreakGlassReviewDtos.RevokeTokenResponse revokeToken(
      UUID eventId, UUID reviewerId, String reason, String source, HttpServletRequest request) {
    if (!props.revocationEnabled()) {
      throw new BreakGlassException(
          "BREAK_GLASS_TOKEN_REVOCATION_DISABLED",
          HttpStatus.FORBIDDEN,
          "Break-glass token revocation is disabled");
    }
    BreakGlassAccessEvent e = getOrThrow(eventId);
    if (e.getTokenJti() == null || e.getTokenJti().isBlank()) {
      throw new BreakGlassException(
          "BREAK_GLASS_TOKEN_REVOCATION_UNAVAILABLE",
          HttpStatus.CONFLICT,
          "Break-glass token jti is unavailable");
    }
    if (denylistRepository.findByJti(e.getTokenJti()).isPresent()) {
      auditService.record(
          "BREAK_GLASS_TOKEN_ALREADY_REVOKED",
          reviewerId,
          "BREAK_GLASS",
          e.getId(),
          request,
          Map.of("source", source));
      return new BreakGlassReviewDtos.RevokeTokenResponse(false, false, true);
    }
    if (e.getExpiresAt().isBefore(Instant.now())) {
      auditService.record(
          "BREAK_GLASS_TOKEN_ALREADY_EXPIRED",
          reviewerId,
          "BREAK_GLASS",
          e.getId(),
          request,
          Map.of("source", source));
      return new BreakGlassReviewDtos.RevokeTokenResponse(false, true, false);
    }
    Instant now = Instant.now();
    denylistRepository.save(
        new BreakGlassTokenDenylistEntry(
            UUID.randomUUID(),
            e.getTokenJti(),
            e.getSessionId(),
            e.getId(),
            reviewerId,
            e.getIssuedAt(),
            now,
            e.getExpiresAt(),
            reason == null ? "" : reason.trim(),
            normalizeSource(source),
            now));
    e.markTokenRevoked(reviewerId, reason);
    repository.save(e);
    auditService.record(
        "BREAK_GLASS_TOKEN_REVOKED",
        reviewerId,
        "BREAK_GLASS",
        e.getId(),
        request,
        Map.of("source", source, "reasonPresent", reason != null && !reason.isBlank()));
    return new BreakGlassReviewDtos.RevokeTokenResponse(true, false, false);
  }

  @Transactional(readOnly = true)
  public BreakGlassReviewDtos.TokenRevokedStatusResponse tokenRevokedStatus(String jti) {
    return denylistRepository
        .findByJti(jti == null ? "" : jti.trim())
        .map(
            e ->
                new BreakGlassReviewDtos.TokenRevokedStatusResponse(
                    true, shortSession(e.getSessionId()), e.getExpiresAt(), e.getSource()))
        .orElseGet(() -> new BreakGlassReviewDtos.TokenRevokedStatusResponse(false, "", null, ""));
  }

  @Transactional(readOnly = true)
  public long activeDenylistEntries() {
    return denylistRepository.countByExpiresAtAfter(Instant.now());
  }

  private BreakGlassAccessEvent getOrThrow(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(
            () ->
                new BreakGlassException(
                    "BREAK_GLASS_EVENT_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "Break-glass event not found"));
  }

  private static String shortSession(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return "";
    }
    return sessionId.length() <= 12 ? sessionId : sessionId.substring(0, 12);
  }

  private static String summarizeReason(String reason) {
    if (reason == null || reason.isBlank()) {
      return "";
    }
    String out = reason.trim();
    return out.length() <= 120 ? out : out.substring(0, 120);
  }

  private static String tokenStatus(BreakGlassAccessEvent e) {
    if (e.getTokenRevokedAt() != null) {
      return "REVOKED";
    }
    if (e.getExpiresAt().isBefore(Instant.now())) {
      return "EXPIRED";
    }
    return e.getTokenJti() == null || e.getTokenJti().isBlank() ? "UNKNOWN" : "ACTIVE";
  }

  static String maskJti(String jti) {
    if (jti == null || jti.isBlank()) {
      return "";
    }
    String trimmed = jti.trim();
    if (trimmed.length() <= 8) {
      return "****";
    }
    return trimmed.substring(0, 4) + "…" + trimmed.substring(trimmed.length() - 4);
  }

  static String normalizeSource(String source) {
    if (source == null || source.isBlank()) {
      return BreakGlassRevocationSource.ADMIN_REVOKE.wire();
    }
    if ("MANUAL_REVOKE".equalsIgnoreCase(source) || "REVIEW_REJECT".equalsIgnoreCase(source)) {
      return BreakGlassRevocationSource.from(source).wire();
    }
    return BreakGlassRevocationSource.from(source).wire();
  }

  private static String hash(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    try {
      byte[] digest =
          java.security.MessageDigest.getInstance("SHA-256")
              .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return "sha256:" + java.util.HexFormat.of().formatHex(digest);
    } catch (Exception e) {
      return "";
    }
  }
}
