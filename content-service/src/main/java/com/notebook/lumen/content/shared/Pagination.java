package com.notebook.lumen.content.shared;

import com.notebook.lumen.content.shared.exception.ContentException;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

public final class Pagination {
  public static final int DEFAULT_PAGE = 0;
  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  private Pagination() {}

  public static Pageable pageable(Integer page, Integer size, String sort, Set<String> allowed) {
    int resolvedPage = page == null ? DEFAULT_PAGE : page;
    int resolvedSize = size == null ? DEFAULT_SIZE : size;
    if (resolvedPage < 0) {
      throw bad("INVALID_PAGE_REQUEST", "page must be greater than or equal to 0");
    }
    if (resolvedSize < 1) {
      throw bad("INVALID_PAGE_SIZE", "size must be greater than or equal to 1");
    }
    if (resolvedSize > MAX_SIZE) {
      throw bad("INVALID_PAGE_SIZE", "size must be less than or equal to 100");
    }
    return PageRequest.of(resolvedPage, resolvedSize, sort(sort, allowed));
  }

  private static Sort sort(String raw, Set<String> allowed) {
    if (raw == null || raw.isBlank()) {
      return Sort.by(Sort.Direction.DESC, "createdAt");
    }
    String[] parts = raw.split(",", -1);
    String field = parts[0].trim();
    if (!allowed.contains(field)) {
      throw bad("INVALID_SORT_FIELD", "Unsupported sort field: " + field);
    }
    Sort.Direction direction = Sort.Direction.ASC;
    if (parts.length > 1) {
      try {
        direction = Sort.Direction.fromString(parts[1].trim());
      } catch (IllegalArgumentException e) {
        throw bad("INVALID_SORT_DIRECTION", "sort direction must be asc or desc");
      }
    }
    return Sort.by(direction, field);
  }

  private static ContentException bad(String code, String message) {
    return new ContentException(HttpStatus.BAD_REQUEST, code, message);
  }
}
