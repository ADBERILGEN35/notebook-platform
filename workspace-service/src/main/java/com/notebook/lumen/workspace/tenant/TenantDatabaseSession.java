package com.notebook.lumen.workspace.tenant;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TenantDatabaseSession {
  private final EntityManager entityManager;
  private final TenantContext tenantContext;
  private final boolean rlsEnabled;

  public TenantDatabaseSession(
      EntityManager entityManager,
      TenantContext tenantContext,
      @Value("${app.rls.enabled:false}") boolean rlsEnabled) {
    this.entityManager = entityManager;
    this.tenantContext = tenantContext;
    this.rlsEnabled = rlsEnabled;
  }

  public void applyWorkspace(UUID workspaceId) {
    tenantContext.set(workspaceId);
    if (rlsEnabled) {
      entityManager
          .createNativeQuery("select set_config('app.current_workspace_id', :workspaceId, true)")
          .setParameter("workspaceId", workspaceId.toString())
          .getSingleResult();
    }
  }

  public boolean rlsEnabled() {
    return rlsEnabled;
  }
}
