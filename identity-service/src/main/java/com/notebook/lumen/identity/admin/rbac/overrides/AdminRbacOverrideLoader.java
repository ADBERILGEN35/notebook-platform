package com.notebook.lumen.identity.admin.rbac.overrides;

import com.notebook.lumen.identity.audit.AuditService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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

  private final AtomicReference<RuntimeBundle> state =
      new AtomicReference<>(RuntimeBundle.disabled());

  private volatile boolean gaugesRegistered;

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
    bootstrapFromDisk(null);
    registerGaugesOnce();
  }

  private void registerGaugesOnce() {
    if (meterRegistry == null || gaugesRegistered) {
      return;
    }
    synchronized (this) {
      if (gaugesRegistered) {
        return;
      }
      Gauge.builder(
              "admin_rbac_overrides_current_assignments",
              this,
              l -> l.state.get().effective().assignments().size())
          .tag("state", "valid")
          .strongReference(true)
          .register(meterRegistry);
      Gauge.builder(
              "admin_rbac_overrides_current_assignments",
              this,
              l -> l.state.get().effective().ignoredRowCount())
          .tag("state", "ignored")
          .strongReference(true)
          .register(meterRegistry);
      Gauge.builder(
              "admin_rbac_overrides_last_reload_timestamp",
              this,
              l -> {
                Instant t = l.state.get().lastReloadAttemptAt();
                return t == null ? 0.0 : (double) t.getEpochSecond();
              })
          .strongReference(true)
          .register(meterRegistry);
      gaugesRegistered = true;
    }
  }

  /** Effective snapshot used for RBAC (last-known-good when enabled). */
  public AdminRbacOverrideSnapshot snapshot() {
    return state.get().effective();
  }

  public AdminRbacOverridesDtos.OverridesStatusResponse status() {
    RuntimeBundle b = state.get();
    AdminRbacOverrideSnapshot s = b.effective();
    int valid = s.assignments().size();
    int ignored = s.ignoredRowCount();
    int total = valid + ignored;
    List<String> merged = new ArrayList<>();
    merged.addAll(s.loadWarnings());
    for (String w : b.lastReloadWarnings()) {
      if (!merged.contains(w)) {
        merged.add(w);
      }
    }
    int warnCount = merged.size();
    int errCount = s.loadErrors().size();
    return new AdminRbacOverridesDtos.OverridesStatusResponse(
        props.enabled(),
        props.reloadEnabled(),
        props.lastKnownGoodEnabled(),
        props.failClosed(),
        s.fileConfigured(),
        s.loaded(),
        s.fileBasename(),
        s.lastLoadedAt(),
        s.checksum(),
        s.manifestVersion(),
        b.lastReloadAttemptAt(),
        b.lastReloadResult(),
        total,
        valid,
        ignored,
        warnCount,
        errCount,
        List.copyOf(merged));
  }

  public AdminRbacOverridesDtos.OverridesValidateResponse validateContent(String yaml) {
    AdminRbacOverrideParseResult pr = parser.parse(yaml == null ? "" : yaml, props, false);
    boolean limit = pr.warnings().contains("OVERRIDE_LIMIT_EXCEEDED");
    boolean ok = pr.errors().isEmpty() && !limit;
    int total = pr.validAssignmentCount() + pr.ignoredAssignmentCount();
    return new AdminRbacOverridesDtos.OverridesValidateResponse(
        ok,
        total,
        pr.validAssignmentCount(),
        pr.ignoredAssignmentCount(),
        pr.warnings(),
        pr.errors());
  }

  /**
   * Reload overrides from disk (manual or watch). Enforces {@link
   * AdminRbacOverridesProperties#reloadEnabled()}, reason length, and last-known-good semantics.
   */
  public AdminRbacOverridesDtos.OverridesReloadResponse reload(UUID actorUserId, String reason) {
    String r = reason == null ? "" : reason.trim();
    if (r.length() < 10) {
      throw AdminRbacOverridesReloadException.reasonRequired();
    }
    if (!props.enabled()) {
      throw AdminRbacOverridesReloadException.reloadDisabled();
    }
    if (!props.reloadEnabled()) {
      throw AdminRbacOverridesReloadException.reloadDisabled();
    }
    auditService.record(
        "ADMIN_RBAC_OVERRIDES_RELOAD_REQUESTED",
        actorUserId,
        "ADMIN_RBAC_OVERRIDES",
        null,
        null,
        java.util.Map.of(
            "reasonPresent", true,
            "checksum", "",
            "validAssignmentCount", 0,
            "ignoredAssignmentCount", 0,
            "warningCount", 0));

    Timer.Sample sample = meterRegistry == null ? null : Timer.start(meterRegistry);
    Instant attemptAt = Instant.now();
    RuntimeBundle prior = state.get();
    try {
      DiskLoadResult load = loadFromDiskBytes();
      if (!load.fileExists()) {
        return finishReloadMissingFile(actorUserId, sample, attemptAt, prior);
      }
      if (load.parseResult() == null || !load.errors().isEmpty()) {
        return finishReloadInvalid(actorUserId, sample, attemptAt, prior, load);
      }
      AdminRbacOverrideParseResult pr = load.parseResult();
      List<String> warnings = new ArrayList<>(pr.warnings());
      addDuplicateWarnings(pr.acceptedAssignments(), warnings);
      if (props.requireValidChecksum()) {
        // Reserved for future signed manifest / expected checksum wiring.
      }
      AdminRbacOverrideSnapshot snap =
          new AdminRbacOverrideSnapshot(
              true,
              true,
              Instant.now(),
              basename(props.filePath()),
              load.checksum(),
              pr.manifestVersion(),
              List.copyOf(pr.acceptedAssignments()),
              List.copyOf(warnings),
              List.of(),
              pr.ignoredAssignmentCount());
      RuntimeBundle next =
          new RuntimeBundle(
              snap,
              attemptAt,
              "SUCCESS",
              List.copyOf(warnings),
              pr.acceptedAssignments().size(),
              pr.ignoredAssignmentCount(),
              load.checksum());
      state.set(next);
      bumpReloadCounter("SUCCESS");
      bumpWarnings(warnings);
      bumpAssignmentMetrics(pr);
      if (sample != null) {
        sample.stop(
            Timer.builder("admin_rbac_overrides_reload_duration_seconds").register(meterRegistry));
      }
      auditService.record(
          "ADMIN_RBAC_OVERRIDES_RELOAD_SUCCEEDED",
          actorUserId,
          "ADMIN_RBAC_OVERRIDES",
          null,
          null,
          java.util.Map.of(
              "checksum",
              load.checksum(),
              "validAssignmentCount",
              pr.acceptedAssignments().size(),
              "ignoredAssignmentCount",
              pr.ignoredAssignmentCount(),
              "warningCount",
              warnings.size(),
              "reasonPresent",
              true));
      return new AdminRbacOverridesDtos.OverridesReloadResponse(
          true,
          "SUCCESS",
          load.checksum(),
          pr.acceptedAssignments().size(),
          pr.ignoredAssignmentCount(),
          List.copyOf(warnings));
    } catch (AdminRbacOverridesReloadException e) {
      throw e;
    }
  }

  private AdminRbacOverridesDtos.OverridesReloadResponse finishReloadMissingFile(
      UUID actorUserId, Timer.Sample sample, Instant attemptAt, RuntimeBundle prior) {
    List<String> w = List.of("OVERRIDE_FILE_UNAVAILABLE");
    boolean hadLkg = prior.effective().loaded() && props.lastKnownGoodEnabled();
    if (hadLkg) {
      RuntimeBundle next =
          new RuntimeBundle(
              prior.effective(),
              attemptAt,
              "FAILED",
              w,
              prior.lastReloadValidCount(),
              prior.lastReloadIgnoredCount(),
              "");
      state.set(next);
      bumpReloadCounter("FAILED");
      bumpLastKnownGoodUsed();
      auditService.record(
          "ADMIN_RBAC_OVERRIDES_LAST_KNOWN_GOOD_USED",
          actorUserId,
          "ADMIN_RBAC_OVERRIDES",
          null,
          null,
          java.util.Map.of(
              "checksum",
              prior.effective().checksum(),
              "validAssignmentCount",
              prior.effective().assignments().size(),
              "ignoredAssignmentCount",
              prior.effective().ignoredRowCount(),
              "warningCount",
              w.size(),
              "reasonPresent",
              true));
      auditService.record(
          "ADMIN_RBAC_OVERRIDES_RELOAD_FAILED",
          actorUserId,
          "ADMIN_RBAC_OVERRIDES",
          null,
          null,
          java.util.Map.of(
              "checksum",
              "",
              "validAssignmentCount",
              0,
              "ignoredAssignmentCount",
              0,
              "warningCount",
              w.size(),
              "reasonPresent",
              true));
      if (sample != null) {
        sample.stop(
            Timer.builder("admin_rbac_overrides_reload_duration_seconds").register(meterRegistry));
      }
      if (props.reloadFailClosed()) {
        throw AdminRbacOverridesReloadException.fileUnavailable();
      }
      return new AdminRbacOverridesDtos.OverridesReloadResponse(
          false, "FAILED", prior.effective().checksum(), 0, 0, w);
    }
    AdminRbacOverrideSnapshot failedSnap =
        new AdminRbacOverrideSnapshot(
            false,
            true,
            Instant.now(),
            basename(props.filePath()),
            "",
            "",
            List.of(),
            w,
            List.of(),
            0);
    state.set(new RuntimeBundle(failedSnap, attemptAt, "FAILED", w, 0, 0, ""));
    bumpReloadCounter("FAILED");
    if (sample != null) {
      sample.stop(
          Timer.builder("admin_rbac_overrides_reload_duration_seconds").register(meterRegistry));
    }
    auditService.record(
        "ADMIN_RBAC_OVERRIDES_RELOAD_FAILED",
        actorUserId,
        "ADMIN_RBAC_OVERRIDES",
        null,
        null,
        java.util.Map.of(
            "checksum",
            "",
            "validAssignmentCount",
            0,
            "ignoredAssignmentCount",
            0,
            "warningCount",
            w.size(),
            "reasonPresent",
            true));
    if (props.reloadFailClosed()) {
      throw AdminRbacOverridesReloadException.fileUnavailable();
    }
    return new AdminRbacOverridesDtos.OverridesReloadResponse(false, "FAILED", "", 0, 0, w);
  }

  private AdminRbacOverridesDtos.OverridesReloadResponse finishReloadInvalid(
      UUID actorUserId,
      Timer.Sample sample,
      Instant attemptAt,
      RuntimeBundle prior,
      DiskLoadResult load) {
    List<String> warnings = new ArrayList<>();
    AdminRbacOverrideParseResult pr0 = load.parseResult();
    if (pr0 != null) {
      warnings.addAll(pr0.warnings());
    }
    for (String e : load.errors()) {
      warnings.add("OVERRIDE_" + e);
    }
    boolean hadLkg = prior.effective().loaded() && props.lastKnownGoodEnabled();
    if (hadLkg) {
      RuntimeBundle next =
          new RuntimeBundle(
              prior.effective(),
              attemptAt,
              "FAILED",
              List.copyOf(warnings),
              prior.lastReloadValidCount(),
              prior.lastReloadIgnoredCount(),
              load.checksum());
      state.set(next);
      bumpReloadCounter("FAILED");
      bumpLastKnownGoodUsed();
      auditService.record(
          "ADMIN_RBAC_OVERRIDES_LAST_KNOWN_GOOD_USED",
          actorUserId,
          "ADMIN_RBAC_OVERRIDES",
          null,
          null,
          java.util.Map.of(
              "checksum",
              prior.effective().checksum(),
              "validAssignmentCount",
              prior.effective().assignments().size(),
              "ignoredAssignmentCount",
              prior.effective().ignoredRowCount(),
              "warningCount",
              warnings.size(),
              "reasonPresent",
              true));
      auditService.record(
          "ADMIN_RBAC_OVERRIDES_RELOAD_FAILED",
          actorUserId,
          "ADMIN_RBAC_OVERRIDES",
          null,
          null,
          java.util.Map.of(
              "checksum",
              load.checksum(),
              "validAssignmentCount",
              0,
              "ignoredAssignmentCount",
              0,
              "warningCount",
              warnings.size(),
              "reasonPresent",
              true));
      if (sample != null) {
        sample.stop(
            Timer.builder("admin_rbac_overrides_reload_duration_seconds").register(meterRegistry));
      }
      if (props.reloadFailClosed()) {
        throw AdminRbacOverridesReloadException.invalidManifest("Manifest validation failed.");
      }
      return new AdminRbacOverridesDtos.OverridesReloadResponse(
          false, "FAILED", prior.effective().checksum(), 0, 0, List.copyOf(warnings));
    }
    AdminRbacOverrideParseResult pr = load.parseResult();
    List<String> errList =
        pr != null ? new ArrayList<>(pr.errors()) : new ArrayList<>(load.errors());
    AdminRbacOverrideSnapshot failedSnap =
        new AdminRbacOverrideSnapshot(
            false,
            true,
            Instant.now(),
            basename(props.filePath()),
            load.checksum(),
            pr == null ? "" : pr.manifestVersion(),
            List.of(),
            new ArrayList<>(warnings),
            List.copyOf(errList),
            pr == null ? 0 : pr.ignoredAssignmentCount());
    state.set(
        new RuntimeBundle(
            failedSnap, attemptAt, "FAILED", List.copyOf(warnings), 0, 0, load.checksum()));
    bumpReloadCounter("FAILED");
    if (sample != null) {
      sample.stop(
          Timer.builder("admin_rbac_overrides_reload_duration_seconds").register(meterRegistry));
    }
    auditService.record(
        "ADMIN_RBAC_OVERRIDES_RELOAD_FAILED",
        actorUserId,
        "ADMIN_RBAC_OVERRIDES",
        null,
        null,
        java.util.Map.of(
            "checksum",
            load.checksum(),
            "validAssignmentCount",
            0,
            "ignoredAssignmentCount",
            pr == null ? 0 : pr.ignoredAssignmentCount(),
            "warningCount",
            warnings.size(),
            "reasonPresent",
            true));
    if (props.reloadFailClosed()) {
      throw AdminRbacOverridesReloadException.invalidManifest("Manifest validation failed.");
    }
    return new AdminRbacOverridesDtos.OverridesReloadResponse(
        false,
        "FAILED",
        load.checksum(),
        0,
        pr == null ? 0 : pr.ignoredAssignmentCount(),
        warnings);
  }

  private record DiskLoadResult(
      boolean fileExists,
      String checksum,
      AdminRbacOverrideParseResult parseResult,
      List<String> errors) {

    static DiskLoadResult missing() {
      return new DiskLoadResult(false, "", null, List.of());
    }
  }

  private DiskLoadResult loadFromDiskBytes() {
    Path p = Path.of(props.filePath());
    if (!Files.isRegularFile(p)) {
      return DiskLoadResult.missing();
    }
    try {
      byte[] bytes = Files.readAllBytes(p);
      String checksum = sha256Hex(bytes);
      String raw = new String(bytes, StandardCharsets.UTF_8);
      AdminRbacOverrideParseResult pr = parser.parse(raw, props, true);
      return new DiskLoadResult(true, checksum, pr, new ArrayList<>(pr.errors()));
    } catch (Exception e) {
      log.warn("admin_rbac_overrides_read_failed", e);
      return new DiskLoadResult(true, "", null, List.of("READ_ERROR"));
    }
  }

  void bootstrapFromDisk(UUID actorUserId) {
    if (!props.enabled()) {
      state.set(RuntimeBundle.disabled());
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
        auditLoadFailed(actorUserId, w);
        throw new IllegalStateException(
            "ADMIN_RBAC_OVERRIDES_FAIL_CLOSED is true but override file is missing: "
                + props.filePath());
      }
      AdminRbacOverrideSnapshot snap =
          new AdminRbacOverrideSnapshot(
              false,
              true,
              Instant.now(),
              basename(props.filePath()),
              "",
              "",
              List.of(),
              w,
              List.of(),
              0);
      state.set(new RuntimeBundle(snap, Instant.now(), "BOOTSTRAP_FAILED", w, 0, 0, ""));
      bumpLoadedCounter("failure");
      bumpWarnings(w);
      auditLoadFailed(actorUserId, w);
      log.warn("admin_rbac_overrides_file_missing path={}", props.filePath());
      return;
    }
    try {
      byte[] bytes = Files.readAllBytes(p);
      String checksum = sha256Hex(bytes);
      String raw = new String(bytes, StandardCharsets.UTF_8);
      AdminRbacOverrideParseResult pr = parser.parse(raw, props, true);
      List<String> warnings = new ArrayList<>(pr.warnings());
      addDuplicateWarnings(pr.acceptedAssignments(), warnings);
      List<String> errors = new ArrayList<>(pr.errors());
      if (!errors.isEmpty()) {
        if (props.failClosed()) {
          bumpLoadedCounter("failure");
          throw new IllegalStateException("admin-rbac-overrides invalid: " + errors);
        }
        AdminRbacOverrideSnapshot snap =
            new AdminRbacOverrideSnapshot(
                false,
                true,
                Instant.now(),
                basename(props.filePath()),
                checksum,
                pr.manifestVersion(),
                List.of(),
                warnings,
                errors,
                pr.ignoredAssignmentCount());
        state.set(
            new RuntimeBundle(snap, Instant.now(), "BOOTSTRAP_FAILED", warnings, 0, 0, checksum));
        bumpLoadedCounter("failure");
        bumpWarnings(warnings);
        auditLoadFailed(actorUserId, warnings);
        return;
      }
      AdminRbacOverrideSnapshot snap =
          new AdminRbacOverrideSnapshot(
              true,
              true,
              Instant.now(),
              basename(props.filePath()),
              checksum,
              pr.manifestVersion(),
              List.copyOf(pr.acceptedAssignments()),
              List.copyOf(warnings),
              List.of(),
              pr.ignoredAssignmentCount());
      state.set(
          new RuntimeBundle(
              snap,
              Instant.now(),
              "BOOTSTRAP_SUCCESS",
              List.copyOf(warnings),
              pr.acceptedAssignments().size(),
              pr.ignoredAssignmentCount(),
              checksum));
      bumpLoadedCounter("success");
      bumpWarnings(warnings);
      bumpAssignmentMetrics(pr);
      auditLoaded(actorUserId, pr, checksum);
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      List<String> w = new ArrayList<>();
      w.add("OVERRIDE_FILE_UNAVAILABLE");
      if (props.failClosed()) {
        bumpLoadedCounter("failure");
        throw new IllegalStateException(
            "Failed to read admin RBAC overrides: " + e.getMessage(), e);
      }
      log.warn("admin_rbac_overrides_read_failed", e);
      AdminRbacOverrideSnapshot snap =
          new AdminRbacOverrideSnapshot(
              false,
              true,
              Instant.now(),
              basename(props.filePath()),
              "",
              "",
              List.of(),
              w,
              List.of(),
              0);
      state.set(new RuntimeBundle(snap, Instant.now(), "BOOTSTRAP_FAILED", w, 0, 0, ""));
      bumpLoadedCounter("failure");
      bumpWarnings(w);
      auditLoadFailed(actorUserId, w);
    }
  }

  private record RuntimeBundle(
      AdminRbacOverrideSnapshot effective,
      Instant lastReloadAttemptAt,
      String lastReloadResult,
      List<String> lastReloadWarnings,
      int lastReloadValidCount,
      int lastReloadIgnoredCount,
      String lastAttemptChecksum) {

    static RuntimeBundle disabled() {
      return new RuntimeBundle(
          AdminRbacOverrideSnapshot.emptyDisabled(), null, "SKIPPED_DISABLED", List.of(), 0, 0, "");
    }
  }

  private void addDuplicateWarnings(
      List<AdminRbacOverrideAssignmentRow> rows, List<String> warnings) {
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

  private static String sha256Hex(byte[] bytes) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
      return "sha256:" + HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private void auditLoaded(UUID actor, AdminRbacOverrideParseResult pr, String checksum) {
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
            pr.warnings().size(),
            "checksum",
            checksum));
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
    Counter.builder("admin_rbac_overrides_loaded_total")
        .tag("result", result)
        .register(meterRegistry)
        .increment();
  }

  private void bumpReloadCounter(String result) {
    if (meterRegistry == null) {
      return;
    }
    Counter.builder("admin_rbac_overrides_reload_total")
        .tag("result", result)
        .register(meterRegistry)
        .increment();
  }

  private void bumpLastKnownGoodUsed() {
    if (meterRegistry == null) {
      return;
    }
    Counter.builder("admin_rbac_overrides_last_known_good_used_total")
        .register(meterRegistry)
        .increment();
  }

  private void bumpWarnings(List<String> warnings) {
    if (meterRegistry == null) {
      return;
    }
    for (String w : warnings) {
      Counter.builder("admin_rbac_overrides_warnings_total")
          .tag("type", w)
          .register(meterRegistry)
          .increment();
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

  /** Watch-triggered reload (no end-user actor). */
  public void reloadFromWatch() {
    if (!props.enabled() || !props.reloadEnabled() || !props.watchEnabled()) {
      return;
    }
    reload(null, "system:watch-debounced reload after manifest change.");
  }

  /**
   * Called by {@link AdminRbacOverridesFileWatcher} when the on-disk checksum differs from the
   * effective snapshot (no debounce beyond poll interval — operators should prefer manual reload
   * for critical changes).
   */
  public void maybeWatchReload() {
    if (!props.enabled() || !props.reloadEnabled() || !props.watchEnabled()) {
      return;
    }
    if (!snapshot().loaded()) {
      return;
    }
    Optional<String> cur = tryReadCurrentChecksum();
    if (cur.isEmpty()) {
      return;
    }
    String effective = snapshot().checksum();
    if (effective != null && cur.get().equals(effective)) {
      return;
    }
    reloadFromWatch();
  }

  Optional<String> tryReadCurrentChecksum() {
    Path p = Path.of(props.filePath());
    if (!Files.isRegularFile(p)) {
      return Optional.empty();
    }
    try {
      return Optional.of(sha256Hex(Files.readAllBytes(p)));
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
