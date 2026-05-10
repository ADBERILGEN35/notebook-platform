package com.notebook.lumen.notification.admin.deadletter;

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
import com.notebook.lumen.notification.user.fanout.NotificationFanoutOutbox;
import com.notebook.lumen.notification.user.fanout.NotificationFanoutOutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class FanoutDeadLetterAdminServiceTest {

  @Mock private NotificationFanoutOutboxRepository outboxRepository;
  @Mock private DeadLetterRequeueRequestRepository requeueRequestRepository;
  @Mock private AuditService auditService;

  private FanoutDeadLetterAdminService service;

  @BeforeEach
  void setUp() {
    service =
        new FanoutDeadLetterAdminService(
            outboxRepository,
            requeueRequestRepository,
            new NotificationDeadLetterProperties(3, 200, "pepper"),
            auditService);
  }

  @Test
  void list_mapsRecipientHashWithoutPayload() {
    UUID uid = UUID.randomUUID();
    var row =
        new NotificationFanoutOutbox(
            UUID.randomUUID(), UUID.randomUUID(), uid, "notification.created", Map.of("secret", "x"), Instant.now());
    row.markDead("REDIS_PUBLISH_FAILED: redis down", Instant.parse("2026-05-10T12:00:00Z"));
    when(outboxRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(row)));

    var page = service.list(null, null, null, 0, 50, "createdAt,desc");

    assertThat(page.items()).hasSize(1);
    assertThat(page.items().getFirst().eventType()).isEqualTo("notification.created");
    assertThat(page.items().getFirst().recipientUserIdHash()).isNotBlank();
    assertThat(page.items().getFirst().lastErrorCode()).isEqualTo("REDIS_PUBLISH_FAILED");
    assertThat(page.items().getFirst().lastErrorSummary()).contains("redis down");
  }

  @Test
  void dryRun_deadAndUnderLimit_canRequeue() {
    UUID id = UUID.randomUUID();
    var row =
        new NotificationFanoutOutbox(
            id, UUID.randomUUID(), UUID.randomUUID(), "notification.created", Map.of(), Instant.now());
    row.markDead("x", Instant.now());
    when(outboxRepository.findById(id)).thenReturn(Optional.of(row));

    var r = service.dryRun(id);

    assertThat(r.canRequeue()).isTrue();
    assertThat(r.checks()).extracting(DeadLetterAdminDtos.RequeueDryRunCheck::passed).containsExactly(true, true);
  }

  @Test
  void requeue_dead_becomesPending() {
    UUID id = UUID.randomUUID();
    var row =
        new NotificationFanoutOutbox(
            id, UUID.randomUUID(), UUID.randomUUID(), "notification.created", Map.of(), Instant.now());
    row.markDead("err", Instant.now());
    when(outboxRepository.findByIdForUpdate(id)).thenReturn(Optional.of(row));
    when(requeueRequestRepository.findBySourceAndDeadLetterIdAndIdempotencyKey(
            eq("FANOUT_OUTBOX"), eq(id), eq("key-1")))
        .thenReturn(Optional.empty());

    var resp = service.requeue(id, "key-1", "Redis outage recovered — retry fanout", "admin-1");

    assertThat(resp.status()).isEqualTo("PENDING");
    assertThat(resp.requeueCount()).isEqualTo(1);
    assertThat(resp.idempotentReplay()).isFalse();
    verify(outboxRepository).save(row);
    verify(requeueRequestRepository).save(any(DeadLetterRequeueRequestEntity.class));
    ArgumentCaptor<Map<String, ?>> meta = ArgumentCaptor.forClass(Map.class);
    verify(auditService).record(any(), any(), any(), meta.capture());
    assertThat(meta.getValue()).containsKey("actorUserId");
  }

  @Test
  void requeue_notDead_throws() {
    UUID id = UUID.randomUUID();
    var row =
        new NotificationFanoutOutbox(
            id, UUID.randomUUID(), UUID.randomUUID(), "notification.created", Map.of(), Instant.now());
    when(outboxRepository.findByIdForUpdate(id)).thenReturn(Optional.of(row));
    when(requeueRequestRepository.findBySourceAndDeadLetterIdAndIdempotencyKey(
            eq("FANOUT_OUTBOX"), eq(id), eq("k")))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.requeue(id, "k", "reason reason reason", "admin"))
        .isInstanceOf(NotificationException.class);
    verify(outboxRepository, never()).save(any());
  }

  @Test
  void requeue_idempotent_returnsWithoutSecondSave() {
    UUID id = UUID.randomUUID();
    var row =
        new NotificationFanoutOutbox(
            id, UUID.randomUUID(), UUID.randomUUID(), "notification.created", Map.of(), Instant.now());
    row.markDead("e", Instant.now());
    row.requeueFromDead(Instant.now(), "admin");
    when(outboxRepository.findByIdForUpdate(id)).thenReturn(Optional.of(row));
    when(requeueRequestRepository.findBySourceAndDeadLetterIdAndIdempotencyKey(
            eq("FANOUT_OUTBOX"), eq(id), eq("same-key")))
        .thenReturn(
            Optional.of(
                new DeadLetterRequeueRequestEntity(
                    UUID.randomUUID(),
                    "FANOUT_OUTBOX",
                    id,
                    "same-key",
                    "admin",
                    "SUCCESS",
                    Instant.now())));

    var r = service.requeue(id, "same-key", "reason reason reason", "admin");
    assertThat(r.idempotentReplay()).isTrue();
    verify(outboxRepository, never()).save(any());
  }
}
