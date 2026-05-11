package com.notebook.lumen.content.search.outbox.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtProperties;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import com.notebook.lumen.content.config.ContentProperties;
import com.notebook.lumen.content.shared.exception.ContentException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SearchOutboxAuthorizerTest {
  private final KeyPair keyPair = keyPair();

  @Test
  void acceptsServiceJwtWithRequiredScope() {
    SearchOutboxAuthorizer authorizer = new SearchOutboxAuthorizer(properties(publicPem()));

    authorizer.authorize(
        "Bearer " + token(SearchOutboxAuthorizer.READ_SCOPE), SearchOutboxAuthorizer.READ_SCOPE);
  }

  @Test
  void rejectsNormalOrWrongScopeToken() {
    SearchOutboxAuthorizer authorizer = new SearchOutboxAuthorizer(properties(publicPem()));

    assertThatThrownBy(
            () ->
                authorizer.authorize(
                    "Bearer " + token("internal:search:index:write"),
                    SearchOutboxAuthorizer.MANAGE_SCOPE))
        .isInstanceOf(ContentException.class)
        .extracting("errorCode")
        .isEqualTo("SEARCH_OUTBOX_ACCESS_DENIED");
  }

  @Test
  void requiresBearerServiceAuthorization() {
    SearchOutboxAuthorizer authorizer = new SearchOutboxAuthorizer(properties(publicPem()));

    assertThatThrownBy(() -> authorizer.authorize(null, SearchOutboxAuthorizer.READ_SCOPE))
        .isInstanceOf(ContentException.class)
        .extracting("errorCode")
        .isEqualTo("INTERNAL_AUTH_REQUIRED");
  }

  private ContentProperties properties(String publicKey) {
    ContentProperties.SearchOutboxAdmin admin =
        new ContentProperties.SearchOutboxAdmin(
            "ops-key-1",
            publicKey,
            "",
            "ops-admin",
            "content-service",
            5,
            SearchOutboxAuthorizer.READ_SCOPE + "," + SearchOutboxAuthorizer.MANAGE_SCOPE);
    return new ContentProperties(
        "",
        null,
        new ContentProperties.Concurrency(false),
        null,
        null,
        new ContentProperties.Search(
            "",
            1000,
            true,
            null,
            null,
            new ContentProperties.SearchOutbox(true, 50, 10, 30, 3600, 10, 300, admin)));
  }

  private String token(String scope) {
    return new ServiceJwtSigner(
            new ServiceJwtProperties(
                "ops-key-1",
                privatePem(),
                "",
                "ops-admin",
                "service:ops-admin",
                "ops-admin",
                Duration.ofSeconds(60)))
        .sign("content-service", scope);
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
      return generator.generateKeyPair();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
