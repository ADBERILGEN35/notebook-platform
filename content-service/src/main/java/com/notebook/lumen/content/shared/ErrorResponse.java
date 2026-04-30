package com.notebook.lumen.content.shared;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
    Instant timestamp,
    int status,
    String errorCode,
    String message,
    String path,
    String requestId,
    List<FieldError> fieldErrors) {
  public record FieldError(String field, String message) {}
}
