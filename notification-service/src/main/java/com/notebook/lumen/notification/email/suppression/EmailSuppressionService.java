package com.notebook.lumen.notification.email.suppression;

import com.notebook.lumen.notification.audit.AuditService;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailSuppressionService {
  private final EmailSuppressionRepository repository;
  private final AuditService auditService;
  private final MeterRegistry meterRegistry;

  public EmailSuppressionService(
      EmailSuppressionRepository repository,
      AuditService auditService,
      MeterRegistry meterRegistry) {
    this.repository = repository;
    this.auditService = auditService;
    this.meterRegistry = meterRegistry;
    for (EmailSuppressionReason reason : EmailSuppressionReason.values()) {
      ToDoubleFunction<EmailSuppressionRepository> countActive =
          repo -> repo.countActiveByReason(reason, Instant.now());
      meterRegistry.gauge(
          "email_suppression_active_total",
          java.util.List.of(
              io.micrometer.core.instrument.Tag.of("reason", reason.name().toLowerCase())),
          repository,
          countActive);
    }
  }

  @Transactional(readOnly = true)
  public Optional<EmailSuppression> active(String email) {
    return repository.findActive(normalize(email), Instant.now());
  }

  @Transactional
  public EmailSuppression manualCreate(
      String email, EmailSuppressionReason reason, Instant expiresAt) {
    if (reason == null || email == null || email.isBlank()) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST,
          "INVALID_EMAIL_SUPPRESSION_REQUEST",
          "email and reason are required");
    }
    String normalized = normalize(email);
    if (repository.findActive(normalized, Instant.now()).isPresent()) {
      throw new NotificationException(
          HttpStatus.CONFLICT,
          "EMAIL_SUPPRESSION_ALREADY_EXISTS",
          "Active email suppression already exists");
    }
    Instant now = Instant.now();
    EmailSuppression suppression =
        repository
            .findByNormalizedEmail(normalized)
            .map(
                existing -> {
                  existing.replace(reason, null, null, "MANUAL", now, expiresAt);
                  return existing;
                })
            .orElseGet(
                () ->
                    repository.save(
                        new EmailSuppression(
                            UUID.randomUUID(),
                            normalized,
                            reason,
                            null,
                            null,
                            "MANUAL",
                            now,
                            expiresAt)));
    meterRegistry
        .counter("email_suppression_created_total", "reason", reason.name().toLowerCase())
        .increment();
    auditService.record(
        "EMAIL_SUPPRESSION_MANUALLY_CREATED",
        "EMAIL_SUPPRESSION",
        suppression.getId(),
        Map.of("recipientEmailMasked", maskEmail(normalized), "reason", reason.name()));
    return suppression;
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
        repository
            .findByNormalizedEmail(normalized)
            .map(
                inactive -> {
                  inactive.replace(reason, provider, providerEventId, source, now, null);
                  return inactive;
                })
            .orElseGet(
                () ->
                    repository.save(
                        new EmailSuppression(
                            UUID.randomUUID(),
                            normalized,
                            reason,
                            provider,
                            providerEventId,
                            source,
                            now,
                            null)));
    meterRegistry
        .counter("email_suppressed_total", "reason", reason.name().toLowerCase())
        .increment();
    meterRegistry
        .counter("email_suppression_created_total", "reason", reason.name().toLowerCase())
        .increment();
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

  @Transactional(readOnly = true)
  public Page<EmailSuppression> search(
      String email, EmailSuppressionReason reason, boolean activeOnly, Pageable pageable) {
    return repository.search(
        email == null || email.isBlank() ? null : normalize(email),
        reason,
        activeOnly,
        Instant.now(),
        enforceSort(pageable));
  }

  @Transactional
  public EmailSuppression release(UUID id) {
    EmailSuppression suppression =
        repository
            .findById(id)
            .orElseThrow(
                () ->
                    new NotificationException(
                        HttpStatus.NOT_FOUND,
                        "EMAIL_SUPPRESSION_NOT_FOUND",
                        "Email suppression not found"));
    if (suppression.getReleasedAt() == null) {
      suppression.release(Instant.now());
      meterRegistry
          .counter(
              "email_suppression_released_total",
              "reason",
              suppression.getReason().name().toLowerCase())
          .increment();
      auditService.record(
          "EMAIL_SUPPRESSION_RELEASED",
          "EMAIL_SUPPRESSION",
          suppression.getId(),
          Map.of(
              "recipientEmailMasked",
              maskEmail(suppression.getEmail()),
              "reason",
              suppression.getReason().name()));
    }
    return suppression;
  }

  private String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private Pageable enforceSort(Pageable pageable) {
    if (pageable == null) {
      return org.springframework.data.domain.PageRequest.of(
          0, 50, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
    return org.springframework.data.domain.PageRequest.of(
        pageable.getPageNumber(),
        Math.min(Math.max(pageable.getPageSize(), 1), 200),
        Sort.by(Sort.Direction.DESC, "createdAt"));
  }

  private String maskEmail(String email) {
    int at = email.indexOf('@');
    return at <= 1 ? "****" : email.charAt(0) + "****" + email.substring(at);
  }

  private String safe(String value) {
    return value == null || value.isBlank() ? "unknown" : value;
  }
}
