package com.notebook.lumen.identity.admin.changerequest.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdminChangeRequestDtos {
  private AdminChangeRequestDtos() {}

  public record ValidateBody(
      String operationType, String requestedValue, String currentValue, String targetEnvironment) {}

  public record ValidateResponse(
      boolean valid,
      boolean requiresApproval,
      Map<String, Object> impactSummary,
      Map<String, Object> validationResult) {}

  public record CreateBody(
      String operationType,
      String requestedValue,
      String currentValue,
      String confirmation,
      String targetEnvironment) {}

  public record DecisionBody(String reason) {}

  public record ChangeRequestItem(
      UUID id,
      UUID requestedByUserId,
      String status,
      String operationType,
      String targetService,
      String targetKey,
      String currentValue,
      String requestedValue,
      String targetEnvironment,
      String severity,
      Map<String, Object> impactSummary,
      Map<String, Object> validationResult,
      Instant createdAt,
      String externalRequestId,
      Instant decidedAt,
      UUID decidedByUserId,
      String decisionReason,
      Instant approvedAt,
      Instant rejectedAt) {}

  public record ListResponse(List<ChangeRequestItem> items) {}

  public record CreateResponse(UUID id, String status, String operationType, Instant createdAt) {}

  public record ApproveResponse(
      UUID id,
      String status,
      Instant decidedAt,
      UUID decidedByUserId,
      String operationType,
      String targetService,
      String targetKey,
      String requestedValue,
      Map<String, String> nextStep) {}

  public record RejectResponse(UUID id, String status, String decisionReason, Instant decidedAt) {}
}
