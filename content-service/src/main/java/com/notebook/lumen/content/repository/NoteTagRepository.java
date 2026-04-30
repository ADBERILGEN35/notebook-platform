package com.notebook.lumen.content.repository;

import com.notebook.lumen.content.domain.NoteTag;
import com.notebook.lumen.content.domain.NoteTagId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteTagRepository extends JpaRepository<NoteTag, NoteTagId> {
  List<NoteTag> findByIdNoteId(UUID noteId);
}
