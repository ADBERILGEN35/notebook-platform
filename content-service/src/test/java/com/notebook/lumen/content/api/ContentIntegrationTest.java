package com.notebook.lumen.content.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.notebook.lumen.content.audit.AuditEventRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
      "content.merge.analysis-enabled=true",
      "content.merge.apply-enabled=true",
      "content.merge.supported-versions=1"
    })
class ContentIntegrationTest {
  private static final UUID WORKSPACE_ID = UUID.randomUUID();
  private static final UUID NOTEBOOK_ID = UUID.randomUUID();
  private static final UUID TAG_ID = UUID.randomUUID();
  private static final User OWNER = new User(UUID.randomUUID());
  private static final User COMMENTER = new User(UUID.randomUUID());
  private static final User VIEWER = new User(UUID.randomUUID());
  private static final User DENIED = new User(UUID.randomUUID());
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
  @Autowired private AuditEventRepository auditEventRepository;
  private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeEach
  void setup() {
    WORKSPACE.permissions.clear();
    WORKSPACE.permissions.put(OWNER.id(), new Permission(true, true, true, true));
    WORKSPACE.permissions.put(COMMENTER.id(), new Permission(true, false, true, false));
    WORKSPACE.permissions.put(VIEWER.id(), new Permission(true, false, false, false));
    WORKSPACE.tagExists = true;
  }

  @AfterAll
  static void stop() {
    WORKSPACE.stop();
  }

  @Test
  void createUpdateVersionsRestoreLinksCommentsTagsAndSearch_work() throws Exception {
    JsonNode note = createNote(OWNER, "First", blocks("paragraph", ""));
    String noteId = note.get("id").asText();
    JsonNode target = createNote(OWNER, "Target", blocks("paragraph", ""));
    String targetId = target.get("id").asText();

    JsonNode updated =
        patch(
            "/notes/" + noteId,
            OWNER,
            """
            {"title":"First Updated","contentBlocks":[{"id":"b1","type":"paragraph","props":{"noteId":"%s"}}]}
            """
                .formatted(targetId),
            200);
    assertThat(updated.get("title").asText()).isEqualTo("First Updated");

    JsonNode versions = get("/notes/" + noteId + "/versions", OWNER, 200);
    assertThat(items(versions)).hasSize(2);

    JsonNode links = get("/notes/" + noteId + "/links/outgoing", OWNER, 200);
    assertThat(items(links)).hasSize(1);
    assertThat(items(links).get(0).get("toNoteId").asText()).isEqualTo(targetId);

    JsonNode restored = post("/notes/" + noteId + "/restore/1", OWNER, null, 200);
    assertThat(restored.get("title").asText()).isEqualTo("First");
    assertThat(items(get("/notes/" + noteId + "/versions", OWNER, 200))).hasSize(3);

    JsonNode comment =
        post(
            "/notes/" + noteId + "/comments",
            COMMENTER,
            """
            {"blockId":"b1","content":"Looks good"}
            """,
            201);
    patch(
        "/comments/" + comment.get("id").asText(),
        COMMENTER,
        "{\"content\":\"Looks better\"}",
        200);
    post("/comments/" + comment.get("id").asText() + "/resolve", OWNER, null, 200);

    put("/notes/" + noteId + "/tags/" + TAG_ID, OWNER, null, 200);
    JsonNode duplicate = put("/notes/" + noteId + "/tags/" + TAG_ID, OWNER, null, 409);
    assertThat(duplicate.get("errorCode").asText()).isEqualTo("DUPLICATE_NOTE_TAG");

    JsonNode search = get("/notes/search?workspaceId=" + WORKSPACE_ID + "&q=First", OWNER, 200);
    assertThat(items(search)).isNotEmpty();
    assertThat(search.get("page").asInt()).isZero();
    assertThat(search.get("size").asInt()).isEqualTo(20);
    assertThat(auditEventRepository.count()).isGreaterThanOrEqualTo(5);
  }

