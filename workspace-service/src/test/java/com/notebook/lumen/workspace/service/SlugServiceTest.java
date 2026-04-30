package com.notebook.lumen.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.notebook.lumen.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SlugServiceTest {

  private final WorkspaceRepository workspaceRepository = Mockito.mock(WorkspaceRepository.class);
  private final SlugService slugService = new SlugService(workspaceRepository);

  @Test
  void normalize_generatesLowercaseKebabCase() {
    assertThat(slugService.normalize("Product Team 2026!")).isEqualTo("product-team-2026");
  }

  @Test
  void generateUnique_addsNumericSuffix() {
    when(workspaceRepository.existsBySlug("team")).thenReturn(true);
    when(workspaceRepository.existsBySlug("team-2")).thenReturn(true);
    when(workspaceRepository.existsBySlug("team-3")).thenReturn(false);

    assertThat(slugService.generateUnique("Team")).isEqualTo("team-3");
  }
}
