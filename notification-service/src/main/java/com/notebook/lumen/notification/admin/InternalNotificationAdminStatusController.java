package com.notebook.lumen.notification.admin;

import com.notebook.lumen.notification.shared.security.InternalNotificationAuthorizer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/admin/status")
public class InternalNotificationAdminStatusController {
  private final InternalAdminStatusProperties statusProperties;
  private final InternalNotificationAuthorizer authorizer;
  private final NotificationAdminStatusService statusService;

  public InternalNotificationAdminStatusController(
      InternalAdminStatusProperties statusProperties,
      InternalNotificationAuthorizer authorizer,
      NotificationAdminStatusService statusService) {
    this.statusProperties = statusProperties;
    this.authorizer = authorizer;
    this.statusService = statusService;
  }

  @GetMapping(path = "/notification", produces = MediaType.APPLICATION_JSON_VALUE)
  public NotificationAdminStatusResponse notification(
      @RequestHeader(value = InternalNotificationAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization) {
    if (!statusProperties.enabled()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Internal admin status is disabled");
    }
    authorizer.authorize(serviceAuthorization, InternalNotificationAuthorizer.ADMIN_STATUS_SCOPE);
    return statusService.build();
  }
}
