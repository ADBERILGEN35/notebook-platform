package com.notebook.lumen.content.service;

import com.notebook.lumen.content.domain.Note;
import com.notebook.lumen.content.dto.NoteMergeDtos.*;
import com.notebook.lumen.content.shared.UserContext;
import com.notebook.lumen.content.shared.exception.ContentException;
import com.notebook.lumen.content.tenant.TenantDatabaseSession;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

@Service
public class NoteMergeAnalyzeService {
  private static final Logger log = LoggerFactory.getLogger(NoteMergeAnalyzeService.class);
  private final NoteService noteService;
  private final PermissionService permissionService;
  private final TenantDatabaseSession tenantDatabaseSession;
  private final BlockValidationService blockValidationService;
  private final NoteEtagSupport noteEtagSupport;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;
  private final boolean enabled;
  private final boolean metricsEnabled;
  private final Set<Integer> supportedVersions;

  public NoteMergeAnalyzeService(
      NoteService noteService,
      PermissionService permissionService,
      TenantDatabaseSession tenantDatabaseSession,
      BlockValidationService blockValidationService,
      NoteEtagSupport noteEtagSupport,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry,
      @Value("${content.merge.analysis-enabled:false}") boolean enabled,
      @Value("${content.merge.supported-versions:1}") String supportedVersionsRaw,
      @Value("${content.merge.metrics-enabled:true}") boolean metricsEnabled) {
    this.noteService = noteService;
    this.permissionService = permissionService;
    this.tenantDatabaseSession = tenantDatabaseSession;
    this.blockValidationService = blockValidationService;
    this.noteEtagSupport = noteEtagSupport;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
    this.enabled = enabled;
    this.metricsEnabled = metricsEnabled;
    this.supportedVersions = parseSupportedVersions(supportedVersionsRaw);
  }

