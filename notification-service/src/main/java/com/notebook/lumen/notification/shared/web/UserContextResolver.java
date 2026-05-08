package com.notebook.lumen.notification.shared.web;

import com.notebook.lumen.notification.shared.exception.NotificationException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class UserContextResolver {
  public static final String USER_ID_HEADER = "X-User-Id";

  public UUID requireUserId(String headerValue) {
    if (headerValue == null || headerValue.isBlank()) {
      throw new NotificationException(
          HttpStatus.UNAUTHORIZED, "NOTIFICATION_ACCESS_DENIED", "User context is required");
    }
    try {
      return UUID.fromString(headerValue.trim());
    } catch (IllegalArgumentException e) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST, "INVALID_USER_NOTIFICATION_REQUEST", "Invalid user context");
    }
  }
}
