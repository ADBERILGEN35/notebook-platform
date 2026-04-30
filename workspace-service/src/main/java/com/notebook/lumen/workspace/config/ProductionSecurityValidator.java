package com.notebook.lumen.workspace.config;

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
    if (properties.internal() == null || !properties.internal().primaryTokenConfigured()) {
      throw new IllegalStateException(
          "INTERNAL_API_TOKEN_PRIMARY is required when workspace-service runs with the prod profile.");
    }
    if (properties.internal().legacyTokenConfigured()) {
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
