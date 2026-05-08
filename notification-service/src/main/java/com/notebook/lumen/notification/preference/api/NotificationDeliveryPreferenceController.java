package com.notebook.lumen.notification.preference.api;

import com.notebook.lumen.notification.preference.application.NotificationDeliveryPreferenceService;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import com.notebook.lumen.notification.shared.web.UserContextResolver;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notification-delivery-preferences")
public class NotificationDeliveryPreferenceController {
  private final NotificationDeliveryPreferenceService service;
  private final UserContextResolver userContextResolver;
  private final NotificationProperties properties;

  public NotificationDeliveryPreferenceController(
      NotificationDeliveryPreferenceService service,
      UserContextResolver userContextResolver,
      NotificationProperties properties) {
    this.service = service;
    this.userContextResolver = userContextResolver;
    this.properties = properties;
  }

  @GetMapping
  public NotificationDeliveryPreferenceResponse get(
      @RequestHeader(UserContextResolver.USER_ID_HEADER) String userIdHeader) {
    ensureEnabled();
    var pref = service.get(requireUserId(userIdHeader));
    return toResponse(pref);
  }

  @PatchMapping
  public NotificationDeliveryPreferenceResponse patch(
      @RequestHeader(UserContextResolver.USER_ID_HEADER) String userIdHeader,
      @Valid @RequestBody NotificationDeliveryPreferenceRequest request) {
    ensureEnabled();
    var updated =
        service.update(
            requireUserId(userIdHeader),
            request.emailDigestEnabled(),
            request.emailDigestFrequency(),
            request.quietHoursEnabled(),
            request.quietHoursStart(),
            request.quietHoursEnd(),
            request.timezone());
    return toResponse(updated);
  }

  private NotificationDeliveryPreferenceResponse toResponse(
      com.notebook.lumen.notification.preference.domain.UserNotificationDeliveryPreference pref) {
    return new NotificationDeliveryPreferenceResponse(
        pref.isEmailDigestEnabled(),
        pref.getEmailDigestFrequency(),
        pref.isQuietHoursEnabled(),
        pref.getQuietHoursStart(),
        pref.getQuietHoursEnd(),
        pref.getTimezone());
  }

  private UUID requireUserId(String userIdHeader) {
    try {
      return userContextResolver.requireUserId(userIdHeader);
    } catch (NotificationException ex) {
      throw new NotificationException(
          HttpStatus.FORBIDDEN,
          "NOTIFICATION_PREFERENCE_ACCESS_DENIED",
          "Notification preference access denied");
    }
  }

  private void ensureEnabled() {
    if (properties != null && properties.preferences() != null && !properties.preferences().enabled()) {
      throw new NotificationException(
          HttpStatus.NOT_FOUND,
          "NOTIFICATION_PREFERENCE_NOT_FOUND",
          "Notification preferences are disabled");
    }
  }
}
