package com.notebook.lumen.content.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class WorkspaceClientContractTest {
  private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID NOTEBOOK_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID TAG_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

  private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
  private HttpServer server;
  private WorkspaceClient client;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", this::handle);
    server.start();
    RestClient restClient =
        RestClient.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build();
    client =
        HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(WorkspaceClient.class);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void notebookPermissions_contractPathQueryAndResponseShapeAreCompatible() throws Exception {
    WorkspaceClient.NotebookPermissionResponse response =
        client.notebookPermissions(NOTEBOOK_ID, USER_ID);

    assertThat(response.workspaceId()).isEqualTo(WORKSPACE_ID);
    assertThat(response.notebookId()).isEqualTo(NOTEBOOK_ID);
    assertThat(response.role()).isEqualTo("OWNER");
    assertThat(response.canRead()).isTrue();
    assertThat(response.canEdit()).isTrue();
    assertThat(response.canComment()).isTrue();
    assertThat(response.canManage()).isTrue();

    assertRequiredFields(
        """
        {
          "workspaceId": "%s",
          "notebookId": "%s",
          "role": "OWNER",
          "canRead": true,
          "canEdit": true,
          "canComment": true,
          "canManage": true
        }
        """
            .formatted(WORKSPACE_ID, NOTEBOOK_ID),
        Set.of(
            "workspaceId", "notebookId", "role", "canRead", "canEdit", "canComment", "canManage"));
  }

  @Test
  void tagExists_contractPathQueryAndResponseShapeAreCompatible() throws Exception {
    WorkspaceClient.TagExistsResponse response = client.tagExists(WORKSPACE_ID, TAG_ID, "NOTE");

    assertThat(response.workspaceId()).isEqualTo(WORKSPACE_ID);
    assertThat(response.tagId()).isEqualTo(TAG_ID);
    assertThat(response.scope()).isEqualTo("NOTE");
    assertThat(response.exists()).isTrue();

    assertRequiredFields(
        """
        {
          "workspaceId": "%s",
          "tagId": "%s",
          "scope": "NOTE",
          "exists": true
        }
        """
            .formatted(WORKSPACE_ID, TAG_ID),
        Set.of("workspaceId", "tagId", "scope", "exists"));
  }

  @Test
  void contractSnapshotValidationRejectsMissingOrWrongTypeFields() {
    assertThatThrownBy(
            () ->
                assertRequiredFields(
                    """
                    {"workspaceId":"%s","notebookId":"%s","role":"OWNER","canRead":true}
                    """
                        .formatted(WORKSPACE_ID, NOTEBOOK_ID),
                    Set.of(
                        "workspaceId",
                        "notebookId",
                        "role",
                        "canRead",
                        "canEdit",
                        "canComment",
                        "canManage")))
        .isInstanceOf(AssertionError.class);

    assertThatThrownBy(
            () ->
                assertRequiredFields(
                    """
                    {"workspaceId":"%s","tagId":"%s","scope":"NOTE","exists":"true"}
                    """
                        .formatted(WORKSPACE_ID, TAG_ID),
                    Set.of("workspaceId", "tagId", "scope", "exists")))
        .isInstanceOf(AssertionError.class);
  }

  private void assertRequiredFields(String json, Set<String> fields) throws Exception {
    JsonNode node = objectMapper.readTree(json);
    for (String field : fields) {
      assertThat(node.has(field)).as(field).isTrue();
      assertThat(node.get(field).isNull()).as(field).isFalse();
    }
    if (fields.contains("canRead")) {
      assertThat(node.get("workspaceId").isTextual()).as("workspaceId").isTrue();
      assertThat(node.get("notebookId").isTextual()).as("notebookId").isTrue();
      assertThat(node.get("role").isTextual()).as("role").isTrue();
      assertThat(node.get("canRead").isBoolean()).as("canRead").isTrue();
      assertThat(node.get("canEdit").isBoolean()).as("canEdit").isTrue();
      assertThat(node.get("canComment").isBoolean()).as("canComment").isTrue();
      assertThat(node.get("canManage").isBoolean()).as("canManage").isTrue();
      objectMapper.readValue(json, WorkspaceClient.NotebookPermissionResponse.class);
    } else {
      assertThat(node.get("workspaceId").isTextual()).as("workspaceId").isTrue();
      assertThat(node.get("tagId").isTextual()).as("tagId").isTrue();
      assertThat(node.get("scope").isTextual()).as("scope").isTrue();
      assertThat(node.get("exists").isBoolean()).as("exists").isTrue();
      objectMapper.readValue(json, WorkspaceClient.TagExistsResponse.class);
    }
  }

  private void handle(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    String query = exchange.getRequestURI().getQuery();
    String body;
    int status = 200;

    if (path.equals("/internal/notebooks/" + NOTEBOOK_ID + "/permissions")
        && ("userId=" + USER_ID).equals(query)) {
      body =
          """
          {"workspaceId":"%s","notebookId":"%s","role":"OWNER","canRead":true,"canEdit":true,"canComment":true,"canManage":true}
          """
              .formatted(WORKSPACE_ID, NOTEBOOK_ID);
    } else if (path.equals("/internal/workspaces/" + WORKSPACE_ID + "/tags/" + TAG_ID + "/exists")
        && "scope=NOTE".equals(query)) {
      body =
          """
          {"workspaceId":"%s","tagId":"%s","scope":"NOTE","exists":true}
          """
              .formatted(WORKSPACE_ID, TAG_ID);
    } else {
      status = 404;
      body = "{\"errorCode\":\"CONTRACT_MISMATCH\"}";
    }

    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
