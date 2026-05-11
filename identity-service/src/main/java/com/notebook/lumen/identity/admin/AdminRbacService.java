package com.notebook.lumen.identity.admin;

import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import com.notebook.lumen.identity.sso.SsoProperties;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AdminRbacService {
  private final AdminRbacProperties properties;

  public AdminRbacService(AdminRbacProperties properties) {
    this.properties = properties;
  }

  public boolean enabled() {
    return properties.enabled();
  }

  public boolean legacyPlatformAdminImpliesAll() {
    return properties.legacyPlatformAdminImpliesAll();
  }

  /**
   * Maps normalized lowercase IdP group names to platform roles when fine-grained RBAC is enabled.
   * When disabled, returns empty — caller should use legacy SSO admin group checks.
   */
  public List<String> mapIdpGroupsToRoles(SsoProperties.Provider provider, List<String> idpGroups) {
    if (!properties.enabled()) {
      return List.of();
    }
    Set<String> normalizedInput = normalizeGroups(idpGroups);
    LinkedHashSet<String> roles = new LinkedHashSet<>();
    addRoleIfGroupMatches(
        normalizedInput,
        properties.groupPlatformAdmin(),
        PlatformAdminRbacConstants.ROLE_PLATFORM_ADMIN,
        roles);
    addRoleIfGroupMatches(
        normalizedInput,
        properties.groupAuditViewer(),
        PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_VIEWER,
        roles);
    addRoleIfGroupMatches(
        normalizedInput,
        properties.groupAuditExporter(),
        PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_EXPORTER,
        roles);
    addRoleIfGroupMatches(
        normalizedInput,
        properties.groupSecurityAdmin(),
        PlatformAdminRbacConstants.ROLE_PLATFORM_SECURITY_ADMIN,
        roles);
    addRoleIfGroupMatches(
        normalizedInput,
        properties.groupIdentityAdmin(),
        PlatformAdminRbacConstants.ROLE_PLATFORM_IDENTITY_ADMIN,
        roles);
    addRoleIfGroupMatches(
        normalizedInput,
        properties.groupChangeRequestAuthor(),
        PlatformAdminRbacConstants.ROLE_PLATFORM_CHANGE_REQUEST_AUTHOR,
        roles);
    addRoleIfGroupMatches(
        normalizedInput,
        properties.groupChangeRequestApprover(),
        PlatformAdminRbacConstants.ROLE_PLATFORM_CHANGE_REQUEST_APPROVER,
        roles);
    addRoleIfGroupMatches(
        normalizedInput,
        properties.groupObservabilityViewer(),
        PlatformAdminRbacConstants.ROLE_PLATFORM_OBSERVABILITY_VIEWER,
        roles);
    if (provider != null && !provider.adminGroupSet().isEmpty()) {
      boolean legacyAdmin =
          idpGroups.stream()
              .map(value -> value.toLowerCase(Locale.ROOT))
              .anyMatch(provider.adminGroupSet()::contains);
      if (legacyAdmin) {
        roles.add(PlatformAdminRbacConstants.ROLE_PLATFORM_ADMIN);
      }
    }
    return roles.stream().sorted().toList();
  }

  /** Resolves SCIM group display/external identifiers (lowercase) to roles. */
  public List<String> mapScimGroupKeysToRoles(Set<String> normalizedScimGroupKeys) {
    if (!properties.enabled() || normalizedScimGroupKeys.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<String> roles = new LinkedHashSet<>();
    addRoleIfGroupMatches(
        normalizedScimGroupKeys,
        properties.groupPlatformAdmin(),
        PlatformAdminRbacConstants.ROLE_PLATFORM_ADMIN,
        roles);
    addRoleIfGroupMatches(
        normalizedScimGroupKeys,
        properties.groupAuditViewer(),
        PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_VIEWER,
        roles);
    addRoleIfGroupMatches(
        normalizedScimGroupKeys,
        properties.groupAuditExporter(),
        PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_EXPORTER,
        roles);
    addRoleIfGroupMatches(
        normalizedScimGroupKeys,
        properties.groupSecurityAdmin(),
        PlatformAdminRbacConstants.ROLE_PLATFORM_SECURITY_ADMIN,
        roles);
    addRoleIfGroupMatches(
        normalizedScimGroupKeys,
        properties.groupIdentityAdmin(),
        PlatformAdminRbacConstants.ROLE_PLATFORM_IDENTITY_ADMIN,
        roles);
    addRoleIfGroupMatches(
        normalizedScimGroupKeys,
        properties.groupChangeRequestAuthor(),
        PlatformAdminRbacConstants.ROLE_PLATFORM_CHANGE_REQUEST_AUTHOR,
        roles);
    addRoleIfGroupMatches(
        normalizedScimGroupKeys,
        properties.groupChangeRequestApprover(),
        PlatformAdminRbacConstants.ROLE_PLATFORM_CHANGE_REQUEST_APPROVER,
        roles);
    addRoleIfGroupMatches(
        normalizedScimGroupKeys,
        properties.groupObservabilityViewer(),
        PlatformAdminRbacConstants.ROLE_PLATFORM_OBSERVABILITY_VIEWER,
        roles);
    return roles.stream().sorted().toList();
  }

  public List<String> resolvePermissions(List<String> normalizedPlatformRoles) {
    if (!properties.enabled()
        || normalizedPlatformRoles == null
        || normalizedPlatformRoles.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<String> perms = new LinkedHashSet<>();
    for (String raw : normalizedPlatformRoles) {
      if (raw == null || raw.isBlank()) {
        continue;
      }
      String role = raw.trim().toUpperCase(Locale.ROOT);
      switch (role) {
        case PlatformAdminRbacConstants.ROLE_PLATFORM_ADMIN -> {
          if (properties.legacyPlatformAdminImpliesAll()) {
            perms.addAll(PlatformAdminRbacConstants.allPermissions());
          } else {
            perms.addAll(PlatformAdminRbacConstants.allPermissions());
          }
        }
        case PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_VIEWER -> {
          perms.add(PlatformAdminRbacConstants.PERM_AUDIT_READ);
        }
        case PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_EXPORTER -> {
          perms.add(PlatformAdminRbacConstants.PERM_AUDIT_READ);
          perms.add(PlatformAdminRbacConstants.PERM_AUDIT_EXPORT);
        }
        case PlatformAdminRbacConstants.ROLE_PLATFORM_SECURITY_ADMIN -> {
          perms.add(PlatformAdminRbacConstants.PERM_ENTERPRISE_STATUS_READ);
          perms.add(PlatformAdminRbacConstants.PERM_RBAC_READ);
          perms.add(PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_LIST);
          perms.add(PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_CREATE);
          perms.add(PlatformAdminRbacConstants.PERM_SECURITY_CHANGE_REQUEST_CREATE);
          perms.add(PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_GITOPS_DRY_RUN);
          perms.add(PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_GITOPS_CREATE);
          perms.add(PlatformAdminRbacConstants.PERM_NOTIFICATIONS_DEAD_LETTER_READ);
          perms.add(PlatformAdminRbacConstants.PERM_NOTIFICATIONS_DEAD_LETTER_REQUEUE);
          perms.add(PlatformAdminRbacConstants.PERM_NOTIFICATIONS_RETENTION_READ);
          perms.add(PlatformAdminRbacConstants.PERM_NOTIFICATIONS_RETENTION_RUN);
          perms.add(PlatformAdminRbacConstants.PERM_NOTIFICATIONS_LEGAL_HOLD_READ);
          perms.add(PlatformAdminRbacConstants.PERM_NOTIFICATIONS_LEGAL_HOLD_WRITE);
        }
        case PlatformAdminRbacConstants.ROLE_PLATFORM_IDENTITY_ADMIN -> {
          perms.add(PlatformAdminRbacConstants.PERM_IDENTITY_READ);
          perms.add(PlatformAdminRbacConstants.PERM_ENTERPRISE_STATUS_READ);
          perms.add(PlatformAdminRbacConstants.PERM_RBAC_READ);
          perms.add(PlatformAdminRbacConstants.PERM_RBAC_CHANGE_REQUEST_CREATE);
          perms.add(PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_LIST);
          perms.add(PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_CREATE);
          perms.add(PlatformAdminRbacConstants.PERM_SCIM_CHANGE_REQUEST_CREATE);
          perms.add(PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_GITOPS_DRY_RUN);
          perms.add(PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_GITOPS_CREATE);
        }
        case PlatformAdminRbacConstants.ROLE_PLATFORM_CHANGE_REQUEST_AUTHOR -> {
          perms.add(PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_LIST);
          perms.add(PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_CREATE);
          perms.add(PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_CANCEL);
        }
        case PlatformAdminRbacConstants.ROLE_PLATFORM_CHANGE_REQUEST_APPROVER -> {
          perms.add(PlatformAdminRbacConstants.PERM_ENTERPRISE_STATUS_READ);
          perms.add(PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_LIST);
          perms.add(PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_APPROVE);
          perms.add(PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_REJECT);
          perms.add(PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_GITOPS_DRY_RUN);
          perms.add(PlatformAdminRbacConstants.PERM_CHANGE_REQUEST_GITOPS_CREATE);
        }
        case PlatformAdminRbacConstants.ROLE_PLATFORM_OBSERVABILITY_VIEWER -> {
          perms.add(PlatformAdminRbacConstants.PERM_ENTERPRISE_STATUS_READ);
          perms.add(PlatformAdminRbacConstants.PERM_NOTIFICATIONS_ANALYTICS_READ);
          perms.add(PlatformAdminRbacConstants.PERM_NOTIFICATIONS_DEAD_LETTER_READ);
          perms.add(PlatformAdminRbacConstants.PERM_NOTIFICATIONS_RETENTION_READ);
          perms.add(PlatformAdminRbacConstants.PERM_NOTIFICATIONS_LEGAL_HOLD_READ);
        }
        default -> {
          // unknown custom role — ignore
        }
      }
    }
    return perms.stream().sorted().toList();
  }

  public AdminRbacStatusSnapshot statusSnapshot() {
    return new AdminRbacStatusSnapshot(
        properties.enabled(),
        properties.legacyPlatformAdminImpliesAll(),
        roleConfigured(
            properties.groupPlatformAdmin(), PlatformAdminRbacConstants.ROLE_PLATFORM_ADMIN),
        roleConfigured(
            properties.groupAuditViewer(), PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_VIEWER),
        roleConfigured(
            properties.groupAuditExporter(),
            PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_EXPORTER),
        roleConfigured(
            properties.groupSecurityAdmin(),
            PlatformAdminRbacConstants.ROLE_PLATFORM_SECURITY_ADMIN),
        roleConfigured(
            properties.groupIdentityAdmin(),
            PlatformAdminRbacConstants.ROLE_PLATFORM_IDENTITY_ADMIN),
        roleConfigured(
            properties.groupChangeRequestAuthor(),
            PlatformAdminRbacConstants.ROLE_PLATFORM_CHANGE_REQUEST_AUTHOR),
        roleConfigured(
            properties.groupChangeRequestApprover(),
            PlatformAdminRbacConstants.ROLE_PLATFORM_CHANGE_REQUEST_APPROVER),
        roleConfigured(
            properties.groupObservabilityViewer(),
            PlatformAdminRbacConstants.ROLE_PLATFORM_OBSERVABILITY_VIEWER));
  }

  private static boolean roleConfigured(String groupPattern, String roleName) {
    return groupPattern != null && !groupPattern.isBlank();
  }

  private static void addRoleIfGroupMatches(
      Collection<String> normalizedGroups, String configuredGroup, String role, Set<String> roles) {
    if (configuredGroup == null || configuredGroup.isBlank()) {
      return;
    }
    String needle = configuredGroup.toLowerCase(Locale.ROOT).trim();
    if (normalizedGroups.stream().anyMatch(g -> g.equals(needle))) {
      roles.add(role);
    }
  }

  private static Set<String> normalizeGroups(List<String> groups) {
    Set<String> out = new LinkedHashSet<>();
    if (groups == null) {
      return out;
    }
    for (String g : groups) {
      if (g != null && !g.isBlank()) {
        out.add(g.toLowerCase(Locale.ROOT).trim());
      }
    }
    return out;
  }

  public record AdminRbacStatusSnapshot(
      boolean enabled,
      boolean legacyPlatformAdminImpliesAll,
      boolean platformAdmin,
      boolean auditViewer,
      boolean auditExporter,
      boolean securityAdmin,
      boolean identityAdmin,
      boolean changeRequestAuthor,
      boolean changeRequestApprover,
      boolean observabilityViewer) {}
}
