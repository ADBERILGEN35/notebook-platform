package com.notebook.lumen.notification.email.suppression.api;

import com.notebook.lumen.notification.email.suppression.EmailSuppressionReason;
import com.notebook.lumen.notification.email.suppression.EmailSuppressionService;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import com.notebook.lumen.notification.shared.security.InternalNotificationAuthorizer;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/email/suppressions")
public class InternalEmailSuppressionController {
  private final EmailSuppressionService suppressionService;
  private final InternalNotificationAuthorizer authorizer;

  public InternalEmailSuppressionController(
      EmailSuppressionService suppressionService, InternalNotificationAuthorizer authorizer) {
    this.suppressionService = suppressionService;
    this.authorizer = authorizer;
  }

  @GetMapping
  public EmailSuppressionPageResponse list(
      @RequestHeader(InternalNotificationAuthorizer.HEADER_NAME) String serviceAuthorization,
      @RequestParam(required = false) String email,
      @RequestParam(required = false) EmailSuppressionReason reason,
      @RequestParam(defaultValue = "true") boolean activeOnly,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    authorizeSuppression(
        serviceAuthorization, InternalNotificationAuthorizer.SUPPRESSION_READ_SCOPE);
    var suppressions =
        suppressionService.search(
            email, reason, activeOnly, PageRequest.of(Math.max(page, 0), clampSize(size)));
    return new EmailSuppressionPageResponse(
        suppressions.map(EmailSuppressionResponse::from).toList(),
        suppressions.getNumber(),
        suppressions.getSize(),
        suppressions.getTotalElements(),
        suppressions.getTotalPages(),
        suppressions.isLast());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public EmailSuppressionResponse create(
      @RequestHeader(InternalNotificationAuthorizer.HEADER_NAME) String serviceAuthorization,
      @Valid @RequestBody EmailSuppressionCreateRequest request) {
    authorizeSuppression(
        serviceAuthorization, InternalNotificationAuthorizer.SUPPRESSION_MANAGE_SCOPE);
    return EmailSuppressionResponse.from(
        suppressionService.manualCreate(request.email(), request.reason(), request.expiresAt()));
  }

  @PostMapping("/{id}/release")
  public EmailSuppressionResponse release(
      @RequestHeader(InternalNotificationAuthorizer.HEADER_NAME) String serviceAuthorization,
      @PathVariable UUID id) {
    authorizeSuppression(
        serviceAuthorization, InternalNotificationAuthorizer.SUPPRESSION_MANAGE_SCOPE);
    return EmailSuppressionResponse.from(suppressionService.release(id));
  }

  private int clampSize(int size) {
    return Math.min(Math.max(size, 1), 200);
  }

  private void authorizeSuppression(String serviceAuthorization, String scope) {
    try {
      authorizer.authorize(serviceAuthorization, scope);
    } catch (NotificationException e) {
      if (e.getStatus() == HttpStatus.FORBIDDEN) {
        throw new NotificationException(
            HttpStatus.FORBIDDEN,
            "EMAIL_SUPPRESSION_ACCESS_DENIED",
            "Email suppression scope is required");
      }
      throw e;
    }
  }
}
