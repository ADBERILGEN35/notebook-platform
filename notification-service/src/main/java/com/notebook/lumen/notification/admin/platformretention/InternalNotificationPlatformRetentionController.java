package com.notebook.lumen.notification.admin.platformretention;

import com.notebook.lumen.notification.admin.platformretention.NotificationPlatformRetentionDtos.NotificationPlatformRetentionPlanResponse;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import com.notebook.lumen.notification.shared.security.InternalNotificationAuthorizer;
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
@RequestMapping("/internal/admin/retention/notification")
public class InternalNotificationPlatformRetentionController {

  private final InternalNotificationAuthorizer authorizer;
  private final NotificationPlatformRetentionPlanService planService;

  public InternalNotificationPlatformRetentionController(
      InternalNotificationAuthorizer authorizer,
      NotificationPlatformRetentionPlanService planService) {
    this.authorizer = authorizer;
    this.planService = planService;
  }

  @GetMapping(path = "/plan", produces = MediaType.APPLICATION_JSON_VALUE)
  public NotificationPlatformRetentionPlanResponse plan(
      @RequestHeader(value = InternalNotificationAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      @RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun,
      @RequestParam(name = "target", required = false) String target,
      @RequestParam(name = "legalHoldScopes", required = false) String legalHoldScopes,
      @RequestParam(name = "generatedAt", required = false) Instant generatedAt) {
    authorizer.authorize(
        serviceAuthorization, InternalNotificationAuthorizer.ADMIN_RETENTION_READ_SCOPE);
    if (!dryRun) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST,
          "RETENTION_DRY_RUN_ONLY",
          "Notification retention plan endpoint supports dry-run only");
    }
    Optional<NotificationPlatformRetentionTargetKey> targetFilter =
        target == null || target.isBlank()
            ? Optional.empty()
            : NotificationPlatformRetentionTargetKey.fromKey(target)
                .or(
                    () -> {
                      throw new NotificationException(
                          HttpStatus.BAD_REQUEST,
                          "RETENTION_TARGET_UNKNOWN",
                          "Unknown target: " + target);
                    });
    Set<NotificationPlatformRetentionLegalHoldScope> scopes = parseScopes(legalHoldScopes);
    return planService.buildPlan(targetFilter, scopes, Optional.ofNullable(generatedAt));
  }

  private Set<NotificationPlatformRetentionLegalHoldScope> parseScopes(String raw) {
    if (raw == null || raw.isBlank()) return Set.of();
    Set<NotificationPlatformRetentionLegalHoldScope> parsed =
        EnumSet.noneOf(NotificationPlatformRetentionLegalHoldScope.class);
    for (String part : Arrays.asList(raw.split(","))) {
      String token = part.trim();
      if (token.isEmpty()) continue;
      String head = token.contains(":") ? token.substring(0, token.indexOf(':')) : token;
      NotificationPlatformRetentionLegalHoldScope.fromString(head).ifPresent(parsed::add);
    }
    return parsed;
  }
}
