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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("rls-test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkspaceStrictHeaderRlsIntegrationTest {
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
  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  void aggregateWorkspaceEndpoints_requireCorrectWorkspaceHeaderWhenRlsStrictModeIsEnabled()
      throws Exception {
    User owner = new User(UUID.randomUUID(), "owner-" + UUID.randomUUID() + "@example.com");
    JsonNode workspace =
        send(
            "POST",
            "/workspaces",
            owner,
            null,
            """
            {"name":"RLS Strict Workspace","type":"TEAM"}\
            """,
            201);
    UUID workspaceId = UUID.fromString(workspace.get("id").asText());
    JsonNode notebook =
        send(
            "POST",
            "/workspaces/" + workspaceId + "/notebooks",
            owner,
            workspaceId,
            """
            {"name":"Strict Notebook","icon":"book"}\
            """,
            201);
    JsonNode tag =
        send(
            "POST",
            "/workspaces/" + workspaceId + "/tags",
            owner,
            workspaceId,
            """
            {"name":"Strict Tag","color":"#22cc88","scope":"NOTEBOOK"}\
            """,
            201);
    JsonNode invitation =
        send(
            "POST",
            "/workspaces/" + workspaceId + "/invitations",
            owner,
            workspaceId,
            """
            {"email":"invitee@example.com","role":"MEMBER"}\
            """,
            201);

    assertStrictHeader("/notebooks/" + notebook.get("id").asText(), owner, workspaceId);
    assertStrictHeader(
        "/tags/" + tag.get("id").asText(),
        owner,
        workspaceId,
        "PATCH",
        """
        {"name":"Strict Tag Updated","color":"#22cc88"}\
        """);
    assertStrictHeader(
        "/invitations/" + invitation.get("id").asText() + "/revoke", owner, workspaceId, "POST");

    JsonNode workspaces = send("GET", "/workspaces", owner, null, null, 200);
    assertThat(workspaces.get("items")).isNotEmpty();
  }

  private void assertStrictHeader(String path, User user, UUID workspaceId) throws Exception {
    assertStrictHeader(path, user, workspaceId, "GET");
  }

  private void assertStrictHeader(String path, User user, UUID workspaceId, String method)
      throws Exception {
    assertStrictHeader(path, user, workspaceId, method, null);
  }

  private void assertStrictHeader(
      String path, User user, UUID workspaceId, String method, String body) throws Exception {
    JsonNode missing = send(method, path, user, null, body, 400);
    assertThat(missing.get("errorCode").asText()).isEqualTo("MISSING_WORKSPACE_CONTEXT");

    JsonNode wrong = send(method, path, user, UUID.randomUUID(), body, 400);
    assertThat(wrong.get("errorCode").asText()).isEqualTo("INVALID_WORKSPACE_CONTEXT");

    JsonNode ok = send(method, path, user, workspaceId, body, 200);
    assertThat(ok.isMissingNode()).isFalse();
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
        http.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
    if (response.body() == null || response.body().isBlank()) {
      return objectMapper.createObjectNode();
    }
    return objectMapper.readTree(response.body());
  }

  private record User(UUID id, String email) {}
}
