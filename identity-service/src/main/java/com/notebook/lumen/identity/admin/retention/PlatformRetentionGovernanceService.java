package com.notebook.lumen.identity.admin.retention;

import com.notebook.lumen.identity.audit.AuditService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformRetentionGovernanceService {
  private static final String AGGREGATE = "PLATFORM_RETENTION_GOVERNANCE";
  private static final int MAX_REASON_LENGTH = 2_000;

  private final RetentionTargetRegistry registry;
  private final PlatformLegalHoldRepository legalHoldRepository;
  private final PlatformRetentionProperties properties;
  private final AuditService auditService;
  private final MeterRegistry meterRegistry;

  public PlatformRetentionGovernanceService(
      RetentionTargetRegistry registry,
      PlatformLegalHoldRepository legalHoldRepository,
      PlatformRetentionProperties properties,
      AuditService auditService,
      MeterRegistry meterRegistry) {
    this.registry = registry;
    this.legalHoldRepository = legalHoldRepository;
    this.properties = properties;
    this.auditService = auditService;
    this.meterRegistry = meterRegistry;
  }

  public PlatformRetentionDtos.TargetsResponse targets(HttpServletRequest request) {
    List<PlatformRetentionDtos.TargetResponse> targets =
        registry.targets().stream().map(this::toTarget).toList();
    auditService.record(
        "PLATFORM_RETENTION_TARGETS_VIEWED",
        null,
        AGGREGATE,
        null,
        request,
        Map.of("targetCount", targets.size()));
    for (RetentionTargetDefinition target : registry.targets()) {
      meterRegistry
          .counter(
              "platform_retention_targets_total",
              "status",
              target.status().name(),
              "riskLevel",
              target.riskLevel().name())
          .increment(0);
    }
    return new PlatformRetentionDtos.TargetsResponse(Instant.now(), targets);
  }

  public PlatformRetentionDtos.PlanResponse plan(
      String targetFilter, boolean dryRun, HttpServletRequest request) {
    Instant now = Instant.now();
    List<PlatformLegalHold> activeHolds = activeHolds();
    List<PlatformRetentionDtos.PlanTargetResponse> out = new ArrayList<>();
    for (RetentionTargetDefinition target : registry.targets()) {
      if (targetFilter != null
          && !targetFilter.isBlank()
          && !target.targetKey().equals(targetFilter.trim())) {
        continue;
      }
      out.add(planTarget(target, activeHolds, now));
    }
    List<String> warnings = new ArrayList<>();
    warnings.add("Platform retention governance is dry-run only; no destructive purge endpoint exists.");
    if (!dryRun) {
      warnings.add("dryRun=false requested but ignored by Faz 98 platform planner.");
    }
    auditService.record(
        "PLATFORM_RETENTION_PLAN_GENERATED",
        null,
        AGGREGATE,
        null,
        request,
        Map.of(
            "targetFilterPresent",
            targetFilter != null && !targetFilter.isBlank(),
            "dryRun",
            true,
            "targetCount",
            out.size()));
    meterRegistry.counter("platform_retention_plan_generated_total").increment();
    return new PlatformRetentionDtos.PlanResponse(now, true, List.copyOf(out), List.copyOf(warnings));
  }

  public PlatformRetentionDtos.LegalHoldListResponse legalHolds(
      Optional<PlatformLegalHoldStatus> status) {
    List<PlatformLegalHold> rows =
        status
            .map(legalHoldRepository::findByStatusOrderByCreatedAtDesc)
            .orElseGet(legalHoldRepository::findAllByOrderByCreatedAtDesc);
    return new PlatformRetentionDtos.LegalHoldListResponse(rows.stream().map(this::toHold).toList());
  }

  @Transactional
  public PlatformRetentionDtos.LegalHoldResponse createLegalHold(
      PlatformRetentionDtos.LegalHoldCreateRequest request,
      UUID actorUserId,
      HttpServletRequest httpRequest) {
    validateLegalHoldEnabled();
    String holdKey = validateHoldKey(request.holdKey());
    String reason = validateReason(request.reason(), "reason");
    PlatformLegalHoldScope scope =
        request.scope() == null ? PlatformLegalHoldScope.ALL_PLATFORM : request.scope();
    if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
      auditDenied("PLATFORM_LEGAL_HOLD_CREATE_DENIED", actorUserId, httpRequest, "EXPIRES_AT_INVALID");
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expiresAt must be in the future");
    }
    if (legalHoldRepository.existsByHoldKey(holdKey)) {
      auditDenied("PLATFORM_LEGAL_HOLD_CREATE_DENIED", actorUserId, httpRequest, "DUPLICATE_HOLD_KEY");
      throw new ResponseStatusException(HttpStatus.CONFLICT, "holdKey already exists");
    }
    PlatformLegalHold saved =
        legalHoldRepository.save(
            new PlatformLegalHold(
                UUID.randomUUID(),
                holdKey,
                scope,
                request.scopeRefId(),
                reason,
                actorUserId,
                Instant.now(),
                request.expiresAt()));
    auditService.record(
        "PLATFORM_LEGAL_HOLD_CREATED",
        actorUserId,
        "PLATFORM_LEGAL_HOLD",
        saved.getId(),
        httpRequest,
        Map.of(
            "holdKey",
            holdKey,
            "scope",
            scope.name(),
            "scopeRefPresent",
            request.scopeRefId() != null,
            "reasonPresent",
            true));
    meterRegistry.gauge(
        "platform_legal_holds_active",
        List.of(io.micrometer.core.instrument.Tag.of("scope", scope.name())),
        legalHoldRepository,
        repo -> repo.countByStatus(PlatformLegalHoldStatus.ACTIVE));
    return toHold(saved);
  }

  @Transactional
  public PlatformRetentionDtos.LegalHoldResponse releaseLegalHold(
      UUID id,
      PlatformRetentionDtos.LegalHoldReleaseRequest request,
      UUID actorUserId,
      HttpServletRequest httpRequest) {
    validateLegalHoldEnabled();
    String reason = validateReason(request.reason(), "release reason");
    PlatformLegalHold hold =
        legalHoldRepository
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Legal hold not found"));
    if (hold.getStatus() != PlatformLegalHoldStatus.ACTIVE) {
      auditDenied("PLATFORM_LEGAL_HOLD_RELEASE_DENIED", actorUserId, httpRequest, "NOT_ACTIVE");
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Only ACTIVE holds can be released");
    }
    hold.release(actorUserId, reason, Instant.now());
    PlatformLegalHold saved = legalHoldRepository.save(hold);
    auditService.record(
        "PLATFORM_LEGAL_HOLD_RELEASED",
        actorUserId,
        "PLATFORM_LEGAL_HOLD",
        saved.getId(),
        httpRequest,
        Map.of(
            "holdKey",
            saved.getHoldKey(),
            "scope",
            saved.getScope().name(),
            "reasonPresent",
            true));
    return toHold(saved);
  }

  private PlatformRetentionDtos.PlanTargetResponse planTarget(
      RetentionTargetDefinition target, List<PlatformLegalHold> activeHolds, Instant now) {
    List<String> warnings = new ArrayList<>();
    warnings.add("Inventory only. Destructive purge is not implemented.");
    if (!target.dryRunSupported()) {
      warnings.add("Dry-run count is not implemented for this target.");
    }
    List<String> holdKeys = blockingHoldKeys(target, activeHolds, now, warnings);
    boolean blocked = !holdKeys.isEmpty();
    if (blocked) {
      meterRegistry
          .counter("platform_retention_blocked_by_hold_total", "targetKey", target.targetKey())
          .increment();
    }
    return new PlatformRetentionDtos.PlanTargetResponse(
        target.targetKey(),
        target.service(),
        target.status(),
        target.defaultRetentionDays(),
        null,
        0,
        blocked,
        target.riskLevel(),
        holdKeys,
        warnings.stream().distinct().toList());
  }

  private List<String> blockingHoldKeys(
      RetentionTargetDefinition target,
      List<PlatformLegalHold> activeHolds,
      Instant now,
      List<String> warnings) {
    if (!properties.legalHoldEnabled() || !target.legalHoldSupported()) {
      return List.of();
    }
    List<String> keys = new ArrayList<>();
    for (PlatformLegalHold hold : activeHolds) {
      if (!hold.getScope().blocks(target)) continue;
      keys.add(hold.getHoldKey());
      if (hold.getExpiresAt() != null && hold.getExpiresAt().isBefore(now)) {
        warnings.add(
            "Hold "
                + hold.getHoldKey()
                + " has passed expiresAt but remains ACTIVE; explicit release required.");
      }
    }
    if (!keys.isEmpty()) {
      warnings.add("Target is blocked by active platform legal hold.");
    }
    return keys.stream().sorted().distinct().toList();
  }

  private List<PlatformLegalHold> activeHolds() {
    if (!properties.legalHoldEnabled()) {
      return List.of();
    }
    return legalHoldRepository.findByStatusOrderByCreatedAtDesc(PlatformLegalHoldStatus.ACTIVE);
  }

  private PlatformRetentionDtos.TargetResponse toTarget(RetentionTargetDefinition target) {
    return new PlatformRetentionDtos.TargetResponse(
        target.targetKey(),
        target.service(),
        target.displayName(),
        target.description(),
        target.dataClass(),
        target.defaultRetentionDays(),
        target.legalHoldSupported(),
        target.destructivePurgeSupported(),
        target.dryRunSupported(),
        target.archiveRequiredBeforePurge(),
        target.riskLevel(),
        target.status());
  }

  private PlatformRetentionDtos.LegalHoldResponse toHold(PlatformLegalHold hold) {
    return new PlatformRetentionDtos.LegalHoldResponse(
        hold.getId(),
        hold.getHoldKey(),
        hold.getScope(),
        hold.getScopeRefId() != null,
        hold.getStatus(),
        hold.getCreatedByUserId(),
        hold.getCreatedAt(),
        hold.getReleasedByUserId(),
        hold.getReleasedAt(),
        hold.getExpiresAt());
  }

  private void validateLegalHoldEnabled() {
    if (!properties.enabled() || !properties.legalHoldEnabled()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Platform legal hold is disabled");
    }
  }

  private static String validateHoldKey(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "holdKey is required");
    }
    String value = raw.trim();
    if (value.length() > 200) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "holdKey too long");
    }
    return value;
  }

  private static String validateReason(String raw, String label) {
    if (raw == null || raw.trim().length() < 10) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " must be at least 10 characters");
    }
    return raw.trim().length() > MAX_REASON_LENGTH
        ? raw.trim().substring(0, MAX_REASON_LENGTH)
        : raw.trim();
  }

  private void auditDenied(
      String eventType, UUID actorUserId, HttpServletRequest request, String reasonCode) {
    auditService.record(
        eventType,
        actorUserId,
        AGGREGATE,
        null,
        request,
        Map.of("code", reasonCode, "reasonPresent", true));
  }
}
