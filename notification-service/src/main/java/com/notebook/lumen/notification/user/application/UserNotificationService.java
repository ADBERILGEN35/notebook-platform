package com.notebook.lumen.notification.user.application;

import com.notebook.lumen.common.security.sanitization.SensitiveDataSanitizer;
import com.notebook.lumen.notification.audit.AuditService;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import com.notebook.lumen.notification.user.api.InAppNotificationCreateRequest;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.user.fanout.NotificationFanoutOutbox;
import com.notebook.lumen.notification.user.fanout.NotificationFanoutOutboxRepository;
import com.notebook.lumen.notification.user.realtime.NotificationSseEventDispatcher;
import com.notebook.lumen.notification.user.realtime.NotificationSseEventEnvelope;
import com.notebook.lumen.notification.user.domain.UserNotification;
import com.notebook.lumen.notification.user.domain.UserNotificationSeverity;
import com.notebook.lumen.notification.user.domain.UserNotificationType;
import com.notebook.lumen.notification.user.infrastructure.UserNotificationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserNotificationService {
  private static final Logger log = LoggerFactory.getLogger(UserNotificationService.class);

  private final UserNotificationRepository repository;
  private final AuditService auditService;
  private final MeterRegistry meterRegistry;
  private final NotificationSseEventDispatcher sseDispatcher;
  private final NotificationFanoutOutboxRepository fanoutOutboxRepository;
  private final NotificationProperties notificationProperties;

  public UserNotificationService(
      UserNotificationRepository repository,
      AuditService auditService,
      MeterRegistry meterRegistry,
      NotificationSseEventDispatcher sseDispatcher,
      NotificationFanoutOutboxRepository fanoutOutboxRepository,
      NotificationProperties notificationProperties) {
    this.repository = repository;
    this.auditService = auditService;
    this.meterRegistry = meterRegistry;
    this.sseDispatcher = sseDispatcher;
    this.fanoutOutboxRepository = fanoutOutboxRepository;
    this.notificationProperties = notificationProperties;
  }

  @Transactional
  public UserNotification create(InAppNotificationCreateRequest request) {
    validateActionUrl(request.actionUrl());
    String idempotencyKey = normalizeNullable(request.idempotencyKey());
    if (idempotencyKey != null) {
      var existing =
          repository.findByRecipientUserIdAndIdempotencyKey(request.recipientUserId(), idempotencyKey);
      if (existing.isPresent()) {
        return existing.get();
      }
    }
    Instant now = Instant.now();
    UserNotification notification =
        repository.save(
            new UserNotification(
                UUID.randomUUID(),
                request.recipientUserId(),
                request.workspaceId(),
                request.type(),
                truncate(request.title(), 200),
                truncate(request.message(), 4000),
                request.severity(),
                normalizeNullable(request.actionUrl()),
                sanitizeMetadata(request.metadata()),
                idempotencyKey,
                now));
    meterRegistry
        .counter(
            "user_notifications_created_total",
            "type",
            notification.getType().name(),
            "severity",
            notification.getSeverity().name())
        .increment();
    meterRegistry.counter("user_notifications_unread_count_query_total", "trigger", "create").increment();
    auditService.record(
        "USER_NOTIFICATION_CREATED",
        "USER_NOTIFICATION",
        notification.getId(),
        Map.of(
            "recipientUserId",
            notification.getRecipientUserId().toString(),
            "type",
            notification.getType().name(),
            "severity",
            notification.getSeverity().name()));
    var envelope =
        sseDispatcher.buildCreatedEnvelope(notification, unreadCount(notification.getRecipientUserId()));
    publishFanout(envelope);
    return notification;
  }

  @Transactional(readOnly = true)
  public Page<UserNotification> list(
      UUID recipientUserId,
      boolean unreadOnly,
      UserNotificationType type,
      UUID workspaceId,
      int page,
      int size,
      String sort) {
    PageRequest pageable =
        PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), 100),
            toSort(sort == null || sort.isBlank() ? "createdAt,desc" : sort));
    return repository.findVisibleForRecipient(recipientUserId, unreadOnly, type, workspaceId, pageable);
  }

  @Transactional(readOnly = true)
  public long unreadCount(UUID recipientUserId) {
    meterRegistry.counter("user_notifications_unread_count_query_total", "trigger", "api").increment();
    return repository.countUnread(recipientUserId);
  }

  @Transactional
  public UserNotification markRead(UUID recipientUserId, UUID notificationId) {
    UserNotification notification = owned(notificationId, recipientUserId);
    notification.markRead(Instant.now());
    meterRegistry.counter("user_notifications_read_total", "type", notification.getType().name()).increment();
    auditService.record(
        "USER_NOTIFICATION_READ",
        "USER_NOTIFICATION",
        notification.getId(),
        Map.of(
            "recipientUserId",
            recipientUserId.toString(),
            "type",
            notification.getType().name(),
            "severity",
            notification.getSeverity().name()));
    var envelope =
        sseDispatcher.buildReadEnvelope(
            recipientUserId, notificationId, unreadCount(recipientUserId));
    publishFanout(envelope);
    return notification;
  }

  @Transactional
  public int markReadAll(UUID recipientUserId, UUID workspaceId) {
    int changed = 0;
    Instant now = Instant.now();
    for (UserNotification notification : repository.findVisibleOwnedForReadAll(recipientUserId, workspaceId)) {
      if (notification.getReadAt() == null) {
        notification.markRead(now);
        changed++;
        meterRegistry
            .counter("user_notifications_read_total", "type", notification.getType().name())
            .increment();
      }
    }
    auditService.record(
        "USER_NOTIFICATIONS_READ_ALL",
        "USER_NOTIFICATION",
        recipientUserId,
        Map.of("recipientUserId", recipientUserId.toString(), "changedCount", changed));
    var envelope =
        sseDispatcher.buildUnreadCountEnvelope(recipientUserId, unreadCount(recipientUserId));
    publishFanout(envelope);
    return changed;
  }

  @Transactional
  public UserNotification archive(UUID recipientUserId, UUID notificationId) {
    UserNotification notification = owned(notificationId, recipientUserId);
    notification.archive(Instant.now());
    meterRegistry
        .counter("user_notifications_archived_total", "type", notification.getType().name())
        .increment();
    auditService.record(
        "USER_NOTIFICATION_ARCHIVED",
        "USER_NOTIFICATION",
        notification.getId(),
        Map.of(
            "recipientUserId",
            recipientUserId.toString(),
            "type",
            notification.getType().name(),
            "severity",
            notification.getSeverity().name()));
    var envelope =
        sseDispatcher.buildArchivedEnvelope(
            recipientUserId, notificationId, unreadCount(recipientUserId));
    publishFanout(envelope);
    return notification;
  }

  private void publishFanout(NotificationSseEventEnvelope envelope) {
    NotificationProperties.Fanout fanout = notificationProperties.fanout();
    if (fanout.outboxEnabled()) {
      fanoutOutboxRepository.save(
          new NotificationFanoutOutbox(
              UUID.randomUUID(),
              envelope.eventId(),
              envelope.recipientUserId(),
              envelope.eventType(),
              new LinkedHashMap<>(envelope.payload()),
              envelope.createdAt()));
    }
    boolean dispatchNow = !fanout.outboxEnabled() || fanout.immediateLocalDelivery();
    if (!dispatchNow) {
      return;
    }
    try {
      sseDispatcher.dispatchAllowDistributedFailure(envelope);
    } catch (RuntimeException e) {
      if (fanout.outboxEnabled()) {
        log.warn(
            "Immediate notification fanout failed; durable outbox will retry eventId={}",
            envelope.eventId(),
            e);
        meterRegistry.counter("notifications_fanout_immediate_failures_total").increment();
        return;
      }
      throw e;
    }
  }

  public UserNotification createSecuritySessionsRevoked(UUID recipientUserId) {
    return create(
        new InAppNotificationCreateRequest(
            recipientUserId,
            null,
            UserNotificationType.SECURITY_SESSIONS_REVOKED,
            "Security notice",
            "All active sessions were revoked. Re-login required on other devices.",
            UserNotificationSeverity.WARNING,
            "/app/settings/security",
            Map.of("source", "identity-revoke-all"),
            "security-revoke-all:" + recipientUserId + ":" + Instant.now().getEpochSecond() / 60));
  }

  private UserNotification owned(UUID notificationId, UUID recipientUserId) {
    return repository
        .findVisibleOwned(notificationId, recipientUserId)
        .orElseThrow(
            () ->
                new NotificationException(
                    HttpStatus.NOT_FOUND,
                    "USER_NOTIFICATION_NOT_FOUND",
                    "User notification not found"));
  }

  private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return Map.of();
    }
    return SensitiveDataSanitizer.sanitizeMetadata(metadata);
  }

  private String normalizeNullable(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String truncate(String value, int maxLen) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
  }

  private void validateActionUrl(String actionUrl) {
    if (actionUrl == null || actionUrl.isBlank()) {
      return;
    }
    String normalized = actionUrl.trim().toLowerCase(java.util.Locale.ROOT);
    if (normalized.startsWith("http://")
        || normalized.startsWith("https://")
        || normalized.startsWith("javascript:")
        || !normalized.startsWith("/app/")) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST,
          "INVALID_NOTIFICATION_ACTION_URL",
          "Notification actionUrl must be internal /app/* path");
    }
  }

  private Sort toSort(String sortRaw) {
    String[] parts = sortRaw.split(",");
    if (parts.length != 2) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST, "INVALID_USER_NOTIFICATION_REQUEST", "Invalid sort parameter");
    }
    String field = parts[0].trim();
    String direction = parts[1].trim().toLowerCase(java.util.Locale.ROOT);
    if (!field.equals("createdAt")) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST,
          "INVALID_USER_NOTIFICATION_REQUEST",
          "Unsupported sort field");
    }
    Sort.Direction dir =
        switch (direction) {
          case "asc" -> Sort.Direction.ASC;
          case "desc" -> Sort.Direction.DESC;
          default ->
              throw new NotificationException(
                  HttpStatus.BAD_REQUEST,
                  "INVALID_USER_NOTIFICATION_REQUEST",
                  "Invalid sort direction");
        };
    return Sort.by(dir, "createdAt");
  }
}
