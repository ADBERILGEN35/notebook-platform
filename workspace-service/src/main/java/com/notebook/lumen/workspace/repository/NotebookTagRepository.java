package com.notebook.lumen.workspace.repository;

import com.notebook.lumen.workspace.domain.NotebookTag;
import com.notebook.lumen.workspace.domain.NotebookTagId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotebookTagRepository extends JpaRepository<NotebookTag, NotebookTagId> {}
