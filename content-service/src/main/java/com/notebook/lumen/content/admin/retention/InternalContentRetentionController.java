package com.notebook.lumen.content.admin.retention;

import com.notebook.lumen.content.admin.retention.ContentRetentionPlanDtos.ContentRetentionPlanResponse;
import com.notebook.lumen.content.shared.exception.ContentException;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin/retention/content")
public class InternalContentRetentionController {

  private final ContentRetentionAdminAuthorizer authorizer;
  private final ContentRetentionPlanService planService;

  public InternalContentRetentionController(
      ContentRetentionAdminAuthorizer authorizer, ContentRetentionPlanService planService) {
    this.authorizer = authorizer;
    this.planService = planService;
  }

  @GetMapping(path = "/plan", produces = MediaType.APPLICATION_JSON_VALUE)
  public ContentRetentionPlanResponse plan(
      @RequestHeader(value = ContentRetentionAdminAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      @RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun,
      @RequestParam(name = "target", required = false) String target,
      @RequestParam(name = "legalHoldScopes", required = false) String legalHoldScopes,
      @RequestParam(name = "generatedAt", required = false) Instant generatedAt) {
    authorizer.authorize(serviceAuthorization);
    if (!dryRun) {
      throw new ContentException(
          HttpStatus.BAD_REQUEST,
          "RETENTION_DRY_RUN_ONLY",
          "Content retention plan endpoint supports dry-run only");
    }
    Optional<ContentRetentionTargetKey> targetFilter =
        target == null || target.isBlank()
            ? Optional.empty()
            : ContentRetentionTargetKey.fromKey(target)
                .or(
                    () -> {
                      throw new ContentException(
                          HttpStatus.BAD_REQUEST,
                          "RETENTION_TARGET_UNKNOWN",
                          "Unknown target: " + target);
                    });
    Set<ContentRetentionLegalHoldScope> scopes = parseScopes(legalHoldScopes);
    return planService.buildPlan(targetFilter, scopes, Optional.ofNullable(generatedAt));
  }

  private Set<ContentRetentionLegalHoldScope> parseScopes(String raw) {
    if (raw == null || raw.isBlank()) return Set.of();
    Set<ContentRetentionLegalHoldScope> parsed = EnumSet.noneOf(ContentRetentionLegalHoldScope.class);
    for (String part : Arrays.asList(raw.split(","))) {
      String token = part.trim();
      if (token.isEmpty()) continue;
      String head = token.contains(":") ? token.substring(0, token.indexOf(':')) : token;
      ContentRetentionLegalHoldScope.fromString(head).ifPresent(parsed::add);
    }
    return parsed;
  }
}
