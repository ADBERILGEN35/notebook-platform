package com.notebook.lumen.workspace.service;

import com.notebook.lumen.workspace.repository.WorkspaceRepository;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class SlugService {

  private final WorkspaceRepository workspaceRepository;

  public SlugService(WorkspaceRepository workspaceRepository) {
    this.workspaceRepository = workspaceRepository;
  }

  public String normalize(String value) {
    String slug =
        Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
    return slug.isBlank() ? "workspace" : slug;
  }

  public String generateUnique(String name) {
    String base = normalize(name);
    String candidate = base;
    int suffix = 2;
    while (workspaceRepository.existsBySlug(candidate)) {
      candidate = base + "-" + suffix++;
    }
    return candidate;
  }
}
