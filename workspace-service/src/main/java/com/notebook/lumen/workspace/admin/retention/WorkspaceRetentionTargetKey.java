package com.notebook.lumen.workspace.admin.retention;

import java.util.Optional;

public enum WorkspaceRetentionTargetKey {
  WORKSPACE_INVITATIONS_EXPIRED(
      "workspace.invitations_expired", WorkspaceRetentionTargetStatus.DRY_RUN_READY),
  WORKSPACE_AUDIT_LIKE_EVENTS(
      "workspace.audit_like_events", WorkspaceRetentionTargetStatus.DRY_RUN_READY),
  WORKSPACE_MEMBERSHIP_INACTIVE(
      "workspace.membership_inactive", WorkspaceRetentionTargetStatus.INVENTORY_ONLY),
  WORKSPACE_WORKSPACES("workspace.workspaces", WorkspaceRetentionTargetStatus.INVENTORY_ONLY),
  WORKSPACE_NOTEBOOKS("workspace.notebooks", WorkspaceRetentionTargetStatus.INVENTORY_ONLY),
  WORKSPACE_TAGS("workspace.tags", WorkspaceRetentionTargetStatus.INVENTORY_ONLY),
  WORKSPACE_MEMBERSHIPS("workspace.memberships", WorkspaceRetentionTargetStatus.INVENTORY_ONLY);

  private final String key;
  private final WorkspaceRetentionTargetStatus defaultStatus;

  WorkspaceRetentionTargetKey(String key, WorkspaceRetentionTargetStatus defaultStatus) {
    this.key = key;
    this.defaultStatus = defaultStatus;
  }

  public String key() {
    return key;
  }

  public WorkspaceRetentionTargetStatus defaultStatus() {
    return defaultStatus;
  }

  public static Optional<WorkspaceRetentionTargetKey> fromKey(String raw) {
    if (raw == null) return Optional.empty();
    String trimmed = raw.trim();
    for (WorkspaceRetentionTargetKey value : values()) {
      if (value.key.equals(trimmed)) {
        return Optional.of(value);
      }
    }
    return Optional.empty();
  }
}
