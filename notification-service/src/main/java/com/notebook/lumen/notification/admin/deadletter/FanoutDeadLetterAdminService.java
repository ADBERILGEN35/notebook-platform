package com.notebook.lumen.notification.admin.deadletter;

import com.notebook.lumen.notification.audit.AuditService;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import com.notebook.lumen.notification.user.fanout.NotificationFanoutOutbox;
import com.notebook.lumen.notification.user.fanout.NotificationFanoutOutboxRepository;
import com.notebook.lumen.notification.user.fanout.NotificationFanoutOutboxStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FanoutDeadLetterAdminService {

  static final String RESULT_SUCCESS = "SUCCESS";
  private static final String SOURCE_NAME = DeadLetterSource.FANOUT_OUTBOX.name();

  private final NotificationFanoutOutboxRepository outboxRepository;
  private final DeadLetterRequeueRequestRepository requeueRequestRepository;
  private final NotificationDeadLetterProperties deadLetterProperties;
  private final AuditService auditService;

  public FanoutDeadLetterAdminService(
      NotificationFanoutOutboxRepository outboxRepository,
      DeadLetterRequeueRequestRepository requeueRequestRepository,
      NotificationDeadLetterProperties deadLetterProperties,
      AuditService auditService) {
    this.outboxRepository = outboxRepository;
    this.requeueRequestRepository = requeueRequestRepository;
    this.deadLetterProperties = deadLetterProperties;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public DeadLetterAdminDtos.DeadLetterListResponse list(
      String eventType,
      Instant createdFrom,
      Instant createdTo,
      int page,
      int size,
      String sortParam) {
    int capped = Math.min(Math.max(size, 1), deadLetterProperties.pageMaxSize());
    Sort sort = parseSort(sortParam);
    Specification<NotificationFanoutOutbox> spec =
        deadFanoutSpec(eventType, createdFrom, createdTo);
    Page<NotificationFanoutOutbox> result =
        outboxRepository.findAll(spec, PageRequest.of(Math.max(page, 0), capped, sort));
    List<DeadLetterAdminDtos.DeadLetterListItemDto> items =
        result.getContent().stream().map(this::toListItem).toList();
    return new DeadLetterAdminDtos.DeadLetterListResponse(
        items, result.getNumber(), result.getSize(), result.getTotalElements());
  }

  private Sort parseSort(String sortParam) {
    if (sortParam == null || sortParam.isBlank()) {
      return Sort.by(Sort.Direction.DESC, "createdAt");
    }
    String[] parts = sortParam.split(",", 2);
    String field = parts[0].trim();
    Sort.Direction dir =
        parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
    if (!List.of("createdAt", "deadAt", "eventType", "attemptCount", "requeueCount")
        .contains(field)) {
      return Sort.by(Sort.Direction.DESC, "createdAt");
    }
    return Sort.by(dir, field);
  }

  private Specification<NotificationFanoutOutbox> deadFanoutSpec(
      String eventType, Instant createdFrom, Instant createdTo) {
    return (root, query, cb) -> {
      List<Predicate> ps = new ArrayList<>();
      ps.add(cb.equal(root.get("status"), NotificationFanoutOutboxStatus.DEAD));
      if (eventType != null && !eventType.isBlank()) {
        ps.add(cb.equal(root.get("eventType"), eventType.trim()));
      }
      if (createdFrom != null) {
        ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
      }
      if (createdTo != null) {
        ps.add(cb.lessThan(root.get("createdAt"), createdTo));
      }
      return cb.and(ps.toArray(Predicate[]::new));
    };
  }

  private DeadLetterAdminDtos.DeadLetterListItemDto toListItem(NotificationFanoutOutbox o) {
    String pepper = deadLetterProperties.recipientHashPepper();
    return new DeadLetterAdminDtos.DeadLetterListItemDto(
        o.getId().toString(),
        SOURCE_NAME,
        o.getEventType(),
        DeadLetterRecipientHasher.hash(o.getRecipientUserId(), pepper),
        o.getStatus().name(),
        o.getAttemptCount(),
        o.getRequeueCount(),
        DeadLetterErrorFormatter.lastErrorCode(o.getLastError()),
        DeadLetterErrorFormatter.lastErrorSummary(o.getLastError()),
        o.getCreatedAt(),
        lastUpdated(o),
        o.getDeadAt());
  }

  /** Best-effort "updated" for UI: deadAt or lastRequeuedAt or createdAt. */
  private Instant lastUpdated(NotificationFanoutOutbox o) {
    if (o.getDeadAt() != null) {
      return o.getDeadAt();
    }
    if (o.getLastRequeuedAt() != null) {
      return o.getLastRequeuedAt();
    }
    return o.getCreatedAt();
  }

  @Transactional(readOnly = true)
  public DeadLetterAdminDtos.RequeueDryRunResponse dryRun(UUID id) {
    NotificationFanoutOutbox row =
        outboxRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new NotificationException(
                        HttpStatus.NOT_FOUND,
                        "DEAD_LETTER_NOT_FOUND",
                        "Dead-letter row not found"));
    List<DeadLetterAdminDtos.RequeueDryRunCheck> checks = new ArrayList<>();
    boolean statusDead = row.getStatus() == NotificationFanoutOutboxStatus.DEAD;
    checks.add(new DeadLetterAdminDtos.RequeueDryRunCheck("STATUS_DEAD", statusDead));
    boolean underLimit = row.getRequeueCount() < deadLetterProperties.maxRequeueCount();
    checks.add(new DeadLetterAdminDtos.RequeueDryRunCheck("REQUEUE_LIMIT_OK", underLimit));
    boolean can = statusDead && underLimit;
    var impact =
        new DeadLetterAdminDtos.RequeueDryRunImpact(
            "MEDIUM",
            "LOW",
            "SSE fanout events are idempotent acceleration; clients should tolerate duplicates.");
    auditService.record(
        DeadLetterAuditEventType.REQUEUE_DRY_RUN,
        "NOTIFICATION_FANOUT_OUTBOX",
        id,
        Map.of(
            "source", SOURCE_NAME,
            "eventType", row.getEventType(),
            "attemptCount", row.getAttemptCount(),
            "requeueCount", row.getRequeueCount(),
            "canRequeue", can));
    return new DeadLetterAdminDtos.RequeueDryRunResponse(
        id.toString(), can, SOURCE_NAME, impact, checks);
  }

  @Transactional
  public DeadLetterAdminDtos.FanoutRequeueResponse requeue(
      UUID id, String idempotencyKey, String reason, String actorUserId) {
    validateReason(reason);
    validateIdempotencyKey(idempotencyKey);
    if (actorUserId == null || actorUserId.isBlank()) {
      auditService.record(
          DeadLetterAuditEventType.REQUEUE_DENIED,
          "NOTIFICATION_FANOUT_OUTBOX",
          id,
          Map.of("source", SOURCE_NAME, "reasonPresent", true, "code", "ACTOR_REQUIRED"));
      throw new NotificationException(
          HttpStatus.BAD_REQUEST, "DEAD_LETTER_ACTOR_REQUIRED", "Admin actor header is required");
    }

    NotificationFanoutOutbox row =
        outboxRepository
            .findByIdForUpdate(id)
            .orElseThrow(
                () ->
                    new NotificationException(
                        HttpStatus.NOT_FOUND,
                        "DEAD_LETTER_NOT_FOUND",
                        "Dead-letter row not found"));

    var idem =
        requeueRequestRepository.findBySourceAndDeadLetterIdAndIdempotencyKey(
            SOURCE_NAME, id, idempotencyKey.trim());
    if (idem.isPresent() && RESULT_SUCCESS.equals(idem.get().getResultStatus())) {
      return new DeadLetterAdminDtos.FanoutRequeueResponse(
          id.toString(),
          SOURCE_NAME,
          row.getStatus().name(),
          row.getAttemptCount(),
          row.getRequeueCount(),
          row.getNextAttemptAt(),
          true);
    }

    if (row.getStatus() != NotificationFanoutOutboxStatus.DEAD) {
      auditDenied(id, row, "NOT_DEAD", actorUserId);
      throw new NotificationException(
          HttpStatus.CONFLICT, "DEAD_LETTER_NOT_DEAD", "Only DEAD rows can be requeued");
    }
    if (row.getRequeueCount() >= deadLetterProperties.maxRequeueCount()) {
      auditDenied(id, row, "REQUEUE_LIMIT", actorUserId);
      throw new NotificationException(
          HttpStatus.CONFLICT,
          "DEAD_LETTER_REQUEUE_LIMIT",
          "Max admin requeue count exceeded for this row");
    }

    Instant now = Instant.now();
    row.requeueFromDead(now, actorUserId);
    outboxRepository.save(row);
    requeueRequestRepository.save(
        new DeadLetterRequeueRequestEntity(
            UUID.randomUUID(),
            SOURCE_NAME,
            id,
            idempotencyKey.trim(),
            actorUserId,
            RESULT_SUCCESS,
            now));

    auditService.record(
        DeadLetterAuditEventType.REQUEUED,
        "NOTIFICATION_FANOUT_OUTBOX",
        id,
        Map.of(
            "source",
            SOURCE_NAME,
            "eventType",
            row.getEventType(),
            "attemptCount",
            row.getAttemptCount(),
            "requeueCount",
            row.getRequeueCount(),
            "reasonPresent",
            true,
            "actorUserId",
            actorUserId));

    return new DeadLetterAdminDtos.FanoutRequeueResponse(
        id.toString(),
        SOURCE_NAME,
        row.getStatus().name(),
        row.getAttemptCount(),
        row.getRequeueCount(),
        row.getNextAttemptAt(),
        false);
  }

  private void auditDenied(UUID id, NotificationFanoutOutbox row, String code, String actorUserId) {
    auditService.record(
        DeadLetterAuditEventType.REQUEUE_DENIED,
        "NOTIFICATION_FANOUT_OUTBOX",
        id,
        Map.of(
            "source", SOURCE_NAME,
            "code", code,
            "eventType", row.getEventType(),
            "status", row.getStatus().name(),
            "attemptCount", row.getAttemptCount(),
            "requeueCount", row.getRequeueCount(),
            "actorUserId", actorUserId));
  }

  /**
   * @return wrapper if idempotent replay should short-circuit; {@code null} if caller must proceed
   *     under lock.
   */
  private static void validateReason(String reason) {
    if (reason == null || reason.isBlank()) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST, "DEAD_LETTER_REASON_REQUIRED", "Requeue reason is required");
    }
    String t = reason.trim();
    if (t.length() < 5) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST,
          "DEAD_LETTER_REASON_TOO_SHORT",
          "Reason must be at least 5 characters");
    }
    if (t.length() > 2000) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST, "DEAD_LETTER_REASON_TOO_LONG", "Reason exceeds max length");
    }
  }

  private static void validateIdempotencyKey(String key) {
    if (key == null || key.isBlank()) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST, "DEAD_LETTER_IDEMPOTENCY_REQUIRED", "idempotencyKey is required");
    }
    String t = key.trim();
    if (t.length() > 128) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST, "DEAD_LETTER_IDEMPOTENCY_INVALID", "idempotencyKey too long");
    }
  }

  public void auditListViewed(int resultCount) {
    auditService.record(
        DeadLetterAuditEventType.VIEWED,
        "NOTIFICATION_FANOUT_OUTBOX",
        null,
        Map.of("source", SOURCE_NAME, "resultCount", resultCount));
  }
}
