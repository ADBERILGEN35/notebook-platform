package com.notebook.lumen.workspace.config;

import com.notebook.lumen.common.security.servicejwt.InternalAuthMode;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class ProductionSecurityValidator implements ApplicationRunner {
  private final WorkspaceProperties properties;
  private final Environment environment;

  public ProductionSecurityValidator(WorkspaceProperties properties, Environment environment) {
    this.properties = properties;
    this.environment = environment;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!environment.acceptsProfiles(Profiles.of("prod"))) {
      return;
    }
    WorkspaceProperties.Internal internal = properties.internal();
    InternalAuthMode mode =
        internal == null ? InternalAuthMode.DUAL : InternalAuthMode.parse(internal.authMode());
    boolean trustedServiceConfigured =
        internal != null
            && internal.trustedContentService() != null
            && internal.trustedContentService().configured();
    boolean primaryTokenConfigured = internal != null && internal.primaryTokenConfigured();
    if (mode == InternalAuthMode.SERVICE_JWT && !trustedServiceConfigured) {
      throw new IllegalStateException(
          "TRUSTED_SERVICE_CONTENT_SERVICE_PUBLIC_KEY_PATH is required in service-jwt mode.");
    }
    if (mode == InternalAuthMode.STATIC_TOKEN && !primaryTokenConfigured) {
      throw new IllegalStateException(
          "INTERNAL_API_TOKEN_PRIMARY is required when workspace-service runs with the prod profile.");
    }
    if (mode == InternalAuthMode.DUAL && !trustedServiceConfigured && !primaryTokenConfigured) {
      throw new IllegalStateException(
          "Either service JWT trust config or INTERNAL_API_TOKEN_PRIMARY is required in dual mode.");
    }
    if (internal != null && internal.legacyTokenConfigured()) {
      throw new IllegalStateException(
          "Legacy INTERNAL_API_TOKEN is not accepted when workspace-service runs with the prod profile.");
    }
    String dbPassword = environment.getProperty("spring.datasource.password");
    if (dbPassword == null || dbPassword.isBlank()) {
      throw new IllegalStateException(
          "DB_PASSWORD is required when workspace-service runs with the prod profile.");
    }
  }
}
