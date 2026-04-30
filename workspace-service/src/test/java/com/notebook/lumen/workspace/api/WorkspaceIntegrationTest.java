package com.notebook.lumen.workspace.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.workspace.audit.AuditEventRepository;
import com.notebook.lumen.workspace.domain.Invitation;
import com.notebook.lumen.workspace.domain.WorkspaceRole;
import com.notebook.lumen.workspace.repository.InvitationRepository;
import com.notebook.lumen.workspace.service.InvitationTokenService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
      "workspace.invitations.expose-token-in-response=true"
    })
class WorkspaceIntegrationTest {

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

  @Autowired private InvitationRepository invitationRepository;

  @Autowired private InvitationTokenService tokenService;

  @Autowired private AuditEventRepository auditEventRepository;

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void createWorkspace_listAndOwnerMemberCreated() throws Exception {
    User owner = new User(UUID.randomUUID(), "owner-" + UUID.randomUUID() + "@example.com");

    JsonNode workspace =
        postJson(
            "/workspaces",
            owner,
            """
            {"name":"Product Team","type":"TEAM"}\
            """,
            201);

    assertThat(workspace.get("slug").asText()).isEqualTo("product-team");

    JsonNode list = getJson("/workspaces", owner, 200);
    assertThat(list).hasSize(1);

    JsonNode members =
        getJson("/workspaces/" + workspace.get("id").asText() + "/members", owner, 200);
    assertThat(members).hasSize(1);
    assertThat(members.get(0).get("userId").asText()).isEqualTo(owner.id().toString());
    assertThat(members.get(0).get("role").asText()).isEqualTo("OWNER");
    assertThat(auditEventRepository.count()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void missingUserContext_andValidationErrors_includeStandardFields() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/workspaces"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"No User\",\"type\":\"TEAM\"}"))
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(401);
    JsonNode error = objectMapper.readTree(response.body());
    assertThat(error.get("errorCode").asText()).isEqualTo("MISSING_USER_CONTEXT");
    assertThat(error.get("requestId").asText()).isNotBlank();

    User owner = new User(UUID.randomUUID(), "owner-" + UUID.randomUUID() + "@example.com");
    JsonNode validation = postJson("/workspaces", owner, "{\"name\":\"\",\"type\":\"TEAM\"}", 400);
    assertThat(validation.get("errorCode").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(validation.get("fieldErrors")).isNotEmpty();
  }

  @Test
  void personalWorkspace_isUniquePerUser() throws Exception {
    User owner = new User(UUID.randomUUID(), "personal-" + UUID.randomUUID() + "@example.com");

    postJson(
        "/workspaces",
        owner,
        """
            {"name":"Personal","type":"PERSONAL"}\
            """,
        201);

    JsonNode error =
        postJson(
            "/workspaces",
            owner,
            """
            {"name":"Personal 2","type":"PERSONAL"}\
            """,
            409);
    assertThat(error.get("errorCode").asText()).isEqualTo("PERSONAL_WORKSPACE_ALREADY_EXISTS");
  }

  @Test
  void ownerSafetyAndRoleRules_areEnforced() throws Exception {
    User owner = new User(UUID.randomUUID(), "owner-" + UUID.randomUUID() + "@example.com");
    User member = new User(UUID.randomUUID(), "member-" + UUID.randomUUID() + "@example.com");
    User admin = new User(UUID.randomUUID(), "admin-" + UUID.randomUUID() + "@example.com");
    JsonNode workspace = createTeam(owner, "Rules Team");
    String workspaceId = workspace.get("id").asText();
    acceptInvite(owner, member, workspaceId, "MEMBER");
    acceptInvite(owner, admin, workspaceId, "ADMIN");

    JsonNode memberRoleUpdate =
        patchJson(
            "/workspaces/" + workspaceId + "/members/" + member.id() + "/role",
            member,
            """
            {"role":"ADMIN"}\
            """,
            403);
    assertThat(memberRoleUpdate.get("errorCode").asText()).isEqualTo("WORKSPACE_ACCESS_DENIED");

    JsonNode downgrade =
        patchJson(
            "/workspaces/" + workspaceId + "/members/" + owner.id() + "/role",
            owner,
            """
            {"role":"ADMIN"}\
            """,
            409);
    assertThat(downgrade.get("errorCode").asText()).isEqualTo("LAST_OWNER_CANNOT_BE_CHANGED");

    JsonNode remove =
        deleteJson("/workspaces/" + workspaceId + "/members/" + owner.id(), owner, 409);
    assertThat(remove.get("errorCode").asText()).isEqualTo("LAST_OWNER_CANNOT_BE_REMOVED");

    JsonNode adminOwnerInvite =
        postJson(
            "/workspaces/" + workspaceId + "/invitations",
            admin,
            """
            {"email":"new-owner@example.com","role":"OWNER"}\
            """,
            403);
    assertThat(adminOwnerInvite.get("errorCode").asText()).isEqualTo("WORKSPACE_ACCESS_DENIED");
  }

