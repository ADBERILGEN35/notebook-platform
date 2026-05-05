package com.notebook.lumen.identity.audit;

import java.util.List;
import org.springframework.data.domain.Page;

public record AuditPageResponse<T>(
    List<T> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext,
    boolean hasPrevious) {
  static <T> AuditPageResponse<T> from(Page<T> page) {
    return new AuditPageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.hasNext(),
        page.hasPrevious());
  }
}
