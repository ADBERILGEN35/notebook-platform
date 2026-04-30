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
      "app.rls.strict-workspace-header=true",
      "workspace.identity.service-url=http://127.0.0.1:1"
    })
class WorkspaceStrictWorkspaceHeaderIntegrationTest {
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
  void aggregateNotebookEndpoint_requiresWorkspaceHeaderOnlyInStrictMode() throws Exception {
    User owner = new User(UUID.randomUUID(), "owner-" + UUID.randomUUID() + "@example.com");
    JsonNode workspace =
        send(
            "POST",
            "/workspaces",
            owner,
            null,
            """
            {"name":"Strict Team","type":"TEAM"}\
            """,
            201);
    UUID workspaceId = UUID.fromString(workspace.get("id").asText());
    JsonNode notebook =
        send(
            "POST",
            "/workspaces/" + workspaceId + "/notebooks",
            owner,
            null,
            """
            {"name":"Strict Notebook","icon":"book"}\
            """,
            201);

    JsonNode missing =
        send("GET", "/notebooks/" + notebook.get("id").asText(), owner, null, null, 400);
    assertThat(missing.get("errorCode").asText()).isEqualTo("MISSING_WORKSPACE_CONTEXT");

    JsonNode conflict =
        send(
            "GET",
            "/notebooks/" + notebook.get("id").asText(),
            owner,
            UUID.randomUUID(),
            null,
            400);
    assertThat(conflict.get("errorCode").asText()).isEqualTo("INVALID_WORKSPACE_CONTEXT");

    JsonNode ok =
        send("GET", "/notebooks/" + notebook.get("id").asText(), owner, workspaceId, null, 200);
    assertThat(ok.get("id").asText()).isEqualTo(notebook.get("id").asText());
  }

  private JsonNode send(
      String method, String path, User user, UUID workspaceId, String body, int expectedStatus)
      throws Exception {
    HttpRequest.BodyPublisher publisher =
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body);
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .header("X-User-Id", user.id().toString())
            .header("X-User-Email", user.email());
    if (workspaceId != null) {
      builder.header("X-Workspace-Id", workspaceId.toString());
    }
    HttpResponse<String> response =
        httpClient.send(
            builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
    if (response.body() == null || response.body().isBlank()) {
      return objectMapper.createObjectNode();
    }
    return objectMapper.readTree(response.body());
  }

  private record User(UUID id, String email) {}
}
