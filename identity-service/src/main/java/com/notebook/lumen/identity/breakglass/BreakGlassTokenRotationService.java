package com.notebook.lumen.identity.breakglass;

import com.notebook.lumen.identity.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 94: governance state machine for break-glass static-token rotation.
 *
 * <p>The service never accepts, returns, or logs a raw token or raw hash. It only stores short
 * fingerprints (prefix of SHA-256 of the configured hash) so a reviewer can confirm "the hash
 * changed" without seeing the secret. New token material is provisioned externally via External
 * Secret / GitOps; this service only tracks the governance state.
 */
@Service
public class BreakGlassTokenRotationService {

  static final String MODE_STATIC = "static-token";

  private final BreakGlassTokenRotationEventRepository repository;
  private final BreakGlassProperties props;
  private final AuditService auditService;

  public BreakGlassTokenRotationService(
      BreakGlassTokenRotationEventRepository repository,
      BreakGlassProperties props,
      AuditService auditService) {
    this.repository = repository;
    this.props = props;
    this.auditService = auditService;
  }

  /** Called from BreakGlassService after a successful static-token login. */
  @Transactional
  public Optional<BreakGlassTokenRotationEvent> recordRequiredAfterStaticUse(
      UUID eventId, String sessionId, HttpServletRequest request) {
    if (!props.rotationTrackingEnabled()) {
      return Optional.empty();
    }
    String configuredHash = props.tokenHash();
    if (configuredHash == null || configuredHash.isBlank()) {
      return Optional.empty();
    }
    String oldFingerprint = fingerprint(configuredHash);
    List<BreakGlassTokenRotationEvent> existing =
        repository.findByStatusAndOldTokenHashFingerprint(
            BreakGlassTokenRotationEventStatus.REQUIRED, oldFingerprint);
    if (!existing.isEmpty()) {
      auditMetadata(
          "BREAK_GLASS_TOKEN_ROTATION_DUPLICATE_SUPPRESSED",
          existing.get(0).getId(),
          null,
          oldFingerprint,
          null,
          request);
      return Optional.of(existing.get(0));
    }
    long open =
        repository.countByStatus(BreakGlassTokenRotationEventStatus.REQUIRED)
            + repository.countByStatus(BreakGlassTokenRotationEventStatus.ACKNOWLEDGED);
    if (open >= props.rotationMaxOpenEvents()) {
      auditMetadata(
          "BREAK_GLASS_TOKEN_ROTATION_LIMIT_REACHED", null, null, oldFingerprint, null, request);
      throw BreakGlassException.rotationLimitExceeded();
    }
    Instant now = Instant.now();
    BreakGlassTokenRotationEvent event =
        new BreakGlassTokenRotationEvent(
            UUID.randomUUID(),
            "rk-" + UUID.randomUUID(),
            MODE_STATIC,
            BreakGlassTokenRotationEventStatus.REQUIRED,
            eventId,
            sessionId,
            oldFingerprint,
            now,
            now,
            now);
    repository.save(event);
    auditMetadata(
        "BREAK_GLASS_TOKEN_ROTATION_REQUIRED", event.getId(), null, oldFingerprint, null, request);
    return Optional.of(event);
  }

  @Transactional(readOnly = true)
  public BreakGlassRotationDtos.RotationEventListResponse list(
      String status, int page, int size, HttpServletRequest request) {
    var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
    org.springframework.data.domain.Page<BreakGlassTokenRotationEvent> rows;
    if (status == null || status.isBlank()) {
      rows = repository.findAllByOrderByRequiredAtDesc(pageable);
    } else {
      List<BreakGlassTokenRotationEventStatus> statuses =
          List.of(BreakGlassTokenRotationEventStatus.valueOf(status.trim().toUpperCase()));
      rows = repository.findByStatusInOrderByRequiredAtDesc(statuses, pageable);
    }
    long openCount =
        repository.countByStatus(BreakGlassTokenRotationEventStatus.REQUIRED)
            + repository.countByStatus(BreakGlassTokenRotationEventStatus.ACKNOWLEDGED);
    long requiredCount = repository.countByStatus(BreakGlassTokenRotationEventStatus.REQUIRED);
    auditMetadata("BREAK_GLASS_TOKEN_ROTATION_VIEWED", null, null, null, null, request);
    return new BreakGlassRotationDtos.RotationEventListResponse(
        rows.stream().map(BreakGlassTokenRotationService::toItem).toList(),
        rows.getTotalElements(),
        openCount,
        requiredCount);
  }