  public NoteMergeAnalyzeResponse analyze(
      UserContext user, UUID noteId, NoteMergeAnalyzeRequest request) {
    long startedAt = System.nanoTime();
    int mergeVersion =
        request == null || request.clientMergeVersion() == null ? 0 : request.clientMergeVersion();
    String result = "error";
    int conflictCount = 0;
    List<String> conflictTypes = List.of();
    try {
      if (!enabled) {
        result = "disabled";
        throw bad("NOTE_MERGE_ANALYSIS_DISABLED", "Merge analysis endpoint is disabled");
      }
      if (request == null || request.base() == null || request.local() == null) {
        result = "invalid";
        throw bad("INVALID_NOTE_MERGE_REQUEST", "base and local snapshots are required");
      }
      if (!supportedVersions.contains(request.clientMergeVersion())) {
        result = "unsupported_version";
        throw bad("UNSUPPORTED_MERGE_VERSION", "Unsupported merge version");
      }

      validateSnapshot(request.base().contentBlocks(), "INVALID_NOTE_MERGE_BASE");
      validateSnapshot(request.local().contentBlocks(), "INVALID_NOTE_MERGE_LOCAL");

      Note note = noteService.load(noteId);
      tenantDatabaseSession.applyWorkspace(note.getWorkspaceId());
      noteService.assertAggregateWorkspaceHeader(user, note.getWorkspaceId());
      permissionService.requireWritable(user.userId(), note.getNotebookId());

      JsonNode remoteBlocks = readStoredBlocks(note);
      try {
        blockValidationService.validate(remoteBlocks);
      } catch (ContentException ex) {
        result = "invalid";
        throw new ContentException(
            HttpStatus.CONFLICT,
            "NOTE_MERGE_UNSUPPORTED_CONTENT",
            "Latest server content is not supported for merge analysis");
      }

      if (request.base().etag() != null && !request.base().etag().isBlank()) {
        try {
          noteEtagSupport.parseIfMatchRevision(request.base().etag());
        } catch (ContentException ex) {
          result = "invalid";
          throw bad("INVALID_NOTE_MERGE_BASE", "base.etag is invalid");
        }
      }

      List<NoteMergeConflict> conflicts = new ArrayList<>();
      List<String> localChanges = new ArrayList<>();
      List<String> remoteChanges = new ArrayList<>();

      Snapshot base =
          new Snapshot(request.base().title(), deepCopyArray(request.base().contentBlocks()));
      Snapshot local =
          new Snapshot(request.local().title(), deepCopyArray(request.local().contentBlocks()));
      Snapshot remote = new Snapshot(note.getTitle(), deepCopyArray(remoteBlocks));

      analyze(base, local, remote, localChanges, remoteChanges, conflicts);
      conflictCount = conflicts.size();
      conflictTypes = conflicts.stream().map(NoteMergeConflict::type).toList();

      NoteMergeSuggestion suggestion = null;
      if (conflicts.isEmpty()) {
        suggestion = buildSuggestion(base, local, remote);
      }
      result = conflicts.isEmpty() ? "success" : "conflict";
      incrementAnalyzeResult(result, request.clientMergeVersion());
      if (suggestion != null) {
        incrementSafeSuggestion(request.clientMergeVersion());
      }
      conflictTypes.forEach(type -> incrementConflict(type, "analyze"));

      return new NoteMergeAnalyzeResponse(
          noteId,
          request.base().etag(),
          noteEtagSupport.buildEtag(note.getNoteRevision()),
          request.clientMergeVersion(),
          supportedVersions.stream().sorted().toList(),
          suggestion != null,
          !conflicts.isEmpty(),
          new NoteMergeSummary(
              localChanges,
              remoteChanges,
              conflicts.stream().map(NoteMergeConflict::message).toList()),
          suggestion,
          conflicts);
    } catch (ContentException ex) {
      if ("error".equals(result)) {
        result = mapAnalyzeResult(ex.getErrorCode());
      }
      incrementAnalyzeResult(result, mergeVersion);
      throw ex;
    } finally {
      recordDuration("note_merge_analyze_duration_seconds", result, startedAt);
      long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
      log.info(
          "note_merge_analyze result={} mergeVersion={} canAutoMerge={} conflictCount={} conflictTypes={} durationMs={}",
          result,
          mergeVersion,
          "success".equals(result),
          conflictCount,
          conflictTypes,
          durationMs);
    }
  }

  private void analyze(
      Snapshot base,
      Snapshot local,
      Snapshot remote,
      List<String> localChanges,
      List<String> remoteChanges,
      List<NoteMergeConflict> conflicts) {
    Index baseIdx = index(base.blocks());
    Index localIdx = index(local.blocks());
    Index remoteIdx = index(remote.blocks());

    if (baseIdx.hasMissingId || localIdx.hasMissingId) {
      conflicts.add(conflict("MISSING_BLOCK_ID", null, "Missing block id detected"));
      return;
    }
    if (!baseIdx.duplicateIds.isEmpty()
        || !localIdx.duplicateIds.isEmpty()
        || !remoteIdx.duplicateIds.isEmpty()) {
      conflicts.add(conflict("DUPLICATE_BLOCK_ID", null, "Duplicate block id detected"));
      return;
    }

    boolean localTitleChanged = !Objects.equals(base.title(), local.title());
    boolean remoteTitleChanged = !Objects.equals(base.title(), remote.title());
    if (localTitleChanged) localChanges.add("Title changed");
    if (remoteTitleChanged) remoteChanges.add("Title changed on server");
    if (localTitleChanged && remoteTitleChanged && !Objects.equals(local.title(), remote.title())) {
      conflicts.add(conflict("TITLE_DIVERGENT", null, "Both sides changed title differently"));
    }

    if (!Objects.equals(baseIdx.order, localIdx.order)
        || !Objects.equals(baseIdx.order, remoteIdx.order)) {
      conflicts.add(conflict("MOVE_OR_STRUCTURE", null, "Block reordering/move detected"));
    }

    Set<String> allIds = new LinkedHashSet<>();
    allIds.addAll(baseIdx.byId.keySet());
    allIds.addAll(localIdx.byId.keySet());
    allIds.addAll(remoteIdx.byId.keySet());

    for (String id : allIds) {
      String b = baseIdx.signatures.get(id);
      String l = localIdx.signatures.get(id);
      String r = remoteIdx.signatures.get(id);
      boolean localChanged = !Objects.equals(b, l);
      boolean remoteChanged = !Objects.equals(b, r);
      if (localChanged) localChanges.add("Changed block " + id);
      if (remoteChanged) remoteChanges.add("Server changed block " + id);
      if (localChanged && remoteChanged && !Objects.equals(l, r)) {
        boolean localDeleted = !localIdx.byId.containsKey(id);
        boolean remoteDeleted = !remoteIdx.byId.containsKey(id);
        if (localDeleted || remoteDeleted) {
          conflicts.add(conflict("DELETE_VS_EDIT", id, "Delete vs edit conflict on block"));
        } else {
          conflicts.add(conflict("SAME_BLOCK_CHANGED", id, "Both versions changed the same block"));
        }
      }
    }
  }

