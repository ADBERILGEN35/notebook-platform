package com.notebook.lumen.notification.user.api;

import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import com.notebook.lumen.notification.shared.web.UserContextResolver;
import com.notebook.lumen.notification.user.application.UserNotificationService;
import com.notebook.lumen.notification.user.domain.UserNotificationType;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
public class UserNotificationController {
  private final UserContextResolver userContextResolver;
  private final UserNotificationService notificationService;
  private final NotificationProperties properties;

  public UserNotificationController(
      UserContextResolver userContextResolver,
      UserNotificationService notificationService,
      NotificationProperties properties) {
    this.userContextResolver = userContextResolver;
    this.notificationService = notificationService;
    this.properties = properties;
  }

  @GetMapping
  public UserNotificationPageResponse list(
      @RequestHeader(UserContextResolver.USER_ID_HEADER) String userIdHeader,
      @RequestParam(defaultValue = "false") boolean unreadOnly,
      @RequestParam(required = false) UserNotificationType type,
      @RequestParam(required = false) UUID workspaceId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "createdAt,desc") String sort) {
    ensureEnabled();
    UUID userId = userContextResolver.requireUserId(userIdHeader);
    var notifications =
        notificationService.list(userId, unreadOnly, type, workspaceId, page, size, sort);
    return new UserNotificationPageResponse(
        notifications.map(UserNotificationResponse::from).toList(),
        notifications.getNumber(),
        notifications.getSize(),
        notifications.getTotalElements(),
        notifications.getTotalPages(),
        notifications.hasNext(),
        notifications.hasPrevious());
  }

  @GetMapping("/unread-count")
  public UserUnreadCountResponse unreadCount(
      @RequestHeader(UserContextResolver.USER_ID_HEADER) String userIdHeader) {
    ensureEnabled();
    UUID userId = userContextResolver.requireUserId(userIdHeader);
    return new UserUnreadCountResponse(notificationService.unreadCount(userId));
  }

  @PostMapping("/{notificationId}/read")
  public UserNotificationResponse markRead(
      @RequestHeader(UserContextResolver.USER_ID_HEADER) String userIdHeader,
      @PathVariable UUID notificationId) {
    ensureEnabled();
    UUID userId = userContextResolver.requireUserId(userIdHeader);
    return UserNotificationResponse.from(notificationService.markRead(userId, notificationId));
  }

  @PostMapping("/read-all")
  public java.util.Map<String, Integer> markReadAll(
      @RequestHeader(UserContextResolver.USER_ID_HEADER) String userIdHeader,
      @RequestBody(required = false) ReadAllNotificationsRequest request) {
    ensureEnabled();
    UUID userId = userContextResolver.requireUserId(userIdHeader);
    int changed = notificationService.markReadAll(userId, request == null ? null : request.workspaceId());
    return java.util.Map.of("updatedCount", changed);
  }

  @PostMapping("/{notificationId}/archive")
  public UserNotificationResponse archive(
      @RequestHeader(UserContextResolver.USER_ID_HEADER) String userIdHeader,
      @PathVariable UUID notificationId) {
    ensureEnabled();
    UUID userId = userContextResolver.requireUserId(userIdHeader);
    return UserNotificationResponse.from(notificationService.archive(userId, notificationId));
  }

  private void ensureEnabled() {
    if (properties.inApp() == null || !properties.inApp().enabled()) {
      throw new NotificationException(
          HttpStatus.NOT_FOUND, "IN_APP_NOTIFICATIONS_DISABLED", "In-app notifications are disabled");
    }
  }
}
