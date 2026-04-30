package com.notebook.lumen.gateway.error;

import java.time.Instant;

public record ErrorResponse(
    Instant timestamp,
    int status,
    String errorCode,
    String message,
    String path,
    String requestId) {}
