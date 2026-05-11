package com.notebook.lumen.content.search.source.security;

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

class SearchIndexSourceAuthorizerTest {
  private final KeyPair keyPair = keyPair();

  @Test
  void acceptsSearchServiceJwtWithSourceScope() {
    SearchIndexSourceAuthorizer authorizer =
        new SearchIndexSourceAuthorizer(properties(publicPem()));

    authorizer.authorize("Bearer " + token(SearchIndexSourceAuthorizer.REQUIRED_SCOPE));
  }

  @Test
  void rejectsWrongScope() {
    SearchIndexSourceAuthorizer authorizer =
        new SearchIndexSourceAuthorizer(properties(publicPem()));

    assertThatThrownBy(() -> authorizer.authorize("Bearer " + token("internal:search:index:write")))
        .isInstanceOf(ContentException.class)
        .extracting("errorCode")
        .isEqualTo("SEARCH_INDEX_SOURCE_ACCESS_DENIED");
  }

  private ContentProperties properties(String publicKey) {
    ContentProperties.TrustedService trusted =
        new ContentProperties.TrustedService(
            "search-key-1",
            publicKey,
            "",
            "search-service",
            "content-service",
            5,
            SearchIndexSourceAuthorizer.REQUIRED_SCOPE);
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
            new ContentProperties.SearchIndexSource(trusted),
            new ContentProperties.SearchOutbox(true, 50, 10, 30, 3600, 10, 300, null)));
  }

  private String token(String scope) {
    return new ServiceJwtSigner(
            new ServiceJwtProperties(
                "search-key-1",
                privatePem(),
                "",
                "search-service",
                "service:search-service",
                "search-service",
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
