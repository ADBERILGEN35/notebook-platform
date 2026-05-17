package com.notebook.lumen.identity.breakglass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.notebook.lumen.identity.audit.AuditService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class BreakGlassTokenRotationServiceTest {

  private BreakGlassTokenRotationEventRepository repository;
  private AuditService auditService;
  private FakeRepository fakeRepository;

  @BeforeEach
  void setUp() {
    fakeRepository = new FakeRepository();
    repository = fakeRepository;
    auditService = mock(AuditService.class);
  }

  @Test
  void recordRequired_createsEventWhenTrackingEnabled() {
    BreakGlassTokenRotationService svc =
        new BreakGlassTokenRotationService(
            repository, props(true, false, "sha256:deadbeef"), auditService);
    Optional<BreakGlassTokenRotationEvent> created =
        svc.recordRequiredAfterStaticUse(UUID.randomUUID(), "session-1", null);
    assertThat(created).isPresent();
    assertThat(created.get().getStatus()).isEqualTo(BreakGlassTokenRotationEventStatus.REQUIRED);
    assertThat(created.get().getOldTokenHashFingerprint()).startsWith("fp:");
    verify(auditService, atLeastOnce())
        .record(eq("BREAK_GLASS_TOKEN_ROTATION_REQUIRED"), any(), any(), any(), any(), any());
  }

  @Test
  void recordRequired_isNoopWhenDisabled() {
    BreakGlassTokenRotationService svc =
        new BreakGlassTokenRotationService(
            repository, props(false, false, "sha256:deadbeef"), auditService);
    Optional<BreakGlassTokenRotationEvent> created =
        svc.recordRequiredAfterStaticUse(null, null, null);
    assertThat(created).isEmpty();
    assertThat(fakeRepository.saved).isEmpty();
  }

  @Test
  void recordRequired_doesNotDuplicateForSameFingerprint() {
    BreakGlassTokenRotationService svc =
        new BreakGlassTokenRotationService(
            repository, props(true, false, "sha256:deadbeef"), auditService);
    Optional<BreakGlassTokenRotationEvent> first =
        svc.recordRequiredAfterStaticUse(UUID.randomUUID(), "s1", null);
    Optional<BreakGlassTokenRotationEvent> second =
        svc.recordRequiredAfterStaticUse(UUID.randomUUID(), "s2", null);
    assertThat(first).isPresent();
    assertThat(second).isPresent();
    assertThat(second.get().getId()).isEqualTo(first.get().getId());
  }

  @Test
  void acknowledge_requiresApiEnabled() {
    BreakGlassTokenRotationService svc =
        new BreakGlassTokenRotationService(
            repository, props(true, false, "sha256:deadbeef"), auditService);
    svc.recordRequiredAfterStaticUse(UUID.randomUUID(), "s1", null);
    BreakGlassTokenRotationEvent ev = fakeRepository.saved.values().iterator().next();
    assertThatThrownBy(
            () ->
                svc.acknowledge(
                    ev.getId(),
                    UUID.randomUUID(),
                    new BreakGlassRotationDtos.AcknowledgeRequest("Started rotation procedure."),
                    null))
        .isInstanceOf(BreakGlassException.class)
        .satisfies(
            e ->
                assertThat(((BreakGlassException) e).getErrorCode())
                    .isEqualTo("BREAK_GLASS_ROTATION_API_DISABLED"));
  }

  @Test
  void verify_failsWhenHashUnchanged() {
    BreakGlassTokenRotationService svc =
        new BreakGlassTokenRotationService(
            repository, props(true, true, "sha256:deadbeef"), auditService);
    svc.recordRequiredAfterStaticUse(UUID.randomUUID(), "s1", null);
    BreakGlassTokenRotationEvent ev = fakeRepository.saved.values().iterator().next();
    UUID actor = UUID.randomUUID();
    svc.acknowledge(
        ev.getId(),
        actor,
        new BreakGlassRotationDtos.AcknowledgeRequest("Begin rotation runbook."),
        null);
    assertThatThrownBy(
            () ->
                svc.verify(
                    ev.getId(),
                    actor,
                    new BreakGlassRotationDtos.VerifyRequest("Restarted pods after secret update."),
                    null))
        .isInstanceOf(BreakGlassException.class)
        .satisfies(
            e ->
                assertThat(((BreakGlassException) e).getErrorCode())
                    .isEqualTo("BREAK_GLASS_ROTATION_NOT_CHANGED"));
  }

  @Test
  void verify_succeedsWhenHashChanged() {
    BreakGlassProperties initial = props(true, true, "sha256:deadbeef");
    BreakGlassTokenRotationService svc =
        new BreakGlassTokenRotationService(repository, initial, auditService);
    svc.recordRequiredAfterStaticUse(UUID.randomUUID(), "s1", null);
    BreakGlassTokenRotationEvent ev = fakeRepository.saved.values().iterator().next();
    UUID actor = UUID.randomUUID();
    svc.acknowledge(
        ev.getId(),
        actor,
        new BreakGlassRotationDtos.AcknowledgeRequest("Begin rotation runbook."),
        null);

    BreakGlassProperties rotated = props(true, true, "sha256:cafef00d");
    BreakGlassTokenRotationService svcRotated =
        new BreakGlassTokenRotationService(repository, rotated, auditService);
    BreakGlassRotationDtos.RotationEventDetail detail =
        svcRotated.verify(
            ev.getId(),
            actor,
            new BreakGlassRotationDtos.VerifyRequest("Restarted pods after secret update."),
            null);
    assertThat(detail.status()).isEqualTo("VERIFIED");
    assertThat(detail.newFingerprint()).isNotEqualTo(detail.oldFingerprint());
    assertThat(detail.newFingerprint()).startsWith("fp:");
  }

  @Test
  void close_requiresVerified() {
    BreakGlassTokenRotationService svc =
        new BreakGlassTokenRotationService(
            repository, props(true, true, "sha256:deadbeef"), auditService);
    svc.recordRequiredAfterStaticUse(UUID.randomUUID(), "s1", null);
    BreakGlassTokenRotationEvent ev = fakeRepository.saved.values().iterator().next();
    UUID actor = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                svc.close(
                    ev.getId(),
                    actor,
                    new BreakGlassRotationDtos.CloseRequest("Closing without verify."),
                    null))
        .isInstanceOf(BreakGlassException.class)
        .satisfies(
            e ->
                assertThat(((BreakGlassException) e).getErrorCode())
                    .isEqualTo("BREAK_GLASS_ROTATION_INVALID_TRANSITION"));
  }

  @Test
  void detail_doesNotExposeRawTokenOrHash() {
    BreakGlassTokenRotationService svc =
        new BreakGlassTokenRotationService(
            repository, props(true, true, "sha256:deadbeef"), auditService);
    svc.recordRequiredAfterStaticUse(UUID.randomUUID(), "s1", null);
    BreakGlassTokenRotationEvent ev = fakeRepository.saved.values().iterator().next();
    BreakGlassRotationDtos.RotationEventDetail detail = svc.detail(ev.getId(), null);
    assertThat(detail.oldFingerprint()).startsWith("fp:");
    // fingerprint length capped to fingerprintLength + prefix
    assertThat(detail.oldFingerprint().length()).isLessThanOrEqualTo("fp:".length() + 32);
    assertThat(detail.oldFingerprint()).doesNotContain("deadbeef");
  }

  private static BreakGlassProperties props(boolean tracking, boolean api, String tokenHash) {
    return new BreakGlassProperties(
        true,
        "static-token",
        true,
        false,
        false,
        "disabled",
        true,
        60,
        true,
        false,
        false,
        true,
        24,
        30,
        false,
        tokenHash,
        "",
        "notebook-break-glass-offline",
        "identity-service",
        300,
        15,
        true,
        true,
        1,
        true,
        5,
        15,
        true,
        tracking,
        api,
        12,
        5);
  }

  private static final class FakeRepository implements BreakGlassTokenRotationEventRepository {
    final Map<UUID, BreakGlassTokenRotationEvent> saved = new HashMap<>();

    @Override
    public Optional<BreakGlassTokenRotationEvent> findByRotationKey(String rotationKey) {
      return saved.values().stream()
          .filter(e -> rotationKey.equals(e.getRotationKey()))
          .findFirst();
    }

    @Override
    public Page<BreakGlassTokenRotationEvent> findByStatusInOrderByRequiredAtDesc(
        List<BreakGlassTokenRotationEventStatus> statuses, Pageable pageable) {
      List<BreakGlassTokenRotationEvent> filtered = new ArrayList<>();
      for (BreakGlassTokenRotationEvent e : saved.values()) {
        if (statuses.contains(e.getStatus())) filtered.add(e);
      }
      return new PageImpl<>(filtered, pageable, filtered.size());
    }

    @Override
    public Page<BreakGlassTokenRotationEvent> findAllByOrderByRequiredAtDesc(Pageable pageable) {
      return new PageImpl<>(new ArrayList<>(saved.values()), pageable, saved.size());
    }

    @Override
    public long countByStatus(BreakGlassTokenRotationEventStatus status) {
      return saved.values().stream().filter(e -> e.getStatus() == status).count();
    }

    @Override
    public List<BreakGlassTokenRotationEvent> findByStatusAndOldTokenHashFingerprint(
        BreakGlassTokenRotationEventStatus status, String fingerprint) {
      List<BreakGlassTokenRotationEvent> out = new ArrayList<>();
      for (BreakGlassTokenRotationEvent e : saved.values()) {
        if (e.getStatus() == status
            && fingerprint != null
            && fingerprint.equals(e.getOldTokenHashFingerprint())) {
          out.add(e);
        }
      }
      return out;
    }

    @Override
    public Optional<BreakGlassTokenRotationEvent> findTopByStatusOrderByVerifiedAtDesc(
        BreakGlassTokenRotationEventStatus status) {
      return saved.values().stream()
          .filter(e -> e.getStatus() == status && e.getVerifiedAt() != null)
          .max((a, b) -> a.getVerifiedAt().compareTo(b.getVerifiedAt()));
    }

    @Override
    public Optional<BreakGlassTokenRotationEvent> findTopByOrderByRequiredAtDesc() {
      return saved.values().stream().max((a, b) -> a.getRequiredAt().compareTo(b.getRequiredAt()));
    }

    @Override
    public Optional<BreakGlassTokenRotationEvent> findTopByStatusInOrderByRequiredAtAsc(
        List<BreakGlassTokenRotationEventStatus> statuses) {
      return saved.values().stream()
          .filter(e -> statuses.contains(e.getStatus()))
          .min((a, b) -> a.getRequiredAt().compareTo(b.getRequiredAt()));
    }

    @Override
    public long countByRequiredAtAfter(Instant threshold) {
      return saved.values().stream().filter(e -> e.getRequiredAt().isAfter(threshold)).count();
    }

    @Override
    public <S extends BreakGlassTokenRotationEvent> S save(S entity) {
      saved.put(entity.getId(), entity);
      return entity;
    }

    @Override
    public <S extends BreakGlassTokenRotationEvent> List<S> saveAll(Iterable<S> entities) {
      List<S> out = new ArrayList<>();
      for (S e : entities) {
        saved.put(e.getId(), e);
        out.add(e);
      }
      return out;
    }

    @Override
    public Optional<BreakGlassTokenRotationEvent> findById(UUID uuid) {
      return Optional.ofNullable(saved.get(uuid));
    }

    @Override
    public boolean existsById(UUID uuid) {
      return saved.containsKey(uuid);
    }

    @Override
    public List<BreakGlassTokenRotationEvent> findAll() {
      return new ArrayList<>(saved.values());
    }

    @Override
    public List<BreakGlassTokenRotationEvent> findAllById(Iterable<UUID> uuids) {
      List<BreakGlassTokenRotationEvent> out = new ArrayList<>();
      for (UUID id : uuids) {
        BreakGlassTokenRotationEvent e = saved.get(id);
        if (e != null) out.add(e);
      }
      return out;
    }

    @Override
    public long count() {
      return saved.size();
    }

    @Override
    public void deleteById(UUID uuid) {
      saved.remove(uuid);
    }

    @Override
    public void delete(BreakGlassTokenRotationEvent entity) {
      saved.remove(entity.getId());
    }

    @Override
    public void deleteAllById(Iterable<? extends UUID> uuids) {
      for (UUID id : uuids) saved.remove(id);
    }

    @Override
    public void deleteAll(Iterable<? extends BreakGlassTokenRotationEvent> entities) {
      for (BreakGlassTokenRotationEvent e : entities) saved.remove(e.getId());
    }

    @Override
    public void deleteAll() {
      saved.clear();
    }

    @Override
    public List<BreakGlassTokenRotationEvent> findAll(org.springframework.data.domain.Sort sort) {
      return findAll();
    }

    @Override
    public Page<BreakGlassTokenRotationEvent> findAll(Pageable pageable) {
      List<BreakGlassTokenRotationEvent> all = new ArrayList<>(saved.values());
      return new PageImpl<>(all, pageable, all.size());
    }

    @Override
    public void flush() {}

    @Override
    public <S extends BreakGlassTokenRotationEvent> S saveAndFlush(S entity) {
      return save(entity);
    }

    @Override
    public <S extends BreakGlassTokenRotationEvent> List<S> saveAllAndFlush(Iterable<S> entities) {
      return saveAll(entities);
    }

    @Override
    public void deleteAllInBatch(Iterable<BreakGlassTokenRotationEvent> entities) {
      deleteAll(entities);
    }

    @Override
    public void deleteAllByIdInBatch(Iterable<UUID> uuids) {
      deleteAllById(uuids);
    }

    @Override
    public void deleteAllInBatch() {
      saved.clear();
    }

    @Override
    public BreakGlassTokenRotationEvent getOne(UUID uuid) {
      return saved.get(uuid);
    }

    @Override
    public BreakGlassTokenRotationEvent getById(UUID uuid) {
      return saved.get(uuid);
    }

    @Override
    public BreakGlassTokenRotationEvent getReferenceById(UUID uuid) {
      return saved.get(uuid);
    }

    @Override
    public <S extends BreakGlassTokenRotationEvent> Optional<S> findOne(
        org.springframework.data.domain.Example<S> example) {
      return Optional.empty();
    }

    @Override
    public <S extends BreakGlassTokenRotationEvent> List<S> findAll(
        org.springframework.data.domain.Example<S> example) {
      return List.of();
    }

    @Override
    public <S extends BreakGlassTokenRotationEvent> List<S> findAll(
        org.springframework.data.domain.Example<S> example,
        org.springframework.data.domain.Sort sort) {
      return List.of();
    }

    @Override
    public <S extends BreakGlassTokenRotationEvent> Page<S> findAll(
        org.springframework.data.domain.Example<S> example, Pageable pageable) {
      return Page.empty();
    }

    @Override
    public <S extends BreakGlassTokenRotationEvent> long count(
        org.springframework.data.domain.Example<S> example) {
      return 0;
    }

    @Override
    public <S extends BreakGlassTokenRotationEvent> boolean exists(
        org.springframework.data.domain.Example<S> example) {
      return false;
    }

    @Override
    public <S extends BreakGlassTokenRotationEvent, R> R findBy(
        org.springframework.data.domain.Example<S> example,
        java.util.function.Function<
                org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R>
            queryFunction) {
      return null;
    }
  }
}
