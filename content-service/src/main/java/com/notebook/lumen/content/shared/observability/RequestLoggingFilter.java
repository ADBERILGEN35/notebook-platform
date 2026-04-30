package com.notebook.lumen.content.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
  public static final String REQUEST_ID = "X-Request-Id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long start = System.nanoTime();
    String requestId = resolveRequestId(request.getHeader(REQUEST_ID));
    MDC.put("requestId", requestId);
    MDC.put("method", request.getMethod());
    MDC.put("path", request.getRequestURI());
    request.setAttribute("requestId", requestId);
    putIfPresent("userId", request.getHeader("X-User-Id"));
    putIfPresent("workspaceId", request.getHeader("X-Workspace-Id"));
    response.setHeader(REQUEST_ID, requestId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.put("status", String.valueOf(response.getStatus()));
      MDC.put("durationMs", String.valueOf((System.nanoTime() - start) / 1_000_000));
      log.info("http_request");
      MDC.clear();
    }
  }

  private void putIfPresent(String key, String value) {
    if (value != null && !value.isBlank()) MDC.put(key, value);
  }

  private String resolveRequestId(String incoming) {
    if (incoming != null) {
      try {
        return UUID.fromString(incoming).toString();
      } catch (IllegalArgumentException ignored) {
      }
    }
    return UUID.randomUUID().toString();
  }
}
