package com.notebook.lumen.identity.admin.changerequest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "platform_admin_change_requests")
public class PlatformAdminChangeRequest {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "requested_by_user_id", nullable = false)
  private UUID requestedByUserId;

  @Column(name = "requested_by_email", length = 320)
  private String requestedByEmail;

  @Column(name = "operation_type", nullable = false, length = 80)
  private String operationType;

  @Column(name = "target_service", nullable = false, length = 80)
  private String targetService;

  @Column(name = "target_key", nullable = false, length = 160)
  private String targetKey;

  @Column(name = "current_value", length = 500)
  private String currentValue;

  @Column(name = "requested_value", nullable = false, length = 500)
  private String requestedValue;

  @Column(name = "target_environment", nullable = false, length = 32)
  private String targetEnvironment;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ChangeRequestStatus status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "impact_summary", columnDefinition = "jsonb")
  private Map<String, Object> impactSummary;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "validation_result", columnDefinition = "jsonb")
  private Map<String, Object> validationResult;

  @Column(name = "external_request_id", length = 128)
  private String externalRequestId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "decided_by_user_id")
  private UUID decidedByUserId;

  @Column(name = "applied_at")
  private Instant appliedAt;

  @Column(name = "decision_reason", length = 2000)
  private String decisionReason;

  @Column(name = "approval_policy_snapshot", length = 256)
  private String approvalPolicySnapshot;

  @Column(name = "severity", length = 16)
  private String severity;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "rejected_at")
  private Instant rejectedAt;

  protected PlatformAdminChangeRequest() {}

  public PlatformAdminChangeRequest(
      UUID id,
      UUID requestedByUserId,
      String requestedByEmail,
      String operationType,
      String targetService,
      String targetKey,
      String currentValue,
      String requestedValue,
      String targetEnvironment,
      ChangeRequestStatus status,
      Map<String, Object> impactSummary,
      Map<String, Object> validationResult,
      String externalRequestId,
      Instant createdAt,
      String severity) {
    this.id = id;
    this.requestedByUserId = requestedByUserId;
    this.requestedByEmail = requestedByEmail;
    this.operationType = operationType;
    this.targetService = targetService;
    this.targetKey = targetKey;
    this.currentValue = currentValue;
    this.requestedValue = requestedValue;
    this.targetEnvironment = targetEnvironment;
    this.status = status;
    this.impactSummary = impactSummary;
    this.validationResult = validationResult;
    this.externalRequestId = externalRequestId;
    this.createdAt = createdAt;
    this.severity = severity;
  }

  public UUID getId() {
    return id;
  }

  public UUID getRequestedByUserId() {
    return requestedByUserId;
  }

  public String getRequestedByEmail() {
    return requestedByEmail;
  }

  public String getOperationType() {
    return operationType;
  }

  public String getTargetService() {
    return targetService;
  }

  public String getTargetKey() {
    return targetKey;
  }

  public String getCurrentValue() {
    return currentValue;
  }

  public String getRequestedValue() {
    return requestedValue;
  }

  public String getTargetEnvironment() {
    return targetEnvironment;
  }

  public ChangeRequestStatus getStatus() {
    return status;
  }

  public Map<String, Object> getImpactSummary() {
    return impactSummary;
  }

  public Map<String, Object> getValidationResult() {
    return validationResult;
  }

  public String getExternalRequestId() {
    return externalRequestId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public UUID getDecidedByUserId() {
    return decidedByUserId;
  }

  public String getDecisionReason() {
    return decisionReason;
  }

  public String getApprovalPolicySnapshot() {
    return approvalPolicySnapshot;
  }

  public String getSeverity() {
    return severity;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public Instant getRejectedAt() {
    return rejectedAt;
  }

  public void cancel(UUID actorUserId, Instant now) {
    this.status = ChangeRequestStatus.CANCELLED;
    this.decidedAt = now;
    this.decidedByUserId = actorUserId;
  }

  public void approve(UUID approverUserId, Instant now, String reason, String policySnapshot) {
    this.status = ChangeRequestStatus.APPROVED;
    this.decidedAt = now;
    this.decidedByUserId = approverUserId;
    this.decisionReason = blankToNull(reason);
    this.approvalPolicySnapshot = policySnapshot;
    this.approvedAt = now;
    this.rejectedAt = null;
  }

  public void reject(UUID approverUserId, Instant now, String reason, String policySnapshot) {
    this.status = ChangeRequestStatus.REJECTED;
    this.decidedAt = now;
    this.decidedByUserId = approverUserId;
    this.decisionReason = reason;
    this.approvalPolicySnapshot = policySnapshot;
    this.rejectedAt = now;
    this.approvedAt = null;
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
