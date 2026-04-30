package com.notebook.lumen.identity.audit;

import com.notebook.lumen.common.security.sanitization.SensitiveDataSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
  private static final Logger log = LoggerFactory.getLogger(AuditService.class);

  private final AuditEventRepository repository;

  public AuditService(AuditEventRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      String eventType,
      UUID actorUserId,
      String aggregateType,
      UUID aggregateId,
      HttpServletRequest request,
      Map<String, Object> metadata) {
    try {
      repository.save(
          new AuditEvent(
              UUID.randomUUID(),
              eventType,
              actorUserId,
              null,
              aggregateType,
              aggregateId,
              requestId(request),
              ip(request),
              request == null ? null : request.getHeader("User-Agent"),
              SensitiveDataSanitizer.sanitizeMetadata(metadata),
              Instant.now()));
    } catch (RuntimeException e) {
      log.error(
          "Audit event write failed eventType={} aggregateType={}", eventType, aggregateType, e);
    }
  }

  private String requestId(HttpServletRequest request) {
    if (request != null && request.getAttribute("requestId") != null) {
      return request.getAttribute("requestId").toString();
    }
    String fromMdc = MDC.get("requestId");
    return fromMdc == null && request != null ? request.getHeader("X-Request-Id") : fromMdc;
  }

  private String ip(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
