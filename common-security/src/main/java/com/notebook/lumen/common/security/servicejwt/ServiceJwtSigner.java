package com.notebook.lumen.common.security.servicejwt;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public class ServiceJwtSigner {
  private static final String DEFAULT_EPHEMERAL_KID = "ephemeral-service-jwt";

  private final ServiceJwtProperties properties;
  private final RSASSASigner signer;
  private final Clock clock;
  private final String signingKid;

  public ServiceJwtSigner(ServiceJwtProperties properties) {
    this(properties, Clock.systemUTC());
  }

  public ServiceJwtSigner(ServiceJwtProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
    RSAPrivateKey privateKey = resolvePrivateKey(properties);
    this.signer = new RSASSASigner(privateKey);
    this.signingKid = resolveSigningKid(properties);
  }

  private static RSAPrivateKey resolvePrivateKey(ServiceJwtProperties properties) {
    boolean hasInline = properties.privateKey() != null && !properties.privateKey().isBlank();
    boolean hasPath =
        properties.privateKeyPath() != null && !properties.privateKeyPath().isBlank();
    if (hasInline || hasPath) {
      return RsaPemUtils.loadPrivateKey(
          "INTERNAL_SERVICE_JWT_PRIVATE_KEY", properties.privateKey(), properties.privateKeyPath());
    }
    if (!allowEphemeralKeys()) {
      throw new IllegalArgumentException("Missing RSA key material for INTERNAL_SERVICE_JWT_PRIVATE_KEY");
    }
    return generateEphemeralPrivateKey();
  }

  private static String resolveSigningKid(ServiceJwtProperties properties) {
    if (properties.activeKid() != null && !properties.activeKid().isBlank()) {
      return properties.activeKid();
    }
    return DEFAULT_EPHEMERAL_KID;
  }

  private static boolean allowEphemeralKeys() {
    if (Boolean.getBoolean("internal.service.jwt.allowEphemeralKeys")) {
      return true;
    }
    String internal = System.getenv("INTERNAL_SERVICE_JWT_ALLOW_EPHEMERAL_KEYS");
    if (internal != null && !internal.isBlank()) {
      return Boolean.parseBoolean(internal);
    }
    String jwt = System.getenv("JWT_ALLOW_EPHEMERAL_KEYS");
    return jwt != null && Boolean.parseBoolean(jwt);
  }

  private static RSAPrivateKey generateEphemeralPrivateKey() {
    try {
      KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
      keyPairGenerator.initialize(2048);
      KeyPair keyPair = keyPairGenerator.generateKeyPair();
      return (RSAPrivateKey) keyPair.getPrivate();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to generate ephemeral RSA keys: " + e.getMessage(), e);
    }
  }

  public String sign(String audience, String scope) {
    try {
      Instant issuedAt = Instant.now(clock);
      Duration ttl = properties.ttl() == null ? Duration.ofSeconds(60) : properties.ttl();
      Instant expiresAt = issuedAt.plus(ttl);
      UUID jwtId = UUID.randomUUID();
      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .issuer(properties.issuer())
              .subject(properties.subject())
              .audience(audience)
              .claim("scope", scope)
              .claim("service_name", properties.serviceName())
              .claim("token_type", "service")
              .jwtID(jwtId.toString())
              .issueTime(Date.from(issuedAt))
              .expirationTime(Date.from(expiresAt))
              .build();
      JWSHeader header =
          new JWSHeader.Builder(JWSAlgorithm.RS256)
              .keyID(signingKid)
              .type(JOSEObjectType.JWT)
              .build();
      SignedJWT jwt = new SignedJWT(header, claims);
      jwt.sign(signer);
      return jwt.serialize();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to sign service JWT");
    }
  }
}
