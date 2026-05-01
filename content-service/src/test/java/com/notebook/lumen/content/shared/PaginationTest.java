package com.notebook.lumen.content.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.notebook.lumen.content.shared.exception.ContentException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PaginationTest {
  private static final Set<String> SORTS = Set.of("title", "createdAt");

  @Test
  void defaultsAndSortWhitelistWork() {
    var pageable = Pagination.pageable(null, null, "title,asc", SORTS);

    assertThat(pageable.getPageNumber()).isZero();
    assertThat(pageable.getPageSize()).isEqualTo(20);
    assertThat(pageable.getSort().getOrderFor("title").getDirection().isAscending()).isTrue();
  }

  @Test
  void invalidValuesAreRejectedWithPaginationCodes() {
    assertThatThrownBy(() -> Pagination.pageable(-1, 20, null, SORTS))
        .isInstanceOf(ContentException.class)
        .extracting("errorCode")
        .isEqualTo("INVALID_PAGE_REQUEST");
    assertThatThrownBy(() -> Pagination.pageable(0, 0, null, SORTS))
        .isInstanceOf(ContentException.class)
        .extracting("errorCode")
        .isEqualTo("INVALID_PAGE_SIZE");
    assertThatThrownBy(() -> Pagination.pageable(0, 20, "bad,asc", SORTS))
        .isInstanceOf(ContentException.class)
        .extracting("errorCode")
        .isEqualTo("INVALID_SORT_FIELD");
    assertThatThrownBy(() -> Pagination.pageable(0, 20, "title,sideways", SORTS))
        .isInstanceOf(ContentException.class)
        .extracting("errorCode")
        .isEqualTo("INVALID_SORT_DIRECTION");
  }
}
