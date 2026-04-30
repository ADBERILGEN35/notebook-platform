package com.notebook.lumen.content.repository;

import com.notebook.lumen.content.domain.NoteVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteVersionRepository extends JpaRepository<NoteVersion, UUID> {
  List<NoteVersion> findByNoteIdOrderByVersionNumberAsc(UUID noteId);

  Optional<NoteVersion> findByNoteIdAndVersionNumber(UUID noteId, int versionNumber);

  int countByNoteId(UUID noteId);
}
