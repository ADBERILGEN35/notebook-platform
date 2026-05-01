package com.notebook.lumen.content.repository;

import com.notebook.lumen.content.domain.Comment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
  Optional<Comment> findByIdAndDeletedAtIsNull(UUID id);

  List<Comment> findByNoteIdAndDeletedAtIsNull(UUID noteId);

  Page<Comment> findByNoteIdAndDeletedAtIsNull(UUID noteId, Pageable pageable);
}
