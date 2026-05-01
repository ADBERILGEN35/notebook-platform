package com.notebook.lumen.content.config;

import com.notebook.lumen.common.security.servicejwt.InternalAuthMode;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class ProductionSecurityValidator implements ApplicationRunner {
  private final ContentProperties properties;
  private final Environment environment;

  public ProductionSecurityValidator(ContentProperties properties, Environment environment) {
    this.properties = properties;
    this.environment = environment;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!environment.acceptsProfiles(Profiles.of("prod"))) {
      return;
    }
    InternalAuthMode mode = InternalAuthMode.parse(properties.workspace().internalAuthMode());
    boolean signingConfigured =
        properties.serviceJwt() != null && properties.serviceJwt().signingConfigured();
    if (mode == InternalAuthMode.SERVICE_JWT && !signingConfigured) {
      throw new IllegalStateException(
          "INTERNAL_SERVICE_JWT_PRIVATE_KEY_PATH is required in service-jwt mode.");
    }
    if (mode == InternalAuthMode.STATIC_TOKEN && !properties.workspace().primaryTokenConfigured()) {
      throw new IllegalStateException(
          "WORKSPACE_INTERNAL_API_TOKEN_PRIMARY is required when content-service runs with the prod profile.");
    }
    if (mode == InternalAuthMode.DUAL
        && !signingConfigured
        && !properties.workspace().primaryTokenConfigured()) {
      throw new IllegalStateException(
          "Either service JWT signing config or WORKSPACE_INTERNAL_API_TOKEN_PRIMARY is required in dual mode.");
    }
    if (properties.workspace().legacyTokenConfigured()) {
      throw new IllegalStateException(
          "Legacy WORKSPACE_INTERNAL_API_TOKEN is not accepted when content-service runs with the prod profile.");
    }
    String dbPassword = environment.getProperty("spring.datasource.password");
    if (dbPassword == null || dbPassword.isBlank()) {
      throw new IllegalStateException(
          "DB_PASSWORD is required when content-service runs with the prod profile.");
    }
  }
}
