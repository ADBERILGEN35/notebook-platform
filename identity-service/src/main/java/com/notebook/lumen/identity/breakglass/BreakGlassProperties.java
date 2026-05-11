package com.notebook.lumen.identity.breakglass;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "identity.break-glass")
public record BreakGlassProperties(
    @DefaultValue("false") boolean enabled,
    /** Expected token hash (recommended format: sha256:hex). */
    @DefaultValue("") String tokenHash,
    @DefaultValue("15") int sessionTtlMinutes,
    @DefaultValue("true") boolean requireMfa,
    @DefaultValue("true") boolean requireReason,
    @DefaultValue("1") int maxActiveSessions,
    @DefaultValue("true") boolean notifyOnUse) {

  public BreakGlassProperties {
    if (sessionTtlMinutes < 1) {
      sessionTtlMinutes = 15;
    }
    if (maxActiveSessions < 1) {
      maxActiveSessions = 1;
    }
    tokenHash = tokenHash == null ? "" : tokenHash.trim();
  }
}

