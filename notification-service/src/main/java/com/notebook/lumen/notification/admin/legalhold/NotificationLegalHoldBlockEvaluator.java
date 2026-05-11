package com.notebook.lumen.notification.admin.legalhold;

import com.notebook.lumen.notification.admin.retention.RetentionPurgeKind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class NotificationLegalHoldBlockEvaluator {

  private final NotificationLegalHoldRepository repository;
  private final NotificationLegalHoldProperties properties;

  public NotificationLegalHoldBlockEvaluator(
      NotificationLegalHoldRepository repository, NotificationLegalHoldProperties properties) {
    this.repository = repository;
    this.properties = properties;
  }

  public boolean blockingEnabled() {
    return properties.enabled();
  }

  public List<NotificationLegalHoldEntity> loadActiveHolds() {
    if (!properties.enabled()) {
      return List.of();
    }
    return repository.findByStatusOrderByCreatedAtDesc(LegalHoldStatus.ACTIVE);
  }

  public boolean isBlocked(RetentionPurgeKind kind, List<NotificationLegalHoldEntity> activeHolds) {
    if (!properties.enabled() || activeHolds.isEmpty()) {
      return false;
    }
    return activeHolds.stream().anyMatch(h -> h.getScope().blocks(kind));
  }

  public HoldBlockResult evaluate(
      RetentionPurgeKind kind, List<NotificationLegalHoldEntity> activeHolds, Instant now) {
    List<String> keys = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    if (!properties.enabled() || activeHolds.isEmpty()) {
      return new HoldBlockResult(false, keys, warnings);
    }
    for (NotificationLegalHoldEntity h : activeHolds) {
      if (!h.getScope().blocks(kind)) {
        continue;
      }
      keys.add(h.getHoldKey());
      if (h.getExpiresAt() != null && h.getExpiresAt().isBefore(now)) {
        warnings.add(
            "Hold "
                + h.getHoldKey()
                + " has passed expiresAt but remains ACTIVE; explicit release required to allow purge.");
      }
    }
    if (!keys.isEmpty()) {
      warnings.add(0, "Target is blocked by active legal hold.");
    }
    return new HoldBlockResult(
        !keys.isEmpty(), keys.stream().sorted().distinct().toList(), warnings);
  }

  /** Distinct hold keys that block any of the given purge kinds. */
  public List<String> blockingKeysForKinds(
      Set<RetentionPurgeKind> kinds, List<NotificationLegalHoldEntity> holds) {
    if (!properties.enabled() || holds.isEmpty()) {
      return List.of();
    }
    Set<String> out = new LinkedHashSet<>();
    for (RetentionPurgeKind k : kinds) {
      for (NotificationLegalHoldEntity h : holds) {
        if (h.getScope().blocks(k)) {
          out.add(h.getHoldKey());
        }
      }
    }
    return out.stream().sorted().toList();
  }

  public record HoldBlockResult(
      boolean blocked, List<String> activeHoldKeys, List<String> warnings) {}
}
