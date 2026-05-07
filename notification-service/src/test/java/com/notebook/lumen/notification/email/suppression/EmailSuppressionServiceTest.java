package com.notebook.lumen.notification.email.suppression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.notification.audit.AuditService;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmailSuppressionServiceTest {
  private final EmailSuppressionRepository repository = mock(EmailSuppressionRepository.class);
  private final EmailSuppressionService service =
      new EmailSuppressionService(repository, mock(AuditService.class), new SimpleMeterRegistry());

  @Test
  void manualCreateRejectsExistingActiveSuppression() {
    when(repository.findActive(eq("user@example.com"), any()))
        .thenReturn(
            Optional.of(
                new EmailSuppression(
                    UUID.randomUUID(),
                    "user@example.com",
                    EmailSuppressionReason.BOUNCE,
                    "generic-http",
                    "evt-1",
                    "WEBHOOK",
                    Instant.now(),
                    null)));

    assertThatThrownBy(
            () -> service.manualCreate("USER@example.com", EmailSuppressionReason.MANUAL, null))
        .isInstanceOf(NotificationException.class)
        .extracting("errorCode")
        .isEqualTo("EMAIL_SUPPRESSION_ALREADY_EXISTS");

    verify(repository, never()).save(any());
  }

  @Test
  void releaseMarksSuppressionReleased() {
    UUID id = UUID.randomUUID();
    EmailSuppression suppression =
        new EmailSuppression(
            id,
            "user@example.com",
            EmailSuppressionReason.MANUAL,
            null,
            null,
            "MANUAL",
            Instant.now(),
            null);
    when(repository.findById(id)).thenReturn(Optional.of(suppression));

    var released = service.release(id);

    assertThat(released.getReleasedAt()).isNotNull();
  }

  @Test
  void manualCreateReusesReleasedSuppressionRow() {
    EmailSuppression suppression =
        new EmailSuppression(
            UUID.randomUUID(),
            "user@example.com",
            EmailSuppressionReason.BOUNCE,
            "generic-http",
            "evt-1",
            "WEBHOOK",
            Instant.now(),
            null);
    suppression.release(Instant.now());
    when(repository.findActive(eq("user@example.com"), any())).thenReturn(Optional.empty());
    when(repository.findByNormalizedEmail("user@example.com")).thenReturn(Optional.of(suppression));

    var created = service.manualCreate("USER@example.com", EmailSuppressionReason.MANUAL, null);

    assertThat(created.getReason()).isEqualTo(EmailSuppressionReason.MANUAL);
    assertThat(created.getReleasedAt()).isNull();
    verify(repository, never()).save(any());
  }

  @Test
  void expiredSuppressionIsNotActive() {
    EmailSuppression suppression =
        new EmailSuppression(
            UUID.randomUUID(),
            "user@example.com",
            EmailSuppressionReason.MANUAL,
            null,
            null,
            "MANUAL",
            Instant.now().minusSeconds(600),
            Instant.now().minusSeconds(60));

    assertThat(suppression.active(Instant.now())).isFalse();
  }
}
