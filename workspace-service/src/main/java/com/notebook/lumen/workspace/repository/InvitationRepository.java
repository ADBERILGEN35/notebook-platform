package com.notebook.lumen.workspace.repository;

import com.notebook.lumen.workspace.domain.Invitation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
  Optional<Invitation> findByTokenHash(String tokenHash);

  List<Invitation> findByWorkspaceId(UUID workspaceId);

  Page<Invitation> findByWorkspaceId(UUID workspaceId, Pageable pageable);
}