  @Test
  void permissionsAndInvalidLinks_areEnforced() throws Exception {
    JsonNode note = createNote(OWNER, "Permission", blocks("paragraph", ""));
    String noteId = note.get("id").asText();

    patch(
        "/notes/" + noteId,
        COMMENTER,
        "{\"title\":\"Nope\",\"contentBlocks\":[{\"id\":\"b1\",\"type\":\"paragraph\"}]}",
        403);
    post("/notes/" + noteId + "/comments", VIEWER, "{\"content\":\"Nope\"}", 403);

    JsonNode invalidLink =
        patch(
            "/notes/" + noteId,
            OWNER,
            """
            {"title":"Bad link","contentBlocks":[{"id":"b1","type":"paragraph","props":{"href":"note://%s"}}]}
            """
                .formatted(UUID.randomUUID()),
            400);
    assertThat(invalidLink.get("errorCode").asText()).isEqualTo("INVALID_NOTE_LINK");

    JsonNode invalidWorkspace = getWithWorkspace("/notes/" + noteId, OWNER, UUID.randomUUID(), 400);
    assertThat(invalidWorkspace.get("errorCode").asText()).isEqualTo("INVALID_WORKSPACE_CONTEXT");

    JsonNode missingWorkspace = getWithoutWorkspace("/notes/" + noteId, OWNER, 400);
    assertThat(missingWorkspace.get("errorCode").asText()).isEqualTo("MISSING_WORKSPACE_CONTEXT");

    JsonNode deniedRead = get("/notes/" + noteId, DENIED, 403);
    assertThat(deniedRead.get("errorCode").asText()).isEqualTo("NOTEBOOK_ACCESS_DENIED");
  }

  @Test
  void tagExistsFalse_mapsToTagNotFound() throws Exception {
    JsonNode note = createNote(OWNER, "Tag permission", blocks("paragraph", ""));
    WORKSPACE.tagExists = false;

    JsonNode error = put("/notes/" + note.get("id").asText() + "/tags/" + TAG_ID, OWNER, null, 404);
    assertThat(error.get("errorCode").asText()).isEqualTo("TAG_NOT_FOUND");
  }

  @Test
  void noteUpdateAndRestore_supportIfMatchAndReturnEtag() throws Exception {
    JsonNode note = createNote(OWNER, "ETag", blocks("paragraph", ""));
    String noteId = note.get("id").asText();

    HttpResponse<String> getResponse = sendResponse("GET", "/notes/" + noteId, OWNER, WORKSPACE_ID, null);
    assertThat(getResponse.statusCode()).isEqualTo(200);
    String etag = getResponse.headers().firstValue("etag").orElse(null);
    assertThat(etag).isNotBlank();

    JsonNode updated =
        patchWithIfMatch(
            "/notes/" + noteId,
            OWNER,
            "{\"title\":\"Updated with If-Match\",\"contentBlocks\":[{\"id\":\"b1\",\"type\":\"paragraph\"}]}",
            etag,
            200);
    assertThat(updated.get("title").asText()).isEqualTo("Updated with If-Match");

    JsonNode staleConflict =
        patchWithIfMatch(
            "/notes/" + noteId,
            OWNER,
            "{\"title\":\"Should conflict\",\"contentBlocks\":[{\"id\":\"b1\",\"type\":\"paragraph\"}]}",
            etag,
            412);
    assertThat(staleConflict.get("errorCode").asText()).isEqualTo("NOTE_CONFLICT");

    JsonNode invalidIfMatch =
        patchWithIfMatch(
            "/notes/" + noteId,
            OWNER,
            "{\"title\":\"Invalid\",\"contentBlocks\":[{\"id\":\"b1\",\"type\":\"paragraph\"}]}",
            "\"bad-etag\"",
            400);
    assertThat(invalidIfMatch.get("errorCode").asText()).isEqualTo("INVALID_IF_MATCH_HEADER");
  }

