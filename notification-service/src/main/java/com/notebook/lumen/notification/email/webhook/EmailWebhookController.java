package com.notebook.lumen.notification.email.webhook;

import com.notebook.lumen.notification.audit.AuditService;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/email")
public class EmailWebhookController {
  private final NotificationProperties properties;
  private final EmailWebhookVerifierRegistry verifierRegistry;
  private final EmailWebhookParser parser;
  private final EmailProviderEventService eventService;
  private final AuditService auditService;
  private final MeterRegistry meterRegistry;

  public EmailWebhookController(
      NotificationProperties properties,
      EmailWebhookVerifierRegistry verifierRegistry,
      EmailWebhookParser parser,
      EmailProviderEventService eventService,
      AuditService auditService,
      MeterRegistry meterRegistry) {
    this.properties = properties;
    this.verifierRegistry = verifierRegistry;
    this.parser = parser;
    this.eventService = eventService;
    this.auditService = auditService;
    this.meterRegistry = meterRegistry;
  }

  @PostMapping("/{provider}")
  public EmailWebhookResponse receive(
      @PathVariable String provider, @RequestHeader HttpHeaders headers, @RequestBody String body) {
    if (!properties.email().webhooks().enabled()) {
      throw new NotificationException(
          HttpStatus.FORBIDDEN, "EMAIL_WEBHOOK_DISABLED", "Email webhooks are disabled");
    }
    String normalizedProvider = provider.trim().toLowerCase(java.util.Locale.ROOT);
    var verification = verifierRegistry.verifierFor(normalizedProvider).verify(headers, body);
    if (!verification.verified()) {
      if (verification.replayRejected()) {
        meterRegistry
            .counter("email_webhook_replay_rejected_total", "provider", normalizedProvider)
            .increment();
        auditService.record(
            "EMAIL_WEBHOOK_SIGNATURE_REJECTED",
            "EMAIL_WEBHOOK",
            UUID.nameUUIDFromBytes(
                normalizedProvider.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            Map.of("provider", normalizedProvider, "reason", "replay"));
        throw new NotificationException(
            HttpStatus.UNAUTHORIZED,
            "EMAIL_WEBHOOK_REPLAY_REJECTED",
            "Email webhook timestamp is outside tolerance");
      }
      meterRegistry
          .counter("email_webhook_signature_failures_total", "provider", normalizedProvider)
          .increment();
      auditService.record(
          "EMAIL_WEBHOOK_SIGNATURE_REJECTED",
          "EMAIL_WEBHOOK",
          UUID.nameUUIDFromBytes(
              normalizedProvider.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
          Map.of("provider", normalizedProvider));
      throw new NotificationException(
          HttpStatus.UNAUTHORIZED,
          "INVALID_EMAIL_WEBHOOK_SIGNATURE",
          "Email webhook signature is invalid");
    }
    var events = parser.parse(normalizedProvider, body);
    events.forEach(event -> eventService.process(normalizedProvider, event));
    return new EmailWebhookResponse(events.size());
  }

  public record EmailWebhookResponse(int acceptedEvents) {}
}
