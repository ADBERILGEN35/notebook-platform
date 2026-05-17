package com.notebook.lumen.workspace.admin.retention;

import com.notebook.lumen.workspace.admin.retention.WorkspaceRetentionPlanDtos.WorkspaceRetentionPlanResponse;
import com.notebook.lumen.workspace.shared.exception.Exceptions;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin/retention/workspace")
public class InternalWorkspaceRetentionController {

  private final WorkspaceRetentionAdminAuthorizer authorizer;
  private final WorkspaceRetentionPlanService planService;

  public InternalWorkspaceRetentionController(
      WorkspaceRetentionAdminAuthorizer authorizer, WorkspaceRetentionPlanService planService) {
    this.authorizer = authorizer;
    this.planService = planService;
  }

  @GetMapping(path = "/plan", produces = "application/json")
  public WorkspaceRetentionPlanResponse plan(
      @RequestHeader(value = WorkspaceRetentionAdminAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      @RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun,
      @RequestParam(name = "target", required = false) String target,
      @RequestParam(name = "legalHoldScopes", required = false) String legalHoldScopes,
      @RequestParam(name = "generatedAt", required = false) Instant generatedAt) {
    authorizer.authorize(serviceAuthorization);
    if (!dryRun) {
      throw Exceptions.badRequest(
          "RETENTION_DRY_RUN_ONLY", "Workspace retention plan endpoint supports dry-run only");
    }
    Optional<WorkspaceRetentionTargetKey> targetFilter =
        target == null || target.isBlank()
            ? Optional.empty()
            : WorkspaceRetentionTargetKey.fromKey(target)
                .or(
                    () -> {
                      throw Exceptions.badRequest(
                          "RETENTION_TARGET_UNKNOWN", "Unknown target: " + target);
                    });
    Set<WorkspaceRetentionLegalHoldScope> scopes = parseScopes(legalHoldScopes);
    return planService.buildPlan(targetFilter, scopes, Optional.ofNullable(generatedAt));
  }

  private Set<WorkspaceRetentionLegalHoldScope> parseScopes(String raw) {
    if (raw == null || raw.isBlank()) return Set.of();
    Set<WorkspaceRetentionLegalHoldScope> parsed =
        EnumSet.noneOf(WorkspaceRetentionLegalHoldScope.class);
    for (String part : Arrays.asList(raw.split(","))) {
      String token = part.trim();
      if (token.isEmpty()) continue;
      String head = token.contains(":") ? token.substring(0, token.indexOf(':')) : token;
      WorkspaceRetentionLegalHoldScope.fromString(head).ifPresent(parsed::add);
    }
    return parsed;
  }
}
