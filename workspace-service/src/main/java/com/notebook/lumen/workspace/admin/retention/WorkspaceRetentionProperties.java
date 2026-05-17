package com.notebook.lumen.workspace.admin.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workspace.retention")
public record WorkspaceRetentionProperties(
    boolean dryRunCountsEnabled,
    int maxCountQueryLimit,
    int expiredInvitationRetentionDays,
    int auditEventRetentionDays) {

  public WorkspaceRetentionProperties {
    if (maxCountQueryLimit <= 0) {
      maxCountQueryLimit = 100_000;
    }
    if (expiredInvitationRetentionDays <= 0) {
      expiredInvitationRetentionDays = 90;
    }
    if (auditEventRetentionDays <= 0) {
      auditEventRetentionDays = 365;
    }
  }

  public int retentionDaysFor(WorkspaceRetentionTargetKey target) {
    return switch (target) {
      case WORKSPACE_INVITATIONS_EXPIRED -> expiredInvitationRetentionDays;
      case WORKSPACE_AUDIT_LIKE_EVENTS -> auditEventRetentionDays;
      default -> 0;
    };
  }
}
