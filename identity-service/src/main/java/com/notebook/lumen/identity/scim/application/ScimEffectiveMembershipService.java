package com.notebook.lumen.identity.scim.application;

import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.domain.ScimGroup;
import com.notebook.lumen.identity.scim.domain.ScimMemberType;
import com.notebook.lumen.identity.scim.infrastructure.ScimGroupMembershipRepository;
import com.notebook.lumen.identity.scim.infrastructure.ScimGroupRepository;
import com.notebook.lumen.identity.user.domain.User;
import com.notebook.lumen.identity.user.domain.UserStatus;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScimEffectiveMembershipService {
  private final ScimGroupMembershipRepository membershipRepository;
  private final ScimGroupRepository scimGroupRepository;

  public ScimEffectiveMembershipService(
      ScimGroupMembershipRepository membershipRepository, ScimGroupRepository scimGroupRepository) {
    this.membershipRepository = membershipRepository;
    this.scimGroupRepository = scimGroupRepository;
  }

  @Transactional(readOnly = true)
  public boolean userEffectiveMatchesAdminGroup(User user, ScimProperties scimProperties) {
    if (!scimProperties.groupsEnabled() || scimProperties.adminGroupSet().isEmpty()) {
      return false;
    }
    if (user.getStatus() != UserStatus.ACTIVE || user.getDeprovisionedAt() != null) {
      return false;
    }
    Set<UUID> effective = effectiveActiveGroupIdsForUser(user.getId());
    if (effective.isEmpty()) {
      return false;
    }
    var admin = scimProperties.adminGroupSet();
    for (UUID gid : effective) {
      Optional<ScimGroup> g = scimGroupRepository.findByIdAndActiveIsTrue(gid);
      if (g.isEmpty()) {
        continue;
      }
      ScimGroup group = g.get();
      String d = group.getDisplayName() == null ? "" : group.getDisplayName().toLowerCase(Locale.ROOT);
      String e = group.getExternalId() == null ? "" : group.getExternalId().toLowerCase(Locale.ROOT);
      if (admin.stream().anyMatch(x -> x.equals(d) || x.equals(e))) {
        return true;
      }
    }
    return false;
  }

  @Transactional(readOnly = true)
  public Set<UUID> effectiveActiveGroupIdsForUser(UUID userId) {
    Set<UUID> direct =
        new HashSet<>(
            membershipRepository.findByMemberTypeAndMemberUser_Id(ScimMemberType.USER, userId).stream()
                .map(m -> m.getGroup().getId())
                .toList());
    Set<UUID> effective = new HashSet<>();
    ArrayDeque<UUID> queue = new ArrayDeque<>(direct);
    Set<UUID> queued = new HashSet<>(direct);
    while (!queue.isEmpty()) {
      UUID gid = queue.poll();
      Optional<ScimGroup> group = scimGroupRepository.findByIdAndActiveIsTrue(gid);
      if (group.isEmpty()) {
        continue;
      }
      if (!effective.add(gid)) {
        continue;
      }
      for (UUID parentId :
          membershipRepository.findParentGroupIdsContainingChild(ScimMemberType.GROUP, gid)) {
        if (queued.add(parentId)) {
          queue.add(parentId);
        }
      }
    }
    return effective;
  }

  /**
   * Lowercase display names and external IDs for all effective active SCIM groups for a user
   * (including parents), used for RBAC group mapping.
   */
  @Transactional(readOnly = true)
  public Set<String> effectiveGroupKeysForUser(UUID userId, ScimProperties scimProperties) {
    Set<String> keys = new LinkedHashSet<>();
    if (!scimProperties.groupsEnabled()) {
      return keys;
    }
    Set<UUID> effective = effectiveActiveGroupIdsForUser(userId);
    for (UUID gid : effective) {
      Optional<ScimGroup> g = scimGroupRepository.findByIdAndActiveIsTrue(gid);
      if (g.isEmpty()) {
        continue;
      }
      ScimGroup group = g.get();
      if (group.getDisplayName() != null && !group.getDisplayName().isBlank()) {
        keys.add(group.getDisplayName().toLowerCase(Locale.ROOT).trim());
      }
      if (group.getExternalId() != null && !group.getExternalId().isBlank()) {
        keys.add(group.getExternalId().toLowerCase(Locale.ROOT).trim());
      }
    }
    return keys;
  }
}
