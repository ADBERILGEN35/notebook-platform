package com.notebook.lumen.workspace.tenant;

import java.util.Optional;
import java.util.UUID;

public class TenantContext {
  private static final ThreadLocal<UUID> CURRENT_WORKSPACE_ID = new ThreadLocal<>();

  public void set(UUID workspaceId) {
    if (workspaceId == null) {
      throw new IllegalArgumentException("workspaceId is required");
    }
    CURRENT_WORKSPACE_ID.set(workspaceId);
  }

  public UUID getRequired() {
    UUID workspaceId = CURRENT_WORKSPACE_ID.get();
    if (workspaceId == null) {
      throw new IllegalStateException("Tenant workspace context is not set");
    }
    return workspaceId;
  }

  public Optional<UUID> getOptional() {
    return Optional.ofNullable(CURRENT_WORKSPACE_ID.get());
  }

  public void clear() {
    CURRENT_WORKSPACE_ID.remove();
  }
}