  @Transactional(readOnly = true)
  public BreakGlassRotationDtos.RotationEventDetail detail(UUID id, HttpServletRequest request) {
    BreakGlassTokenRotationEvent e = getOrThrow(id);
    auditMetadata(
        "BREAK_GLASS_TOKEN_ROTATION_VIEWED",
        e.getId(),
        null,
        e.getOldTokenHashFingerprint(),
        e.getNewTokenHashFingerprint(),
        request);
    return toDetail(e);
  }

  @Transactional(readOnly = true)
  public BreakGlassRotationDtos.RotationSummary summary() {
    boolean tracking = props.rotationTrackingEnabled();
    long required =
        tracking ? repository.countByStatus(BreakGlassTokenRotationEventStatus.REQUIRED) : 0L;
    long ack =
        tracking ? repository.countByStatus(BreakGlassTokenRotationEventStatus.ACKNOWLEDGED) : 0L;
    Instant oldest =
        tracking
            ? repository
                .findTopByStatusInOrderByRequiredAtAsc(
                    List.of(
                        BreakGlassTokenRotationEventStatus.REQUIRED,
                        BreakGlassTokenRotationEventStatus.ACKNOWLEDGED))
                .map(BreakGlassTokenRotationEvent::getRequiredAt)
                .orElse(null)
            : null;
    Instant lastVerified =
        tracking
            ? repository
                .findTopByStatusOrderByVerifiedAtDesc(BreakGlassTokenRotationEventStatus.VERIFIED)
                .map(BreakGlassTokenRotationEvent::getVerifiedAt)
                .orElse(null)
            : null;
    return new BreakGlassRotationDtos.RotationSummary(
        tracking, required > 0, required + ack, oldest, lastVerified);
  }

  @Transactional
  public BreakGlassRotationDtos.RotationEventDetail acknowledge(
      UUID id,
      UUID actorUserId,
      BreakGlassRotationDtos.AcknowledgeRequest body,
      HttpServletRequest request) {
    if (!props.rotationApiEnabled()) {
      throw BreakGlassException.rotationApiDisabled();
    }
    BreakGlassTokenRotationEvent e = getOrThrow(id);
    if (e.getStatus() != BreakGlassTokenRotationEventStatus.REQUIRED) {
      throw BreakGlassException.rotationInvalidTransition(
          "Only REQUIRED events can be acknowledged; current=" + e.getStatus());
    }
    String reason = body == null || body.reason() == null ? "" : body.reason().trim();
    e.markAcknowledged(actorUserId, reason);
    repository.save(e);
    auditMetadata(
        "BREAK_GLASS_TOKEN_ROTATION_ACKNOWLEDGED",
        e.getId(),
        actorUserId,
        e.getOldTokenHashFingerprint(),
        null,
        request);
    return toDetail(e);
  }

  @Transactional
  public BreakGlassRotationDtos.RotationEventDetail verify(
      UUID id,
      UUID actorUserId,
      BreakGlassRotationDtos.VerifyRequest body,
      HttpServletRequest request) {
    if (!props.rotationApiEnabled()) {
      throw BreakGlassException.rotationApiDisabled();
    }
    BreakGlassTokenRotationEvent e = getOrThrow(id);
    if (e.getStatus() != BreakGlassTokenRotationEventStatus.REQUIRED
        && e.getStatus() != BreakGlassTokenRotationEventStatus.ACKNOWLEDGED) {
      throw BreakGlassException.rotationInvalidTransition(
          "Only REQUIRED or ACKNOWLEDGED events can be verified; current=" + e.getStatus());
    }
    String configuredHash = props.tokenHash();
    if (configuredHash == null || configuredHash.isBlank()) {
      auditMetadata(
          "BREAK_GLASS_TOKEN_ROTATION_VERIFY_FAILED",
          e.getId(),
          actorUserId,
          e.getOldTokenHashFingerprint(),
          null,
          request);
      throw BreakGlassException.rotationNotChanged();
    }
    String currentFingerprint = fingerprint(configuredHash);
    if (currentFingerprint.equals(e.getOldTokenHashFingerprint())) {
      auditMetadata(
          "BREAK_GLASS_TOKEN_ROTATION_VERIFY_FAILED",
          e.getId(),
          actorUserId,
          e.getOldTokenHashFingerprint(),
          currentFingerprint,
          request);
      throw BreakGlassException.rotationNotChanged();
    }
    String reason = body == null || body.reason() == null ? "" : body.reason().trim();
    e.markVerified(actorUserId, currentFingerprint, reason);
    repository.save(e);
    auditMetadata(
        "BREAK_GLASS_TOKEN_ROTATION_VERIFIED",
        e.getId(),
        actorUserId,
        e.getOldTokenHashFingerprint(),
        currentFingerprint,
        request);
    return toDetail(e);
  }

