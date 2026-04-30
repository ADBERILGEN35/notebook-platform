package com.notebook.lumen.workspace.repository;

import com.notebook.lumen.workspace.domain.NotebookMember;
import com.notebook.lumen.workspace.domain.NotebookMemberId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotebookMemberRepository extends JpaRepository<NotebookMember, NotebookMemberId> {
  List<NotebookMember> findByIdNotebookId(UUID notebookId);

  Optional<NotebookMember> findByIdNotebookIdAndIdUserId(UUID notebookId, UUID userId);
}
