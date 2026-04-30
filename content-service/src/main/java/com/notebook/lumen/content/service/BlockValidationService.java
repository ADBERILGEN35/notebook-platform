package com.notebook.lumen.content.service;

import com.notebook.lumen.content.config.ContentProperties;
import com.notebook.lumen.content.shared.exception.ContentException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class BlockValidationService {
  private static final Set<String> TYPES =
      Set.of(
          "paragraph",
          "heading_1",
          "heading_2",
          "heading_3",
          "bullet_list_item",
          "numbered_list_item",
          "code",
          "mermaid",
          "todo",
          "callout",
          "quote",
          "divider",
          "image",
          "table");
  private final ContentProperties properties;

  public BlockValidationService(ContentProperties properties) {
    this.properties = properties;
  }

  public void validate(JsonNode blocks) {
    if (blocks == null || !blocks.isArray()) throw invalid("contentBlocks root must be an array");
    if (blocks.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
        > properties.blocks().maxJsonBytes()) {
      throw invalid("contentBlocks exceeds maximum configured size");
    }
    blocks.forEach(block -> validateBlock(block, 1));
  }

  private void validateBlock(JsonNode block, int depth) {
    if (depth > properties.blocks().maxDepth())
      throw invalid("contentBlocks exceeds maximum nesting depth");
    if (!block.isObject()) throw invalid("Each block must be an object");
    if (!block.hasNonNull("id") || block.get("id").asText().isBlank())
      throw invalid("Each block must include id");
    if (!block.hasNonNull("type") || block.get("type").asText().isBlank())
      throw invalid("Each block must include type");
    if (!properties.blocks().allowUnknownBlockTypes()
        && !TYPES.contains(block.get("type").asText()))
      throw invalid("Unknown block type: " + block.get("type").asText());
    if (block.has("content") && !block.get("content").isArray())
      throw invalid("content must be an array when present");
    if (block.has("props") && !block.get("props").isObject())
      throw invalid("props must be an object when present");
    if (block.has("children")) {
      if (!block.get("children").isArray()) throw invalid("children must be an array when present");
      block.get("children").forEach(child -> validateBlock(child, depth + 1));
    }
  }

  private ContentException invalid(String message) {
    return new ContentException(HttpStatus.BAD_REQUEST, "INVALID_BLOCK_CONTENT", message);
  }
}
