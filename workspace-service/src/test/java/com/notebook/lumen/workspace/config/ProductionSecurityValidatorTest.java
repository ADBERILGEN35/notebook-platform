package com.notebook.lumen.workspace.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionSecurityValidatorTest {
  @Test
  void run_requiresInternalTokenForProdProfile() {
    WorkspaceProperties properties =
        new WorkspaceProperties(
            null, null, new WorkspaceProperties.Internal("", "", "", "static-token", null));
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("prod");
    environment.setProperty("spring.datasource.password", "db-secret");

    ProductionSecurityValidator validator =
        new ProductionSecurityValidator(properties, environment);

    assertThatThrownBy(() -> validator.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("INTERNAL_API_TOKEN_PRIMARY");
  }

  @Test
  void run_rejectsLegacyInternalTokenForProdProfile() {
    WorkspaceProperties properties =
        new WorkspaceProperties(
            null,
            null,
            new WorkspaceProperties.Internal("legacy", "primary", "", "static-token", null));
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("prod");
    environment.setProperty("spring.datasource.password", "db-secret");

    ProductionSecurityValidator validator =
        new ProductionSecurityValidator(properties, environment);

    assertThatThrownBy(() -> validator.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Legacy INTERNAL_API_TOKEN");
  }
}
