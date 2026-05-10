package com.notebook.lumen.identity.admin.rbac;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import com.notebook.lumen.identity.admin.AdminRbacProperties;
import com.notebook.lumen.identity.admin.AdminRbacService;
import com.notebook.lumen.identity.admin.changerequest.PlatformAdminChangeRequestRepository;
import com.notebook.lumen.identity.admin.rbac.api.AdminRbacVisibilityDtos;
import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.application.ScimEffectiveMembershipService;
import com.notebook.lumen.identity.scim.domain.ScimGroup;
import com.notebook.lumen.identity.scim.infrastructure.ScimGroupRepository;
import com.notebook.lumen.identity.sso.SsoProperties;
import com.notebook.lumen.identity.sso.domain.ExternalIdentity;
import com.notebook.lumen.identity.sso.infrastructure.ExternalIdentityRepository;
import com.notebook.lumen.identity.shared.exception.UserNotFoundException;
import com.notebook.lumen.identity.user.domain.User;
import com.notebook.lumen.identity.user.domain.UserStatus;
import com.notebook.lumen.identity.user.infrastructure.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminRbacVisibilityService {
  private static final String SRC_SSO_GROUP = "SSO_GROUP";
  private static final String SRC_SCIM_GROUP = "SCIM_GROUP";
  private static final String SRC_LEGACY_PLATFORM_ADMIN = "LEGACY_PLATFORM_ADMIN";
  private static final String SRC_ALLOWLIST = "ALLOWLIST";

  private final AdminRbacProperties adminRbacProperties;
  private final AdminRbacService adminRbacService;
  private final UserRepository userRepository;
  private final ExternalIdentityRepository externalIdentityRepository;
  private final SsoProperties ssoProperties;
  private final ObjectMapper objectMapper;
  private final ScimEffectiveMembershipService scimEffectiveMembershipService;
  private final ScimProperties scimProperties;
  private final ScimGroupRepository scimGroupRepository;
  private final PlatformAdminChangeRequestRepository changeRequestRepository;

  public AdminRbacVisibilityService(
      AdminRbacProperties adminRbacProperties,
      AdminRbacService adminRbacService,
      UserRepository userRepository,
      ExternalIdentityRepository externalIdentityRepository,
      SsoProperties ssoProperties,
      ObjectMapper objectMapper,
      ScimEffectiveMembershipService scimEffectiveMembershipService,
      ScimProperties scimProperties,
      ScimGroupRepository scimGroupRepository,
      PlatformAdminChangeRequestRepository changeRequestRepository) {
    this.adminRbacProperties = adminRbacProperties;
    this.adminRbacService = adminRbacService;
    this.userRepository = userRepository;
    this.externalIdentityRepository = externalIdentityRepository;
    this.ssoProperties = ssoProperties;
    this.objectMapper = objectMapper;
    this.scimEffectiveMembershipService = scimEffectiveMembershipService;
    this.scimProperties = scimProperties;
    this.scimGroupRepository = scimGroupRepository;
    this.changeRequestRepository = changeRequestRepository;
  }

  private void ensureEnabled() {
    if (!adminRbacProperties.visibilityEnabled()) {
      throw new AdminRbacVisibilityDisabledException();
    }
  }

  @Transactional(readOnly = true)
  public AdminRbacVisibilityDtos.UserListResponse listUsers(
      String q, String roleFilter, String permissionFilter, int page, int size) {
    ensureEnabled();
    String qq = q == null || q.isBlank() ? null : q.trim().toLowerCase(Locale.ROOT);
    int p = Math.max(0, page);
    int s = Math.min(100, Math.max(1, size));
    Pageable pr = PageRequest.of(p, s, Sort.by(Sort.Direction.ASC, "email"));
    Page<User> pg = userRepository.pageForAdminRbacDirectory(qq, pr);
    List<AdminRbacVisibilityDtos.UserListItem> items = new ArrayList<>();
    for (User u : pg.getContent()) {
      AdminRbacVisibilityDtos.UserListItem row = toRow(u);
      if (roleFilter != null && !roleFilter.isBlank()) {
        String rf = roleFilter.trim().toUpperCase(Locale.ROOT);
        if (!row.platformRoles().contains(rf)) {
          continue;
        }
      }
      if (permissionFilter != null && !permissionFilter.isBlank()) {
        String perm = permissionFilter.trim();
        if (!row.platformPermissions().contains(perm)) {
          continue;
        }
      }
      items.add(row);
    }
    return new AdminRbacVisibilityDtos.UserListResponse(items, p, s, pg.getTotalElements());
  }

  @Transactional(readOnly = true)
  public AdminRbacVisibilityDtos.UserDetailResponse userDetail(UUID userId) {
    ensureEnabled();
    User u =
        userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    AdminRbacVisibilityDtos.UserListItem row = toRow(u);
    long pendingCr = changeRequestRepository.countByRequestedByUserId(userId);
    return new AdminRbacVisibilityDtos.UserDetailResponse(row, pendingCr);
  }

  private AdminRbacVisibilityDtos.UserListItem toRow(User user) {
    List<String> warnings = new ArrayList<>();
    if (user.getDeprovisionedAt() != null) {
      warnings.add("DEPROVISIONED_USER");
    }
    List<AdminRbacVisibilityDtos.RoleSource> sources = new ArrayList<>();
    LinkedHashSet<String> roles = new LinkedHashSet<>();

    if (user.getDeprovisionedAt() != null || user.getStatus() != UserStatus.ACTIVE) {
      return new AdminRbacVisibilityDtos.UserListItem(
          user.getId().toString(),
          user.getEmail(),
          user.getStatus().name(),
          List.of(),
          List.of(),
          sources,
          user.getLastLoginAt(),
          false,
          warnings);
    }

    appendSsoSources(user, sources, roles);
    appendScimSources(user, sources, roles);
    appendAllowlistSource(user, sources);

    List<String> roleList = roles.stream().sorted().toList();
    List<String> perms =
        adminRbacProperties.enabled()
            ? adminRbacService.resolvePermissions(roleList)
            : List.of();

    if (roles.contains(PlatformAdminRbacConstants.ROLE_PLATFORM_ADMIN)) {
      warnings.add("BROAD_PLATFORM_ADMIN");
    }
    boolean allowlistOnlyHint =
        adminRbacProperties.visibilityAllowlistEmailSet().contains(emailKey(user.getEmail()))
            && roles.isEmpty();
    if (allowlistOnlyHint) {
      warnings.add("ALLOWLIST_ONLY_HINT");
    }

    return new AdminRbacVisibilityDtos.UserListItem(
        user.getId().toString(),
        user.getEmail(),
        user.getStatus().name(),
        roleList,
        perms,
        sources,
        user.getLastLoginAt(),
        false,
        warnings);
  }

  private void appendAllowlistSource(User user, List<AdminRbacVisibilityDtos.RoleSource> sources) {
    if (adminRbacProperties.visibilityAllowlistEmailSet().isEmpty()) {
      return;
    }
    if (adminRbacProperties.visibilityAllowlistEmailSet().contains(emailKey(user.getEmail()))) {
      sources.add(new AdminRbacVisibilityDtos.RoleSource(SRC_ALLOWLIST, "gateway-allowlist-mirror", List.of()));
    }
  }

  private static String emailKey(String email) {
    return email == null ? "" : email.toLowerCase(Locale.ROOT).trim();
  }

  private void appendSsoSources(User user, List<AdminRbacVisibilityDtos.RoleSource> sources, LinkedHashSet<String> roles) {
    List<ExternalIdentity> links =
        externalIdentityRepository.findByUser_IdOrderByLastLoginAtDesc(user.getId(), PageRequest.of(0, 1));
    if (links.isEmpty()) {
      return;
    }
    ExternalIdentity ext = links.get(0);
    Optional<SsoProperties.Provider> providerOpt =
        ssoProperties.enabledProviders().stream()
            .filter(p -> p.registrationId().equals(ext.getProvider()))
            .findFirst();
    if (providerOpt.isEmpty()) {
      return;
    }
    SsoProperties.Provider provider = providerOpt.get();
    List<String> groups = parseGroupsFromStoredClaims(ext.getClaims(), provider.groupsClaim());
    for (String g : groups) {
      if (g == null || g.isBlank()) {
        continue;
      }
      String gl = g.toLowerCase(Locale.ROOT).trim();
      boolean legacy =
          !provider.adminGroupSet().isEmpty() && provider.adminGroupSet().contains(gl);
      if (legacy) {
        sources.add(
            new AdminRbacVisibilityDtos.RoleSource(
                SRC_LEGACY_PLATFORM_ADMIN,
                provider.registrationId() + "-legacy-admin",
                List.of(PlatformAdminRbacConstants.ROLE_PLATFORM_ADMIN)));
        roles.add(PlatformAdminRbacConstants.ROLE_PLATFORM_ADMIN);
        continue;
      }
      if (adminRbacProperties.enabled()) {
        List<String> mapped = adminRbacService.mapIdpGroupsToRoles(provider, List.of(g));
        if (!mapped.isEmpty()) {
          String label = configuredIdpGroupLabel(gl);
          sources.add(new AdminRbacVisibilityDtos.RoleSource(SRC_SSO_GROUP, label, List.copyOf(mapped)));
          roles.addAll(mapped);
        }
      }
    }
  }

  private String configuredIdpGroupLabel(String normalizedGroupLower) {
    if (normalizedGroupLower.equals(trimLower(adminRbacProperties.groupPlatformAdmin()))) {
      return adminRbacProperties.groupPlatformAdmin().trim();
    }
    if (normalizedGroupLower.equals(trimLower(adminRbacProperties.groupAuditViewer()))) {
      return adminRbacProperties.groupAuditViewer().trim();
    }
    if (normalizedGroupLower.equals(trimLower(adminRbacProperties.groupAuditExporter()))) {
      return adminRbacProperties.groupAuditExporter().trim();
    }
    if (normalizedGroupLower.equals(trimLower(adminRbacProperties.groupSecurityAdmin()))) {
      return adminRbacProperties.groupSecurityAdmin().trim();
    }
    if (normalizedGroupLower.equals(trimLower(adminRbacProperties.groupIdentityAdmin()))) {
      return adminRbacProperties.groupIdentityAdmin().trim();
    }
    if (normalizedGroupLower.equals(trimLower(adminRbacProperties.groupChangeRequestAuthor()))) {
      return adminRbacProperties.groupChangeRequestAuthor().trim();
    }
    if (normalizedGroupLower.equals(trimLower(adminRbacProperties.groupChangeRequestApprover()))) {
      return adminRbacProperties.groupChangeRequestApprover().trim();
    }
    if (normalizedGroupLower.equals(trimLower(adminRbacProperties.groupObservabilityViewer()))) {
      return adminRbacProperties.groupObservabilityViewer().trim();
    }
    return "sso-group";
  }

  private static String trimLower(String s) {
    return s == null || s.isBlank() ? "" : s.toLowerCase(Locale.ROOT).trim();
  }

  private void appendScimSources(User user, List<AdminRbacVisibilityDtos.RoleSource> sources, LinkedHashSet<String> roles) {
    if (user.getStatus() != UserStatus.ACTIVE || user.getDeprovisionedAt() != null) {
      return;
    }
    if (scimEffectiveMembershipService.userEffectiveMatchesAdminGroup(user, scimProperties)) {
      sources.add(
          new AdminRbacVisibilityDtos.RoleSource(
              SRC_SCIM_GROUP,
              "scim-admin-group",
              List.of(PlatformAdminRbacConstants.ROLE_PLATFORM_ADMIN)));
      roles.add(PlatformAdminRbacConstants.ROLE_PLATFORM_ADMIN);
    }
    if (!scimProperties.groupsEnabled()) {
      return;
    }
    Set<UUID> effective = scimEffectiveMembershipService.effectiveActiveGroupIdsForUser(user.getId());
    for (UUID gid : effective) {
      Optional<ScimGroup> og = scimGroupRepository.findByIdAndActiveIsTrue(gid);
      if (og.isEmpty()) {
        continue;
      }
      ScimGroup group = og.get();
      LinkedHashSet<String> keys = new LinkedHashSet<>();
      if (group.getDisplayName() != null && !group.getDisplayName().isBlank()) {
        keys.add(group.getDisplayName().toLowerCase(Locale.ROOT).trim());
      }
      if (group.getExternalId() != null && !group.getExternalId().isBlank()) {
        keys.add(group.getExternalId().toLowerCase(Locale.ROOT).trim());
      }
      if (keys.isEmpty()) {
        continue;
      }
      if (adminRbacProperties.enabled()) {
        List<String> mapped = adminRbacService.mapScimGroupKeysToRoles(keys);
        if (!mapped.isEmpty()) {
          String name =
              group.getDisplayName() != null && !group.getDisplayName().isBlank()
                  ? group.getDisplayName()
                  : group.getExternalId();
          sources.add(new AdminRbacVisibilityDtos.RoleSource(SRC_SCIM_GROUP, name, List.copyOf(mapped)));
          roles.addAll(mapped);
        }
      }
    }
  }

  private List<String> parseGroupsFromStoredClaims(String claimsJson, String groupsClaim) {
    if (claimsJson == null || claimsJson.isBlank() || groupsClaim == null || groupsClaim.isBlank()) {
      return List.of();
    }
    try {
      JsonNode root = objectMapper.readTree(claimsJson);
      JsonNode node = root.path(groupsClaim);
      if (node.isArray()) {
        List<String> out = new ArrayList<>();
        node.forEach(n -> out.add(n.asText("")));
        return out;
      }
      if (node.isTextual()) {
        String raw = node.asText("");
        String[] parts = raw.split(",");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
          String t = part.trim();
          if (!t.isBlank()) {
            out.add(t);
          }
        }
        return out;
      }
    } catch (Exception ignored) {
    }
    return List.of();
  }
}
