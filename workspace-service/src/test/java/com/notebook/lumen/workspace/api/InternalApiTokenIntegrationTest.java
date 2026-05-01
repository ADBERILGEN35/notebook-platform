package com.notebook.lumen.workspace.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "workspace.identity.service-url=http://127.0.0.1:1",
      "workspace.invitations.expose-token-in-response=true",
      "workspace.internal.auth-mode=static-token",
      "workspace.internal.primary-token=test-internal-token-primary",
      "workspace.internal.secondary-token=test-internal-token-secondary"
    })
class InternalApiTokenIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16")
          .withDatabaseName("notebook_platform")
          .withUsername("notebook")
          .withPassword("notebook");

  @DynamicPropertySource
  static void registerProps(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @LocalServerPort private int port;

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void internalToken_isRequiredWhenConfigured() throws Exception {
    UUID ownerId = UUID.randomUUID();
    JsonNode workspace =
        send(
            "POST",
            "/workspaces",
            ownerId,
            """
            {"name":"Token Team","type":"TEAM"}\
            """,
            null,
            201);
    JsonNode notebook =
        send(
            "POST",
            "/workspaces/" + workspace.get("id").asText() + "/notebooks",
            ownerId,
            """
            {"name":"Protected Contract","icon":"lock"}\
            """,
            null,
            201);

    JsonNode missing =
        send(
            "GET",
            "/internal/notebooks/" + notebook.get("id").asText() + "/permissions?userId=" + ownerId,
            ownerId,
            null,
            null,
            401);
    assertThat(missing.get("errorCode").asText()).isEqualTo("INTERNAL_AUTH_REQUIRED");

    JsonNode wrong =
        send(
            "GET",
            "/internal/notebooks/" + notebook.get("id").asText() + "/permissions?userId=" + ownerId,
            ownerId,
            null,
            "wrong",
            401);
    assertThat(wrong.get("errorCode").asText()).isEqualTo("INVALID_INTERNAL_TOKEN");

    JsonNode ok =
        send(
            "GET",
            "/internal/notebooks/" + notebook.get("id").asText() + "/permissions?userId=" + ownerId,
            ownerId,
            null,
            "test-internal-token-primary",
            200);
    assertThat(ok.get("canManage").asBoolean()).isTrue();

    JsonNode secondary =
        send(
            "GET",
            "/internal/notebooks/" + notebook.get("id").asText() + "/permissions?userId=" + ownerId,
            ownerId,
            null,
            "test-internal-token-secondary",
            200);
    assertThat(secondary.get("canManage").asBoolean()).isTrue();
  }

  private JsonNode send(
      String method,
      String path,
      UUID userId,
      String body,
      String internalToken,
      int expectedStatus)
      throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .header("X-User-Id", userId.toString())
            .header("X-User-Email", "user-" + userId + "@example.com");
    if (internalToken != null) {
      builder.header("X-Internal-Token", internalToken);
    }
    HttpRequest.BodyPublisher publisher =
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body);
    HttpResponse<String> response =
        httpClient.send(
            builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
    if (internalToken != null) {
      assertThat(response.body()).doesNotContain(internalToken);
    }
    return response.body() == null || response.body().isBlank()
        ? objectMapper.createObjectNode()
        : objectMapper.readTree(response.body());
  }
}
