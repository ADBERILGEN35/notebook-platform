package com.notebook.lumen.notification.email.suppression;

import com.notebook.lumen.notification.audit.AuditService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailSuppressionService {
  private final EmailSuppressionRepository repository;
  private final AuditService auditService;
  private final MeterRegistry meterRegistry;

  public EmailSuppressionService(
      EmailSuppressionRepository repository, AuditService auditService, MeterRegistry meterRegistry) {
    this.repository = repository;
    this.auditService = auditService;
    this.meterRegistry = meterRegistry;
  }

  @Transactional(readOnly = true)
  public Optional<EmailSuppression> active(String email) {
    return repository.findActive(normalize(email), Instant.now());
  }

  @Transactional
  public EmailSuppression createIfAbsent(
      String email,
      EmailSuppressionReason reason,
      String provider,
      String providerEventId,
      String source) {
    String normalized = normalize(email);
    var existing = repository.findActive(normalized, Instant.now());
    if (existing.isPresent()) {
      return existing.get();
    }
    Instant now = Instant.now();
    EmailSuppression suppression =
        new EmailSuppression(
            UUID.randomUUID(), normalized, reason, provider, providerEventId, source, now, null);
    repository.save(suppression);
    meterRegistry.counter("email_suppressed_total", "reason", reason.name().toLowerCase()).increment();
    auditService.record(
        "EMAIL_SUPPRESSION_CREATED",
        "EMAIL_SUPPRESSION",
        suppression.getId(),
        Map.of(
            "recipientEmailMasked",
            maskEmail(normalized),
            "reason",
            reason.name(),
            "provider",
            safe(provider)));
    return suppression;
  }

  private String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private String maskEmail(String email) {
    int at = email.indexOf('@');
    return at <= 1 ? "****" : email.charAt(0) + "****" + email.substring(at);
  }

  private String safe(String value) {
    return value == null || value.isBlank() ? "unknown" : value;
  }
}
