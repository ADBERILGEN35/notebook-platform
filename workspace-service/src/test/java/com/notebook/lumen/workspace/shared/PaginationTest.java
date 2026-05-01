package com.notebook.lumen.workspace.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.notebook.lumen.workspace.shared.exception.WorkspaceRuntimeException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PaginationTest {
  private static final Set<String> SORTS = Set.of("name", "createdAt");

  @Test
  void defaultsAndSortWhitelistWork() {
    var pageable = Pagination.pageable(null, null, "name,desc", SORTS);

    assertThat(pageable.getPageNumber()).isZero();
    assertThat(pageable.getPageSize()).isEqualTo(20);
    assertThat(pageable.getSort().getOrderFor("name").getDirection().isDescending()).isTrue();
  }

  @Test
  void invalidValuesAreRejectedWithPaginationCodes() {
    assertThatThrownBy(() -> Pagination.pageable(-1, 20, null, SORTS))
        .isInstanceOf(WorkspaceRuntimeException.class)
        .extracting("errorCode")
        .isEqualTo("INVALID_PAGE_REQUEST");
    assertThatThrownBy(() -> Pagination.pageable(0, 101, null, SORTS))
        .isInstanceOf(WorkspaceRuntimeException.class)
        .extracting("errorCode")
        .isEqualTo("INVALID_PAGE_SIZE");
    assertThatThrownBy(() -> Pagination.pageable(0, 20, "bad,asc", SORTS))
        .isInstanceOf(WorkspaceRuntimeException.class)
        .extracting("errorCode")
        .isEqualTo("INVALID_SORT_FIELD");
    assertThatThrownBy(() -> Pagination.pageable(0, 20, "name,sideways", SORTS))
        .isInstanceOf(WorkspaceRuntimeException.class)
        .extracting("errorCode")
        .isEqualTo("INVALID_SORT_DIRECTION");
  }
}
