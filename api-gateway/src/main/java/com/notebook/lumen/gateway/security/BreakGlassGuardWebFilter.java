package com.notebook.lumen.gateway.security;

import com.notebook.lumen.gateway.config.GatewayBreakGlassProperties;
import com.notebook.lumen.gateway.error.ErrorCode;
import com.notebook.lumen.gateway.error.GatewayErrorResponseWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Guardrails for emergency break-glass tokens:
 *
 * <ul>
 *   <li>Gateway must explicitly allow break-glass admin tokens.
 *   <li>Admin-write is blocked by default for break-glass sessions.
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class BreakGlassGuardWebFilter implements WebFilter {

  private static final Logger log = LoggerFactory.getLogger(BreakGlassGuardWebFilter.class);

  private final GatewayBreakGlassProperties properties;
  private final GatewayErrorResponseWriter errorResponseWriter;

  public BreakGlassGuardWebFilter(
      GatewayBreakGlassProperties properties, GatewayErrorResponseWriter errorResponseWriter) {
    this.properties = properties;
    this.errorResponseWriter = errorResponseWriter;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    return exchange
        .getPrincipal()
        .ofType(JwtAuthenticationToken.class)
        .map(java.util.Optional::of)
        .defaultIfEmpty(java.util.Optional.empty())
        .flatMap(
            authOpt -> {
              JwtAuthenticationToken auth = authOpt.orElse(null);
              if (auth == null) {
                return chain.filter(exchange);
              }
              Boolean bg = auth.getToken().getClaim("break_glass");
              if (bg == null || !bg) {
                return chain.filter(exchange);
              }
              if (!properties.adminAllowed()) {
                log.warn(
                    "break_glass_rejected_gateway_disabled path={} sub={}",
                    exchange.getRequest().getPath().value(),
                    auth.getToken().getSubject());
                return errorResponseWriter.write(
                    exchange,
                    HttpStatus.FORBIDDEN,
                    ErrorCode.BREAK_GLASS_NOT_ALLOWED_AT_GATEWAY,
                    "Break-glass tokens are not allowed at this gateway.");
              }

              if (isWrite(exchange) && !properties.allowAdminWrite()) {
                log.warn(
                    "break_glass_write_blocked path={} sub={}",
                    exchange.getRequest().getPath().value(),
                    auth.getToken().getSubject());
                return errorResponseWriter.write(
                    exchange,
                    HttpStatus.FORBIDDEN,
                    ErrorCode.BREAK_GLASS_ADMIN_WRITE_BLOCKED,
                    "Admin write is blocked for break-glass sessions.");
              }
              return chain.filter(exchange);
            });
  }

  private boolean isWrite(ServerWebExchange exchange) {
    HttpMethod method = exchange.getRequest().getMethod();
    if (method == null) {
      return false;
    }
    if (HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method) || HttpMethod.OPTIONS.equals(method)) {
      return false;
    }
    String path = exchange.getRequest().getPath().value();
    // Only apply to admin surface writes.
    return path.startsWith("/admin/");
  }
}

