package com.notebook.lumen.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import com.notebook.lumen.content.client.WorkspaceClient;
import com.notebook.lumen.content.domain.Note;
import com.notebook.lumen.content.dto.NoteMergeDtos.NoteMergeAnalyzeRequest;
import com.notebook.lumen.content.dto.NoteMergeDtos.NoteMergeLocalSnapshot;
import com.notebook.lumen.content.dto.NoteMergeDtos.NoteMergeSnapshot;
import com.notebook.lumen.content.shared.UserContext;
import com.notebook.lumen.content.shared.exception.ContentException;
import com.notebook.lumen.content.tenant.TenantDatabaseSession;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class NoteMergeAnalyzeServiceTest {
  @Mock private NoteService noteService;
  @Mock private PermissionService permissionService;
  @Mock private TenantDatabaseSession tenantDatabaseSession;
  @Mock private BlockValidationService blockValidationService;
  @Mock private NoteEtagSupport noteEtagSupport;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private NoteMergeAnalyzeService service;

  @BeforeEach
  void setUp() {
    service =
        new NoteMergeAnalyzeService(
            noteService,
            permissionService,
            tenantDatabaseSession,
            blockValidationService,
            noteEtagSupport,
            objectMapper,
            new SimpleMeterRegistry(),
            true,
            "1",
            true);
  }

  @Test
  void analyze_returnsAutoMergeForDisjointTitleAndContentChanges() throws Exception {
    UUID noteId = UUID.randomUUID();
    UUID workspaceId = UUID.randomUUID();
    UUID notebookId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    when(noteService.load(noteId))
        .thenReturn(
            new Note(
                noteId,
                workspaceId,
                notebookId,
                null,
                "Base",
                "[{\"id\":\"b1\",\"type\":\"paragraph\",\"props\":{\"v\":2}}]",
                1,
                userId,
                Instant.now()));
    doNothing()
        .when(noteService)
        .assertAggregateWorkspaceHeader(new UserContext(userId, workspaceId), workspaceId);
    when(permissionService.requireWritable(userId, notebookId))
        .thenReturn(
            new WorkspaceClient.NotebookPermissionResponse(
                workspaceId, notebookId, "OWNER", true, true, true, true));
    when(noteEtagSupport.buildEtag(0)).thenReturn("note-rev-0");
    when(noteEtagSupport.parseIfMatchRevision("\"note-rev-0\"")).thenReturn(0L);

    NoteMergeAnalyzeRequest request =
        new NoteMergeAnalyzeRequest(
            new NoteMergeSnapshot(
                "\"note-rev-0\"",
                "Base",
                objectMapper.readTree("[{\"id\":\"b1\",\"type\":\"paragraph\",\"props\":{\"v\":1}}]")),
            new NoteMergeLocalSnapshot(
                "Local title",
                objectMapper.readTree("[{\"id\":\"b1\",\"type\":\"paragraph\",\"props\":{\"v\":1}}]")),
            1);

    var response = service.analyze(new UserContext(userId, workspaceId), noteId, request);
    assertThat(response.canAutoMerge()).isTrue();
    assertThat(response.hasConflicts()).isFalse();
    assertThat(response.suggested()).isNotNull();
    assertThat(response.suggested().title()).isEqualTo("Local title");
  }

  @Test
  void analyze_returnsConflictWhenSameBlockChangedBothSides() throws Exception {
    UUID noteId = UUID.randomUUID();
    UUID workspaceId = UUID.randomUUID();
    UUID notebookId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    when(noteService.load(noteId))
        .thenReturn(
            new Note(
                noteId,
                workspaceId,
                notebookId,
                null,
                "Base",
                "[{\"id\":\"b1\",\"type\":\"paragraph\",\"props\":{\"v\":3}}]",
                1,
                userId,
                Instant.now()));
    doNothing()
        .when(noteService)
        .assertAggregateWorkspaceHeader(new UserContext(userId, workspaceId), workspaceId);
    when(permissionService.requireWritable(userId, notebookId))
        .thenReturn(
            new WorkspaceClient.NotebookPermissionResponse(
                workspaceId, notebookId, "OWNER", true, true, true, true));
    when(noteEtagSupport.buildEtag(0)).thenReturn("note-rev-0");
    when(noteEtagSupport.parseIfMatchRevision("\"note-rev-0\"")).thenReturn(0L);

    NoteMergeAnalyzeRequest request =
        new NoteMergeAnalyzeRequest(
            new NoteMergeSnapshot(
                "\"note-rev-0\"",
                "Base",
                objectMapper.readTree("[{\"id\":\"b1\",\"type\":\"paragraph\",\"props\":{\"v\":1}}]")),
            new NoteMergeLocalSnapshot(
                "Base",
                objectMapper.readTree("[{\"id\":\"b1\",\"type\":\"paragraph\",\"props\":{\"v\":2}}]")),
            1);

    var response = service.analyze(new UserContext(userId, workspaceId), noteId, request);
    assertThat(response.canAutoMerge()).isFalse();
    assertThat(response.hasConflicts()).isTrue();
    assertThat(response.conflicts()).extracting("type").contains("SAME_BLOCK_CHANGED");
  }

  @Test
  void analyze_rejectsUnsupportedVersion() {
    UUID noteId = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                service.analyze(
                    new UserContext(UUID.randomUUID(), UUID.randomUUID()),
                    noteId,
                    new NoteMergeAnalyzeRequest(
                        new NoteMergeSnapshot("etag", "Base", objectMapper.createArrayNode()),
                        new NoteMergeLocalSnapshot("Local", objectMapper.createArrayNode()),
                        99)))
        .isInstanceOf(ContentException.class)
        .hasMessageContaining("Unsupported merge version");
  }
}
