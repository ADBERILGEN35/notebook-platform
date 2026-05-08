package com.notebook.lumen.notification.admin;

import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.shared.config.NotificationSseProperties;
import org.springframework.stereotype.Service;

@Service
public class NotificationAdminStatusService {
  private final NotificationProperties notificationProperties;
  private final NotificationSseProperties sseProperties;

  public NotificationAdminStatusService(
      NotificationProperties notificationProperties, NotificationSseProperties sseProperties) {
    this.notificationProperties = notificationProperties;
    this.sseProperties = sseProperties;
  }

  public NotificationAdminStatusResponse build() {
    var email = notificationProperties.email();
    String provider = email.provider() == null ? "log" : email.provider().trim();
    boolean providerConfigured = emailProviderConfigured(provider, email);
    var inApp = new NotificationAdminStatusResponse.InApp(notificationProperties.inApp().enabled());
    boolean distEnabled = sseProperties.getDistributed().isEnabled();
    String channel = sseProperties.getDistributed().getChannel();
    boolean redisChannelConfigured =
        distEnabled && channel != null && !channel.isBlank();
    var sse =
        new NotificationAdminStatusResponse.Sse(
            sseProperties.isEnabled(), distEnabled, redisChannelConfigured);
    var digest = notificationProperties.digest();
    var digestBlock =
        new NotificationAdminStatusResponse.Digest(digest.enabled(), digest.workerEnabled());
    var emailBlock = new NotificationAdminStatusResponse.Email(provider, providerConfigured);
    var webhookBlock =
        new NotificationAdminStatusResponse.Webhook(email.webhooks().enabled());
    var suppression = new NotificationAdminStatusResponse.Suppression(true);
    return new NotificationAdminStatusResponse(
        inApp, sse, digestBlock, emailBlock, webhookBlock, suppression, false, null);
  }

  private static boolean emailProviderConfigured(String provider, NotificationProperties.Email email) {
    return switch (provider.toLowerCase()) {
      case "log", "noop" -> true;
      case "smtp" -> email.smtp() != null
          && email.smtp().host() != null
          && !email.smtp().host().isBlank();
      case "generic-http", "sendgrid" -> email.genericHttp() != null
          && email.genericHttp().url() != null
          && !email.genericHttp().url().isBlank();
      default -> false;
    };
  }
}
