package com.notebook.lumen.identity.admin.rbac.overrides;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** One normalized assignment row from the GitOps manifest (immutable). */
public record AdminRbacOverrideAssignmentRow(
    String stableId,
    UUID userId,
    String role,
    String action,
    String reasonRef,
    UUID requestedBy,
    UUID approvedBy,
    String status,
    Instant expiresAt,
    Instant createdAt,
    String metadataSource) {

  public boolean isGrant() {
    return "GRANT".equalsIgnoreCase(action);
  }

  public boolean isRevoke() {
    return "REVOKE".equalsIgnoreCase(action);
  }

  public static String metadataSourceFrom(Map<String, Object> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return "gitops";
    }
    Object s = metadata.get("source");
    return s == null || String.valueOf(s).isBlank() ? "gitops" : String.valueOf(s).trim();
  }
}
