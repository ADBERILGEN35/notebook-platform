package com.notebook.lumen.notification.admin.deadletter;

import java.time.Instant;
import java.util.List;

public final class DeadLetterAdminDtos {

  private DeadLetterAdminDtos() {}

  public record DeadLetterListItemDto(
      String id,
      String source,
      String eventType,
      String recipientUserIdHash,
      String status,
      int attemptCount,
      int requeueCount,
      String lastErrorCode,
      String lastErrorSummary,
      Instant createdAt,
      Instant updatedAt,
      Instant deadAt) {}

  public record DeadLetterListResponse(
      List<DeadLetterListItemDto> items, int page, int size, long totalElements) {}

  public record RequeueDryRunImpact(String severity, String duplicateRisk, String reason) {}

  public record RequeueDryRunCheck(String code, boolean passed) {}

  public record RequeueDryRunResponse(
      String id,
      boolean canRequeue,
      String source,
      RequeueDryRunImpact impact,
      List<RequeueDryRunCheck> checks) {}

  public record FanoutRequeueHttpRequest(String idempotencyKey, String reason) {}

  public record FanoutRequeueResponse(
      String id,
      String source,
      String status,
      int attemptCount,
      int requeueCount,
      Instant nextAttemptAt,
      boolean idempotentReplay) {}
}
