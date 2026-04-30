package com.notebook.lumen.content.config;

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
    if (!properties.workspace().primaryTokenConfigured()) {
      throw new IllegalStateException(
          "WORKSPACE_INTERNAL_API_TOKEN_PRIMARY is required when content-service runs with the prod profile.");
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
