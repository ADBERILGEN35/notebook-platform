package com.notebook.lumen.gateway.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    Instant timestamp,
    int status,
    String errorCode,
    String message,
    String path,
    String requestId,
    Map<String, Object> details) {

  public ErrorResponse(
      Instant timestamp,
      int status,
      String errorCode,
      String message,
      String path,
      String requestId) {
    this(timestamp, status, errorCode, message, path, requestId, null);
  }
}
