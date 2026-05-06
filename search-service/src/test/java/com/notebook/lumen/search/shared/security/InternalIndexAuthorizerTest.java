package com.notebook.lumen.search.shared.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtProperties;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import com.notebook.lumen.search.shared.config.SearchProperties;
import com.notebook.lumen.search.shared.exception.SearchException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class InternalIndexAuthorizerTest {
  private final KeyPair keyPair = keyPair();
  private final InternalIndexAuthorizer authorizer =
      new InternalIndexAuthorizer(properties(publicPem()));

  @Test
  void acceptsServiceJwtWithIndexScope() {
    authorizer.authorize("Bearer " + serviceToken("search-service", "internal:search:index:write"));
  }

  @Test
  void rejectsMissingServiceJwt() {
    assertThatThrownBy(() -> authorizer.authorize(""))
        .isInstanceOf(SearchException.class)
        .extracting("errorCode")
        .isEqualTo("SEARCH_ACCESS_DENIED");
  }

  @Test
  void rejectsWrongScope() {
    assertThatThrownBy(
            () -> authorizer.authorize("Bearer " + serviceToken("search-service", "other")))
        .isInstanceOf(SearchException.class)
        .extracting("errorCode")
        .isEqualTo("SEARCH_ACCESS_DENIED");
  }

  @Test
  void rejectsWrongAudience() {
    assertThatThrownBy(
            () ->
                authorizer.authorize(
                    "Bearer " + serviceToken("content-service", "internal:search:index:write")))
        .isInstanceOf(SearchException.class)
        .extracting("errorCode")
        .isEqualTo("INVALID_SERVICE_AUDIENCE");
  }

  private String serviceToken(String audience, String scope) {
    return new ServiceJwtSigner(
            new ServiceJwtProperties(
                "content-key-1",
                privatePem(),
                "",
                "content-service",
                "service:content-service",
                "content-service",
                Duration.ofSeconds(60)))
        .sign(audience, scope);
  }

  private SearchProperties properties(String publicKey) {
    return new SearchProperties(
        200000,
        120,
        2,
        50,
        new SearchProperties.Workspace("http://localhost", 1000, 2),
        new SearchProperties.ContentSource("http://localhost", 1000, "content-service"),
        null,
        new SearchProperties.Internal(
            new SearchProperties.TrustedService(
                "content-key-1",
                publicKey,
                "",
                "content-service",
                "search-service",
                5,
                "internal:search:index:write"),
            null),
        new SearchProperties.Reindex(true, 100, 10, 100, false));
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
