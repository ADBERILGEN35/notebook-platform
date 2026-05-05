package com.notebook.lumen.identity.audit;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {
  private static final int DEFAULT_SIZE = 50;
  private static final int MAX_SIZE = 200;
  private static final Set<String> SORT_FIELDS = Set.of("createdAt", "eventType", "aggregateType");

  private final AuditEventRepository repository;

  public AuditQueryService(AuditEventRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public AuditPageResponse<AuditEventResponse> query(AuditQuery query) {
    validate(query);
    PageRequest pageRequest =
        PageRequest.of(
            query.page() == null ? 0 : query.page(),
            query.size() == null ? DEFAULT_SIZE : query.size(),
            sort(query.sort()));
    return AuditPageResponse.from(
        repository.findAll(spec(query), pageRequest).map(AuditEventResponse::from));
  }

  private Specification<AuditEvent> spec(AuditQuery query) {
    return (root, ignored, cb) -> {
      ArrayList<Predicate> predicates = new ArrayList<>();
      addEquals(predicates, cb, root.get("eventType"), query.eventType());
      addEquals(predicates, cb, root.get("actorUserId"), query.actorUserId());
      addEquals(predicates, cb, root.get("workspaceId"), query.workspaceId());
      addEquals(predicates, cb, root.get("aggregateType"), query.aggregateType());
      addEquals(predicates, cb, root.get("aggregateId"), query.aggregateId());
      addEquals(predicates, cb, root.get("requestId"), query.requestId());
      if (query.createdFrom() != null)
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), query.createdFrom()));
      if (query.createdTo() != null)
        predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), query.createdTo()));
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  private <T> void addEquals(
      ArrayList<Predicate> predicates,
      jakarta.persistence.criteria.CriteriaBuilder cb,
      jakarta.persistence.criteria.Path<T> path,
      T value) {
    if (value != null && (!(value instanceof String text) || !text.isBlank())) {
      predicates.add(cb.equal(path, value));
    }
  }

  private void validate(AuditQuery query) {
    int page = query.page() == null ? 0 : query.page();
    int size = query.size() == null ? DEFAULT_SIZE : query.size();
    if (page < 0) bad("INVALID_AUDIT_FILTER", "page must be greater than or equal to 0");
    if (size < 1 || size > MAX_SIZE) bad("INVALID_PAGE_SIZE", "size must be between 1 and 200");
    if (query.createdFrom() != null && query.createdTo() != null) {
      if (query.createdFrom().isAfter(query.createdTo())) {
        bad("INVALID_AUDIT_TIME_RANGE", "createdFrom must be before or equal to createdTo");
      }
      if (query.createdFrom().plus(90, ChronoUnit.DAYS).isBefore(query.createdTo())) {
        bad("AUDIT_QUERY_RANGE_TOO_LARGE", "Audit query range must be 90 days or less");
      }
    }
  }

  private Sort sort(String raw) {
    if (raw == null || raw.isBlank()) return Sort.by(Sort.Direction.DESC, "createdAt");
    String[] parts = raw.split(",", -1);
    String field = parts[0].trim();
    if (!SORT_FIELDS.contains(field)) bad("INVALID_SORT_FIELD", "Unsupported sort field: " + field);
    Sort.Direction direction = Sort.Direction.DESC;
    if (parts.length > 1) {
      try {
        direction = Sort.Direction.fromString(parts[1].trim());
      } catch (IllegalArgumentException e) {
        bad("INVALID_SORT_FIELD", "sort direction must be asc or desc");
      }
    }
    return Sort.by(direction, field);
  }

  private void bad(String code, String message) {
    throw new AuditAccessException(HttpStatus.BAD_REQUEST, code, message);
  }

  public record AuditQuery(
      String eventType,
      UUID actorUserId,
      UUID workspaceId,
      String aggregateType,
      UUID aggregateId,
      String requestId,
      Instant createdFrom,
      Instant createdTo,
      Integer page,
      Integer size,
      String sort) {}
}
