package com.notebook.lumen.search.index.application;

import com.notebook.lumen.search.shared.config.SearchProperties;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class ContentBlockTextExtractor {
  private static final Set<String> TEXT_FIELDS =
      Set.of("text", "content", "title", "caption", "label", "value");

  private final SearchProperties properties;

  public ContentBlockTextExtractor(SearchProperties properties) {
    this.properties = properties;
  }

  public String extract(JsonNode blocks) {
    if (blocks == null || blocks.isNull()) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    append(blocks, builder);
    String text = builder.toString().replaceAll("\\s+", " ").trim();
    int maxChars = Math.max(1, properties.maxIndexedChars());
    return text.length() <= maxChars ? text : text.substring(0, maxChars);
  }

  private void append(JsonNode node, StringBuilder builder) {
    if (node == null || node.isNull() || builder.length() >= properties.maxIndexedChars()) {
      return;
    }
    if (node.isTextual()) {
      appendText(builder, node.asText());
      return;
    }
    if (node.isArray()) {
      node.forEach(child -> append(child, builder));
      return;
    }
    if (!node.isObject()) {
      return;
    }
    TEXT_FIELDS.forEach(
        field -> {
          JsonNode value = node.get(field);
          if (value != null && value.isTextual()) {
            appendText(builder, value.asText());
          }
        });
    append(node.get("props"), builder);
    append(node.get("content"), builder);
    append(node.get("cells"), builder);
    append(node.get("children"), builder);
  }

  private void appendText(StringBuilder builder, String text) {
    if (text == null || text.isBlank() || builder.length() >= properties.maxIndexedChars()) {
      return;
    }
    if (!builder.isEmpty()) {
      builder.append(' ');
    }
    builder.append(text);
  }
}
