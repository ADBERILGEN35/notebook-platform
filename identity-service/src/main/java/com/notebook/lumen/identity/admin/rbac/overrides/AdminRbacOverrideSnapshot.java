package com.notebook.lumen.identity.admin.rbac.overrides;

import java.time.Instant;
import java.util.List;

public record AdminRbacOverrideSnapshot(
    boolean loaded,
    boolean fileConfigured,
    Instant lastLoadedAt,
    String fileBasename,
    List<AdminRbacOverrideAssignmentRow> assignments,
    List<String> loadWarnings,
    List<String> loadErrors,
    int ignoredRowCount) {

  public static AdminRbacOverrideSnapshot emptyDisabled() {
    return new AdminRbacOverrideSnapshot(
        false, false, null, "", List.of(), List.of(), List.of(), 0);
  }
}