  @Test
  void notebookMembersTagsAndDuplicateTagRules_work() throws Exception {
    User owner = new User(UUID.randomUUID(), "owner-" + UUID.randomUUID() + "@example.com");
    User editor = new User(UUID.randomUUID(), "editor-" + UUID.randomUUID() + "@example.com");
    JsonNode workspace = createTeam(owner, "Notebook Team");
    String workspaceId = workspace.get("id").asText();
    acceptInvite(owner, editor, workspaceId, "MEMBER");

    JsonNode notebook =
        postJson(
            "/workspaces/" + workspaceId + "/notebooks",
            owner,
            """
            {"name":"Roadmap","icon":"book"}\
            """,
            201);
    String notebookId = notebook.get("id").asText();

    JsonNode upsert =
        putJson(
            "/notebooks/" + notebookId + "/members/" + editor.id(),
            owner,
            """
            {"role":"EDITOR"}\
            """,
            200);
    assertThat(upsert.get("role").asText()).isEqualTo("EDITOR");

    JsonNode updated =
        patchJson(
            "/notebooks/" + notebookId,
            editor,
            """
            {"name":"Roadmap 2026","icon":"book"}\
            """,
            200);
    assertThat(updated.get("name").asText()).isEqualTo("Roadmap 2026");

    JsonNode tag =
        postJson(
            "/workspaces/" + workspaceId + "/tags",
            owner,
            """
            {"name":"Planning","color":"#22cc88","scope":"NOTEBOOK"}\
            """,
            201);
    putJson("/notebooks/" + notebookId + "/tags/" + tag.get("id").asText(), owner, null, 204);

    JsonNode duplicate =
        postJson(
            "/workspaces/" + workspaceId + "/tags",
            owner,
            """
            {"name":"planning","color":"#22cc88","scope":"NOTEBOOK"}\
            """,
            409);
    assertThat(duplicate.get("errorCode").asText()).isEqualTo("DUPLICATE_TAG");
  }

  @Test
  void internalNotebookPermissionContract_mapsWorkspaceAndNotebookRoles() throws Exception {
    User owner = new User(UUID.randomUUID(), "owner-" + UUID.randomUUID() + "@example.com");
    User admin = new User(UUID.randomUUID(), "admin-" + UUID.randomUUID() + "@example.com");
    User member = new User(UUID.randomUUID(), "member-" + UUID.randomUUID() + "@example.com");
    User editor = new User(UUID.randomUUID(), "editor-" + UUID.randomUUID() + "@example.com");
    User commenter = new User(UUID.randomUUID(), "commenter-" + UUID.randomUUID() + "@example.com");
    User viewer = new User(UUID.randomUUID(), "viewer-" + UUID.randomUUID() + "@example.com");
    User outsider = new User(UUID.randomUUID(), "outsider-" + UUID.randomUUID() + "@example.com");
    JsonNode workspace = createTeam(owner, "Internal Permission Team");
    String workspaceId = workspace.get("id").asText();
    acceptInvite(owner, admin, workspaceId, "ADMIN");
    acceptInvite(owner, member, workspaceId, "MEMBER");
    acceptInvite(owner, editor, workspaceId, "MEMBER");
    acceptInvite(owner, commenter, workspaceId, "MEMBER");
    acceptInvite(owner, viewer, workspaceId, "MEMBER");

    JsonNode notebook =
        postJson(
            "/workspaces/" + workspaceId + "/notebooks",
            owner,
            """
            {"name":"Internal Contract","icon":"book"}\
            """,
            201);
    String notebookId = notebook.get("id").asText();
    upsertNotebookMember(owner, notebookId, editor, "EDITOR");
    upsertNotebookMember(owner, notebookId, commenter, "COMMENTER");
    upsertNotebookMember(owner, notebookId, viewer, "VIEWER");

    assertFullAccess(internalPermissions(notebookId, owner.id()));
    assertFullAccess(internalPermissions(notebookId, admin.id()));

    JsonNode editorPermission = internalPermissions(notebookId, editor.id());
    assertThat(editorPermission.get("role").asText()).isEqualTo("EDITOR");
    assertThat(editorPermission.get("canRead").asBoolean()).isTrue();
    assertThat(editorPermission.get("canEdit").asBoolean()).isTrue();
    assertThat(editorPermission.get("canComment").asBoolean()).isTrue();
    assertThat(editorPermission.get("canManage").asBoolean()).isFalse();

    JsonNode commenterPermission = internalPermissions(notebookId, commenter.id());
    assertThat(commenterPermission.get("role").asText()).isEqualTo("COMMENTER");
    assertThat(commenterPermission.get("canRead").asBoolean()).isTrue();
    assertThat(commenterPermission.get("canEdit").asBoolean()).isFalse();
    assertThat(commenterPermission.get("canComment").asBoolean()).isTrue();
    assertThat(commenterPermission.get("canManage").asBoolean()).isFalse();

    JsonNode viewerPermission = internalPermissions(notebookId, viewer.id());
    assertThat(viewerPermission.get("role").asText()).isEqualTo("VIEWER");
    assertThat(viewerPermission.get("canRead").asBoolean()).isTrue();
    assertThat(viewerPermission.get("canComment").asBoolean()).isFalse();

    JsonNode memberPermission = internalPermissions(notebookId, member.id());
    assertThat(memberPermission.get("role").asText()).isEqualTo("VIEWER");
    assertThat(memberPermission.get("canRead").asBoolean()).isTrue();
    assertThat(memberPermission.get("canEdit").asBoolean()).isFalse();

    JsonNode outsiderPermission = internalPermissions(notebookId, outsider.id());
    assertThat(outsiderPermission.get("role").isNull()).isTrue();
    assertThat(outsiderPermission.get("canRead").asBoolean()).isFalse();
    assertThat(outsiderPermission.get("canEdit").asBoolean()).isFalse();
    assertThat(outsiderPermission.get("canComment").asBoolean()).isFalse();
    assertThat(outsiderPermission.get("canManage").asBoolean()).isFalse();
  }

