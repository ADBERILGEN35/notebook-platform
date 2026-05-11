package com.notebook.lumen.notification.admin.legalhold;

import com.notebook.lumen.notification.audit.AuditService;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationLegalHoldAdminService {

  private static final String AGGREGATE = "NOTIFICATION_LEGAL_HOLD";

  private final NotificationLegalHoldRepository repository;
  private final NotificationLegalHoldProperties properties;
  private final AuditService auditService;
  private final NotificationLegalHoldMetrics metrics;

  public NotificationLegalHoldAdminService(
      NotificationLegalHoldRepository repository,
      NotificationLegalHoldProperties properties,
      AuditService auditService,
      NotificationLegalHoldMetrics metrics) {
    this.repository = repository;
    this.properties = properties;
    this.auditService = auditService;
    this.metrics = metrics;
  }

  public LegalHoldAdminDtos.LegalHoldListResponse list(Optional<LegalHoldStatus> statusFilter) {
    List<NotificationLegalHoldEntity> rows;
    if (statusFilter.isEmpty()) {
      rows = repository.findAll();
      rows.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
    } else {
      rows = repository.findByStatusOrderByCreatedAtDesc(statusFilter.get());
    }
    List<LegalHoldAdminDtos.LegalHoldResponse> out = new ArrayList<>();
    for (NotificationLegalHoldEntity e : rows) {
      out.add(toResponse(e));
    }
    auditService.record(
        LegalHoldAuditEventType.VIEWED,
        AGGREGATE,
        UUID.randomUUID(),
        Map.of("resultCount", out.size(), "statusFilterPresent", statusFilter.isPresent()));
    return new LegalHoldAdminDtos.LegalHoldListResponse(List.copyOf(out));
  }

  @Transactional
  public LegalHoldAdminDtos.LegalHoldResponse create(
      LegalHoldAdminDtos.LegalHoldCreateRequest req, UUID actorUserId, String actorEmail) {
    validateApiEnabled();
    if (!properties.enabled()) {
      auditService.record(
          LegalHoldAuditEventType.CREATE_DENIED,
          AGGREGATE,
          UUID.randomUUID(),
          Map.of("code", "LEGAL_HOLD_DISABLED", "actorUserId", actorUserId.toString()));
      throw new NotificationException(
          HttpStatus.FORBIDDEN,
          "LEGAL_HOLD_DISABLED",
          "Notification legal hold feature is disabled.");
    }
    String holdKey = validateHoldKey(req.holdKey());
    String reasonText = validateReason(req.reason(), 10, "reason");
    LegalHoldScope scope = LegalHoldScope.parse(req.scope());
    if (req.expiresAt() != null && !req.expiresAt().isAfter(Instant.now())) {
      auditService.record(
          LegalHoldAuditEventType.CREATE_DENIED,
          AGGREGATE,
          UUID.randomUUID(),
          Map.of("code", "EXPIRES_AT_INVALID", "actorUserId", actorUserId.toString()));
      throw new NotificationException(
          HttpStatus.BAD_REQUEST,
          "LEGAL_HOLD_EXPIRES_AT",
          "expiresAt must be in the future when set.");
    }
    if (repository.existsByHoldKey(holdKey)) {
      auditService.record(
          LegalHoldAuditEventType.CREATE_DENIED,
          AGGREGATE,
          UUID.randomUUID(),
          Map.of(
              "code",
              "DUPLICATE_HOLD_KEY",
              "holdKey",
              holdKey,
              "actorUserId",
              actorUserId.toString()));
      throw new NotificationException(
          HttpStatus.CONFLICT, "LEGAL_HOLD_DUPLICATE_KEY", "holdKey already exists.");
    }
    long active = repository.countByStatus(LegalHoldStatus.ACTIVE);
    if (active >= properties.maxActive()) {
      auditService.record(
          LegalHoldAuditEventType.CREATE_DENIED,
          AGGREGATE,
          UUID.randomUUID(),
          Map.of("code", "MAX_ACTIVE", "actorUserId", actorUserId.toString()));
      throw new NotificationException(
          HttpStatus.BAD_REQUEST,
          "LEGAL_HOLD_MAX_ACTIVE",
          "Maximum number of active legal holds reached.");
    }
    Instant now = Instant.now();
    var entity =
        new NotificationLegalHoldEntity(
            UUID.randomUUID(),
            holdKey,
            scope,
            reasonText,
            actorUserId,
            actorEmail,
            now,
            req.expiresAt(),
            Map.of());
    repository.save(entity);
    metrics.recordCreated(scope);
    auditService.record(
        LegalHoldAuditEventType.CREATED,
        AGGREGATE,
        entity.getId(),
        Map.of(
            "holdKey",
            holdKey,
            "scope",
            scope.name(),
            "status",
            LegalHoldStatus.ACTIVE.name(),
            "reasonPresent",
            true,
            "expiresAtPresent",
            req.expiresAt() != null,
            "actorUserId",
            actorUserId.toString()));
    return toResponse(entity);
  }

  @Transactional
  public LegalHoldAdminDtos.LegalHoldResponse release(
      UUID id, LegalHoldAdminDtos.LegalHoldReleaseRequest req, UUID actorUserId) {
    validateApiEnabled();
    if (!properties.enabled()) {
      auditService.record(
          LegalHoldAuditEventType.RELEASE_DENIED,
          AGGREGATE,
          UUID.randomUUID(),
          Map.of("code", "LEGAL_HOLD_DISABLED", "actorUserId", actorUserId.toString()));
      throw new NotificationException(
          HttpStatus.FORBIDDEN,
          "LEGAL_HOLD_DISABLED",
          "Notification legal hold feature is disabled.");
    }
    validateReason(req.reason(), 10, "release reason");
    NotificationLegalHoldEntity entity =
        repository
            .findById(id)
            .orElseThrow(
                () ->
                    new NotificationException(
                        HttpStatus.NOT_FOUND, "LEGAL_HOLD_NOT_FOUND", "Legal hold not found."));
    if (entity.getStatus() != LegalHoldStatus.ACTIVE) {
      auditService.record(
          LegalHoldAuditEventType.RELEASE_DENIED,
          AGGREGATE,
          id,
          Map.of(
              "code",
              "NOT_ACTIVE",
              "holdKey",
              entity.getHoldKey(),
              "status",
              entity.getStatus().name(),
              "actorUserId",
              actorUserId.toString()));
      throw new NotificationException(
          HttpStatus.CONFLICT, "LEGAL_HOLD_NOT_ACTIVE", "Only ACTIVE holds can be released.");
    }
    Instant now = Instant.now();
    entity.release(actorUserId, req.reason(), now);
    repository.save(entity);
    metrics.recordReleased(entity.getScope());
    auditService.record(
        LegalHoldAuditEventType.RELEASED,
        AGGREGATE,
        id,
        Map.of(
            "holdKey",
            entity.getHoldKey(),
            "scope",
            entity.getScope().name(),
            "status",
            LegalHoldStatus.RELEASED.name(),
            "reasonPresent",
            true,
            "actorUserId",
            actorUserId.toString()));
    return toResponse(entity);
  }

  private void validateApiEnabled() {
    if (!properties.adminApiEnabled()) {
      throw new NotificationException(
          HttpStatus.NOT_FOUND, "LEGAL_HOLD_ADMIN_DISABLED", "Legal hold admin API is disabled.");
    }
  }

  private static String validateHoldKey(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST, "LEGAL_HOLD_KEY", "holdKey is required.");
    }
    String k = raw.trim();
    if (k.length() > 200) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST, "LEGAL_HOLD_KEY", "holdKey too long.");
    }
    return k;
  }

  private static String validateReason(String raw, int minLen, String label) {
    if (raw == null || raw.trim().length() < minLen) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST,
          "LEGAL_HOLD_REASON",
          label + " must be at least " + minLen + " characters.");
    }
    return raw.trim();
  }

  private static LegalHoldAdminDtos.LegalHoldResponse toResponse(NotificationLegalHoldEntity e) {
    return new LegalHoldAdminDtos.LegalHoldResponse(
        e.getId(),
        e.getHoldKey(),
        e.getScope().name(),
        e.getStatus().name(),
        e.getCreatedAt(),
        e.getExpiresAt(),
        e.getCreatedByUserId(),
        e.getCreatedByEmail(),
        e.getReleasedAt(),
        e.getReleasedByUserId());
  }
}
