package com.notebook.lumen.content.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class WorkspaceInternalAuthHeadersTest {
  private final KeyPair keyPair = keyPair();

  @Test
  void serviceJwtModeSendsServiceAuthorizationHeader() {
    WorkspaceInternalAuthHeaders headers =
        new WorkspaceInternalAuthHeaders(properties("service-jwt"));
    HttpHeaders httpHeaders = new HttpHeaders();

    headers.apply(httpHeaders, "/internal/notebooks/123/permissions");

    assertThat(httpHeaders.getFirst("X-Service-Authorization")).startsWith("Bearer ");
    assertThat(httpHeaders.getFirst("X-Internal-Token")).isNull();
  }

  @Test
  void staticModeSendsInternalTokenHeader() {
    WorkspaceInternalAuthHeaders headers =
        new WorkspaceInternalAuthHeaders(properties("static-token"));
    HttpHeaders httpHeaders = new HttpHeaders();

    headers.apply(httpHeaders, "/internal/notebooks/123/permissions");

    assertThat(httpHeaders.getFirst("X-Internal-Token")).isEqualTo("static-token");
    assertThat(httpHeaders.getFirst("X-Service-Authorization")).isNull();
  }

  @Test
  void dualModePrefersServiceJwtWhenSigningKeyIsConfigured() {
    WorkspaceInternalAuthHeaders headers = new WorkspaceInternalAuthHeaders(properties("dual"));
    HttpHeaders httpHeaders = new HttpHeaders();

    headers.apply(httpHeaders, "/internal/workspaces/1/tags/2/exists");

    assertThat(httpHeaders.getFirst("X-Service-Authorization")).startsWith("Bearer ");
    assertThat(httpHeaders.getFirst("X-Internal-Token")).isNull();
  }

  private ContentProperties properties(String mode) {
    return new ContentProperties(
        null,
        new ContentProperties.Workspace(
            "http://localhost", 1000, 50, 10000, 2, "", "static-token", "", mode),
        new ContentProperties.ServiceJwt(
            "content-key-1",
            privatePem(),
            "",
            "",
            "content-service",
            "service:content-service",
            "content-service",
            60,
            "workspace-service"));
  }

  private String privatePem() {
    return "-----BEGIN "
        + "PRIVATE KEY-----\n"
        + Base64.getMimeEncoder(64, "\n".getBytes())
            .encodeToString(keyPair.getPrivate().getEncoded())
        + "\n-----END "
        + "PRIVATE KEY-----";
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
