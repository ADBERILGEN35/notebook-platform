package com.notebook.lumen.identity.shared.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.notebook.lumen.identity.shared.security.JwtKeyProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionSecurityValidatorTest {
  @Test
  void run_requiresPrivateKeyForProdProfile() {
    JwtProperties jwtProperties = new JwtProperties();
    jwtProperties.setAllowEphemeralKeys(false);
    MockEnvironment environment = prodEnvironment();

    ProductionSecurityValidator validator =
        new ProductionSecurityValidator(
            jwtProperties, new JwtKeyProvider(jwtProperties), environment);

    assertThatThrownBy(() -> validator.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT signing keys");
  }

  @Test
  void run_rejectsEphemeralKeysForProdProfile() {
    JwtProperties jwtProperties = new JwtProperties();
    jwtProperties.setAllowEphemeralKeys(true);
    jwtProperties.setPrivateKey("inline-key");
    MockEnvironment environment = prodEnvironment();

    ProductionSecurityValidator validator =
        new ProductionSecurityValidator(
            jwtProperties, new JwtKeyProvider(jwtProperties), environment);

    assertThatThrownBy(() -> validator.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_ALLOW_EPHEMERAL_KEYS");
  }

  private MockEnvironment prodEnvironment() {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("prod");
    environment.setProperty("spring.datasource.password", "db-secret");
    return environment;
  }
}
