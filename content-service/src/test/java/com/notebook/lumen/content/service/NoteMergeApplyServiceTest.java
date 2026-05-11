package com.notebook.lumen.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.notebook.lumen.content.audit.AuditService;
import com.notebook.lumen.content.domain.NoteMergeIdempotencyKey;
import com.notebook.lumen.content.dto.NoteMergeDtos.*;
import com.notebook.lumen.content.repository.NoteMergeIdempotencyKeyRepository;
import com.notebook.lumen.content.shared.UserContext;
import com.notebook.lumen.content.shared.exception.ContentException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class NoteMergeApplyServiceTest {
  @Mock private NoteMergeAnalyzeService analyzeService;
  @Mock private NoteService noteService;
  @Mock private AuditService auditService;
  @Mock private NoteEtagSupport noteEtagSupport;
  @Mock private NoteMergeIdempotencyKeyRepository idempotencyRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private NoteMergeApplyService service;

  @BeforeEach
  void setUp() {
    service =
        new NoteMergeApplyService(
            analyzeService,
            noteService,
            auditService,
            noteEtagSupport,
            idempotencyRepository,
            new SimpleMeterRegistry(),
            true,
            true,
            true,
            false,
            24);
  }

  @Test
  void apply_succeedsForAutoMergeAndExpectedEtagMatch() throws Exception {
    UUID noteId = UUID.randomUUID();
    UUID workspaceId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UserContext user = new UserContext(userId, workspaceId);
    NoteMergeApplyRequest request =
        new NoteMergeApplyRequest(
            new NoteMergeSnapshot(
                "\"note-rev-0\"",
                "Base",
                objectMapper.readTree("[{\"id\":\"b1\",\"type\":\"paragraph\"}]")),
            new NoteMergeLocalSnapshot(
                "Local", objectMapper.readTree("[{\"id\":\"b1\",\"type\":\"paragraph\"}]")),
            "\"note-rev-1\"",
            1,
            "idem-1");

    when(noteEtagSupport.parseIfMatchRevision("\"note-rev-1\"")).thenReturn(1L);
    when(idempotencyRepository.findByUserIdAndNoteIdAndIdempotencyKey(userId, noteId, "idem-1"))
        .thenReturn(Optional.empty());
    when(idempotencyRepository.save(any(NoteMergeIdempotencyKey.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(analyzeService.analyze(
            user,
            noteId,
            new NoteMergeAnalyzeRequest(request.base(), request.local(), request.mergeVersion())))
        .thenReturn(
            new NoteMergeAnalyzeResponse(
                noteId,
                "\"note-rev-0\"",
                "\"note-rev-1\"",
                1,
                List.of(1),
                true,
                false,
                new NoteMergeSummary(List.of("local"), List.of("remote"), List.of()),
                new NoteMergeSuggestion(
                    "Merged", objectMapper.readTree("[{\"id\":\"b1\",\"type\":\"paragraph\"}]")),
                List.of()));
    var noteResponse =
        new com.notebook.lumen.content.dto.NoteResponse(
            noteId,
            workspaceId,
            UUID.randomUUID(),
            null,
            "Merged",
            objectMapper.readTree("[{\"id\":\"b1\",\"type\":\"paragraph\"}]"),
            1,
            userId,
            userId,
            Instant.now(),
            Instant.now(),
            null,
            2L);
    when(noteService.applyMerged(
            user,
            noteId,
            "\"note-rev-1\"",
            "Merged",
            objectMapper.readTree("[{\"id\":\"b1\",\"type\":\"paragraph\"}]"),
            1,
            "\"note-rev-0\"",
            "idem-1"))
        .thenReturn(new NoteService.MergeApplyResult(noteResponse, 12));
    when(noteEtagSupport.buildEtag(2L)).thenReturn("\"note-rev-2\"");

    NoteMergeApplyService.ApplyResult result = service.apply(user, noteId, request);
    assertThat(result).isInstanceOf(NoteMergeApplyService.ApplyResult.Success.class);
    var response = ((NoteMergeApplyService.ApplyResult.Success) result).response();
    assertThat(response.merged()).isTrue();
    assertThat(response.etag()).isEqualTo("\"note-rev-2\"");
    assertThat(response.version()).isEqualTo(12);
  }

  @Test
  void apply_returnsConflictWhenAnalyzeHasConflicts() throws Exception {
    UUID noteId = UUID.randomUUID();
    UserContext user = new UserContext(UUID.randomUUID(), UUID.randomUUID());
    NoteMergeApplyRequest request =
        new NoteMergeApplyRequest(
            new NoteMergeSnapshot(
                "\"note-rev-0\"",
                "Base",
                objectMapper.readTree("[{\"id\":\"b1\",\"type\":\"paragraph\"}]")),
            new NoteMergeLocalSnapshot(
                "Local", objectMapper.readTree("[{\"id\":\"b1\",\"type\":\"paragraph\"}]")),
            "\"note-rev-1\"",
            1,
            null);
    when(noteEtagSupport.parseIfMatchRevision("\"note-rev-1\"")).thenReturn(1L);
    when(analyzeService.analyze(
            user,
            noteId,
            new NoteMergeAnalyzeRequest(request.base(), request.local(), request.mergeVersion())))
        .thenReturn(
            new NoteMergeAnalyzeResponse(
                noteId,
                "\"note-rev-0\"",
                "\"note-rev-1\"",
                1,
                List.of(1),
                false,
                true,
                new NoteMergeSummary(List.of(), List.of(), List.of("conflict")),
                null,
                List.of(new NoteMergeConflict("SAME_BLOCK_CHANGED", "b1", "Both changed"))));

    NoteMergeApplyService.ApplyResult result = service.apply(user, noteId, request);
    assertThat(result).isInstanceOf(NoteMergeApplyService.ApplyResult.Conflict.class);
  }

  @Test
  void apply_failsWhenRemoteEtagChangedAfterAnalyze() throws Exception {
    UUID noteId = UUID.randomUUID();
    UserContext user = new UserContext(UUID.randomUUID(), UUID.randomUUID());
    NoteMergeApplyRequest request =
        new NoteMergeApplyRequest(
            new NoteMergeSnapshot(
                "\"note-rev-0\"",
                "Base",
                objectMapper.readTree("[{\"id\":\"b1\",\"type\":\"paragraph\"}]")),
            new NoteMergeLocalSnapshot(
                "Local", objectMapper.readTree("[{\"id\":\"b1\",\"type\":\"paragraph\"}]")),
            "\"note-rev-1\"",
            1,
            null);
    when(noteEtagSupport.parseIfMatchRevision("\"note-rev-1\"")).thenReturn(1L);
    when(analyzeService.analyze(
            user,
            noteId,
            new NoteMergeAnalyzeRequest(request.base(), request.local(), request.mergeVersion())))
        .thenReturn(
            new NoteMergeAnalyzeResponse(
                noteId,
                "\"note-rev-0\"",
                "\"note-rev-2\"",
                1,
                List.of(1),
                true,
                false,
                new NoteMergeSummary(List.of(), List.of(), List.of()),
                new NoteMergeSuggestion(
                    "Merged", objectMapper.readTree("[{\"id\":\"b1\",\"type\":\"paragraph\"}]")),
                List.of()));

    assertThatThrownBy(() -> service.apply(user, noteId, request))
        .isInstanceOf(ContentException.class)
        .hasMessageContaining("Remote note changed");
  }
}
