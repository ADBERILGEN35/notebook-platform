package com.notebook.lumen.identity.admin;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
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
    String groupObservabilityViewer,
    boolean visibilityEnabled,
    boolean roleChangeRequestsEnabled,
    String visibilityAllowlistEmails) {

  public AdminRbacProperties {
    groupPlatformAdmin = groupPlatformAdmin == null ? "" : groupPlatformAdmin;
    groupAuditViewer = groupAuditViewer == null ? "" : groupAuditViewer;
    groupAuditExporter = groupAuditExporter == null ? "" : groupAuditExporter;
    groupSecurityAdmin = groupSecurityAdmin == null ? "" : groupSecurityAdmin;
    groupIdentityAdmin = groupIdentityAdmin == null ? "" : groupIdentityAdmin;
    groupChangeRequestAuthor = groupChangeRequestAuthor == null ? "" : groupChangeRequestAuthor;
    groupChangeRequestApprover = groupChangeRequestApprover == null ? "" : groupChangeRequestApprover;
    groupObservabilityViewer = groupObservabilityViewer == null ? "" : groupObservabilityViewer;
    visibilityAllowlistEmails = visibilityAllowlistEmails == null ? "" : visibilityAllowlistEmails;
  }

  public Set<String> visibilityAllowlistEmailSet() {
    if (visibilityAllowlistEmails.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(visibilityAllowlistEmails.split(","))
        .map(e -> e.toLowerCase(Locale.ROOT).trim())
        .filter(s -> !s.isBlank())
        .collect(Collectors.toUnmodifiableSet());
  }
}