  @Test
  void internalTagExistsContract_checksScopeWorkspaceAndArchiveState() throws Exception {
    User owner = new User(UUID.randomUUID(), "owner-" + UUID.randomUUID() + "@example.com");
    JsonNode workspace = createTeam(owner, "Internal Tag Team");
    String workspaceId = workspace.get("id").asText();

    JsonNode noteTag =
        postJson(
            "/workspaces/" + workspaceId + "/tags",
            owner,
            """
            {"name":"Important","color":"#22cc88","scope":"NOTE"}\
            """,
            201);
    String tagId = noteTag.get("id").asText();

    JsonNode exists = internalTagExists(workspaceId, tagId, "NOTE");
    assertThat(exists.get("workspaceId").asText()).isEqualTo(workspaceId);
    assertThat(exists.get("tagId").asText()).isEqualTo(tagId);
    assertThat(exists.get("scope").asText()).isEqualTo("NOTE");
    assertThat(exists.get("exists").asBoolean()).isTrue();

    assertThat(internalTagExists(workspaceId, tagId, "NOTEBOOK").get("exists").asBoolean())
        .isFalse();

    deleteJson("/tags/" + tagId, owner, 204);
    assertThat(internalTagExists(workspaceId, tagId, "NOTE").get("exists").asBoolean()).isFalse();
  }

  @Test
  void invitationAccept_validatesEmailAndState() throws Exception {
    User owner = new User(UUID.randomUUID(), "owner-" + UUID.randomUUID() + "@example.com");
    User invitee = new User(UUID.randomUUID(), "invitee-" + UUID.randomUUID() + "@example.com");
    User wrong = new User(UUID.randomUUID(), "wrong-" + UUID.randomUUID() + "@example.com");
    JsonNode workspace = createTeam(owner, "Invite Team");
    String workspaceId = workspace.get("id").asText();

    JsonNode invitation = createInvitation(owner, workspaceId, invitee.email(), "ADMIN");
    String token = invitation.get("inviteToken").asText();

    JsonNode mismatch =
        postJson(
            "/invitations/accept",
            wrong,
            """
            {"token":"%s"}\
            """
                .formatted(token),
            403);
    assertThat(mismatch.get("errorCode").asText()).isEqualTo("INVITATION_EMAIL_MISMATCH");

    JsonNode accepted =
        postJson(
            "/invitations/accept",
            invitee,
            """
            {"token":"%s"}\
            """
                .formatted(token),
            200);
    assertThat(accepted.get("acceptedAt").isNull()).isFalse();

    JsonNode reused =
        postJson(
            "/invitations/accept",
            invitee,
            """
            {"token":"%s"}\
            """
                .formatted(token),
            400);
    assertThat(reused.get("errorCode").asText()).isEqualTo("INVITATION_ALREADY_ACCEPTED");

    JsonNode members = getJson("/workspaces/" + workspaceId + "/members", owner, 200);
    assertThat(members.toString()).contains(invitee.id().toString()).contains("ADMIN");
  }

