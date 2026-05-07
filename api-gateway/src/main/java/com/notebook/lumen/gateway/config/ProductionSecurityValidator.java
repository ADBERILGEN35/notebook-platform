package com.notebook.lumen.gateway.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class ProductionSecurityValidator implements ApplicationRunner {
  private final GatewayJwtProperties jwtProperties;
  private final GatewayCorsProperties corsProperties;
  private final Environment environment;

  public ProductionSecurityValidator(
      GatewayJwtProperties jwtProperties,
      GatewayCorsProperties corsProperties,
      Environment environment) {
    this.jwtProperties = jwtProperties;
    this.corsProperties = corsProperties;
    this.environment = environment;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!environment.acceptsProfiles(Profiles.of("prod"))) {
      return;
    }
    boolean hasPath =
        jwtProperties.publicKeyPath() != null && !jwtProperties.publicKeyPath().isBlank();
    boolean hasInlineKey =
        jwtProperties.publicKey() != null && !jwtProperties.publicKey().isBlank();
    boolean hasJwksUri = jwtProperties.jwksUri() != null && !jwtProperties.jwksUri().isBlank();
    if (!hasJwksUri && !hasPath && !hasInlineKey) {
      throw new IllegalStateException(
          "JWT_JWKS_URI, JWT_PUBLIC_KEY_PATH or JWT_PUBLIC_KEY is required when api-gateway runs with the prod profile.");
    }
    if (corsProperties.allowCredentials()
        && (corsProperties.allowedOrigins() == null || corsProperties.allowedOrigins().contains("*"))) {
      throw new IllegalStateException(
          "CORS wildcard origins are not allowed when CORS_ALLOW_CREDENTIALS=true.");
    }
  }
}
