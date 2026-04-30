package com.notebook.lumen.identity.shared.config;

import com.notebook.lumen.identity.shared.security.JwtKeyProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class ProductionSecurityValidator implements ApplicationRunner {
  private final JwtProperties jwtProperties;
  private final JwtKeyProvider keyProvider;
  private final Environment environment;

  public ProductionSecurityValidator(
      JwtProperties jwtProperties, JwtKeyProvider keyProvider, Environment environment) {
    this.jwtProperties = jwtProperties;
    this.keyProvider = keyProvider;
    this.environment = environment;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!environment.acceptsProfiles(Profiles.of("prod"))) {
      return;
    }
    if (jwtProperties.isAllowEphemeralKeys()) {
      throw new IllegalStateException(
          "JWT_ALLOW_EPHEMERAL_KEYS must be false when identity-service runs with the prod profile.");
    }
    if (!hasText(jwtProperties.getPrivateKey()) && !hasText(jwtProperties.getPrivateKeyPath())) {
      boolean hasConfiguredSigningKeys =
          jwtProperties.getKeys() != null
              && jwtProperties.getKeys().getSigningKeys() != null
              && jwtProperties.getKeys().getSigningKeys().stream()
                  .anyMatch(
                      key ->
                          hasText(key.getPrivateKey())
                              || hasText(key.getPrivateKeyPath())
                              || hasText(key.getPublicKey())
                              || hasText(key.getPublicKeyPath()));
      if (!hasConfiguredSigningKeys) {
        throw new IllegalStateException(
            "JWT signing keys or JWT_PRIVATE_KEY_PATH/JWT_PRIVATE_KEY are required when identity-service runs with the prod profile.");
      }
    }
    if (!hasText(jwtProperties.getKeys() == null ? null : jwtProperties.getKeys().getActiveKid())) {
      throw new IllegalStateException(
          "jwt.keys.active-kid is required when identity-service runs with the prod profile.");
    }
    if (!hasText(environment.getProperty("spring.datasource.password"))) {
      throw new IllegalStateException(
          "DB_PASSWORD is required when identity-service runs with the prod profile.");
    }
    keyProvider.loadOrGenerateKeySet();
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
