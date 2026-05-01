package com.notebook.lumen.content.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionSecurityValidatorTest {
  @Test
  void run_requiresWorkspaceInternalTokenForProdProfile() {
    ContentProperties properties =
        new ContentProperties(
            null,
            new ContentProperties.Workspace(
                "http://localhost", 1000, 50, 10000, 2, "", "", "", "static-token"),
            null);
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("prod");
    environment.setProperty("spring.datasource.password", "db-secret");

    ProductionSecurityValidator validator =
        new ProductionSecurityValidator(properties, environment);

    assertThatThrownBy(() -> validator.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("WORKSPACE_INTERNAL_API_TOKEN_PRIMARY");
  }

  @Test
  void run_rejectsLegacyWorkspaceInternalTokenForProdProfile() {
    ContentProperties properties =
        new ContentProperties(
            null,
            new ContentProperties.Workspace(
                "http://localhost", 1000, 50, 10000, 2, "legacy", "primary", "", "static-token"),
            null);
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("prod");
    environment.setProperty("spring.datasource.password", "db-secret");

    ProductionSecurityValidator validator =
        new ProductionSecurityValidator(properties, environment);

    assertThatThrownBy(() -> validator.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Legacy WORKSPACE_INTERNAL_API_TOKEN");
  }
}
