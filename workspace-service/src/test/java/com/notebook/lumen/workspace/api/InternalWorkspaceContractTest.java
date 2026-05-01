package com.notebook.lumen.workspace.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.workspace.dto.InternalResponses.NotebookPermissionResponse;
import com.notebook.lumen.workspace.dto.InternalResponses.TagExistsResponse;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InternalWorkspaceContractTest {
  private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID NOTEBOOK_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID TAG_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void notebookPermissionResponseMatchesInternalContract() throws Exception {
    String json =
        objectMapper.writeValueAsString(
            new NotebookPermissionResponse(
                WORKSPACE_ID, NOTEBOOK_ID, "OWNER", true, true, true, true));

    JsonNode node =
        assertRequiredFields(
            json,
            "workspaceId",
            "notebookId",
            "role",
            "canRead",
            "canEdit",
            "canComment",
            "canManage");
    assertThat(node.get("workspaceId").isTextual()).isTrue();
    assertThat(node.get("notebookId").isTextual()).isTrue();
    assertThat(node.get("role").isTextual()).isTrue();
    assertThat(node.get("canRead").isBoolean()).isTrue();
    assertThat(node.get("canEdit").isBoolean()).isTrue();
    assertThat(node.get("canComment").isBoolean()).isTrue();
    assertThat(node.get("canManage").isBoolean()).isTrue();
  }

  @Test
  void tagExistsResponseMatchesInternalContract() throws Exception {
    String json =
        objectMapper.writeValueAsString(new TagExistsResponse(WORKSPACE_ID, TAG_ID, "NOTE", true));

    JsonNode node = assertRequiredFields(json, "workspaceId", "tagId", "scope", "exists");
    assertThat(node.get("workspaceId").isTextual()).isTrue();
    assertThat(node.get("tagId").isTextual()).isTrue();
    assertThat(node.get("scope").isTextual()).isTrue();
    assertThat(node.get("exists").isBoolean()).isTrue();
  }

  @Test
  void contractValidationRejectsMissingAndWrongTypeFields() {
    assertThatThrownBy(
            () ->
                assertRequiredFields(
                    """
                    {"workspaceId":"%s","notebookId":"%s","role":"OWNER","canRead":true}
                    """
                        .formatted(WORKSPACE_ID, NOTEBOOK_ID),
                    "workspaceId",
                    "notebookId",
                    "role",
                    "canRead",
                    "canEdit",
                    "canComment",
                    "canManage"))
        .isInstanceOf(AssertionError.class);

    assertThatThrownBy(
            () -> {
              JsonNode node =
                  assertRequiredFields(
                      """
                      {"workspaceId":"%s","tagId":"%s","scope":"NOTE","exists":"true"}
                      """
                          .formatted(WORKSPACE_ID, TAG_ID),
                      "workspaceId",
                      "tagId",
                      "scope",
                      "exists");
              assertThat(node.get("exists").isBoolean()).as("exists").isTrue();
            })
        .isInstanceOf(AssertionError.class);
  }

  private JsonNode assertRequiredFields(String json, String... fields) throws Exception {
    JsonNode node = objectMapper.readTree(json);
    for (String field : Set.of(fields)) {
      assertThat(node.has(field)).as(field).isTrue();
      assertThat(node.get(field).isNull()).as(field).isFalse();
    }
    return node;
  }
}
