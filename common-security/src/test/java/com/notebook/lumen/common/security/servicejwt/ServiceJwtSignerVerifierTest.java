package com.notebook.lumen.common.security.servicejwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ServiceJwtSignerVerifierTest {
  private final KeyPair keyPair = keyPair();

  @Test
  void signerAddsKidAndVerifierValidatesClaims() {
    ServiceJwtSigner signer = signer("content-key-1", Duration.ofSeconds(60));
    ServiceJwtVerifier verifier =
        verifier("content-key-1", "content-service", "workspace-service", Set.of("scope:read"));

    String token = signer.sign("workspace-service", "scope:read");
    ServiceJwtClaims claims = verifier.verify(token, "scope:read");

    assertThat(token).doesNotContain(privatePem());
    assertThat(claims.issuer()).isEqualTo("content-service");
    assertThat(claims.subject()).isEqualTo("service:content-service");
    assertThat(claims.audience()).isEqualTo("workspace-service");
    assertThat(claims.scope()).isEqualTo("scope:read");
    assertThat(claims.jwtId()).isNotNull();
  }

  @Test
  void verifierRejectsWrongAudienceIssuerScopeAndKid() {
    String token = signer("content-key-1", Duration.ofSeconds(60)).sign("workspace-service", "a");

    assertThatThrownBy(
            () ->
                verifier("content-key-1", "content-service", "other", Set.of("a"))
                    .verify(token, "a"))
        .isInstanceOf(ServiceJwtValidationException.class)
        .extracting("errorCode")
        .isEqualTo("INVALID_SERVICE_AUDIENCE");

    assertThatThrownBy(
            () ->
                verifier("content-key-1", "other", "workspace-service", Set.of("a"))
                    .verify(token, "a"))
        .isInstanceOf(ServiceJwtValidationException.class)
        .extracting("errorCode")
        .isEqualTo("INVALID_SERVICE_ISSUER");

    assertThatThrownBy(
            () ->
                verifier("content-key-1", "content-service", "workspace-service", Set.of("b"))
                    .verify(token, "b"))
        .isInstanceOf(ServiceJwtValidationException.class)
        .extracting("errorCode")
        .isEqualTo("INSUFFICIENT_SERVICE_SCOPE");

    assertThatThrownBy(
            () ->
                verifier("other", "content-service", "workspace-service", Set.of("a"))
                    .verify(token, "a"))
        .isInstanceOf(ServiceJwtValidationException.class)
        .extracting("errorCode")
        .isEqualTo("INVALID_SERVICE_JWT");
  }

  @Test
  void verifierRejectsExpiredToken() {
    String token = signer("content-key-1", Duration.ofSeconds(-60)).sign("workspace-service", "a");

    assertThatThrownBy(
            () ->
                verifier("content-key-1", "content-service", "workspace-service", Set.of("a"))
                    .verify(token, "a"))
        .isInstanceOf(ServiceJwtValidationException.class)
        .extracting("errorCode")
        .isEqualTo("EXPIRED_SERVICE_JWT");
  }

  private ServiceJwtSigner signer(String kid, Duration ttl) {
    return new ServiceJwtSigner(
        new ServiceJwtProperties(
            kid,
            privatePem(),
            "",
            "content-service",
            "service:content-service",
            "content-service",
            ttl));
  }

  private ServiceJwtVerifier verifier(
      String kid, String issuer, String audience, Set<String> allowedScopes) {
    return new ServiceJwtVerifier(
        new TrustedServiceProperties(
            kid, publicPem(), "", issuer, audience, Duration.ofSeconds(0), allowedScopes));
  }

  private String privatePem() {
    return pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
  }

  private String publicPem() {
    return pem("PUBLIC KEY", keyPair.getPublic().getEncoded());
  }

  private String pem(String type, byte[] der) {
    return "-----BEGIN "
        + type
        + "-----\n"
        + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
        + "\n-----END "
        + type
        + "-----";
  }

  private KeyPair keyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair generated = generator.generateKeyPair();
      return new KeyPair(
          (RSAPublicKey) generated.getPublic(), (RSAPrivateKey) generated.getPrivate());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
