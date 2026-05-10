package com.notebook.lumen.notification.policy.application;

public final class WorkspaceRoleRules {
  private WorkspaceRoleRules() {}

  public static boolean isOwnerOrAdmin(String role) {
    return "OWNER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
  }
}
