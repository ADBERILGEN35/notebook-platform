package com.notebook.lumen.identity.admin.changerequest;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity.admin.change-requests")
public record AdminChangeRequestProperties(
    boolean enabled, Integer retentionDays, Approvals approvals) {

  public int effectiveRetentionDays() {
    return retentionDays == null || retentionDays <= 0 ? 180 : retentionDays;
  }

  public Approvals effectiveApprovals() {
    return approvals == null ? Approvals.defaults() : approvals;
  }

  /**
   * @param rejectReasonRequiredForHighSeverity null defaults to true
   */
  public record Approvals(
      boolean enabled,
      boolean requireDifferentApprover,
      boolean requireMfaForApproval,
      boolean highSeverityRequiresApproval,
      boolean lowSeverityAutoApprove,
      Boolean rejectReasonRequiredForHighSeverity) {

    public static Approvals defaults() {
      return new Approvals(true, true, true, true, false, null);
    }

    public boolean effectiveRejectReasonRequiredForHigh() {
      return rejectReasonRequiredForHighSeverity == null || rejectReasonRequiredForHighSeverity;
    }
  }
}
