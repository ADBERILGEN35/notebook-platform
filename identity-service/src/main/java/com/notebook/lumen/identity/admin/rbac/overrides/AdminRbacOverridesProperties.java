package com.notebook.lumen.identity.admin.rbac.overrides;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "identity.admin.rbac.overrides")
public record AdminRbacOverridesProperties(
    @DefaultValue("false") boolean enabled,
    String file,
    @DefaultValue("false") boolean failClosed,
    @DefaultValue("500") int maxAssignments,
    @DefaultValue("true") boolean requireApprovedStatus) {

  public AdminRbacOverridesProperties {
    file =
        file == null || file.isBlank()
            ? "/etc/notebook/admin-rbac-overrides/admin-rbac-overrides.yaml"
            : file.trim();
    if (maxAssignments < 1) {
      maxAssignments = 500;
    }
  }

  public String filePath() {
    return file;
  }
}
