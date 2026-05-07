package com.notebook.lumen.content.service;

import com.notebook.lumen.content.shared.exception.ContentException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class NoteEtagSupport {
  private static final String ETAG_PREFIX = "note-rev-";

  public String buildEtag(long noteRevision) {
    return ETAG_PREFIX + noteRevision;
  }

  public long parseIfMatchRevision(String ifMatchHeader) {
    if (ifMatchHeader == null || ifMatchHeader.isBlank()) {
      throw invalidIfMatch();
    }

    String value = ifMatchHeader.trim();
    if (value.startsWith("W/") || value.equals("*")) {
      throw invalidIfMatch();
    }
    if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
      value = value.substring(1, value.length() - 1);
    }
    if (!value.startsWith(ETAG_PREFIX)) {
      throw invalidIfMatch();
    }
    String revision = value.substring(ETAG_PREFIX.length());
    try {
      return Long.parseLong(revision);
    } catch (NumberFormatException ignored) {
      throw invalidIfMatch();
    }
  }

  private ContentException invalidIfMatch() {
    return new ContentException(
        HttpStatus.BAD_REQUEST,
        "INVALID_IF_MATCH_HEADER",
        "If-Match header must use note-rev-* ETag format");
  }
}
