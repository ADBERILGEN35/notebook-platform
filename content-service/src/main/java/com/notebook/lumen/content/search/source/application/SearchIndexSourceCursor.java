package com.notebook.lumen.content.search.source.application;

import com.notebook.lumen.content.shared.exception.ContentException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;

record SearchIndexSourceCursor(Instant updatedAt, UUID noteId) {
  static SearchIndexSourceCursor decode(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] parts = decoded.split("\\|", 2);
      return new SearchIndexSourceCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
    } catch (RuntimeException e) {
      throw new ContentException(
          HttpStatus.BAD_REQUEST, "INVALID_SEARCH_INDEX_SOURCE_REQUEST", "Invalid cursor");
    }
  }

  static String encode(Instant updatedAt, UUID noteId) {
    String value = updatedAt + "|" + noteId;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }
}
