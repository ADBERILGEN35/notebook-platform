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
import tools.jackson.databind.node.ObjectNode;

/**
 * Three-way merge analyzer for BlockNote note content (Faz 71). Faz 95 adds block move/reorder
 * detection plus narrowed conflict types (no silent merge — same-parent reorders can join safe
 * suggestions, anything more complex stays conflict).
 */
@Service
public class NoteMergeAnalyzeService {
  private static final Logger log = LoggerFactory.getLogger(NoteMergeAnalyzeService.class);

  private static final String ROOT_PARENT = "";

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

      MergePlan plan = analyze(base, local, remote, localChanges, remoteChanges, conflicts);
      conflictCount = conflicts.size();
      conflictTypes = conflicts.stream().map(NoteMergeConflict::type).toList();

      NoteMergeSuggestion suggestion = null;
      if (conflicts.isEmpty()) {
        suggestion = buildSuggestion(base, local, remote, plan);
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

  private MergePlan analyze(
      Snapshot base,
      Snapshot local,
      Snapshot remote,
      List<String> localChanges,
      List<String> remoteChanges,
      List<NoteMergeConflict> conflicts) {
    Index baseIdx = index(base.blocks());
    Index localIdx = index(local.blocks());
    Index remoteIdx = index(remote.blocks());

    MergePlan plan = new MergePlan();

    if (baseIdx.hasMissingId || localIdx.hasMissingId) {
      conflicts.add(conflict("MISSING_BLOCK_ID", null, "Missing block id detected"));
      return plan;
    }
    if (!baseIdx.duplicateIds.isEmpty()
        || !localIdx.duplicateIds.isEmpty()
        || !remoteIdx.duplicateIds.isEmpty()) {
      conflicts.add(conflict("DUPLICATE_BLOCK_ID", null, "Duplicate block id detected"));
      return plan;
    }

    boolean localTitleChanged = !Objects.equals(base.title(), local.title());
    boolean remoteTitleChanged = !Objects.equals(base.title(), remote.title());
    if (localTitleChanged) localChanges.add("Title changed");
    if (remoteTitleChanged) remoteChanges.add("Title changed on server");
    if (localTitleChanged && remoteTitleChanged && !Objects.equals(local.title(), remote.title())) {
      conflicts.add(conflict("TITLE_DIVERGENT", null, "Both sides changed title differently"));
    }

    Set<String> allIds = new LinkedHashSet<>();
    allIds.addAll(baseIdx.byId.keySet());
    allIds.addAll(localIdx.byId.keySet());
    allIds.addAll(remoteIdx.byId.keySet());

    for (String id : allIds) {
      boolean inBase = baseIdx.byId.containsKey(id);
      boolean inLocal = localIdx.byId.containsKey(id);
      boolean inRemote = remoteIdx.byId.containsKey(id);

      if (!inBase && inLocal && !inRemote) {
        localChanges.add("Added block " + id);
        continue;
      }
      if (!inBase && !inLocal && inRemote) {
        remoteChanges.add("Server added block " + id);
        continue;
      }
      if (!inBase && inLocal && inRemote) {
        // Both sides independently added the same id — divergent content is unsafe.
        String l = localIdx.blockSignatures.get(id);
        String r = remoteIdx.blockSignatures.get(id);
        if (!Objects.equals(l, r)) {
          conflicts.add(
              conflict("SAME_BLOCK_CHANGED", id, "Both versions added the same block differently"));
        }
        continue;
      }

      boolean localDeleted = inBase && !inLocal;
      boolean remoteDeleted = inBase && !inRemote;

      String baseSig = baseIdx.blockSignatures.get(id);
      String localSig = inLocal ? localIdx.blockSignatures.get(id) : null;
      String remoteSig = inRemote ? remoteIdx.blockSignatures.get(id) : null;

      boolean localEdited = inLocal && !Objects.equals(baseSig, localSig);
      boolean remoteEdited = inRemote && !Objects.equals(baseSig, remoteSig);

      MoveStatus localMove =
          inLocal ? computeMove(baseIdx, localIdx, id) : MoveStatus.NONE;
      MoveStatus remoteMove =
          inRemote ? computeMove(baseIdx, remoteIdx, id) : MoveStatus.NONE;

      if (localEdited) localChanges.add("Changed block " + id);
      if (remoteEdited) remoteChanges.add("Server changed block " + id);

      if (localMove != MoveStatus.NONE) {
        localChanges.add(describeMove(id, baseIdx, localIdx, localMove));
        plan.localMoves.put(id, localMove);
      }
      if (remoteMove != MoveStatus.NONE) {
        remoteChanges.add("Server " + describeMoveServer(id, baseIdx, remoteIdx, remoteMove));
      }
      if (localDeleted) localChanges.add("Removed block " + id);
      if (remoteDeleted) remoteChanges.add("Server removed block " + id);

      // Conflict matrix.
      if (localDeleted && remoteMove != MoveStatus.NONE) {
        conflicts.add(
            conflict(
                "BLOCK_DELETED_AFTER_MOVE",
                id,
                "Block was moved on the server but removed locally"));
        continue;
      }
      if (remoteDeleted && localMove != MoveStatus.NONE) {
        conflicts.add(
            conflict(
                "BLOCK_DELETED_AFTER_MOVE",
                id,
                "Block was moved locally but removed on the server"));
        continue;
      }
      if (localDeleted && remoteEdited) {
        conflicts.add(conflict("DELETE_VS_EDIT", id, "Delete vs edit conflict on block"));
        continue;
      }
      if (remoteDeleted && localEdited) {
        conflicts.add(conflict("DELETE_VS_EDIT", id, "Delete vs edit conflict on block"));
        continue;
      }
      if (localDeleted || remoteDeleted) {
        // Disjoint delete.
        continue;
      }

      if (localMove != MoveStatus.NONE && remoteMove != MoveStatus.NONE) {
        boolean sameDestination =
            Objects.equals(localIdx.parents.get(id), remoteIdx.parents.get(id))
                && Objects.equals(localIdx.indices.get(id), remoteIdx.indices.get(id));
        if (!sameDestination) {
          conflicts.add(
              conflict(
                  "BLOCK_MOVE_CONFLICT",
                  id,
                  "Both versions moved the same block to different positions"));
          continue;
        }
        // Same-target move → treat as if only one side moved; remote already has it.
        plan.localMoves.remove(id);
      }
      if (localMove == MoveStatus.PARENT_CHANGED
          || remoteMove == MoveStatus.PARENT_CHANGED) {
        // Cross-parent moves are out of scope for safe auto-merge (Faz 95).
        conflicts.add(
            conflict(
                "BLOCK_CROSS_PARENT_UNSUPPORTED",
                id,
                "Cross-parent move requires manual review"));
        plan.localMoves.remove(id);
        continue;
      }
      if (localMove != MoveStatus.NONE && remoteEdited) {
        conflicts.add(
            conflict(
                "BLOCK_MOVED_AND_EDITED",
                id,
                "Server edited a block that you moved"));
        plan.localMoves.remove(id);
        continue;
      }
      if (remoteMove != MoveStatus.NONE && localEdited) {
        conflicts.add(
            conflict(
                "BLOCK_MOVED_AND_EDITED",
                id,
                "You edited a block that the server moved"));
        continue;
      }
      if (localEdited && remoteEdited && !Objects.equals(localSig, remoteSig)) {
        conflicts.add(conflict("SAME_BLOCK_CHANGED", id, "Both versions changed the same block"));
      }
    }

    return plan;
  }

  private NoteMergeSuggestion buildSuggestion(
      Snapshot base, Snapshot local, Snapshot remote, MergePlan plan) {
    ArrayNode merged = deepCopyArray(remote.blocks());
    Index baseIdx = index(base.blocks());
    Index localIdx = index(local.blocks());
    Index remoteIdx = index(remote.blocks());

    // Apply local-only edits / deletes to merged (mirrors v1 logic).
    for (String id : baseIdx.byId.keySet()) {
      if (!localIdx.byId.containsKey(id) && remoteIdx.byId.containsKey(id)) {
        // Local deleted, remote untouched → remove (only if base signature matches remote).
        if (Objects.equals(
            baseIdx.blockSignatures.get(id), remoteIdx.blockSignatures.get(id))) {
          removeById(merged, id);
        }
        continue;
      }
      if (!localIdx.byId.containsKey(id)) continue;
      String b = baseIdx.blockSignatures.get(id);
      String l = localIdx.blockSignatures.get(id);
      String r = remoteIdx.blockSignatures.get(id);
      boolean localChanged = !Objects.equals(b, l);
      boolean remoteChanged = !Objects.equals(b, r);
      if (localChanged && !remoteChanged) {
        replaceBlockBodyById(merged, id, localIdx.byId.get(id));
      }
    }

    // Append local-only root additions (same as v1, scope-limited to root).
    for (String id : localIdx.byId.keySet()) {
      if (baseIdx.byId.containsKey(id) || remoteIdx.byId.containsKey(id)) continue;
      if (isRootAddition(local.blocks(), id)) {
        merged.add(localIdx.byId.get(id).deepCopy());
      }
    }

    // Apply local same-parent reorders/moves to merged (Faz 95 safe set).
    if (!plan.localMoves.isEmpty()) {
      if (!applyLocalReorders(merged, localIdx, plan)) {
        return null;
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

  /**
   * Reorders root-level / same-parent sibling groups in {@code merged} so the relative order of ids
   * matches what {@code localIdx} expressed for that parent. Returns {@code false} if the
   * rearrangement could not be applied (signals "fall back to no suggestion").
   */
  private boolean applyLocalReorders(ArrayNode merged, Index localIdx, MergePlan plan) {
    Set<String> parents = new LinkedHashSet<>();
    for (String id : plan.localMoves.keySet()) {
      String parent = localIdx.parents.get(id);
      if (parent == null) return false;
      parents.add(parent);
    }
    for (String parent : parents) {
      List<String> desiredOrder = localIdx.childrenByParent.getOrDefault(parent, List.of());
      if (ROOT_PARENT.equals(parent)) {
        if (!reorderSiblings(merged, desiredOrder)) return false;
      } else {
        ArrayNode siblings = findChildrenArray(merged, parent);
        if (siblings == null) return false;
        if (!reorderSiblings(siblings, desiredOrder)) return false;
      }
    }
    return true;
  }

  private boolean reorderSiblings(ArrayNode siblings, List<String> desiredOrder) {
    Map<String, JsonNode> byId = new LinkedHashMap<>();
    for (JsonNode child : siblings) {
      String id = text(child, "id");
      if (id != null) byId.put(id, child);
    }
    List<JsonNode> reordered = new ArrayList<>();
    Set<String> placed = new LinkedHashSet<>();
    for (String id : desiredOrder) {
      JsonNode node = byId.get(id);
      if (node == null) continue; // node missing on remote → leave merged as-is.
      reordered.add(node);
      placed.add(id);
    }
    // Append remote-only siblings that local did not know about — preserve their original order.
    for (Map.Entry<String, JsonNode> entry : byId.entrySet()) {
      if (placed.contains(entry.getKey())) continue;
      reordered.add(entry.getValue());
    }
    siblings.removeAll();
    for (JsonNode node : reordered) siblings.add(node);
    return true;
  }

  private ArrayNode findChildrenArray(ArrayNode blocks, String parentId) {
    for (JsonNode block : blocks) {
      if (parentId.equals(text(block, "id"))) {
        JsonNode children = block.get("children");
        if (children instanceof ArrayNode arr) return arr;
      }
      JsonNode children = block.get("children");
      if (children instanceof ArrayNode arr) {
        ArrayNode nested = findChildrenArray(arr, parentId);
        if (nested != null) return nested;
      }
    }
    return null;
  }

  private boolean isRootAddition(ArrayNode blocks, String id) {
    for (JsonNode block : blocks) {
      if (id.equals(text(block, "id"))) return true;
    }
    return false;
  }

  /**
   * Replace block's body (type, props, content, children) in place, keeping the existing position.
   * Returns {@code true} if a block with the id was found.
   */
  private boolean replaceBlockBodyById(ArrayNode blocks, String id, JsonNode replacement) {
    for (int i = 0; i < blocks.size(); i++) {
      JsonNode block = blocks.get(i);
      if (id.equals(text(block, "id"))) {
        blocks.set(i, replacement.deepCopy());
        return true;
      }
      JsonNode children = block.get("children");
      if (children instanceof ArrayNode arr && replaceBlockBodyById(arr, id, replacement)) {
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
      if (children instanceof ArrayNode arr && removeById(arr, id)) return true;
    }
    return false;
  }

  private Index index(ArrayNode blocks) {
    Index idx = new Index();
    flatten(blocks, ROOT_PARENT, idx);
    return idx;
  }

  private void flatten(ArrayNode blocks, String parentId, Index idx) {
    List<String> childIds = new ArrayList<>();
    for (int i = 0; i < blocks.size(); i++) {
      JsonNode node = blocks.get(i);
      String id = text(node, "id");
      if (id == null || id.isBlank()) {
        idx.hasMissingId = true;
        continue;
      }
      if (idx.byId.containsKey(id)) {
        idx.duplicateIds.add(id);
      }
      idx.byId.put(id, node.deepCopy());
      idx.parents.put(id, parentId);
      idx.indices.put(id, i);
      childIds.add(id);
      idx.blockSignatures.put(id, bodySignature(node));
      JsonNode children = node.get("children");
      if (children instanceof ArrayNode arr) {
        flatten(arr, id, idx);
      }
    }
    idx.childrenByParent.put(parentId, childIds);
  }

  /**
   * Body signature ignores ordering of children (children list reordering does not flip the parent
   * block as "edited"); inline {@code content} array order is preserved (BlockNote uses it for
   * inline runs).
   */
  private String bodySignature(JsonNode node) {
    ObjectNode shallow = objectMapper.createObjectNode();
    shallow.set("type", node.get("type"));
    if (node.has("props")) shallow.set("props", node.get("props"));
    if (node.has("content")) shallow.set("content", node.get("content"));
    // Children represented as their id sequence — captures "children added/removed/replaced" but
    // not pure reordering within the children list.
    JsonNode children = node.get("children");
    if (children instanceof ArrayNode arr) {
      List<String> ids = new ArrayList<>();
      for (JsonNode child : arr) {
        String cid = text(child, "id");
        if (cid != null) ids.add(cid);
      }
      Collections.sort(ids);
      ArrayNode sortedIds = objectMapper.createArrayNode();
      ids.forEach(sortedIds::add);
      shallow.set("childIdsSorted", sortedIds);
    }
    return shallow.toString();
  }

  private MoveStatus computeMove(Index baseIdx, Index targetIdx, String id) {
    String baseParent = baseIdx.parents.get(id);
    String targetParent = targetIdx.parents.get(id);
    if (baseParent == null || targetParent == null) return MoveStatus.NONE;
    if (!Objects.equals(baseParent, targetParent)) return MoveStatus.PARENT_CHANGED;
    Integer baseIdx2 = baseIdx.indices.get(id);
    Integer targetIdx2 = targetIdx.indices.get(id);
    if (!Objects.equals(baseIdx2, targetIdx2)) return MoveStatus.REORDERED;
    return MoveStatus.NONE;
  }

  private String describeMove(String id, Index baseIdx, Index targetIdx, MoveStatus status) {
    if (status == MoveStatus.PARENT_CHANGED) {
      return "Moved block " + id + " to a different parent";
    }
    Integer from = baseIdx.indices.get(id);
    Integer to = targetIdx.indices.get(id);
    return "Reordered block " + id + " from position " + from + " to " + to;
  }

  private String describeMoveServer(
      String id, Index baseIdx, Index targetIdx, MoveStatus status) {
    if (status == MoveStatus.PARENT_CHANGED) {
      return "moved block " + id + " to a different parent";
    }
    Integer from = baseIdx.indices.get(id);
    Integer to = targetIdx.indices.get(id);
    return "reordered block " + id + " from position " + from + " to " + to;
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

  private enum MoveStatus {
    NONE,
    REORDERED,
    PARENT_CHANGED
  }

  private static final class MergePlan {
    private final Map<String, MoveStatus> localMoves = new LinkedHashMap<>();
  }

  private static final class Index {
    private final Map<String, JsonNode> byId = new LinkedHashMap<>();
    private final Map<String, String> blockSignatures = new LinkedHashMap<>();
    private final Map<String, String> parents = new LinkedHashMap<>();
    private final Map<String, Integer> indices = new LinkedHashMap<>();
    private final Map<String, List<String>> childrenByParent = new LinkedHashMap<>();
    private final Set<String> duplicateIds = new LinkedHashSet<>();
    private boolean hasMissingId = false;
  }
}