  private NoteMergeSuggestion buildSuggestion(Snapshot base, Snapshot local, Snapshot remote) {
    ArrayNode merged = deepCopyArray(remote.blocks());
    Index baseIdx = index(base.blocks());
    Index localIdx = index(local.blocks());
    Index remoteIdx = index(remote.blocks());

    for (String id : baseIdx.byId.keySet()) {
      String b = baseIdx.signatures.get(id);
      String l = localIdx.signatures.get(id);
      String r = remoteIdx.signatures.get(id);
      boolean localChanged = !Objects.equals(b, l);
      boolean remoteChanged = !Objects.equals(b, r);
      if (!localChanged || remoteChanged) continue;
      if (!localIdx.byId.containsKey(id)) {
        removeById(merged, id);
      } else {
        replaceById(merged, id, localIdx.byId.get(id));
      }
    }

    for (String id : localIdx.byId.keySet()) {
      if (baseIdx.byId.containsKey(id) || remoteIdx.byId.containsKey(id)) continue;
      if (isRootAddition(local.blocks(), id)) {
        merged.add(localIdx.byId.get(id));
      }
    }

    String mergedTitle = remote.title();
    boolean localTitleChanged = !Objects.equals(base.title(), local.title());
    boolean remoteTitleChanged = !Objects.equals(base.title(), remote.title());
    if (localTitleChanged && !remoteTitleChanged) {
      mergedTitle = local.title();
    }
    return new NoteMergeSuggestion(mergedTitle, merged);
  }

  private boolean isRootAddition(ArrayNode blocks, String id) {
    for (JsonNode block : blocks) {
      if (id.equals(text(block, "id"))) return true;
    }
    return false;
  }

  private boolean replaceById(ArrayNode blocks, String id, JsonNode replacement) {
    for (int i = 0; i < blocks.size(); i++) {
      JsonNode block = blocks.get(i);
      if (id.equals(text(block, "id"))) {
        blocks.set(i, replacement.deepCopy());
        return true;
      }
      JsonNode children = block.get("children");
      if (children != null
          && children.isArray()
          && replaceById((ArrayNode) children, id, replacement)) {
        return true;
      }
    }
    return false;
  }

  private boolean removeById(ArrayNode blocks, String id) {
    for (int i = 0; i < blocks.size(); i++) {
      JsonNode block = blocks.get(i);
      if (id.equals(text(block, "id"))) {
        blocks.remove(i);
        return true;
      }
      JsonNode children = block.get("children");
      if (children != null && children.isArray() && removeById((ArrayNode) children, id))
        return true;
    }
    return false;
  }

  private Index index(ArrayNode blocks) {
    Index idx = new Index();
    flatten(blocks, idx, 0);
    return idx;
  }

