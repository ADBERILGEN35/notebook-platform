package com.notebook.lumen.common.security.servicejwt;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public class ServiceJwtSigner {
  private final ServiceJwtProperties properties;
  private final RSASSASigner signer;
  private final Clock clock;

  public ServiceJwtSigner(ServiceJwtProperties properties) {
    this(properties, Clock.systemUTC());
  }

  public ServiceJwtSigner(ServiceJwtProperties properties, Clock clock) {
    this.properties = properties;
    this.signer =
        new RSASSASigner(
            RsaPemUtils.loadPrivateKey(
                "INTERNAL_SERVICE_JWT_PRIVATE_KEY",
                properties.privateKey(),
                properties.privateKeyPath()));
    this.clock = clock;
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
              .keyID(properties.activeKid())
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
