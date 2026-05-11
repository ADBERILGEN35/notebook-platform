package com.notebook.lumen.identity.scim.application;

import com.notebook.lumen.identity.scim.domain.ScimMemberType;
import com.notebook.lumen.identity.scim.infrastructure.ScimGroupMembershipRepository;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ScimGroupGraphValidation {
  private final ScimGroupMembershipRepository membershipRepository;

  public ScimGroupGraphValidation(ScimGroupMembershipRepository membershipRepository) {
    this.membershipRepository = membershipRepository;
  }

  public void validateNewNestedMembership(
      UUID parentGroupId, UUID childGroupId, int maxDepthEdges) {
    int maxAllowed = Math.max(1, maxDepthEdges);
    if (parentGroupId.equals(childGroupId)) {
      throw new ScimException(
          HttpStatus.BAD_REQUEST,
          "invalidValue",
          "SCIM_GROUP_CYCLE_DETECTED: group cannot contain itself");
    }
    if (childIsAncestorOfParent(parentGroupId, childGroupId)) {
      throw new ScimException(
          HttpStatus.BAD_REQUEST,
          "invalidValue",
          "SCIM_GROUP_CYCLE_DETECTED: nested group would create a membership cycle");
    }
    int up = maxUpDepth(parentGroupId, new HashMap<>());
    int down = maxDownDepth(childGroupId, new HashMap<>());
    if (up + 1 + down > maxAllowed) {
      throw new ScimException(
          HttpStatus.BAD_REQUEST,
          "invalidValue",
          "SCIM_GROUP_NESTING_DEPTH_EXCEEDED: nesting chain would exceed configured max depth");
    }
  }

  private boolean childIsAncestorOfParent(UUID parentGroupId, UUID childGroupId) {
    ArrayDeque<UUID> queue = new ArrayDeque<>();
    queue.add(parentGroupId);
    Set<UUID> seen = new HashSet<>();
    while (!queue.isEmpty()) {
      UUID x = queue.poll();
      if (x.equals(childGroupId)) {
        return true;
      }
      if (!seen.add(x)) {
        continue;
      }
      for (UUID p :
          membershipRepository.findParentGroupIdsContainingChild(ScimMemberType.GROUP, x)) {
        queue.add(p);
      }
    }
    return false;
  }

  private int maxUpDepth(UUID groupId, Map<UUID, Integer> memo) {
    Integer cached = memo.get(groupId);
    if (cached != null) {
      return cached;
    }
    var parents =
        membershipRepository.findParentGroupIdsContainingChild(ScimMemberType.GROUP, groupId);
    if (parents.isEmpty()) {
      memo.put(groupId, 0);
      return 0;
    }
    int v = 1 + parents.stream().mapToInt(p -> maxUpDepth(p, memo)).max().orElse(0);
    memo.put(groupId, v);
    return v;
  }

  private int maxDownDepth(UUID groupId, Map<UUID, Integer> memo) {
    Integer cached = memo.get(groupId);
    if (cached != null) {
      return cached;
    }
    var children = membershipRepository.findNestedChildGroupIds(ScimMemberType.GROUP, groupId);
    if (children.isEmpty()) {
      memo.put(groupId, 0);
      return 0;
    }
    int v = 1 + children.stream().mapToInt(c -> maxDownDepth(c, memo)).max().orElse(0);
    memo.put(groupId, v);
    return v;
  }
}
