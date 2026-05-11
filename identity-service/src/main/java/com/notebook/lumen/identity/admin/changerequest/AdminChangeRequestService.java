package com.notebook.lumen.identity.admin.changerequest;

import com.notebook.lumen.identity.admin.changerequest.api.AdminChangeRequestDtos;
import com.notebook.lumen.identity.admin.gitops.AdminGitOpsPrProperties;
import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.user.infrastructure.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminChangeRequestService {
  private static final String CONFIRM_PHRASE = "CONFIRM";
  private static final String NEXT_STEP_TYPE = "GITOPS_OR_MANUAL_APPLY";
  private static final String NEXT_STEP_MESSAGE =
      "Apply is not automatic. Update configuration via GitOps or follow the platform runbook.";

  private final AdminChangeRequestProperties properties;
  private final AdminGitOpsPrProperties gitOpsPrProperties;
  private final AdminOperationRegistry registry;
  private final AdminRbacRoleChangeRequestValidator rbacRoleChangeRequestValidator;
  private final PlatformAdminChangeRequestRepository repository;
  private final UserRepository userRepository;
  private final AuditService auditService;

  public AdminChangeRequestService(
      AdminChangeRequestProperties properties,
      AdminGitOpsPrProperties gitOpsPrProperties,
      AdminOperationRegistry registry,
      AdminRbacRoleChangeRequestValidator rbacRoleChangeRequestValidator,
      PlatformAdminChangeRequestRepository repository,
      UserRepository userRepository,
      AuditService auditService) {
    this.properties = properties;
    this.gitOpsPrProperties = gitOpsPrProperties;
    this.registry = registry;
    this.rbacRoleChangeRequestValidator = rbacRoleChangeRequestValidator;
    this.repository = repository;
    this.userRepository = userRepository;
    this.auditService = auditService;
  }

  public AdminChangeRequestDtos.ValidateResponse validate(
      AdminChangeRequestDtos.ValidateBody body) {
    ensureEnabled();
    AdminOperationDefinition def = resolveOperation(body.operationType());
    if (AdminOperationRegistry.isRbacRoleOperation(body.operationType())) {
      return rbacRoleChangeRequestValidator.validate(body, def);
    }
    String targetEnv =
        body.targetEnvironment() == null || body.targetEnvironment().isBlank()
            ? gitOpsPrProperties.defaultEnvironment()
            : body.targetEnvironment().trim();
    gitOpsPrProperties.validateEnvironment(targetEnv);
    String normalized = registry.normalizeValue(def, body.requestedValue());
    if (!def.isValueAllowed(normalized)) {
      throw new AdminChangeRequestException(
          "ADMIN_CHANGE_REQUEST_INVALID",
          HttpStatus.BAD_REQUEST,
          "Invalid requestedValue for "
              + def.operationType()
              + "; allowed: "
              + AdminOperationRegistry.allowedValuesHint(def));
    }
    Map<String, Object> impact = new HashMap<>(registry.toImpactSummary(def));
    if (body.currentValue() != null && !body.currentValue().isBlank()) {
      impact.put("currentValueHint", body.currentValue());
    }
    Map<String, Object> validation = new HashMap<>(registry.validationResult(true, def));
    validation.put("normalizedRequestedValue", normalized);
    validation.put("targetEnvironment", targetEnv.toLowerCase(Locale.ROOT));
    return new AdminChangeRequestDtos.ValidateResponse(
        true, def.requiresApproval(), impact, validation);
  }

  @Transactional
  public AdminChangeRequestDtos.CreateResponse create(
      UUID actorUserId,
      String actorEmail,
      AdminChangeRequestDtos.CreateBody body,
      String externalRequestId,
      HttpServletRequest request) {
    ensureEnabled();
    if (!userRepository.existsById(actorUserId)) {
      throw new AdminChangeRequestException(
          "ADMIN_CHANGE_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "Unknown requesting user");
    }
    AdminOperationDefinition def = resolveOperation(body.operationType());
    if (AdminOperationRegistry.isRbacRoleOperation(body.operationType())) {
      return rbacRoleChangeRequestValidator.create(
          actorUserId, actorEmail, body, def, externalRequestId, request);
    }
    if (body.targetEnvironment() == null || body.targetEnvironment().isBlank()) {
      throw new AdminChangeRequestException(
          "ADMIN_CHANGE_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "targetEnvironment is required");
    }
    gitOpsPrProperties.validateEnvironment(body.targetEnvironment());
    String storedEnv = body.targetEnvironment().trim().toLowerCase(Locale.ROOT);
    String normalized = registry.normalizeValue(def, body.requestedValue());
    if (!def.isValueAllowed(normalized)) {
      throw new AdminChangeRequestException(
          "ADMIN_CHANGE_REQUEST_INVALID",
          HttpStatus.BAD_REQUEST,
          "Invalid requestedValue for " + def.operationType());
    }
    if ("HIGH".equalsIgnoreCase(def.severity())) {
      if (body.confirmation() == null || !CONFIRM_PHRASE.equals(body.confirmation().trim())) {
        throw new AdminChangeRequestException(
            "ADMIN_CHANGE_REQUEST_INVALID",
            HttpStatus.BAD_REQUEST,
            "HIGH severity operations require confirmation field: \"" + CONFIRM_PHRASE + "\"");
      }
    }
    Map<String, Object> impact = new HashMap<>(registry.toImpactSummary(def));
    if (body.currentValue() != null && !body.currentValue().isBlank()) {
      impact.put("currentValueHint", body.currentValue());
    }
    Map<String, Object> validation = new HashMap<>(registry.validationResult(true, def));
    validation.put("normalizedRequestedValue", normalized);

    Instant now = Instant.now();
    PlatformAdminChangeRequest entity =
        new PlatformAdminChangeRequest(
            UUID.randomUUID(),
            actorUserId,
            blankToNull(actorEmail),
            def.operationType(),
            def.targetService(),
            def.targetKey(),
            blankToNull(body.currentValue()),
            normalized,
            storedEnv,
            ChangeRequestStatus.PENDING,
            impact,
            validation,
            blankToNull(externalRequestId),
            now,
            def.severity());

    AdminChangeRequestProperties.Approvals appr = properties.effectiveApprovals();
    boolean autoApprove =
        !AdminOperationRegistry.isRbacRoleOperation(def.operationType())
            && appr.enabled()
            && appr.lowSeverityAutoApprove()
            && !"HIGH".equalsIgnoreCase(def.severity());

    if (autoApprove) {
      entity.approve(actorUserId, now, null, policySnapshot(appr) + ";AUTO_LOW_SEVERITY=true");
      repository.save(entity);
      auditService.record(
          "ADMIN_CHANGE_REQUEST_APPROVED",
          actorUserId,
          "ADMIN_CHANGE_REQUEST",
          entity.getId(),
          request,
          decisionMetadata(entity, actorUserId, false, false));
      return new AdminChangeRequestDtos.CreateResponse(
          entity.getId(),
          entity.getStatus().name(),
          entity.getOperationType(),
          entity.getCreatedAt());
    }

    repository.save(entity);

    auditService.record(
        "ADMIN_CHANGE_REQUEST_CREATED",
        actorUserId,
        "ADMIN_CHANGE_REQUEST",
        entity.getId(),
        request,
        Map.of(
            "operationType",
            def.operationType(),
            "targetService",
            def.targetService(),
            "targetKey",
            def.targetKey(),
            "requestedValue",
            normalized,
            "targetEnvironment",
            storedEnv,
            "severity",
            def.severity(),
            "externalRequestId",
            externalRequestId == null ? "" : externalRequestId));
    return new AdminChangeRequestDtos.CreateResponse(
        entity.getId(),
        entity.getStatus().name(),
        entity.getOperationType(),
        entity.getCreatedAt());
  }

  @Transactional(readOnly = true)
  public AdminChangeRequestDtos.ListResponse list(String statusFilter) {
    ensureEnabled();
    List<PlatformAdminChangeRequest> rows;
    if (statusFilter != null && !statusFilter.isBlank()) {
      try {
        ChangeRequestStatus st = ChangeRequestStatus.valueOf(statusFilter.trim().toUpperCase());
        rows = repository.findTop100ByStatusOrderByCreatedAtDesc(st);
      } catch (IllegalArgumentException e) {
        throw new AdminChangeRequestException(
            "ADMIN_CHANGE_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "Invalid status filter");
      }
    } else {
      rows = repository.findTop100ByOrderByCreatedAtDesc();
    }
    List<AdminChangeRequestDtos.ChangeRequestItem> items = rows.stream().map(this::toItem).toList();
    return new AdminChangeRequestDtos.ListResponse(items);
  }

  @Transactional
  public void cancel(
      UUID requestId, UUID actorUserId, boolean mayCancelAnyPending, HttpServletRequest request) {
    ensureEnabled();
    PlatformAdminChangeRequest entity =
        (mayCancelAnyPending
                ? repository.findById(requestId)
                : repository.findByIdAndRequestedByUserId(requestId, actorUserId))
            .orElseThrow(
                () ->
                    new AdminChangeRequestException(
                        "ADMIN_CHANGE_REQUEST_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        "Change request not found"));
    if (entity.getStatus() != ChangeRequestStatus.PENDING) {
      throw new AdminChangeRequestException(
          "ADMIN_CHANGE_REQUEST_NOT_CANCELLABLE",
          HttpStatus.CONFLICT,
          "Only PENDING change requests can be cancelled");
    }
    entity.cancel(actorUserId, Instant.now());
    repository.save(entity);
    auditService.record(
        "ADMIN_CHANGE_REQUEST_CANCELLED",
        actorUserId,
        "ADMIN_CHANGE_REQUEST",
        entity.getId(),
        request,
        Map.of(
            "operationType",
            entity.getOperationType(),
            "targetService",
            entity.getTargetService()));
  }

  @Transactional
  public AdminChangeRequestDtos.ApproveResponse approve(
      UUID requestId,
      UUID approverUserId,
      AdminChangeRequestDtos.DecisionBody body,
      HttpServletRequest request) {
    ensureEnabled();
    ensureApprovalsEnabled();
    if (!userRepository.existsById(approverUserId)) {
      throw new AdminChangeRequestException(
          "ADMIN_CHANGE_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "Unknown approver user");
    }
    PlatformAdminChangeRequest entity =
        repository
            .findById(requestId)
            .orElseThrow(
                () ->
                    new AdminChangeRequestException(
                        "ADMIN_CHANGE_REQUEST_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        "Change request not found"));
    if (entity.getStatus() != ChangeRequestStatus.PENDING) {
      throw new AdminChangeRequestException(
          "ADMIN_CHANGE_REQUEST_NOT_PENDING",
          HttpStatus.CONFLICT,
          "Only PENDING change requests can be approved");
    }
    ensureOperationStillAllowed(entity.getOperationType());
    AdminChangeRequestProperties.Approvals appr = properties.effectiveApprovals();
    if (appr.requireDifferentApprover() && entity.getRequestedByUserId().equals(approverUserId)) {
      auditService.record(
          "ADMIN_CHANGE_REQUEST_APPROVAL_DENIED",
          approverUserId,
          "ADMIN_CHANGE_REQUEST",
          entity.getId(),
          request,
          Map.of(
              "changeRequestId",
              entity.getId().toString(),
              "operationType",
              entity.getOperationType(),
              "selfApprovalBlocked",
              true,
              "reason",
              "different_approver_required"));
      throw new AdminChangeRequestException(
          "ADMIN_CHANGE_REQUEST_SELF_APPROVAL_NOT_ALLOWED",
          HttpStatus.CONFLICT,
          "A different platform admin must approve this request");
    }
    Instant now = Instant.now();
    entity.approve(approverUserId, now, body == null ? null : body.reason(), policySnapshot(appr));
    repository.save(entity);
    auditService.record(
        "ADMIN_CHANGE_REQUEST_APPROVED",
        approverUserId,
        "ADMIN_CHANGE_REQUEST",
        entity.getId(),
        request,
        decisionMetadata(
            entity,
            approverUserId,
            true,
            body != null && body.reason() != null && !body.reason().isBlank()));
    return new AdminChangeRequestDtos.ApproveResponse(
        entity.getId(),
        entity.getStatus().name(),
        entity.getDecidedAt(),
        entity.getDecidedByUserId(),
        entity.getOperationType(),
        entity.getTargetService(),
        entity.getTargetKey(),
        entity.getRequestedValue(),
        Map.of("type", NEXT_STEP_TYPE, "message", NEXT_STEP_MESSAGE));
  }

  @Transactional
  public AdminChangeRequestDtos.RejectResponse reject(
      UUID requestId,
      UUID approverUserId,
      AdminChangeRequestDtos.DecisionBody body,
      HttpServletRequest request) {
    ensureEnabled();
    ensureApprovalsEnabled();
    if (!userRepository.existsById(approverUserId)) {
      throw new AdminChangeRequestException(
          "ADMIN_CHANGE_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "Unknown approver user");
    }
    PlatformAdminChangeRequest entity =
        repository
            .findById(requestId)
            .orElseThrow(
                () ->
                    new AdminChangeRequestException(
                        "ADMIN_CHANGE_REQUEST_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        "Change request not found"));
    if (entity.getStatus() != ChangeRequestStatus.PENDING) {
      throw new AdminChangeRequestException(
          "ADMIN_CHANGE_REQUEST_NOT_PENDING",
          HttpStatus.CONFLICT,
          "Only PENDING change requests can be rejected");
    }
    ensureOperationStillAllowed(entity.getOperationType());
    AdminChangeRequestProperties.Approvals appr = properties.effectiveApprovals();
    if (appr.requireDifferentApprover() && entity.getRequestedByUserId().equals(approverUserId)) {
      auditService.record(
          "ADMIN_CHANGE_REQUEST_APPROVAL_DENIED",
          approverUserId,
          "ADMIN_CHANGE_REQUEST",
          entity.getId(),
          request,
          Map.of(
              "changeRequestId",
              entity.getId().toString(),
              "operationType",
              entity.getOperationType(),
              "selfApprovalBlocked",
              true,
              "reason",
              "different_approver_required"));
      throw new AdminChangeRequestException(
          "ADMIN_CHANGE_REQUEST_SELF_APPROVAL_NOT_ALLOWED",
          HttpStatus.CONFLICT,
          "A different platform admin must reject/approve this request");
    }
    String reason = body == null ? null : body.reason();
    if (appr.effectiveRejectReasonRequiredForHigh()
        && "HIGH".equalsIgnoreCase(entity.getSeverity())
        && (reason == null || reason.isBlank())) {
      throw new AdminChangeRequestException(
          "ADMIN_CHANGE_REQUEST_REJECT_REASON_REQUIRED",
          HttpStatus.BAD_REQUEST,
          "A rejection reason is required for HIGH severity change requests");
    }
    Instant now = Instant.now();
    entity.reject(approverUserId, now, reason == null ? "" : reason.trim(), policySnapshot(appr));
    repository.save(entity);
    auditService.record(
        "ADMIN_CHANGE_REQUEST_REJECTED",
        approverUserId,
        "ADMIN_CHANGE_REQUEST",
        entity.getId(),
        request,
        decisionMetadata(entity, approverUserId, true, reason != null && !reason.isBlank()));
    return new AdminChangeRequestDtos.RejectResponse(
        entity.getId(),
        entity.getStatus().name(),
        entity.getDecisionReason(),
        entity.getDecidedAt());
  }

  public void recordValidatedAudit(
      UUID actorUserId, AdminChangeRequestDtos.ValidateBody body, HttpServletRequest request) {
    if (AdminOperationRegistry.isRbacRoleOperation(body.operationType())) {
      Map<String, Object> meta = new HashMap<>();
      meta.put("operationType", body.operationType() == null ? "" : body.operationType());
      meta.put("actorUserId", actorUserId.toString());
      if (body.structuredPayload() != null) {
        Object uid = body.structuredPayload().get("userId");
        Object role = body.structuredPayload().get("role");
        if (uid != null) {
          meta.put("targetUserId", String.valueOf(uid));
        }
        if (role != null) {
          meta.put("requestedRole", String.valueOf(role));
        }
        Object reason = body.structuredPayload().get("reason");
        meta.put("reasonPresent", reason != null && !String.valueOf(reason).isBlank());
      } else {
        meta.put("reasonPresent", false);
      }
      auditService.record(
          "ADMIN_RBAC_ROLE_CHANGE_REQUEST_VALIDATED",
          actorUserId,
          "ADMIN_CHANGE_REQUEST",
          null,
          request,
          meta);
      return;
    }
    auditService.record(
        "ADMIN_CHANGE_REQUEST_VALIDATED",
        actorUserId,
        "ADMIN_CHANGE_REQUEST",
        null,
        request,
        Map.of("operationType", body.operationType() == null ? "" : body.operationType()));
  }

  private Map<String, Object> decisionMetadata(
      PlatformAdminChangeRequest entity,
      UUID decidedByUserId,
      boolean decisionReasonPresent,
      boolean explicitReasonInBody) {
    Map<String, Object> m = new HashMap<>();
    m.put("changeRequestId", entity.getId().toString());
    m.put("operationType", entity.getOperationType());
    m.put("targetService", entity.getTargetService());
    m.put("targetKey", entity.getTargetKey());
    m.put("requestedValue", entity.getRequestedValue());
    m.put(
        "targetEnvironment",
        entity.getTargetEnvironment() == null ? "" : entity.getTargetEnvironment());
    m.put("severity", entity.getSeverity() == null ? "" : entity.getSeverity());
    m.put("requestedByUserId", entity.getRequestedByUserId().toString());
    m.put("decidedByUserId", decidedByUserId.toString());
    m.put("decisionReasonPresent", decisionReasonPresent);
    m.put("explicitReasonInBody", explicitReasonInBody);
    m.put("selfApprovalBlocked", false);
    return m;
  }

  private AdminChangeRequestDtos.ChangeRequestItem toItem(PlatformAdminChangeRequest e) {
    return new AdminChangeRequestDtos.ChangeRequestItem(
        e.getId(),
        e.getRequestedByUserId(),
        e.getStatus().name(),
        e.getOperationType(),
        e.getTargetService(),
        e.getTargetKey(),
        e.getCurrentValue(),
        e.getRequestedValue(),
        e.getTargetEnvironment(),
        e.getSeverity(),
        e.getImpactSummary(),
        e.getValidationResult(),
        e.getCreatedAt(),
        e.getExternalRequestId(),
        e.getDecidedAt(),
        e.getDecidedByUserId(),
        e.getDecisionReason(),
        e.getApprovedAt(),
        e.getRejectedAt());
  }

  private void ensureOperationStillAllowed(String operationType) {
    registry
        .find(operationType)
        .orElseThrow(
            () ->
                new AdminChangeRequestException(
                    "ADMIN_OPERATION_NOT_ALLOWED",
                    HttpStatus.CONFLICT,
                    "Operation is no longer allow-listed; cannot change approval state"));
  }

  private AdminOperationDefinition resolveOperation(String operationType) {
    return registry
        .find(operationType)
        .orElseThrow(
            () ->
                new AdminChangeRequestException(
                    "ADMIN_OPERATION_NOT_ALLOWED",
                    HttpStatus.BAD_REQUEST,
                    "Operation is not allow-listed"));
  }

  private void ensureEnabled() {
    if (!properties.enabled()) {
      throw new AdminChangeRequestException(
          "ADMIN_WRITE_DISABLED", HttpStatus.NOT_FOUND, "Admin change requests are disabled");
    }
  }

  private void ensureApprovalsEnabled() {
    if (!properties.effectiveApprovals().enabled()) {
      throw new AdminChangeRequestException(
          "ADMIN_CHANGE_REQUEST_APPROVAL_DISABLED",
          HttpStatus.NOT_FOUND,
          "Change request approvals are disabled");
    }
  }

  private static String policySnapshot(AdminChangeRequestProperties.Approvals appr) {
    return "requireDifferentApprover="
        + appr.requireDifferentApprover()
        + ";approvalsEnabled="
        + appr.enabled()
        + ";lowSeverityAutoApprove="
        + appr.lowSeverityAutoApprove();
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
