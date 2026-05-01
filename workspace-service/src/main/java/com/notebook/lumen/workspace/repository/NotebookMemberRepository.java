package com.notebook.lumen.workspace.repository;

import com.notebook.lumen.workspace.domain.NotebookMember;
import com.notebook.lumen.workspace.domain.NotebookMemberId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotebookMemberRepository extends JpaRepository<NotebookMember, NotebookMemberId> {
  List<NotebookMember> findByIdNotebookId(UUID notebookId);

  Page<NotebookMember> findByIdNotebookId(UUID notebookId, Pageable pageable);

  Optional<NotebookMember> findByIdNotebookIdAndIdUserId(UUID notebookId, UUID userId);
}
