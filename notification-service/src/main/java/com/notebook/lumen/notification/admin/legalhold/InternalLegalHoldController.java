package com.notebook.lumen.notification.admin.legalhold;

import com.notebook.lumen.notification.admin.retention.InternalRetentionController;
import com.notebook.lumen.notification.shared.security.InternalNotificationAuthorizer;
import java.util.Optional;
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
@RequestMapping("/internal/admin/notifications/legal-holds")
public class InternalLegalHoldController {

  private final InternalLegalHoldAdminProperties adminProperties;
  private final InternalNotificationAuthorizer authorizer;
  private final NotificationLegalHoldAdminService legalHoldAdminService;

  public InternalLegalHoldController(
      InternalLegalHoldAdminProperties adminProperties,
      InternalNotificationAuthorizer authorizer,
      NotificationLegalHoldAdminService legalHoldAdminService) {
    this.adminProperties = adminProperties;
    this.authorizer = authorizer;
    this.legalHoldAdminService = legalHoldAdminService;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public LegalHoldAdminDtos.LegalHoldListResponse list(
      @RequestHeader(value = InternalNotificationAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      @RequestParam(required = false) String status) {
    ensureEnabled();
    authorizer.authorize(
        serviceAuthorization, InternalNotificationAuthorizer.ADMIN_NOTIFICATIONS_LEGAL_HOLD_READ_SCOPE);
    Optional<LegalHoldStatus> st = parseStatusFilter(status);
    return legalHoldAdminService.list(st);
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public LegalHoldAdminDtos.LegalHoldResponse create(
      @RequestHeader(value = InternalNotificationAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      @RequestHeader(value = InternalRetentionController.ADMIN_ACTOR_HEADER, required = false) String actorUserId,
      @RequestHeader(value = "X-Admin-Actor-Email", required = false) String actorEmail,
      @RequestBody LegalHoldAdminDtos.LegalHoldCreateRequest body) {
    ensureEnabled();
    authorizer.authorize(
        serviceAuthorization, InternalNotificationAuthorizer.ADMIN_NOTIFICATIONS_LEGAL_HOLD_WRITE_SCOPE);
    UUID actor = requireActor(actorUserId);
    return legalHoldAdminService.create(body, actor, actorEmail);
  }

  @PostMapping(
      path = "/{id}/release",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public LegalHoldAdminDtos.LegalHoldResponse release(
      @RequestHeader(value = InternalNotificationAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      @RequestHeader(value = InternalRetentionController.ADMIN_ACTOR_HEADER, required = false) String actorUserId,
      @PathVariable("id") UUID id,
      @RequestBody LegalHoldAdminDtos.LegalHoldReleaseRequest body) {
    ensureEnabled();
    authorizer.authorize(
        serviceAuthorization, InternalNotificationAuthorizer.ADMIN_NOTIFICATIONS_LEGAL_HOLD_WRITE_SCOPE);
    UUID actor = requireActor(actorUserId);
    return legalHoldAdminService.release(id, body, actor);
  }

  private void ensureEnabled() {
    if (!adminProperties.enabled()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Legal hold admin API is disabled");
    }
  }

  private static UUID requireActor(String actorUserId) {
    if (actorUserId == null || actorUserId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin actor header is required");
    }
    try {
      return UUID.fromString(actorUserId.trim());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid admin actor user id");
    }
  }

  private static Optional<LegalHoldStatus> parseStatusFilter(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(LegalHoldStatus.valueOf(raw.trim().toUpperCase()));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be ACTIVE or RELEASED");
    }
  }
}
