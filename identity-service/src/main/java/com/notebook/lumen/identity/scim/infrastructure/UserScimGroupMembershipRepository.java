package com.notebook.lumen.identity.scim.infrastructure;

import com.notebook.lumen.identity.scim.domain.UserScimGroupMembership;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserScimGroupMembershipRepository extends JpaRepository<UserScimGroupMembership, UUID> {
  List<UserScimGroupMembership> findByUserId(UUID userId);

  void deleteByUserId(UUID userId);
}
