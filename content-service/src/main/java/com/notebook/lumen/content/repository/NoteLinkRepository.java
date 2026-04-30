package com.notebook.lumen.content.repository;

import com.notebook.lumen.content.domain.NoteLink;
import com.notebook.lumen.content.domain.NoteLinkId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteLinkRepository extends JpaRepository<NoteLink, NoteLinkId> {
  List<NoteLink> findByIdFromNoteId(UUID fromNoteId);

  List<NoteLink> findByIdToNoteId(UUID toNoteId);

  void deleteByIdFromNoteId(UUID fromNoteId);
}
