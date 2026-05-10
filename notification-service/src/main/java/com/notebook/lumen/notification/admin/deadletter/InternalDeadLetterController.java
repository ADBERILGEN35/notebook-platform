package com.notebook.lumen.notification.admin.deadletter;

import com.notebook.lumen.notification.shared.security.InternalNotificationAuthorizer;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/admin/notifications/dead-letter")
public class InternalDeadLetterController {

  public static final String ADMIN_ACTOR_HEADER = "X-Admin-Actor-User-Id";

  private final InternalDeadLetterAdminProperties adminProperties;
  private final InternalNotificationAuthorizer authorizer;
  private final FanoutDeadLetterAdminService fanoutDeadLetterAdminService;

  public InternalDeadLetterController(
      InternalDeadLetterAdminProperties adminProperties,
      InternalNotificationAuthorizer authorizer,
      FanoutDeadLetterAdminService fanoutDeadLetterAdminService) {
    this.adminProperties = adminProperties;
    this.authorizer = authorizer;
    this.fanoutDeadLetterAdminService = fanoutDeadLetterAdminService;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public DeadLetterAdminDtos.DeadLetterListResponse list(
      @RequestHeader(value = InternalNotificationAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      @RequestParam(defaultValue = "fanout") String source,
      @RequestParam(defaultValue = "DEAD") String status,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) Instant createdFrom,
      @RequestParam(required = false) Instant createdTo,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @RequestParam(required = false) String sort) {
    ensureEnabled();
    if (!"fanout".equalsIgnoreCase(source)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only source=fanout is supported");
    }
    if (!"DEAD".equalsIgnoreCase(status)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only status=DEAD is supported");
    }
    authorizer.authorize(
        serviceAuthorization, InternalNotificationAuthorizer.ADMIN_NOTIFICATIONS_DEAD_LETTER_READ_SCOPE);
    var body =
        fanoutDeadLetterAdminService.list(eventType, createdFrom, createdTo, page, size, sort);
    fanoutDeadLetterAdminService.auditListViewed(body.items().size());
    return body;
  }

  @PostMapping(
      path = "/{id}/requeue/dry-run",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public DeadLetterAdminDtos.RequeueDryRunResponse dryRun(
      @RequestHeader(value = InternalNotificationAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      @PathVariable UUID id) {
    ensureEnabled();
    authorizer.authorize(
        serviceAuthorization, InternalNotificationAuthorizer.ADMIN_NOTIFICATIONS_DEAD_LETTER_READ_SCOPE);
    return fanoutDeadLetterAdminService.dryRun(id);
  }

  @PostMapping(path = "/{id}/requeue", consumes = MediaType.APPLICATION_JSON_VALUE)
  public DeadLetterAdminDtos.FanoutRequeueResponse requeue(
      @RequestHeader(value = InternalNotificationAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      @RequestHeader(value = ADMIN_ACTOR_HEADER, required = false) String actorUserId,
      @PathVariable UUID id,
      @RequestBody DeadLetterAdminDtos.FanoutRequeueHttpRequest body) {
    ensureEnabled();
    authorizer.authorize(
        serviceAuthorization, InternalNotificationAuthorizer.ADMIN_NOTIFICATIONS_DEAD_LETTER_REQUEUE_SCOPE);
    return fanoutDeadLetterAdminService.requeue(
        id, body.idempotencyKey(), body.reason(), actorUserId);
  }

  private void ensureEnabled() {
    if (!adminProperties.enabled()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dead-letter admin API is disabled");
    }
  }
}
