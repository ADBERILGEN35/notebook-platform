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
    if (properties.internal().trustedNotificationClient() == null
        || !properties.internal().trustedNotificationClient().configured()) {
      throw new IllegalStateException(
          "TRUSTED_SERVICE_WORKSPACE_SERVICE_PUBLIC_KEY_PATH is required in prod");
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
