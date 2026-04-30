package com.notebook.lumen.gateway.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionSecurityValidatorTest {
  @Test
  void run_requiresJwtPublicKeyForProdProfile() {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("prod");
    ProductionSecurityValidator validator =
        new ProductionSecurityValidator(new GatewayJwtProperties("", "", ""), environment);

    assertThatThrownBy(() -> validator.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_JWKS_URI");
  }
}
