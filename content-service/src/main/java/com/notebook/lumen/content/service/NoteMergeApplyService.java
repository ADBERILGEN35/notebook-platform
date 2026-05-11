package com.notebook.lumen.content.service;

import com.notebook.lumen.content.audit.AuditService;
import com.notebook.lumen.content.domain.NoteMergeIdempotencyKey;
import com.notebook.lumen.content.dto.NoteMergeDtos.*;
import com.notebook.lumen.content.repository.NoteMergeIdempotencyKeyRepository;
import com.notebook.lumen.content.shared.UserContext;
import com.notebook.lumen.content.shared.exception.ContentException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoteMergeApplyService {
  private static final Logger log = LoggerFactory.getLogger(NoteMergeApplyService.class);
  private final NoteMergeAnalyzeService analyzeService;
  private final NoteService noteService;
  private final AuditService auditService;
  private final NoteEtagSupport noteEtagSupport;
  private final NoteMergeIdempotencyKeyRepository idempotencyRepository;
  private final MeterRegistry meterRegistry;
  private final boolean applyEnabled;
  private final boolean idempotencyEnabled;
  private final boolean metricsEnabled;
  private final boolean auditFailuresEnabled;
  private final int idempotencyTtlHours;

  public NoteMergeApplyService(
      NoteMergeAnalyzeService analyzeService,
      NoteService noteService,
      AuditService auditService,
      NoteEtagSupport noteEtagSupport,
      NoteMergeIdempotencyKeyRepository idempotencyRepository,
      MeterRegistry meterRegistry,
      @Value("${content.merge.apply-enabled:false}") boolean applyEnabled,
      @Value("${content.merge.idempotency-enabled:true}") boolean idempotencyEnabled,
      @Value("${content.merge.metrics-enabled:true}") boolean metricsEnabled,
      @Value("${content.merge.audit-failures-enabled:false}") boolean auditFailuresEnabled,
      @Value("${content.merge.idempotency-ttl-hours:24}") int idempotencyTtlHours) {
    this.analyzeService = analyzeService;
    this.noteService = noteService;
    this.auditService = auditService;
    this.noteEtagSupport = noteEtagSupport;
    this.idempotencyRepository = idempotencyRepository;
    this.meterRegistry = meterRegistry;
    this.applyEnabled = applyEnabled;
    this.idempotencyEnabled = idempotencyEnabled;
    this.metricsEnabled = metricsEnabled;
    this.auditFailuresEnabled = auditFailuresEnabled;
    this.idempotencyTtlHours = idempotencyTtlHours;
  }

  @Transactional
  public ApplyResult apply(UserContext user, UUID noteId, NoteMergeApplyRequest request) {
    long startedAt = System.nanoTime();
    int mergeVersion =
        request == null || request.mergeVersion() == null ? 0 : request.mergeVersion();
    String result = "error";
    int conflictCount = 0;
    try {
      if (!applyEnabled) {
        result = "disabled";
        throw new ContentException(
            HttpStatus.NOT_FOUND, "NOTE_MERGE_APPLY_DISABLED", "Merge apply endpoint is disabled");
      }
      if (request == null || request.base() == null || request.local() == null) {
        result = "invalid";
        throw bad("INVALID_NOTE_MERGE_REQUEST", "base and local snapshots are required");
      }
      if (request.expectedRemoteEtag() == null || request.expectedRemoteEtag().isBlank()) {
        result = "invalid";
        throw bad("INVALID_NOTE_MERGE_REQUEST", "expectedRemoteEtag is required");
      }
      try {
        noteEtagSupport.parseIfMatchRevision(request.expectedRemoteEtag());
      } catch (ContentException ex) {
        result = "invalid";
        throw bad("INVALID_NOTE_MERGE_REQUEST", "expectedRemoteEtag is invalid");
      }

      String requestHash = hashRequest(request);
      NoteMergeIdempotencyKey claim = null;
      if (idempotencyEnabled
          && request.idempotencyKey() != null
          && !request.idempotencyKey().isBlank()) {
        claim = claimIdempotency(user, noteId, request.idempotencyKey(), requestHash);
        if (claim.getStatus() == NoteMergeIdempotencyKey.Status.COMPLETED) {
          incrementIdempotency("replayed");
          result = "idempotency_replay";
          var replay = buildReplayResponse(user, noteId, claim, request.mergeVersion());
          return new ApplyResult.Success(replay);
        }
      }

      NoteMergeAnalyzeResponse analysis =
          analyzeService.analyze(
              user,
              noteId,
              new NoteMergeAnalyzeRequest(request.base(), request.local(), request.mergeVersion()));
      if (!request.expectedRemoteEtag().equals(analysis.remoteEtag())) {
        if (claim != null) {
          claim.markFailed(Instant.now());
        }
        recordFailureAudit(
            user, noteId, "NOTE_MERGE_REMOTE_CHANGED", request.mergeVersion(), request);
        result = "remote_changed";
        throw new ContentException(
            HttpStatus.PRECONDITION_FAILED,
            "NOTE_MERGE_REMOTE_CHANGED",
            "Remote note changed after merge analysis");
      }
      if (analysis.hasConflicts() || analysis.suggested() == null) {
        if (claim != null) {
          claim.markFailed(Instant.now());
        }
        conflictCount = analysis.conflicts().size();
        analysis.conflicts().forEach(conflict -> incrementConflict(conflict.type(), "apply"));
        recordFailureAudit(
            user, noteId, "NOTE_MERGE_APPLY_CONFLICTED", request.mergeVersion(), request);
        result = "conflict";
        return new ApplyResult.Conflict(
            new NoteMergeApplyConflictResponse(
                "NOTE_MERGE_CONFLICTS",
                request.mergeVersion(),
                false,
                analysis.conflicts(),
                analysis.summary()));
      }

      try {
        NoteService.MergeApplyResult persisted =
            noteService.applyMerged(
                user,
                noteId,
                request.expectedRemoteEtag(),
                analysis.suggested().title(),
                analysis.suggested().contentBlocks(),
                request.mergeVersion(),
                request.base().etag(),
                request.idempotencyKey());
        String etag = noteEtagSupport.buildEtag(persisted.note().noteRevision());
        NoteMergeApplyResponse response =
            new NoteMergeApplyResponse(
                persisted.note().id(),
                true,
                request.mergeVersion(),
                etag,
                persisted.versionNumber(),
                persisted.note().title(),
                persisted.note().contentBlocks(),
                List.of(),
                analysis.summary());
        if (claim != null) {
          claim.markCompleted(
              etag, persisted.versionNumber(), persisted.note().id(), Instant.now());
        }
        cleanupExpiredIdempotency();
        result = "success";
        return new ApplyResult.Success(response);
      } catch (ContentException ex) {
        if (claim != null) {
          claim.markFailed(Instant.now());
        }
        if ("NOTE_CONFLICT".equals(ex.getErrorCode())) {
          recordFailureAudit(
              user, noteId, "NOTE_MERGE_REMOTE_CHANGED", request.mergeVersion(), request);
          result = "remote_changed";
          throw new ContentException(
              HttpStatus.PRECONDITION_FAILED,
              "NOTE_MERGE_REMOTE_CHANGED",
              "Remote note changed before merge apply completed");
        }
        throw ex;
      } catch (Exception ex) {
        if (claim != null) {
          claim.markFailed(Instant.now());
        }
        result = "error";
        throw new ContentException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "NOTE_MERGE_APPLY_FAILED",
            "Unexpected merge apply failure");
      }
    } catch (ContentException ex) {
      if ("error".equals(result)) {
        result = mapApplyResult(ex.getErrorCode());
      }
      if ("IDEMPOTENCY_KEY_REUSED".equals(ex.getErrorCode())) {
        incrementIdempotency("reused");
      } else if ("MERGE_APPLY_IN_PROGRESS".equals(ex.getErrorCode())) {
        incrementIdempotency("in_progress");
      }
      throw ex;
    } finally {
      recordDuration("note_merge_apply_duration_seconds", result, startedAt);
      if (!"idempotency_replay".equals(result)) {
        incrementApplyResult(result, mergeVersion);
      }
      long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
      log.info(
          "note_merge_apply result={} mergeVersion={} expectedRemoteEtagPresent={} idempotencyKeyPresent={} conflictCount={} durationMs={}",
          result,
          mergeVersion,
          request != null
              && request.expectedRemoteEtag() != null
              && !request.expectedRemoteEtag().isBlank(),
          request != null
              && request.idempotencyKey() != null
              && !request.idempotencyKey().isBlank(),
          conflictCount,
          durationMs);
    }
  }

  private NoteMergeIdempotencyKey claimIdempotency(
      UserContext user, UUID noteId, String key, String requestHash) {
    NoteMergeIdempotencyKey existing =
        idempotencyRepository
            .findByUserIdAndNoteIdAndIdempotencyKey(user.userId(), noteId, key)
            .orElse(null);
    if (existing != null) {
      if (!existing.getRequestHash().equals(requestHash)) {
        incrementIdempotency("reused");
        throw new ContentException(
            HttpStatus.CONFLICT,
            "IDEMPOTENCY_KEY_REUSED",
            "Idempotency key cannot be reused with a different request");
      }
      if (existing.getStatus() == NoteMergeIdempotencyKey.Status.IN_PROGRESS) {
        incrementIdempotency("in_progress");
        throw new ContentException(
            HttpStatus.CONFLICT,
            "MERGE_APPLY_IN_PROGRESS",
            "Merge apply with this idempotency key is still in progress");
      }
      return existing;
    }
    NoteMergeIdempotencyKey created =
        new NoteMergeIdempotencyKey(
            UUID.randomUUID(),
            user.workspaceId(),
            user.userId(),
            noteId,
            key,
            requestHash,
            Instant.now());
    incrementIdempotency("created");
    return idempotencyRepository.save(created);
  }

  private NoteMergeApplyResponse buildReplayResponse(
      UserContext user, UUID noteId, NoteMergeIdempotencyKey key, int mergeVersion) {
    var note = noteService.get(user, noteId);
    String etag =
        key.getResultEtag() == null
            ? noteEtagSupport.buildEtag(note.noteRevision())
            : key.getResultEtag();
    int version = key.getResultVersion() == null ? 0 : key.getResultVersion();
    return new NoteMergeApplyResponse(
        note.id(),
        true,
        mergeVersion,
        etag,
        version,
        note.title(),
        note.contentBlocks(),
        List.of(),
        new NoteMergeSummary(List.of(), List.of(), List.of()));
  }

  private String hashRequest(NoteMergeApplyRequest request) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      String payload =
          (request.base().etag() == null ? "" : request.base().etag())
              + "|"
              + request.base().title()
              + "|"
              + request.base().contentBlocks()
              + "|"
              + request.local().title()
              + "|"
              + request.local().contentBlocks()
              + "|"
              + request.expectedRemoteEtag()
              + "|"
              + request.mergeVersion();
      return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("Could not hash merge apply request", ex);
    }
  }

  private void cleanupExpiredIdempotency() {
    if (!idempotencyEnabled || idempotencyTtlHours <= 0) {
      return;
    }
    idempotencyRepository.deleteByCreatedAtBefore(
        Instant.now().minusSeconds((long) idempotencyTtlHours * 3600L));
  }

  private ContentException bad(String code, String message) {
    return new ContentException(HttpStatus.BAD_REQUEST, code, message);
  }

  private String mapApplyResult(String errorCode) {
    if ("NOTE_MERGE_CONFLICTS".equals(errorCode)) return "conflict";
    if ("NOTE_MERGE_REMOTE_CHANGED".equals(errorCode)) return "remote_changed";
    if ("UNSUPPORTED_MERGE_VERSION".equals(errorCode)) return "unsupported_version";
    if ("INVALID_NOTE_MERGE_REQUEST".equals(errorCode)
        || "INVALID_NOTE_MERGE_BASE".equals(errorCode)
        || "INVALID_NOTE_MERGE_LOCAL".equals(errorCode)) return "invalid";
    if ("NOTE_MERGE_APPLY_DISABLED".equals(errorCode)) return "disabled";
    if ("IDEMPOTENCY_KEY_REUSED".equals(errorCode)) return "idempotency_reused";
    if ("MERGE_APPLY_IN_PROGRESS".equals(errorCode)) return "idempotency_in_progress";
    if ("NOTE_MERGE_APPLY_FAILED".equals(errorCode)) return "error";
    return "error";
  }

  private void incrementApplyResult(String result, int mergeVersion) {
    if (!metricsEnabled) return;
    meterRegistry
        .counter(
            "note_merge_apply_requests_total",
            "result",
            result,
            "mergeVersion",
            String.valueOf(mergeVersion))
        .increment();
  }

  private void incrementConflict(String conflictType, String source) {
    if (!metricsEnabled) return;
    meterRegistry
        .counter("note_merge_conflicts_total", "conflictType", conflictType, "source", source)
        .increment();
  }

  private void incrementIdempotency(String result) {
    if (!metricsEnabled) return;
    meterRegistry.counter("note_merge_apply_idempotency_total", "result", result).increment();
  }

  private void recordDuration(String metricName, String result, long startedAt) {
    if (!metricsEnabled) return;
    Timer.builder(metricName)
        .tag("result", result)
        .register(meterRegistry)
        .record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
  }

  private void recordFailureAudit(
      UserContext user,
      UUID noteId,
      String eventType,
      int mergeVersion,
      NoteMergeApplyRequest request) {
    if (!auditFailuresEnabled) return;
    try {
      var note = noteService.get(user, noteId);
      auditService.record(
          eventType,
          user.userId(),
          note.workspaceId(),
          "NOTE",
          noteId,
          Map.of(
              "notebookId",
              note.notebookId().toString(),
              "mergeVersion",
              mergeVersion,
              "expectedRemoteEtagPresent",
              request.expectedRemoteEtag() != null && !request.expectedRemoteEtag().isBlank(),
              "idempotencyKeyPresent",
              request.idempotencyKey() != null && !request.idempotencyKey().isBlank(),
              "source",
              "backend_apply"));
    } catch (RuntimeException ignored) {
      // best-effort failure audit
    }
  }

  public sealed interface ApplyResult permits ApplyResult.Success, ApplyResult.Conflict {
    record Success(NoteMergeApplyResponse response) implements ApplyResult {}

    record Conflict(NoteMergeApplyConflictResponse response) implements ApplyResult {}
  }
}
