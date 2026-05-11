package com.notebook.lumen.identity.admin.rbac.overrides;

import com.notebook.lumen.identity.audit.AuditService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class AdminRbacOverrideLoader {

  private static final Logger log = LoggerFactory.getLogger(AdminRbacOverrideLoader.class);

  private final AdminRbacOverridesProperties props;
  private final AdminRbacOverrideManifestParser parser;
  private final AuditService auditService;
  private final MeterRegistry meterRegistry;

  private final AtomicReference<AdminRbacOverrideSnapshot> snapshot =
      new AtomicReference<>(AdminRbacOverrideSnapshot.emptyDisabled());

  public AdminRbacOverrideLoader(
      AdminRbacOverridesProperties props,
      AdminRbacOverrideManifestParser parser,
      AuditService auditService,
      ObjectProvider<MeterRegistry> meterRegistry) {
    this.props = props;
    this.parser = parser;
    this.auditService = auditService;
    this.meterRegistry = meterRegistry.getIfAvailable();
  }

  @PostConstruct
  void loadOnStartup() {
    reloadFromDisk(null);
  }

  /** Reload manifest from configured path (no-op when disabled). */
  public void reloadFromDisk(UUID actorUserId) {
    if (!props.enabled()) {
      snapshot.set(AdminRbacOverrideSnapshot.emptyDisabled());
      bumpLoadedCounter("skipped_disabled");
      return;
    }
    Path p = Path.of(props.filePath());
    boolean exists = Files.isRegularFile(p);
    if (!exists) {
      List<String> w = new ArrayList<>();
      w.add("OVERRIDE_FILE_UNAVAILABLE");
      if (props.failClosed()) {
        bumpLoadedCounter("failure");
        bumpWarnings(w);
        throw new IllegalStateException(
            "ADMIN_RBAC_OVERRIDES_FAIL_CLOSED is true but override file is missing: " + props.filePath());
      }
      snapshot.set(
          new AdminRbacOverrideSnapshot(
              false, true, Instant.now(), basename(props.filePath()), List.of(), w, List.of(), 0));
      bumpLoadedCounter("failure");
      bumpWarnings(w);
      auditLoadFailed(actorUserId, w);
      log.warn("admin_rbac_overrides_file_missing path={}", props.filePath());
      return;
    }
    try {
      String raw = Files.readString(p);
      AdminRbacOverrideParseResult pr = parser.parse(raw, props, true);
      List<String> warnings = new ArrayList<>(pr.warnings());
      addDuplicateWarnings(pr.acceptedAssignments(), warnings);
      List<String> errors = new ArrayList<>(pr.errors());
      if (!errors.isEmpty()) {
        if (props.failClosed()) {
          bumpLoadedCounter("failure");
          throw new IllegalStateException("admin-rbac-overrides invalid: " + errors);
        }
        snapshot.set(
            new AdminRbacOverrideSnapshot(
                false,
                true,
                Instant.now(),
                basename(props.filePath()),
                List.of(),
                warnings,
                errors,
                pr.ignoredAssignmentCount()));
        bumpLoadedCounter("failure");
        bumpWarnings(warnings);
        auditLoadFailed(actorUserId, warnings);
        return;
      }
      snapshot.set(
          new AdminRbacOverrideSnapshot(
              true,
              true,
              Instant.now(),
              basename(props.filePath()),
              List.copyOf(pr.acceptedAssignments()),
              List.copyOf(warnings),
              List.of(),
              pr.ignoredAssignmentCount()));
      bumpLoadedCounter("success");
      bumpWarnings(warnings);
      bumpAssignmentMetrics(pr);
      auditLoaded(actorUserId, pr);
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      List<String> w = new ArrayList<>();
      w.add("OVERRIDE_FILE_UNAVAILABLE");
      if (props.failClosed()) {
        bumpLoadedCounter("failure");
        throw new IllegalStateException("Failed to read admin RBAC overrides: " + e.getMessage(), e);
      }
      log.warn("admin_rbac_overrides_read_failed", e);
      snapshot.set(
          new AdminRbacOverrideSnapshot(
              false, true, Instant.now(), basename(props.filePath()), List.of(), w, List.of(), 0));
      bumpLoadedCounter("failure");
      bumpWarnings(w);
      auditLoadFailed(actorUserId, w);
    }
  }

  private void addDuplicateWarnings(List<AdminRbacOverrideAssignmentRow> rows, List<String> warnings) {
    Set<String> seen = new HashSet<>();
    for (AdminRbacOverrideAssignmentRow r : rows) {
      String key =
          r.userId().toString()
              + "|"
              + r.role().toUpperCase(Locale.ROOT)
              + "|"
              + r.action().toUpperCase(Locale.ROOT);
      if (!seen.add(key)) {
        warnings.add("RBAC_ASSIGNMENT_DUPLICATE");
      }
    }
  }

  public AdminRbacOverrideSnapshot snapshot() {
    return snapshot.get();
  }

  public AdminRbacOverridesDtos.OverridesStatusResponse status() {
    AdminRbacOverrideSnapshot s = snapshot.get();
    int valid = s.assignments().size();
    int ignored = s.ignoredRowCount();
    int total = valid + ignored;
    return new AdminRbacOverridesDtos.OverridesStatusResponse(
        props.enabled(),
        props.failClosed(),
        s.fileConfigured(),
        s.loaded(),
        s.fileBasename(),
        s.lastLoadedAt(),
        total,
        valid,
        ignored,
        List.copyOf(s.loadWarnings()));
  }

  public AdminRbacOverridesDtos.OverridesValidateResponse validateContent(String yaml) {
    AdminRbacOverrideParseResult pr = parser.parse(yaml == null ? "" : yaml, props, false);
    boolean limit = pr.warnings().contains("OVERRIDE_LIMIT_EXCEEDED");
    boolean ok = pr.errors().isEmpty() && !limit;
    int total = pr.validAssignmentCount() + pr.ignoredAssignmentCount();
    return new AdminRbacOverridesDtos.OverridesValidateResponse(
        ok, total, pr.validAssignmentCount(), pr.ignoredAssignmentCount(), pr.warnings(), pr.errors());
  }

  private void auditLoaded(UUID actor, AdminRbacOverrideParseResult pr) {
    auditService.record(
        "ADMIN_RBAC_OVERRIDES_LOADED",
        actor,
        "ADMIN_RBAC_OVERRIDES",
        null,
        null,
        java.util.Map.of(
            "assignmentCount",
            pr.acceptedAssignments().size(),
            "validCount",
            pr.validAssignmentCount(),
            "ignoredCount",
            pr.ignoredAssignmentCount(),
            "warningsCount",
            pr.warnings().size()));
  }

  private void auditLoadFailed(UUID actor, List<String> warnings) {
    auditService.record(
        "ADMIN_RBAC_OVERRIDES_LOAD_FAILED",
        actor,
        "ADMIN_RBAC_OVERRIDES",
        null,
        null,
        java.util.Map.of(
            "warningsCount",
            warnings.size(),
            "warningsPreview",
            warnings.stream().limit(5).toList().toString()));
  }

  private static String basename(String path) {
    if (path == null || path.isBlank()) {
      return "";
    }
    int i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    return i >= 0 ? path.substring(i + 1) : path;
  }

  private void bumpLoadedCounter(String result) {
    if (meterRegistry == null) {
      return;
    }
    Counter.builder("admin_rbac_overrides_loaded_total").tag("result", result).register(meterRegistry).increment();
  }

  private void bumpWarnings(List<String> warnings) {
    if (meterRegistry == null) {
      return;
    }
    for (String w : warnings) {
      Counter.builder("admin_rbac_overrides_warnings_total").tag("type", w).register(meterRegistry).increment();
    }
  }

  private void bumpAssignmentMetrics(AdminRbacOverrideParseResult pr) {
    if (meterRegistry == null) {
      return;
    }
    meterRegistry
        .counter("admin_rbac_overrides_assignments", "state", "valid")
        .increment(Math.max(0, pr.validAssignmentCount()));
    meterRegistry
        .counter("admin_rbac_overrides_assignments", "state", "ignored")
        .increment(Math.max(0, pr.ignoredAssignmentCount()));
  }
}
