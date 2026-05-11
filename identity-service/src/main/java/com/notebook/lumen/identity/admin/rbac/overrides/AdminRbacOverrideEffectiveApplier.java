package com.notebook.lumen.identity.admin.rbac.overrides;

import com.notebook.lumen.identity.admin.rbac.api.AdminRbacVisibilityDtos;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AdminRbacOverrideEffectiveApplier {

  public record ApplyOutcome(
      LinkedHashSet<String> effectiveRoles,
      List<AdminRbacVisibilityDtos.RoleSource> overrideSources,
      List<String> userWarnings) {}

  /**
   * Applies GitOps override rows for a single user. {@code baseFromIdpAndScim} must be a frozen
   * copy of roles implied by SSO/SCIM mappings only (before this method mutates {@code
   * mergedRoles}).
   */
  public ApplyOutcome apply(
      UUID userId,
      LinkedHashSet<String> mergedRoles,
      LinkedHashSet<String> baseFromIdpAndScim,
      List<AdminRbacOverrideAssignmentRow> orderedRows) {

    List<AdminRbacVisibilityDtos.RoleSource> sources = new ArrayList<>();
    List<String> userWarnings = new ArrayList<>();
    Map<String, Integer> overrideGrantDepth = new HashMap<>();

    for (AdminRbacOverrideAssignmentRow row : orderedRows) {
      if (!row.userId().equals(userId)) {
        continue;
      }
      String role = row.role();
      if (row.isGrant()) {
        mergedRoles.add(role);
        overrideGrantDepth.merge(role, 1, Integer::sum);
        sources.add(
            new AdminRbacVisibilityDtos.RoleSource(
                "GITOPS_OVERRIDE", "admin-rbac-overrides.yaml", List.of(role), row.reasonRef()));
      } else if (row.isRevoke()) {
        int depth = overrideGrantDepth.getOrDefault(role, 0);
        if (depth > 0) {
          int next = depth - 1;
          if (next == 0) {
            overrideGrantDepth.remove(role);
            if (!baseFromIdpAndScim.contains(role)) {
              mergedRoles.remove(role);
            }
          } else {
            overrideGrantDepth.put(role, next);
          }
        } else if (baseFromIdpAndScim.contains(role)) {
          userWarnings.add("OVERRIDE_REVOKE_IGNORED_NON_OVERRIDE_ROLE");
        }
      }
    }

    dedupeWarnings(userWarnings);
    return new ApplyOutcome(mergedRoles, sources, userWarnings);
  }

  private static void dedupeWarnings(List<String> userWarnings) {
    LinkedHashSet<String> seen = new LinkedHashSet<>();
    userWarnings.removeIf(w -> !seen.add(w.toUpperCase(Locale.ROOT)));
  }
}
