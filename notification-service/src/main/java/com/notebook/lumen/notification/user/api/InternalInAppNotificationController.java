package com.notebook.lumen.notification.user.api;

import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import com.notebook.lumen.notification.shared.security.InternalNotificationAuthorizer;
import com.notebook.lumen.notification.preference.application.NotificationPreferenceService;
import com.notebook.lumen.notification.preference.domain.NotificationChannel;
import com.notebook.lumen.notification.user.application.UserNotificationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/notifications/in-app")
public class InternalInAppNotificationController {
  private final InternalNotificationAuthorizer authorizer;
  private final UserNotificationService notificationService;
  private final NotificationPreferenceService preferenceService;
  private final NotificationProperties properties;

  public InternalInAppNotificationController(
      InternalNotificationAuthorizer authorizer,
      UserNotificationService notificationService,
      NotificationPreferenceService preferenceService,
      NotificationProperties properties) {
    this.authorizer = authorizer;
    this.notificationService = notificationService;
    this.preferenceService = preferenceService;
    this.properties = properties;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public InternalInAppNotificationResponse create(
      @RequestHeader(InternalNotificationAuthorizer.HEADER_NAME) String serviceAuthorization,
      @Valid @RequestBody InAppNotificationCreateRequest request) {
    if (properties.inApp() == null || !properties.inApp().enabled()) {
      throw new NotificationException(
          HttpStatus.NOT_FOUND, "IN_APP_NOTIFICATIONS_DISABLED", "In-app notifications are disabled");
    }
    authorizer.authorize(serviceAuthorization, InternalNotificationAuthorizer.IN_APP_CREATE_SCOPE);
    UUID userId = request.recipientUserId();
    boolean enabled =
        preferenceService.isEnabled(userId, request.type(), NotificationChannel.IN_APP);
    if (!enabled) {
      return new InternalInAppNotificationResponse(null, "SKIPPED", "USER_PREFERENCE_DISABLED");
    }
    return new InternalInAppNotificationResponse(
        notificationService.create(request).getId(), "PENDING", null);
  }
}
