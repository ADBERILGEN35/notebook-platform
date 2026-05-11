package com.notebook.lumen.identity.admin.rbac.overrides;

import java.time.Instant;
import java.util.List;

public final class AdminRbacOverridesDtos {
  private AdminRbacOverridesDtos() {}

  public record OverridesStatusResponse(
      boolean enabled,
      boolean reloadEnabled,
      boolean lastKnownGoodEnabled,
      boolean failClosed,
      boolean fileConfigured,
      boolean loaded,
      String fileBasename,
      Instant loadedAt,
      String checksum,
      String manifestVersion,
      Instant lastReloadAttemptAt,
      String lastReloadResult,
      int assignmentCount,
      int validAssignmentCount,
      int ignoredAssignmentCount,
      int warningCount,
      int errorCount,
      List<String> warnings) {}

  public record OverridesValidateRequest(String content) {}

  public record OverridesValidateResponse(
      boolean valid,
      int assignmentCount,
      int validAssignmentCount,
      int ignoredAssignmentCount,
      List<String> warnings,
      List<String> errors) {}

  public record OverridesReloadRequest(String reason) {}

  public record OverridesReloadResponse(
      boolean reloaded,
      String result,
      String checksum,
      int validAssignmentCount,
      int ignoredAssignmentCount,
      List<String> warnings) {}
}
