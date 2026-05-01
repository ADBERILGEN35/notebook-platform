package com.notebook.lumen.content.repository;

import com.notebook.lumen.content.domain.NoteLink;
import com.notebook.lumen.content.domain.NoteLinkId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteLinkRepository extends JpaRepository<NoteLink, NoteLinkId> {
  List<NoteLink> findByIdFromNoteId(UUID fromNoteId);

  Page<NoteLink> findByIdFromNoteId(UUID fromNoteId, Pageable pageable);

  List<NoteLink> findByIdToNoteId(UUID toNoteId);

  Page<NoteLink> findByIdToNoteId(UUID toNoteId, Pageable pageable);

  void deleteByIdFromNoteId(UUID fromNoteId);
}
