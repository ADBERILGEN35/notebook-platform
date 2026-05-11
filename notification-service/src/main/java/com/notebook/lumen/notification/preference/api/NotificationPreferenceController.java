package com.notebook.lumen.notification.preference.api;

import com.notebook.lumen.notification.preference.application.NotificationPreferenceService;
import com.notebook.lumen.notification.preference.application.NotificationPreferenceService.PreferenceUpdate;
import com.notebook.lumen.notification.preference.domain.NotificationChannel;
import com.notebook.lumen.notification.preference.domain.UserNotificationPreference;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import com.notebook.lumen.notification.shared.web.UserContextResolver;
import com.notebook.lumen.notification.user.domain.UserNotificationType;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notification-preferences")
public class NotificationPreferenceController {
  private final NotificationPreferenceService service;
  private final UserContextResolver userContextResolver;
  private final NotificationProperties properties;

  public NotificationPreferenceController(
      NotificationPreferenceService service,
      UserContextResolver userContextResolver,
      NotificationProperties properties) {
    this.service = service;
    this.userContextResolver = userContextResolver;
    this.properties = properties;
  }

  @GetMapping
  public List<NotificationPreferenceResponse> list(
      @RequestHeader(UserContextResolver.USER_ID_HEADER) String userIdHeader) {
    ensureEnabled();
    UUID userId = requirePreferenceUserId(userIdHeader);
    return toResponse(service.list(userId));
  }

  @PatchMapping
  public List<NotificationPreferenceResponse> patch(
      @RequestHeader(UserContextResolver.USER_ID_HEADER) String userIdHeader,
      @Valid @RequestBody NotificationPreferencePatchRequest request) {
    ensureEnabled();
    UUID userId = requirePreferenceUserId(userIdHeader);
    List<PreferenceUpdate> updates =
        request.updates().stream()
            .map(
                item ->
                    new PreferenceUpdate(
                        item.notificationType(),
                        item.channel(),
                        Boolean.TRUE.equals(item.enabled())))
            .toList();
    return toResponse(service.update(userId, updates));
  }

  private List<NotificationPreferenceResponse> toResponse(List<UserNotificationPreference> prefs) {
    Map<UserNotificationType, Map<NotificationChannel, NotificationPreferenceChannelState>>
        grouped = new EnumMap<>(UserNotificationType.class);
    for (UserNotificationPreference pref : prefs) {
      grouped
          .computeIfAbsent(
              pref.getNotificationType(), ignored -> new EnumMap<>(NotificationChannel.class))
          .put(
              pref.getChannel(),
              new NotificationPreferenceChannelState(pref.isEnabled(), pref.isMandatory()));
    }
    List<NotificationPreferenceResponse> response = new ArrayList<>();
    for (Map.Entry<
            UserNotificationType, Map<NotificationChannel, NotificationPreferenceChannelState>>
        entry : grouped.entrySet()) {
      response.add(
          new NotificationPreferenceResponse(
              entry.getKey(),
              NotificationPreferenceService.labelFor(entry.getKey()),
              NotificationPreferenceService.descriptionFor(entry.getKey()),
              entry.getValue()));
    }
    return response;
  }

  private void ensureEnabled() {
    if (properties != null
        && properties.preferences() != null
        && !properties.preferences().enabled()) {
      throw new NotificationException(
          HttpStatus.NOT_FOUND,
          "NOTIFICATION_PREFERENCE_NOT_FOUND",
          "Notification preferences are disabled");
    }
  }

  private UUID requirePreferenceUserId(String userIdHeader) {
    try {
      return userContextResolver.requireUserId(userIdHeader);
    } catch (NotificationException ex) {
      throw new NotificationException(
          HttpStatus.FORBIDDEN,
          "NOTIFICATION_PREFERENCE_ACCESS_DENIED",
          "Notification preference access denied");
    }
  }
}
