package com.notebook.lumen.notification.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.notification.NotificationTestFanout;
import com.notebook.lumen.notification.analytics.NotificationAnalyticsRecorder;
import com.notebook.lumen.notification.audit.AuditService;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import com.notebook.lumen.notification.user.api.InAppNotificationCreateRequest;
import com.notebook.lumen.notification.user.domain.UserNotification;
import com.notebook.lumen.notification.user.domain.UserNotificationSeverity;
import com.notebook.lumen.notification.user.domain.UserNotificationType;
import com.notebook.lumen.notification.user.fanout.NotificationFanoutOutboxRepository;
import com.notebook.lumen.notification.user.infrastructure.UserNotificationRepository;
import com.notebook.lumen.notification.user.realtime.NotificationSseEventDispatcher;
import com.notebook.lumen.notification.user.realtime.NotificationSseEventEnvelope;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserNotificationServiceTest {
  private final UserNotificationRepository repository = mock(UserNotificationRepository.class);
  private final AuditService auditService = mock(AuditService.class);
  private final NotificationSseEventDispatcher sseDispatcher =
      mock(NotificationSseEventDispatcher.class);
  private final NotificationFanoutOutboxRepository fanoutRepository =
      mock(NotificationFanoutOutboxRepository.class);
  private final NotificationProperties notificationProperties = mock(NotificationProperties.class);
  private final NotificationAnalyticsRecorder analyticsRecorder =
      mock(NotificationAnalyticsRecorder.class);

  private final UserNotificationService service =
      new UserNotificationService(
          repository,
          auditService,
          new SimpleMeterRegistry(),
          sseDispatcher,
          fanoutRepository,
          notificationProperties,
          analyticsRecorder);

  @BeforeEach
  void defaultFanoutOff() {
    when(notificationProperties.fanout()).thenReturn(NotificationTestFanout.disabled());
    when(sseDispatcher.buildCreatedEnvelope(any(), anyLong()))
        .thenAnswer(
            inv -> {
              UserNotification n = inv.getArgument(0);
              long unread = inv.getArgument(1);
              return new NotificationSseEventEnvelope(
                  UUID.randomUUID(),
                  "test",
                  n.getRecipientUserId(),
                  "notification.created",
                  Map.of("notificationId", n.getId().toString(), "unreadCount", unread),
                  Instant.now());
            });
    when(sseDispatcher.buildReadEnvelope(any(), any(), anyLong()))
        .thenAnswer(
            inv ->
                new NotificationSseEventEnvelope(
                    UUID.randomUUID(),
                    "test",
                    inv.getArgument(0),
                    "notification.read",
                    Map.of("unreadCount", inv.getArgument(2)),
                    Instant.now()));
    when(sseDispatcher.buildArchivedEnvelope(any(), any(), anyLong()))
        .thenAnswer(
            inv ->
                new NotificationSseEventEnvelope(
                    UUID.randomUUID(),
                    "test",
                    inv.getArgument(0),
                    "notification.archived",
                    Map.of("unreadCount", inv.getArgument(2)),
                    Instant.now()));
    when(sseDispatcher.buildUnreadCountEnvelope(any(), anyLong()))
        .thenAnswer(
            inv ->
                new NotificationSseEventEnvelope(
                    UUID.randomUUID(),
                    "test",
                    inv.getArgument(0),
                    "notification.unread_count",
                    Map.of("unreadCount", inv.getArgument(1)),
                    Instant.now()));
  }

  @Test
  void duplicateIdempotencyKeyReturnsExisting() {
    UUID userId = UUID.randomUUID();
    UserNotification existing = notification(userId, "dup-key", "/app/notes/1");
    when(repository.findByRecipientUserIdAndIdempotencyKey(userId, "dup-key"))
        .thenReturn(Optional.of(existing));

    UserNotification created = service.create(request(userId, "dup-key", "/app/notes/1"));

    assertThat(created.getId()).isEqualTo(existing.getId());
  }

  @Test
  void rejectsExternalActionUrl() {
    UUID userId = UUID.randomUUID();
    assertThatThrownBy(() -> service.create(request(userId, "k1", "https://example.com")))
        .isInstanceOf(NotificationException.class)
        .extracting("errorCode")
        .isEqualTo("INVALID_NOTIFICATION_ACTION_URL");
  }

  @Test
  void markReadRequiresOwnership() {
    UUID userId = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();
    when(repository.findVisibleOwned(notificationId, userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.markRead(userId, notificationId))
        .isInstanceOf(NotificationException.class)
        .extracting("errorCode")
        .isEqualTo("USER_NOTIFICATION_NOT_FOUND");
  }

  @Test
  void createSanitizesSensitiveMetadata() {
    UUID userId = UUID.randomUUID();
    when(repository.save(any()))
        .thenAnswer(invocation -> invocation.getArgument(0, UserNotification.class));

    UserNotification created =
        service.create(
            new InAppNotificationCreateRequest(
                userId,
                null,
                UserNotificationType.SYSTEM_NOTICE,
                "Notice",
                "Message",
                UserNotificationSeverity.INFO,
                "/app/settings/security",
                Map.of("token", "secret-token-value"),
                "k2"));

    assertThat(created.getMetadata()).containsKey("token");
    assertThat(String.valueOf(created.getMetadata().get("token"))).contains("***");
  }

  @Test
  void fanoutOutboxPersistsWhenEnabled() {
    when(notificationProperties.fanout())
        .thenReturn(
            new NotificationProperties.Fanout(true, true, true, 5, 100, 10, 5, 300, 60, 24, 30));
    when(repository.save(any()))
        .thenAnswer(invocation -> invocation.getArgument(0, UserNotification.class));
    UUID userId = UUID.randomUUID();
    service.create(
        new InAppNotificationCreateRequest(
            userId,
            null,
            UserNotificationType.SYSTEM_NOTICE,
            "Notice",
            "Message",
            UserNotificationSeverity.INFO,
            "/app/settings/security",
            Map.of(),
            "fanout-key-1"));

    verify(fanoutRepository).save(any());
    verify(sseDispatcher).dispatchAllowDistributedFailure(any());
  }

  @Test
  void fanoutImmediateOffSkipsDispatchButSavesOutbox() {
    when(notificationProperties.fanout())
        .thenReturn(
            new NotificationProperties.Fanout(true, true, false, 5, 100, 10, 5, 300, 60, 24, 30));
    when(repository.save(any()))
        .thenAnswer(invocation -> invocation.getArgument(0, UserNotification.class));
    UUID userId = UUID.randomUUID();
    service.create(
        new InAppNotificationCreateRequest(
            userId,
            null,
            UserNotificationType.SYSTEM_NOTICE,
            "Notice",
            "Message",
            UserNotificationSeverity.INFO,
            "/app/settings/security",
            Map.of(),
            "fanout-key-2"));

    verify(fanoutRepository).save(any());
    verify(sseDispatcher, never()).dispatchAllowDistributedFailure(any());
  }

  private InAppNotificationCreateRequest request(UUID userId, String key, String actionUrl) {
    return new InAppNotificationCreateRequest(
        userId,
        null,
        UserNotificationType.SECURITY_SESSIONS_REVOKED,
        "Title",
        "Message",
        UserNotificationSeverity.WARNING,
        actionUrl,
        Map.of("noteId", UUID.randomUUID().toString()),
        key);
  }

  private UserNotification notification(UUID userId, String key, String actionUrl) {
    return new UserNotification(
        UUID.randomUUID(),
        userId,
        null,
        UserNotificationType.SYSTEM_NOTICE,
        "title",
        "message",
        UserNotificationSeverity.INFO,
        actionUrl,
        Map.of(),
        key,
        Instant.now());
  }
}
