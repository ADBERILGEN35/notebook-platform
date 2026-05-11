package com.notebook.lumen.notification.admin.retention;

import com.notebook.lumen.notification.shared.security.InternalNotificationAuthorizer;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/admin/notifications/retention")
public class InternalRetentionController {

  public static final String ADMIN_ACTOR_HEADER = "X-Admin-Actor-User-Id";

  private final InternalRetentionAdminProperties adminProperties;
  private final InternalNotificationAuthorizer authorizer;
  private final NotificationRetentionAdminService retentionAdminService;

  public InternalRetentionController(
      InternalRetentionAdminProperties adminProperties,
      InternalNotificationAuthorizer authorizer,
      NotificationRetentionAdminService retentionAdminService) {
    this.adminProperties = adminProperties;
    this.authorizer = authorizer;
    this.retentionAdminService = retentionAdminService;
  }

  @GetMapping(path = "/plan", produces = MediaType.APPLICATION_JSON_VALUE)
  public RetentionAdminDtos.RetentionPlanResponse plan(
      @RequestHeader(value = InternalNotificationAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      @RequestParam(defaultValue = "true") boolean dryRun) {
    ensureEnabled();
    authorizer.authorize(
        serviceAuthorization,
        InternalNotificationAuthorizer.ADMIN_NOTIFICATIONS_RETENTION_READ_SCOPE);
    return retentionAdminService.plan(Instant.now(), dryRun);
  }

  @PostMapping(
      path = "/run",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public RetentionAdminDtos.RetentionRunResponse run(
      @RequestHeader(value = InternalNotificationAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      @RequestHeader(value = ADMIN_ACTOR_HEADER, required = false) String actorUserId,
      @RequestBody(required = false) RetentionAdminDtos.RetentionRunRequest body) {
    ensureEnabled();
    boolean dryRun = body == null || body.effectiveDryRun();
    if (dryRun) {
      authorizer.authorize(
          serviceAuthorization,
          InternalNotificationAuthorizer.ADMIN_NOTIFICATIONS_RETENTION_READ_SCOPE);
    } else {
      authorizer.authorize(
          serviceAuthorization,
          InternalNotificationAuthorizer.ADMIN_NOTIFICATIONS_RETENTION_RUN_SCOPE);
    }
    String target = body == null ? "ALL" : body.target();
    String reason = body == null ? null : body.reason();
    return retentionAdminService.run(dryRun, target, reason, actorUserId);
  }

  private void ensureEnabled() {
    if (!adminProperties.enabled()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Retention admin API is disabled");
    }
  }
}
