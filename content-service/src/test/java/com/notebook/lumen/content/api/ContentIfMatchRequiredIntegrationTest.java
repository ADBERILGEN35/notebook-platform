package com.notebook.lumen.content.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.rls.strict-workspace-header=true",
      "content.concurrency.require-if-match-for-note-update=true"
    })
class ContentIfMatchRequiredIntegrationTest {
  private static final UUID WORKSPACE_ID = UUID.randomUUID();
  private static final UUID NOTEBOOK_ID = UUID.randomUUID();
  private static final User OWNER = new User(UUID.randomUUID());
  private static final WorkspaceMock WORKSPACE = WorkspaceMock.start();

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16")
          .withDatabaseName("notebook_platform")
          .withUsername("notebook")
          .withPassword("notebook");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("content.workspace.service-url", WORKSPACE::baseUrl);
    registry.add("content.workspace.internal-api-token-primary", () -> "contract-token-primary");
  }

  @LocalServerPort int port;
  private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
  private final HttpClient http = HttpClient.newHttpClient();

  @AfterAll
  static void stop() {
    WORKSPACE.stop();
  }

  @Test
  void updateWithoutIfMatch_returnsPreconditionRequired() throws Exception {
    JsonNode note =
        send(
            "POST",
            "/notebooks/" + NOTEBOOK_ID + "/notes",
            """
            {"title":"Need If-Match","contentBlocks":[{"id":"b1","type":"paragraph"}]}
            """,
            201);

    JsonNode response =
        send(
            "PATCH",
            "/notes/" + note.get("id").asText(),
            """
            {"title":"Update fails","contentBlocks":[{"id":"b1","type":"paragraph"}]}
            """,
            428);
    assertThat(response.get("errorCode").asText()).isEqualTo("PRECONDITION_REQUIRED");
  }

  private JsonNode send(String method, String path, String body, int expected) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .header("X-User-Id", OWNER.id().toString())
            .header("X-Workspace-Id", WORKSPACE_ID.toString());
    HttpResponse<String> response =
        http.send(
            builder
                .method(
                    method,
                    body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(expected);
    return response.body().isBlank()
        ? objectMapper.createObjectNode()
        : objectMapper.readTree(response.body());
  }

  private record User(UUID id) {}

  private static final class WorkspaceMock {
    private final HttpServer server;

    private WorkspaceMock(HttpServer server) {
      this.server = server;
    }

    static WorkspaceMock start() {
      try {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        WorkspaceMock mock = new WorkspaceMock(server);
        server.createContext("/", mock::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        return mock;
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    String baseUrl() {
      return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void stop() {
      server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
      if (!"contract-token-primary"
          .equals(exchange.getRequestHeaders().getFirst("X-Internal-Token"))) {
        byte[] bytes =
            "{\"errorCode\":\"INTERNAL_TOKEN_REQUIRED\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(401, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
        return;
      }

      String body =
          "{\"workspaceId\":\"%s\",\"notebookId\":\"%s\",\"role\":\"OWNER\",\"canRead\":true,\"canEdit\":true,\"canComment\":true,\"canManage\":true}"
              .formatted(WORKSPACE_ID, NOTEBOOK_ID);
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    }
  }
}
