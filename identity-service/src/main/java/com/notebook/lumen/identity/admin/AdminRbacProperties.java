package com.notebook.lumen.identity.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity.admin.rbac")
public record AdminRbacProperties(
    boolean enabled,
    boolean legacyPlatformAdminImpliesAll,
    String groupPlatformAdmin,
    String groupAuditViewer,
    String groupAuditExporter,
    String groupSecurityAdmin,
    String groupIdentityAdmin,
    String groupChangeRequestAuthor,
    String groupChangeRequestApprover,
    String groupObservabilityViewer) {

  public AdminRbacProperties {
    groupPlatformAdmin = groupPlatformAdmin == null ? "" : groupPlatformAdmin;
    groupAuditViewer = groupAuditViewer == null ? "" : groupAuditViewer;
    groupAuditExporter = groupAuditExporter == null ? "" : groupAuditExporter;
    groupSecurityAdmin = groupSecurityAdmin == null ? "" : groupSecurityAdmin;
    groupIdentityAdmin = groupIdentityAdmin == null ? "" : groupIdentityAdmin;
    groupChangeRequestAuthor = groupChangeRequestAuthor == null ? "" : groupChangeRequestAuthor;
    groupChangeRequestApprover = groupChangeRequestApprover == null ? "" : groupChangeRequestApprover;
    groupObservabilityViewer = groupObservabilityViewer == null ? "" : groupObservabilityViewer;
  }
}
