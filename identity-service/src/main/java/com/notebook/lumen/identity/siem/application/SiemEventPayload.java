package com.notebook.lumen.identity.siem.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SiemEventPayload(
    UUID id,
    Instant timestamp,
    String sourceService,
    String environment,
    String eventType,
    String category,
    String severity,
    UUID actorUserId,
    UUID subjectUserId,
    UUID workspaceId,
    String requestId,
    String ipAddress,
    String userAgent,
    Map<String, Object> metadata,
    int schemaVersion) {}