  @Transactional
  public BreakGlassRotationDtos.RotationEventDetail close(
      UUID id,
      UUID actorUserId,
      BreakGlassRotationDtos.CloseRequest body,
      HttpServletRequest request) {
    if (!props.rotationApiEnabled()) {
      throw BreakGlassException.rotationApiDisabled();
    }
    BreakGlassTokenRotationEvent e = getOrThrow(id);
    if (e.getStatus() != BreakGlassTokenRotationEventStatus.VERIFIED) {
      throw BreakGlassException.rotationInvalidTransition(
          "Only VERIFIED events can be closed; current=" + e.getStatus());
    }
    String reason = body == null || body.reason() == null ? "" : body.reason().trim();
    e.markClosed(reason);
    repository.save(e);
    auditMetadata(
        "BREAK_GLASS_TOKEN_ROTATION_CLOSED",
        e.getId(),
        actorUserId,
        e.getOldTokenHashFingerprint(),
        e.getNewTokenHashFingerprint(),
        request);
    return toDetail(e);
  }

  private BreakGlassTokenRotationEvent getOrThrow(UUID id) {
    return repository.findById(id).orElseThrow(BreakGlassException::rotationNotFound);
  }

  private String fingerprint(String configuredHash) {
    String input = configuredHash == null ? "" : configuredHash.trim();
    if (input.isBlank()) {
      return "";
    }
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
      String hex = HexFormat.of().formatHex(digest);
      int len = Math.min(props.tokenHashFingerprintLength(), hex.length());
      return "fp:" + hex.substring(0, len);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private void auditMetadata(
      String eventType,
      UUID rotationEventId,
      UUID actorUserId,
      String oldFingerprint,
      String newFingerprint,
      HttpServletRequest request) {
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("mode", MODE_STATIC);
    if (rotationEventId != null) {
      meta.put("rotationEventId", rotationEventId.toString());
    }
    if (oldFingerprint != null && !oldFingerprint.isBlank()) {
      meta.put("oldFingerprint", oldFingerprint);
    }
    if (newFingerprint != null && !newFingerprint.isBlank()) {
      meta.put("newFingerprint", newFingerprint);
    }
    meta.put("reasonPresent", true);
    auditService.record(eventType, actorUserId, "BREAK_GLASS", rotationEventId, request, meta);
  }

  private static BreakGlassRotationDtos.RotationEventItem toItem(BreakGlassTokenRotationEvent e) {
    return new BreakGlassRotationDtos.RotationEventItem(
        e.getId(),
        e.getCredentialMode(),
        e.getStatus().name(),
        e.getOldTokenHashFingerprint(),
        e.getNewTokenHashFingerprint(),
        e.getTriggeredByEventId(),
        e.getTriggeredBySessionId() == null
            ? null
            : (e.getTriggeredBySessionId().length() <= 12
                ? e.getTriggeredBySessionId()
                : e.getTriggeredBySessionId().substring(0, 12)),
        e.getRequiredAt(),
        e.getAcknowledgedAt(),
        e.getVerifiedAt(),
        e.getClosedAt());
  }

  private static BreakGlassRotationDtos.RotationEventDetail toDetail(
      BreakGlassTokenRotationEvent e) {
    return new BreakGlassRotationDtos.RotationEventDetail(
        e.getId(),
        e.getCredentialMode(),
        e.getStatus().name(),
        e.getOldTokenHashFingerprint(),
        e.getNewTokenHashFingerprint(),
        e.getTriggeredByEventId(),
        e.getTriggeredBySessionId() == null
            ? null
            : (e.getTriggeredBySessionId().length() <= 12
                ? e.getTriggeredBySessionId()
                : e.getTriggeredBySessionId().substring(0, 12)),
        e.getRequiredAt(),
        e.getAcknowledgedAt(),
        e.getAcknowledgedByUserId(),
        e.getVerifiedAt(),
        e.getVerifiedByUserId(),
        e.getClosedAt(),
        e.getReason());
  }
}
