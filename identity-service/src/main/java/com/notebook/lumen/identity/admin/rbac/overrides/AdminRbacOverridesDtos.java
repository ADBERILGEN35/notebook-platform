package com.notebook.lumen.identity.admin.rbac.overrides;

import java.time.Instant;
import java.util.List;

public final class AdminRbacOverridesDtos {
  private AdminRbacOverridesDtos() {}

  public record OverridesStatusResponse(
      boolean enabled,
      boolean failClosed,
      boolean fileConfigured,
      boolean loaded,
      String fileBasename,
      Instant lastLoadedAt,
      int assignmentCount,
      int validAssignmentCount,
      int ignoredAssignmentCount,
      List<String> warnings) {}

  public record OverridesValidateRequest(String content) {}

  public record OverridesValidateResponse(
      boolean valid,
      int assignmentCount,
      int validAssignmentCount,
      int ignoredAssignmentCount,
      List<String> warnings,
      List<String> errors) {}
}