  @Test
  void mergeAnalyze_returnsSuggestionForDisjointChanges() throws Exception {
    JsonNode created = createNote(OWNER, "Base", blocks("paragraph", ",\"props\":{\"v\":1}"));
    String noteId = created.get("id").asText();
    String baseEtag =
        sendResponse("GET", "/notes/" + noteId, OWNER, WORKSPACE_ID, null)
            .headers()
            .firstValue("etag")
            .orElseThrow();

    patch(
        "/notes/" + noteId,
        OWNER,
        "{\"title\":\"Base\",\"contentBlocks\":[{\"id\":\"b1\",\"type\":\"paragraph\",\"props\":{\"v\":2}}]}",
        200);

    JsonNode analysis =
        post(
            "/notes/" + noteId + "/merge/analyze",
            OWNER,
            """
            {
              "base":{"etag":"%s","title":"Base","contentBlocks":[{"id":"b1","type":"paragraph","props":{"v":1}}]},
              "local":{"title":"Local title","contentBlocks":[{"id":"b1","type":"paragraph","props":{"v":1}}]},
              "clientMergeVersion":1
            }
            """
                .formatted(baseEtag),
            200);

    assertThat(analysis.get("canAutoMerge").asBoolean()).isTrue();
    assertThat(analysis.get("hasConflicts").asBoolean()).isFalse();
    assertThat(analysis.get("suggested").get("title").asText()).isEqualTo("Local title");
  }

  @Test
  void mergeAnalyze_rejectsCommenterWithoutEditPermission() throws Exception {
    JsonNode created = createNote(OWNER, "Base", blocks("paragraph", ""));
    String noteId = created.get("id").asText();
    JsonNode denied =
        post(
            "/notes/" + noteId + "/merge/analyze",
            COMMENTER,
            """
            {
              "base":{"etag":"\\"note-rev-0\\"","title":"Base","contentBlocks":[{"id":"b1","type":"paragraph"}]},
              "local":{"title":"Local","contentBlocks":[{"id":"b1","type":"paragraph"}]},
              "clientMergeVersion":1
            }
            """,
            403);
    assertThat(denied.get("errorCode").asText()).isEqualTo("NOTE_UPDATE_FORBIDDEN");
  }

  @Test
  void mergeApply_updatesNoteWhenSafeAndExpectedRemoteMatches() throws Exception {
    JsonNode created = createNote(OWNER, "Base", blocks("paragraph", ",\"props\":{\"v\":1}"));
    String noteId = created.get("id").asText();
    String baseEtag =
        sendResponse("GET", "/notes/" + noteId, OWNER, WORKSPACE_ID, null)
            .headers()
            .firstValue("etag")
            .orElseThrow();

    patch(
        "/notes/" + noteId,
        OWNER,
        "{\"title\":\"Base\",\"contentBlocks\":[{\"id\":\"b1\",\"type\":\"paragraph\",\"props\":{\"v\":2}}]}",
        200);
    String remoteEtag =
        sendResponse("GET", "/notes/" + noteId, OWNER, WORKSPACE_ID, null)
            .headers()
            .firstValue("etag")
            .orElseThrow();

    JsonNode applied =
        post(
            "/notes/" + noteId + "/merge/apply",
            OWNER,
            """
            {
              "base":{"etag":"%s","title":"Base","contentBlocks":[{"id":"b1","type":"paragraph","props":{"v":1}}]},
              "local":{"title":"Local title","contentBlocks":[{"id":"b1","type":"paragraph","props":{"v":1}}]},
              "expectedRemoteEtag":"%s",
              "mergeVersion":1,
              "idempotencyKey":"merge-apply-idem-1"
            }
            """
                .formatted(baseEtag, remoteEtag),
            200);
    assertThat(applied.get("merged").asBoolean()).isTrue();
    assertThat(applied.get("title").asText()).isEqualTo("Local title");
    assertThat(items(get("/notes/" + noteId + "/versions", OWNER, 200))).hasSize(3);
  }

  private JsonNode createNote(User user, String title, String blocks) throws Exception {
    return post(
        "/notebooks/" + NOTEBOOK_ID + "/notes",
        user,
        """
            {"title":"%s","contentBlocks":%s}
            """
            .formatted(title, blocks),
        201);
  }

