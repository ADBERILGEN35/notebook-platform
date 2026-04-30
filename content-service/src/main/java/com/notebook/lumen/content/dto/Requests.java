package com.notebook.lumen.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class Requests {
  private Requests() {}

  public record CreateNoteRequest(
      UUID parentNoteId,
      @NotBlank @Size(max = 255) String title,
      @NotNull JsonNode contentBlocks,
      Integer contentSchemaVersion) {}

  public record UpdateNoteRequest(
      @NotBlank @Size(max = 255) String title,
      @NotNull JsonNode contentBlocks,
      Integer contentSchemaVersion) {}

  public record CreateCommentRequest(
      UUID parentCommentId, @Size(max = 120) String blockId, @NotBlank String content) {}

  public record UpdateCommentRequest(@NotBlank String content) {}
}
