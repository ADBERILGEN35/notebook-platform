package com.notebook.lumen.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class NoteMergeDtos {
  private NoteMergeDtos() {}

  public record NoteMergeAnalyzeRequest(
      @NotNull NoteMergeSnapshot base,
      @NotNull NoteMergeLocalSnapshot local,
      @NotNull Integer clientMergeVersion) {}

  public record NoteMergeSnapshot(
      @Size(max = 128) String etag,
      @NotBlank @Size(max = 255) String title,
      @NotNull JsonNode contentBlocks) {}

  public record NoteMergeLocalSnapshot(
      @NotBlank @Size(max = 255) String title, @NotNull JsonNode contentBlocks) {}

  public record NoteMergeAnalyzeResponse(
      UUID noteId,
      String baseEtag,
      String remoteEtag,
      int mergeVersion,
      List<Integer> supportedMergeVersions,
      boolean canAutoMerge,
      boolean hasConflicts,
      NoteMergeSummary summary,
      NoteMergeSuggestion suggested,
      List<NoteMergeConflict> conflicts) {}

  public record NoteMergeApplyRequest(
      @NotNull NoteMergeSnapshot base,
      @NotNull NoteMergeLocalSnapshot local,
      @NotBlank @Size(max = 128) String expectedRemoteEtag,
      @NotNull Integer mergeVersion,
      @Size(max = 128) String idempotencyKey) {}

  public record NoteMergeApplyResponse(
      UUID noteId,
      boolean merged,
      int mergeVersion,
      String etag,
      int version,
      String title,
      JsonNode contentBlocks,
      List<NoteMergeConflict> appliedConflicts,
      NoteMergeSummary summary) {}

  public record NoteMergeApplyConflictResponse(
      String errorCode,
      int mergeVersion,
      boolean canAutoMerge,
      List<NoteMergeConflict> conflicts,
      NoteMergeSummary summary) {}

  public record NoteMergeSummary(
      List<String> localChanges, List<String> remoteChanges, List<String> conflicts) {}

  public record NoteMergeSuggestion(String title, JsonNode contentBlocks) {}

  public record NoteMergeConflict(String type, String blockId, String message) {}
}
