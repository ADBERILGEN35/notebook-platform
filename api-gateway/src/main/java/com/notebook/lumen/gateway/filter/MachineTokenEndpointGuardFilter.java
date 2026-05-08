package com.notebook.lumen.gateway.filter;

import com.notebook.lumen.gateway.error.ErrorCode;
import com.notebook.lumen.gateway.error.GatewayErrorResponseWriter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class MachineTokenEndpointGuardFilter implements GlobalFilter, Ordered {
  private static final String EXPORT_PATH = "/admin/audit-events/export";

  private final GatewayErrorResponseWriter errorResponseWriter;

  public MachineTokenEndpointGuardFilter(GatewayErrorResponseWriter errorResponseWriter) {
    this.errorResponseWriter = errorResponseWriter;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    return exchange
        .getPrincipal()
        .cast(JwtAuthenticationToken.class)
        .map(
            authentication -> {
              String tokenType = authentication.getToken().getClaimAsString("token_type");
              String path = exchange.getRequest().getPath().value();
              return "machine".equals(tokenType) && !EXPORT_PATH.equals(path);
            })
        .defaultIfEmpty(false)
        .flatMap(
            blocked -> {
              if (blocked) {
                return errorResponseWriter.write(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.INVALID_TOKEN_TYPE,
                    "Machine token is allowed only for audit export endpoint");
              }
              return chain.filter(exchange);
            });
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 25;
  }
}
