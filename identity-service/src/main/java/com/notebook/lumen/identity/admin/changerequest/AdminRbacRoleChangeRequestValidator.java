package com.notebook.lumen.identity.admin.changerequest;

import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import com.notebook.lumen.identity.admin.AdminRbacProperties;
import com.notebook.lumen.identity.admin.changerequest.api.AdminChangeRequestDtos;
import com.notebook.lumen.identity.admin.gitops.AdminGitOpsPrProperties;
import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.user.domain.User;
import com.notebook.lumen.identity.user.domain.UserStatus;
import com.notebook.lumen.identity.user.infrastructure.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AdminRbacRoleChangeRequestValidator {
  private static final String CONFIRM_PHRASE = "CONFIRM";

  private final AdminRbacProperties adminRbacProperties;
  private final AdminGitOpsPrProperties gitOpsPrProperties;
  private final AdminOperationRegistry registry;
  private final UserRepository userRepository;
  private final PlatformAdminChangeRequestRepository changeRequestRepository;
  private final AuditService auditService;

  public AdminRbacRoleChangeRequestValidator(
      AdminRbacProperties adminRbacProperties,
      AdminGitOpsPrProperties gitOpsPrProperties,
      AdminOperationRegistry registry,
      UserRepository userRepository,
      PlatformAdminChangeRequestRepository changeRequestRepository,
      AuditService auditService) {
    this.adminRbacProperties = adminRbacProperties;
    this.gitOpsPrProperties = gitOpsPrProperties;
    this.registry = registry;
    this.userRepository = userRepository;
    this.changeRequestRepository = changeRequestRepository;
    this.auditService = auditService;
  }

  public AdminChangeRequestDtos.ValidateResponse validate(
      AdminChangeRequestDtos.ValidateBody body, AdminOperationDefinition def) {
    ensureRbacRequestsEnabled();
    ParsedRbac parsed =
        parse(body.operationType(), body.requestedValue(), body.structuredPayload(), false);
    User target = loadActiveTarget(parsed.targetUserId());
    validateRole(parsed.role());
    String severity = effectiveSeverity(parsed.role());

    String targetEnv =
        body.targetEnvironment() == null || body.targetEnvironment().isBlank()
            ? gitOpsPrProperties.defaultEnvironment()
            : body.targetEnvironment().trim();
    gitOpsPrProperties.validateEnvironment(targetEnv);

    String normalized = parsed.normalizedRequestedValue();
    if (!def.isValueAllowed(normalized)) {
      throw new AdminChangeRequestException(
          "ADMIN_CHANGE_REQUEST_INVALID",
          HttpStatus.BAD_REQUEST,
          "Invalid requestedValue encoding");
    }

    Map<String, Object> impact = new HashMap<>(registry.toImpactSummary(def));
    impact.put("effectiveSeverity", severity);
    impact.put("targetUserId", parsed.targetUserId().toString());
    impact.put("requestedRole", parsed.role());
    impact.put("rbacAction", parsed.action());
    impact.put("runtimeApplySupported", false);
    impact.putAll(impactWarnings(parsed, target, null));
    if (parsed.reason() != null && !parsed.reason().isBlank()) {
      impact.put("reasonPresent", true);
    } else {
      impact.put("reasonPresent", false);
    }

    Map<String, Object> validation = new HashMap<>(registry.validationResult(true, def));
    validation.put("normalizedRequestedValue", normalized);
    validation.put("targetEnvironment", targetEnv.toLowerCase(Locale.ROOT));
    validation.put("targetUserId", parsed.targetUserId().toString());
    validation.put("requestedRole", parsed.role());
    validation.put("rbacAction", parsed.action());
    validation.put("effectiveSeverity", severity);
    return new AdminChangeRequestDtos.ValidateResponse(
        true, def.requiresApproval(), impact, validation);
  }

  public AdminChangeRequestDtos.CreateResponse create(
      UUID actorUserId,
      String actorEmail,
      AdminChangeRequestDtos.CreateBody body,
      AdminOperationDefinition def,
      String externalRequestId,
      HttpServletRequest request) {
    ensureRbacRequestsEnabled();
    ParsedRbac parsed =
        parse(body.operationType(), body.requestedValue(), body.structuredPayload(), true);
    User target = loadActiveTarget(parsed.targetUserId());
    validateRole(parsed.role());
    String severity = effectiveSeverity(parsed.role());

    if (body.targetEnvironment() == null || body.targetEnvironment().isBlank()) {
      throw new AdminChangeRequestException(
          "ADMIN_CHANGE_REQUEST_INVALID", HttpStatus.BAD_REQUEST, "targetEnvironment is required");
    }
    gitOpsPrProperties.validateEnvironment(body.targetEnvironment());
    String storedEnv = body.targetEnvironment().trim().toLowerCase(Locale.ROOT);

    if ("HIGH".equalsIgnoreCase(severity)) {
      if (body.confirmation() == null || !CONFIRM_PHRASE.equals(body.confirmation().trim())) {
        throw new AdminChangeRequestException(
            "ADMIN_CHANGE_REQUEST_INVALID",
            HttpStatus.BAD_REQUEST,
            "HIGH severity operations require confirmation field: \"" + CONFIRM_PHRASE + "\"");
      }
    }

    String normalized = parsed.normalizedRequestedValue();
    if (!def.isValueAllowed(normalized)) {
      throw new AdminChangeRequestException(
          "ADMIN_CHANGE_REQUEST_INVALID",
          HttpStatus.BAD_REQUEST,
          "Invalid requestedValue encoding");
    }

    Map<String, Object> impact = new HashMap<>(registry.toImpactSummary(def));
    impact.put("effectiveSeverity", severity);
    impact.put("targetUserId", parsed.targetUserId().toString());
    impact.put("requestedRole", parsed.role());
    impact.put("rbacAction", parsed.action());
    impact.put("runtimeApplySupported", false);
    impact.put("reasonPresent", true);
    impact.putAll(impactWarnings(parsed, target, actorUserId));

    Map<String, Object> validation = new HashMap<>(registry.validationResult(true, def));
    validation.put("normalizedRequestedValue", normalized);
    validation.put("targetUserId", parsed.targetUserId().toString());
    validation.put("requestedRole", parsed.role());
    validation.put("rbacAction", parsed.action());
    validation.put("effectiveSeverity", severity);
    validation.put("reasonPresent", true);

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
            severity);

    changeRequestRepository.save(entity);

    auditService.record(
        "ADMIN_RBAC_ROLE_CHANGE_REQUEST_CREATED",
        actorUserId,
        "ADMIN_CHANGE_REQUEST",
        entity.getId(),
        request,
        Map.of(
            "operationType",
            def.operationType(),
            "targetUserId",
            parsed.targetUserId().toString(),
            "requestedRole",
            parsed.role(),
            "action",
            parsed.action(),
            "reasonPresent",
            true,
            "severity",
            severity,
            "targetEnvironment",
            storedEnv));

    return new AdminChangeRequestDtos.CreateResponse(
        entity.getId(),
        entity.getStatus().name(),
        entity.getOperationType(),
        entity.getCreatedAt());
  }

  private void ensureRbacRequestsEnabled() {
    if (!adminRbacProperties.roleChangeRequestsEnabled()) {
      throw new AdminChangeRequestException(
          "ADMIN_RBAC_ROLE_REQUESTS_DISABLED",
          HttpStatus.NOT_FOUND,
          "Admin RBAC role change requests are disabled");
    }
  }

  private User loadActiveTarget(UUID userId) {
    User u =
        userRepository
            .findById(userId)
            .orElseThrow(
                () ->
                    new AdminChangeRequestException(
                        "ADMIN_RBAC_TARGET_USER_INVALID",
                        HttpStatus.BAD_REQUEST,
                        "Unknown target user"));
    if (u.getDeletedAt() != null || u.getStatus() == UserStatus.DELETED) {
      throw new AdminChangeRequestException(
          "ADMIN_RBAC_TARGET_USER_INVALID", HttpStatus.BAD_REQUEST, "Target user is deleted");
    }
    if (u.getDeprovisionedAt() != null) {
      throw new AdminChangeRequestException(
          "ADMIN_RBAC_TARGET_USER_DEPROVISIONED",
          HttpStatus.BAD_REQUEST,
          "Cannot request RBAC changes for a deprovisioned user");
    }
    if (u.getStatus() != UserStatus.ACTIVE) {
      throw new AdminChangeRequestException(
          "ADMIN_RBAC_TARGET_USER_INACTIVE", HttpStatus.BAD_REQUEST, "Target user must be ACTIVE");
    }
    return u;
  }

  private static void validateRole(String role) {
    String r = role.trim().toUpperCase(Locale.ROOT);
    if (!PlatformAdminRbacConstants.assignableAdminRoles().contains(r)) {
      throw new AdminChangeRequestException(
          "ADMIN_RBAC_ROLE_INVALID",
          HttpStatus.BAD_REQUEST,
          "Unknown or non-assignable platform role");
    }
  }

  private static String effectiveSeverity(String role) {
    if (PlatformAdminRbacConstants.ROLE_PLATFORM_ADMIN.equals(
        role.trim().toUpperCase(Locale.ROOT))) {
      return "HIGH";
    }
    return "MEDIUM";
  }

  private Map<String, Object> impactWarnings(ParsedRbac parsed, User target, UUID actorUserId) {
    Map<String, Object> m = new HashMap<>();
    if (PlatformAdminRbacConstants.ROLE_PLATFORM_ADMIN.equals(parsed.role())) {
      m.put(
          "platformAdminWarning",
          "PLATFORM_ADMIN changes are high impact; verify IdP/SCIM mappings and break-glass policy.");
    }
    if ("REVOKE".equals(parsed.action())
        && PlatformAdminRbacConstants.ROLE_PLATFORM_ADMIN.equals(parsed.role())) {
      m.put(
          "lastPlatformAdminRevokeWarning",
          "Revoking PLATFORM_ADMIN may lock out operators; confirm another break-glass path exists.");
    }
    if (actorUserId != null
        && actorUserId.equals(parsed.targetUserId())
        && "REVOKE".equals(parsed.action())) {
      m.put(
          "selfRevokeWarning",
          "You are requesting revocation for your own user id; confirm intent.");
    }
    m.put("targetEmailDomain", safeEmailDomain(target.getEmail()));
    return m;
  }

  private static String safeEmailDomain(String email) {
    if (email == null || !email.contains("@")) {
      return "";
    }
    return email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT);
  }

  private ParsedRbac parse(
      String operationType,
      String requestedValue,
      Map<String, Object> structuredPayload,
      boolean requireReason) {
    String op = operationType == null ? "" : operationType.trim();
    String action;
    if (AdminOperationRegistry.OP_ADMIN_RBAC_ROLE_GRANT_REQUEST.equals(op)) {
      action = "GRANT";
    } else if (AdminOperationRegistry.OP_ADMIN_RBAC_ROLE_REVOKE_REQUEST.equals(op)) {
      action = "REVOKE";
    } else {
      throw new AdminChangeRequestException(
          "ADMIN_OPERATION_NOT_ALLOWED", HttpStatus.BAD_REQUEST, "Unsupported RBAC operation");
    }

    UUID userId;
    String role;
    String reason = null;
    if (structuredPayload != null && !structuredPayload.isEmpty()) {
      userId = readUuid(structuredPayload.get("userId"), "userId");
      role = readString(structuredPayload.get("role"), "role").toUpperCase(Locale.ROOT);
      Object r = structuredPayload.get("reason");
      if (r != null) {
        reason = String.valueOf(r).trim();
      }
    } else {
      String raw = requestedValue == null ? "" : requestedValue.trim();
      String[] parts = raw.split(":");
      if (parts.length != 3) {
        throw new AdminChangeRequestException(
            "ADMIN_CHANGE_REQUEST_INVALID",
            HttpStatus.BAD_REQUEST,
            "requestedValue must be ACTION:ROLE:userId or provide structuredPayload");
      }
      if (!action.equalsIgnoreCase(parts[0].trim())) {
        throw new AdminChangeRequestException(
            "ADMIN_CHANGE_REQUEST_INVALID",
            HttpStatus.BAD_REQUEST,
            "requestedValue action does not match operationType");
      }
      role = parts[1].trim().toUpperCase(Locale.ROOT);
      userId = UUID.fromString(parts[2].trim());
    }

    if (requireReason && (reason == null || reason.isBlank())) {
      throw new AdminChangeRequestException(
          "ADMIN_CHANGE_REQUEST_INVALID",
          HttpStatus.BAD_REQUEST,
          "structuredPayload.reason is required for RBAC role change requests");
    }

    String normalized =
        action.toLowerCase(Locale.ROOT) + ":" + role.toLowerCase(Locale.ROOT) + ":" + userId;
    return new ParsedRbac(action, role, userId, reason, normalized);
  }

  private static UUID readUuid(Object raw, String field) {
    if (raw == null) {
      throw new AdminChangeRequestException(
          "ADMIN_CHANGE_REQUEST_INVALID",
          HttpStatus.BAD_REQUEST,
          "structuredPayload." + field + " is required");
    }
    try {
      return UUID.fromString(String.valueOf(raw).trim());
    } catch (IllegalArgumentException e) {
      throw new AdminChangeRequestException(
          "ADMIN_CHANGE_REQUEST_INVALID",
          HttpStatus.BAD_REQUEST,
          "structuredPayload." + field + " must be a UUID");
    }
  }

  private static String readString(Object raw, String field) {
    if (raw == null || String.valueOf(raw).isBlank()) {
      throw new AdminChangeRequestException(
          "ADMIN_CHANGE_REQUEST_INVALID",
          HttpStatus.BAD_REQUEST,
          "structuredPayload." + field + " is required");
    }
    return String.valueOf(raw).trim();
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s;
  }

  private record ParsedRbac(
      String action,
      String role,
      UUID targetUserId,
      String reason,
      String normalizedRequestedValue) {}
}
