package com.notebook.lumen.gateway.security;

import com.notebook.lumen.gateway.config.GatewayAuthProperties;
import com.notebook.lumen.gateway.error.ErrorCode;
import com.notebook.lumen.gateway.error.GatewayErrorResponseWriter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CsrfDoubleSubmitWebFilter implements WebFilter {
  private final GatewayAuthProperties authProperties;
  private final GatewayErrorResponseWriter errorResponseWriter;

  public CsrfDoubleSubmitWebFilter(
      GatewayAuthProperties authProperties, GatewayErrorResponseWriter errorResponseWriter) {
    this.authProperties = authProperties;
    this.errorResponseWriter = errorResponseWriter;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (!authProperties.cookieTransportEnabled()) {
      return chain.filter(exchange);
    }
    HttpMethod method = exchange.getRequest().getMethod();
    if (method == null || isSafeMethod(method)) {
      return chain.filter(exchange);
    }

    String path = exchange.getRequest().getPath().value();
    if (path.startsWith("/auth/login")
        || path.startsWith("/auth/signup")
        || path.startsWith("/webhooks/email/")
        || path.startsWith("/actuator/")) {
      return chain.filter(exchange);
    }

    String authorization = exchange.getRequest().getHeaders().getFirst("Authorization");
    if (authorization != null && !authorization.isBlank()) {
      return chain.filter(exchange);
    }

    var accessCookie =
        exchange.getRequest().getCookies().getFirst(authProperties.effectiveAccessCookieName());
    if (accessCookie == null
        || accessCookie.getValue() == null
        || accessCookie.getValue().isBlank()) {
      return chain.filter(exchange);
    }

    var csrfCookie =
        exchange.getRequest().getCookies().getFirst(authProperties.effectiveCsrfCookieName());
    String csrfHeader =
        exchange.getRequest().getHeaders().getFirst(authProperties.effectiveCsrfHeaderName());
    if (csrfCookie == null || csrfCookie.getValue() == null || csrfCookie.getValue().isBlank()) {
      return errorResponseWriter.write(
          exchange, HttpStatus.FORBIDDEN, ErrorCode.CSRF_TOKEN_REQUIRED, "CSRF token is required");
    }
    if (csrfHeader == null || csrfHeader.isBlank()) {
      return errorResponseWriter.write(
          exchange, HttpStatus.FORBIDDEN, ErrorCode.CSRF_TOKEN_REQUIRED, "CSRF token is required");
    }
    if (!csrfCookie.getValue().equals(csrfHeader)) {
      return errorResponseWriter.write(
          exchange, HttpStatus.FORBIDDEN, ErrorCode.CSRF_TOKEN_INVALID, "Invalid CSRF token");
    }
    return chain.filter(exchange);
  }

  private boolean isSafeMethod(HttpMethod method) {
    return HttpMethod.GET.equals(method)
        || HttpMethod.HEAD.equals(method)
        || HttpMethod.OPTIONS.equals(method);
  }
}
