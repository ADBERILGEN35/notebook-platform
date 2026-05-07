package com.notebook.lumen.search.query.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.notebook.lumen.search.index.application.SearchAuditService;
import com.notebook.lumen.search.provider.SearchProviderRouter;
import com.notebook.lumen.search.shared.config.SearchProperties;
import com.notebook.lumen.search.shared.exception.SearchException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchQueryServiceTest {
  private final SearchQueryService service =
          new SearchQueryService(
          mock(SearchProviderRouter.class),
          mock(SearchPermissionService.class),
          properties(),
          mock(SearchAuditService.class));

  @Test
  void rejectsTooShortQuery() {
    assertThatThrownBy(
            () -> service.search(UUID.randomUUID(), null, UUID.randomUUID(), "a", null, 0, 20))
        .isInstanceOf(SearchException.class)
        .extracting("errorCode")
        .isEqualTo("SEARCH_QUERY_TOO_SHORT");
  }

  @Test
  void rejectsConflictingWorkspaceHeader() {
    assertThatThrownBy(
            () ->
                service.search(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "valid", null, 0, 20))
        .isInstanceOf(SearchException.class)
        .extracting("errorCode")
        .isEqualTo("INVALID_WORKSPACE_CONTEXT");
  }

  private SearchProperties properties() {
    return new SearchProperties(
        200000,
        120,
        2,
        50,
        "",
        "postgres",
        false,
        false,
        new SearchProperties.OpenSearch("", "", "", "notebook-notes", 1000, 3000, false, ""),
        new SearchProperties.Workspace("http://localhost", 1000, 2),
        new SearchProperties.ContentSource("http://localhost", 1000, "content-service"),
        null,
        new SearchProperties.Internal(null, null),
        new SearchProperties.Reindex(true, 100, 10, 100, false, 300, 30));
  }
}