  private JsonNode items(JsonNode page) {
    assertThat(page.has("items")).as(page.toString()).isTrue();
    return page.get("items");
  }

  private String blocks(String type, String extra) {
    return "[{\"id\":\"b1\",\"type\":\"%s\"%s}]".formatted(type, extra);
  }

  private JsonNode get(String path, User user, int status) throws Exception {
    return send("GET", path, user, WORKSPACE_ID, null, status);
  }

  private JsonNode getWithWorkspace(String path, User user, UUID workspaceId, int status)
      throws Exception {
    return send("GET", path, user, workspaceId, null, status);
  }

  private JsonNode getWithoutWorkspace(String path, User user, int status) throws Exception {
    return send("GET", path, user, null, null, status);
  }

  private JsonNode post(String path, User user, String body, int status) throws Exception {
    return send("POST", path, user, WORKSPACE_ID, body, status);
  }

  private JsonNode put(String path, User user, String body, int status) throws Exception {
    return send("PUT", path, user, WORKSPACE_ID, body, status);
  }

  private JsonNode patch(String path, User user, String body, int status) throws Exception {
    return send("PATCH", path, user, WORKSPACE_ID, body, status);
  }

  private JsonNode patchWithIfMatch(
      String path, User user, String body, String ifMatch, int status) throws Exception {
    return send("PATCH", path, user, WORKSPACE_ID, body, status, ifMatch);
  }

  private JsonNode send(
      String method, String path, User user, UUID workspaceId, String body, int expected)
      throws Exception {
    return send(method, path, user, workspaceId, body, expected, null);
  }

  private JsonNode send(
      String method,
      String path,
      User user,
      UUID workspaceId,
      String body,
      int expected,
      String ifMatch)
      throws Exception {
    HttpResponse<String> response = sendResponse(method, path, user, workspaceId, body, ifMatch);
    assertThat(response.statusCode()).as(response.body()).isEqualTo(expected);
    return response.body().isBlank()
        ? objectMapper.createObjectNode()
        : objectMapper.readTree(response.body());
  }

  private HttpResponse<String> sendResponse(
      String method, String path, User user, UUID workspaceId, String body) throws Exception {
    return sendResponse(method, path, user, workspaceId, body, null);
  }

  private HttpResponse<String> sendResponse(
      String method, String path, User user, UUID workspaceId, String body, String ifMatch)
      throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .header("X-User-Id", user.id().toString());
    if (workspaceId != null) {
      builder.header("X-Workspace-Id", workspaceId.toString());
    }
    if (ifMatch != null) {
      builder.header("If-Match", ifMatch);
    }
    return http.send(
        builder
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private record User(UUID id) {}

  private record Permission(
      boolean canRead, boolean canEdit, boolean canComment, boolean canManage) {}

  private static final class WorkspaceMock {
    private final HttpServer server;
    private final Map<UUID, Permission> permissions = new ConcurrentHashMap<>();
    volatile boolean tagExists = true;

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
      String path = exchange.getRequestURI().getPath();
      String query = exchange.getRequestURI().getQuery();
      String body;
      if (path.contains("/permissions")) {
        UUID userId = UUID.fromString(query.substring(query.indexOf("userId=") + 7));
        Permission p = permissions.getOrDefault(userId, new Permission(false, false, false, false));
        body =
            "{\"workspaceId\":\"%s\",\"notebookId\":\"%s\",\"role\":\"TEST\",\"canRead\":%s,\"canEdit\":%s,\"canComment\":%s,\"canManage\":%s}"
                .formatted(
                    WORKSPACE_ID,
                    NOTEBOOK_ID,
                    p.canRead(),
                    p.canEdit(),
                    p.canComment(),
                    p.canManage());
      } else {
        body =
            "{\"workspaceId\":\"%s\",\"tagId\":\"%s\",\"scope\":\"NOTE\",\"exists\":%s}"
                .formatted(WORKSPACE_ID, TAG_ID, tagExists);
      }
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    }
  }
}
