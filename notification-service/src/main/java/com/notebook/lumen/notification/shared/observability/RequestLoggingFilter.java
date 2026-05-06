package com.notebook.lumen.notification.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String requestId = resolveRequestId(request.getHeader("X-Request-Id"));
    MDC.put("requestId", requestId);
    request.setAttribute("requestId", requestId);
    response.setHeader("X-Request-Id", requestId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.clear();
    }
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