  private void flatten(ArrayNode blocks, Index idx, int depth) {
    for (JsonNode node : blocks) {
      String id = text(node, "id");
      if (id == null || id.isBlank()) {
        idx.hasMissingId = true;
        continue;
      }
      if (idx.byId.containsKey(id)) {
        idx.duplicateIds.add(id);
      }
      idx.byId.put(id, node.deepCopy());
      idx.signatures.put(id, node.toString());
      idx.order.add(id + "@" + depth);
      JsonNode children = node.get("children");
      if (children != null && children.isArray()) {
        flatten((ArrayNode) children, idx, depth + 1);
      }
    }
  }

  private void validateSnapshot(JsonNode blocks, String errorCode) {
    try {
      blockValidationService.validate(blocks);
    } catch (ContentException ex) {
      throw new ContentException(HttpStatus.BAD_REQUEST, errorCode, ex.getMessage());
    }
  }

  private JsonNode readStoredBlocks(Note note) {
    try {
      return objectMapper.readTree(note.getContentBlocks());
    } catch (Exception ex) {
      throw new ContentException(
          HttpStatus.CONFLICT,
          "NOTE_MERGE_UNSUPPORTED_CONTENT",
          "Stored note content cannot be parsed");
    }
  }

  private Set<Integer> parseSupportedVersions(String raw) {
    Set<Integer> out = new HashSet<>();
    for (String part : raw.split(",")) {
      String trimmed = part.trim();
      if (trimmed.isEmpty()) continue;
      try {
        out.add(Integer.parseInt(trimmed));
      } catch (NumberFormatException ex) {
        throw new IllegalStateException("Invalid content.merge.supported-versions value: " + raw);
      }
    }
    if (out.isEmpty()) out.add(1);
    return out;
  }

  private ArrayNode deepCopyArray(JsonNode node) {
    if (node == null || !node.isArray()) {
      return objectMapper.createArrayNode();
    }
    return (ArrayNode) node.deepCopy();
  }

  private String text(JsonNode node, String field) {
    JsonNode v = node.get(field);
    return v == null || v.isNull() ? null : v.asText();
  }

  private ContentException bad(String code, String message) {
    return new ContentException(HttpStatus.BAD_REQUEST, code, message);
  }

  private String mapAnalyzeResult(String errorCode) {
    if ("UNSUPPORTED_MERGE_VERSION".equals(errorCode)) return "unsupported_version";
    if ("INVALID_NOTE_MERGE_REQUEST".equals(errorCode)
        || "INVALID_NOTE_MERGE_BASE".equals(errorCode)
        || "INVALID_NOTE_MERGE_LOCAL".equals(errorCode)) return "invalid";
    if ("NOTE_MERGE_ANALYSIS_DISABLED".equals(errorCode)) return "disabled";
    return "error";
  }

  private void incrementAnalyzeResult(String result, int mergeVersion) {
    if (!metricsEnabled) return;
    meterRegistry
        .counter(
            "note_merge_analyze_requests_total",
            "result",
            result,
            "mergeVersion",
            String.valueOf(mergeVersion))
        .increment();
  }

  private void incrementSafeSuggestion(int mergeVersion) {
    if (!metricsEnabled) return;
    meterRegistry
        .counter(
            "note_merge_analyze_safe_suggestions_total",
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

  private void recordDuration(String metricName, String result, long startedAt) {
    if (!metricsEnabled) return;
    Timer.builder(metricName)
        .tag("result", result)
        .register(meterRegistry)
        .record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
  }

  private NoteMergeConflict conflict(String type, String blockId, String message) {
    return new NoteMergeConflict(type, blockId, message);
  }

  private record Snapshot(String title, ArrayNode blocks) {}

  private static final class Index {
    private final Map<String, JsonNode> byId = new LinkedHashMap<>();
    private final Map<String, String> signatures = new LinkedHashMap<>();
    private final Set<String> duplicateIds = new LinkedHashSet<>();
    private final List<String> order = new ArrayList<>();
    private boolean hasMissingId = false;
  }
}
