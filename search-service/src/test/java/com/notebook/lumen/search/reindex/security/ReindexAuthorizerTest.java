package com.notebook.lumen.search.reindex.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtProperties;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import com.notebook.lumen.search.shared.config.SearchProperties;
import com.notebook.lumen.search.shared.exception.SearchException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ReindexAuthorizerTest {
  private final KeyPair keyPair = keyPair();

  @Test
  void acceptsManageScope() {
    ReindexAuthorizer authorizer = new ReindexAuthorizer(properties(publicPem()));

    authorizer.authorize("Bearer " + token(ReindexAuthorizer.MANAGE_SCOPE));
  }

  @Test
  void rejectsWrongScope() {
    ReindexAuthorizer authorizer = new ReindexAuthorizer(properties(publicPem()));

    assertThatThrownBy(() -> authorizer.authorize("Bearer " + token("internal:search:index:write")))
        .isInstanceOf(SearchException.class)
        .extracting("errorCode")
        .isEqualTo("REINDEX_ACCESS_DENIED");
  }

  @Test
  void orphanPreviewUsesPreviewAccessDeniedCode() {
    ReindexAuthorizer authorizer = new ReindexAuthorizer(properties(publicPem()));

    assertThatThrownBy(
            () ->
                authorizer.authorizeOrphanPreview("Bearer " + token("internal:search:index:write")))
        .isInstanceOf(SearchException.class)
        .extracting("errorCode")
        .isEqualTo("ORPHAN_PREVIEW_ACCESS_DENIED");
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
            null,
            new SearchProperties.TrustedService(
                "ops-key-1",
                publicKey,
                "",
                "ops-admin",
                "search-service",
                5,
                ReindexAuthorizer.MANAGE_SCOPE)),
        new SearchProperties.Reindex(true, 100, 10, 100, false));
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
        .sign("search-service", scope);
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
