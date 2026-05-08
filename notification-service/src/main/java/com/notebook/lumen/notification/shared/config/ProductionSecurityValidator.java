package com.notebook.lumen.notification.shared.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProductionSecurityValidator implements ApplicationRunner {
  private final NotificationProperties properties;
  private final Environment environment;

  public ProductionSecurityValidator(NotificationProperties properties, Environment environment) {
    this.properties = properties;
    this.environment = environment;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!environment.matchesProfiles("prod")) {
      return;
    }
    String provider = properties.email().provider();
    if ("log".equalsIgnoreCase(provider) || "noop".equalsIgnoreCase(provider)) {
      throw new IllegalStateException("EMAIL_PROVIDER=log/noop is not allowed in prod");
    }
    if ("smtp".equalsIgnoreCase(provider) && isBlank(properties.email().smtp().password())) {
      throw new IllegalStateException("SMTP_PASSWORD is required when EMAIL_PROVIDER=smtp in prod");
    }
    if (("generic-http".equalsIgnoreCase(provider) || "sendgrid".equalsIgnoreCase(provider))
        && (isBlank(properties.email().genericHttp().url())
            || isBlank(properties.email().genericHttp().apiKey()))) {
      throw new IllegalStateException(
          "EMAIL_GENERIC_HTTP_URL and EMAIL_GENERIC_HTTP_API_KEY are required for HTTP email provider in prod");
    }
    if (properties.email().webhooks().enabled()
        && isBlank(properties.email().webhooks().secret())) {
      throw new IllegalStateException("EMAIL_WEBHOOK_SECRET is required when webhooks are enabled");
    }
    if (properties.email().webhooks().enabled()
        && !properties.email().webhooks().requireTimestamp()) {
      throw new IllegalStateException(
          "EMAIL_WEBHOOK_REQUIRE_TIMESTAMP=true is required when webhooks are enabled in prod");
    }
    if (properties.internal().trustedNotificationClient() == null
        || !properties.internal().trustedNotificationClient().configured()) {
      throw new IllegalStateException(
          "TRUSTED_SERVICE_WORKSPACE_SERVICE_PUBLIC_KEY_PATH is required in prod");
    }
    if (properties.workspace() != null && properties.workspace().preferencesEnabled()) {
      if (isBlank(properties.workspace().serviceUrl())) {
        throw new IllegalStateException(
            "WORKSPACE_SERVICE_URL is required when WORKSPACE_NOTIFICATION_PREFERENCES_ENABLED=true in prod");
      }
      var outbound = properties.workspace().serviceJwt();
      if (outbound == null || !outbound.signingConfigured()) {
        throw new IllegalStateException(
            "NOTIFICATION_WORKSPACE_CLIENT_JWT signing key is required when WORKSPACE_NOTIFICATION_PREFERENCES_ENABLED=true in prod");
      }
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
