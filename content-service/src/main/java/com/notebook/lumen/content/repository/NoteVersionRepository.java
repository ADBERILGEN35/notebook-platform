package com.notebook.lumen.content.repository;

import com.notebook.lumen.content.domain.NoteVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteVersionRepository extends JpaRepository<NoteVersion, UUID> {
  List<NoteVersion> findByNoteIdOrderByVersionNumberAsc(UUID noteId);

  Page<NoteVersion> findByNoteId(UUID noteId, Pageable pageable);

  Optional<NoteVersion> findByNoteIdAndVersionNumber(UUID noteId, int versionNumber);

  int countByNoteId(UUID noteId);
}
