package com.notebook.lumen.content.service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class NoteLinkParser {
  public Set<UUID> parse(JsonNode blocks, UUID selfNoteId) {
    Set<UUID> links = new LinkedHashSet<>();
    walk(blocks, links);
    links.remove(selfNoteId);
    return links;
  }

  private void walk(JsonNode node, Set<UUID> links) {
    if (node == null) return;
    if (node.isObject()) {
      JsonNode props = node.get("props");
      if (props != null && props.isObject()) {
        addUuid(props.get("noteId"), links);
        addUuid(props.get("targetNoteId"), links);
        addHref(props.get("href"), links);
      }
      addHref(node.get("href"), links);
      node.properties().forEach(e -> walk(e.getValue(), links));
    } else if (node.isArray()) {
      node.forEach(child -> walk(child, links));
    }
  }

  private void addUuid(JsonNode value, Set<UUID> links) {
    if (value != null && value.isTextual()) {
      try {
        links.add(UUID.fromString(value.asText()));
      } catch (IllegalArgumentException ignored) {
      }
    }
  }

  private void addHref(JsonNode value, Set<UUID> links) {
    if (value != null && value.isTextual() && value.asText().startsWith("note://")) {
      try {
        links.add(UUID.fromString(value.asText().substring("note://".length())));
      } catch (IllegalArgumentException ignored) {
      }
    }
  }
}
