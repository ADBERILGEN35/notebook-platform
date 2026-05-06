package com.notebook.lumen.search.shared.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProductionSecurityValidator implements ApplicationRunner {
  private final SearchProperties properties;
  private final Environment environment;

  public ProductionSecurityValidator(SearchProperties properties, Environment environment) {
    this.properties = properties;
    this.environment = environment;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!environment.matchesProfiles("prod")) {
      return;
    }
    if (properties.internal().trustedIndexingClient() == null
        || !properties.internal().trustedIndexingClient().configured()) {
      throw new IllegalStateException(
          "TRUSTED_SERVICE_CONTENT_SERVICE_PUBLIC_KEY_PATH is required in prod");
    }
    if (properties.serviceJwt() == null || !properties.serviceJwt().signingConfigured()) {
      throw new IllegalStateException(
          "INTERNAL_SERVICE_JWT_PRIVATE_KEY_PATH is required for search-service in prod");
    }
    String dbPassword = environment.getProperty("spring.datasource.password");
    if (dbPassword == null || dbPassword.isBlank()) {
      throw new IllegalStateException("DB_PASSWORD is required for search-service in prod");
    }
  }
}
