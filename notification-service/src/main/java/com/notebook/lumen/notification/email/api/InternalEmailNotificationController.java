package com.notebook.lumen.notification.email.api;

import com.notebook.lumen.notification.email.application.EmailNotificationService;
import com.notebook.lumen.notification.shared.security.InternalNotificationAuthorizer;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/notifications/email")
public class InternalEmailNotificationController {
  private final EmailNotificationService service;
  private final InternalNotificationAuthorizer authorizer;

  public InternalEmailNotificationController(
      EmailNotificationService service, InternalNotificationAuthorizer authorizer) {
    this.service = service;
    this.authorizer = authorizer;
  }

  @PostMapping
  EmailNotificationResponse create(
      @RequestHeader HttpHeaders headers, @Valid @RequestBody EmailNotificationRequest request) {
    authorizer.authorize(headers.getFirst(InternalNotificationAuthorizer.HEADER_NAME));
    return service.enqueue(request);
  }
}
