package com.notebook.lumen.identity.scim.infrastructure;

import com.notebook.lumen.identity.scim.domain.ScimGroupMembership;
import com.notebook.lumen.identity.scim.domain.ScimMemberType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScimGroupMembershipRepository extends JpaRepository<ScimGroupMembership, UUID> {
  List<ScimGroupMembership> findByGroup_Id(UUID groupId);

  void deleteByGroup_Id(UUID groupId);

  void deleteByMemberGroup_Id(UUID memberGroupId);

  void deleteByMemberTypeAndMemberUser_Id(ScimMemberType memberType, UUID memberUserId);

  List<ScimGroupMembership> findByMemberTypeAndMemberUser_Id(
      ScimMemberType memberType, UUID memberUserId);

  List<ScimGroupMembership> findByMemberTypeAndMemberGroup_Id(
      ScimMemberType memberType, UUID memberGroupId);

  @Query(
      "select m.group.id from ScimGroupMembership m where m.memberType = :t and m.memberGroup.id = :childId")
  List<UUID> findParentGroupIdsContainingChild(
      @Param("t") ScimMemberType t, @Param("childId") UUID childId);

  @Query(
      "select m.memberGroup.id from ScimGroupMembership m where m.memberType = :t and m.group.id = :parentId")
  List<UUID> findNestedChildGroupIds(
      @Param("t") ScimMemberType t, @Param("parentId") UUID parentId);
}