  @Test
  void expiredInvitation_isRejected() throws Exception {
    User owner = new User(UUID.randomUUID(), "owner-" + UUID.randomUUID() + "@example.com");
    User invitee = new User(UUID.randomUUID(), "expired-" + UUID.randomUUID() + "@example.com");
    JsonNode workspace = createTeam(owner, "Expired Invite Team");
    String token = tokenService.generatePlaintextToken();
    Instant now = Instant.now();
    invitationRepository.save(
        new Invitation(
            UUID.randomUUID(),
            UUID.fromString(workspace.get("id").asText()),
            invitee.email(),
            tokenService.hash(token),
            WorkspaceRole.MEMBER,
            now.minusSeconds(60),
            owner.id(),
            now.minusSeconds(120)));

    JsonNode error =
        postJson(
            "/invitations/accept",
            invitee,
            """
            {"token":"%s"}\
            """
                .formatted(token),
            400);
    assertThat(error.get("errorCode").asText()).isEqualTo("INVITATION_EXPIRED");
  }

  private JsonNode createTeam(User owner, String name) throws Exception {
    return postJson(
        "/workspaces",
        owner,
        """
            {"name":"%s","type":"TEAM"}\
            """
            .formatted(name),
        201);
  }

  private JsonNode createInvitation(User owner, String workspaceId, String email, String role)
      throws Exception {
    return postJson(
        "/workspaces/" + workspaceId + "/invitations",
        owner,
        """
            {"email":"%s","role":"%s"}\
            """
            .formatted(email, role),
        201);
  }

  private void acceptInvite(User owner, User invitee, String workspaceId, String role)
      throws Exception {
    JsonNode invitation = createInvitation(owner, workspaceId, invitee.email(), role);
    postJson(
        "/invitations/accept",
        invitee,
        """
            {"token":"%s"}\
            """
            .formatted(invitation.get("inviteToken").asText()),
        200);
  }

  private void upsertNotebookMember(User owner, String notebookId, User member, String role)
      throws Exception {
    putJson(
        "/notebooks/" + notebookId + "/members/" + member.id(),
        owner,
        """
            {"role":"%s"}\
            """
            .formatted(role),
        200);
  }

  private JsonNode internalPermissions(String notebookId, UUID userId) throws Exception {
    return internalGet("/internal/notebooks/" + notebookId + "/permissions?userId=" + userId, 200);
  }

  private JsonNode internalTagExists(String workspaceId, String tagId, String scope)
      throws Exception {
    return internalGet(
        "/internal/workspaces/" + workspaceId + "/tags/" + tagId + "/exists?scope=" + scope, 200);
  }

  private JsonNode internalGet(String path, int expectedStatus) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + path)).GET().build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
    return response.body() == null || response.body().isBlank()
        ? objectMapper.createObjectNode()
        : objectMapper.readTree(response.body());
  }

  private void assertFullAccess(JsonNode permission) {
    assertThat(permission.get("canRead").asBoolean()).isTrue();
    assertThat(permission.get("canEdit").asBoolean()).isTrue();
    assertThat(permission.get("canComment").asBoolean()).isTrue();
    assertThat(permission.get("canManage").asBoolean()).isTrue();
  }

  private JsonNode getJson(String path, User user, int expectedStatus) throws Exception {
    return send("GET", path, user, null, expectedStatus);
  }

  private JsonNode postJson(String path, User user, String body, int expectedStatus)
      throws Exception {
    return send("POST", path, user, body, expectedStatus);
  }

  private JsonNode putJson(String path, User user, String body, int expectedStatus)
      throws Exception {
    return send("PUT", path, user, body, expectedStatus);
  }

  private JsonNode patchJson(String path, User user, String body, int expectedStatus)
      throws Exception {
    return send("PATCH", path, user, body, expectedStatus);
  }

  private JsonNode deleteJson(String path, User user, int expectedStatus) throws Exception {
    return send("DELETE", path, user, null, expectedStatus);
  }

  private JsonNode send(String method, String path, User user, String body, int expectedStatus)
      throws Exception {
    HttpRequest.BodyPublisher publisher =
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body);
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .header("X-User-Id", user.id().toString())
            .header("X-User-Email", user.email())
            .method(method, publisher)
            .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
    if (response.body() == null || response.body().isBlank()) {
      return objectMapper.createObjectNode();
    }
    return objectMapper.readTree(response.body());
  }

  private record User(UUID id, String email) {}
}
