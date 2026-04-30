package com.notebook.lumen.gateway.filter;

import com.notebook.lumen.gateway.error.ErrorCode;
import com.notebook.lumen.gateway.error.GatewayErrorResponseWriter;
import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class HeaderPropagationGlobalFilter implements GlobalFilter, Ordered {

  private final GatewayErrorResponseWriter errorResponseWriter;

  public HeaderPropagationGlobalFilter(GatewayErrorResponseWriter errorResponseWriter) {
    this.errorResponseWriter = errorResponseWriter;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String workspaceId = exchange.getRequest().getHeaders().getFirst(GatewayHeaders.WORKSPACE_ID);
    if (workspaceId != null && !workspaceId.isBlank() && !isValidUuid(workspaceId)) {
      return errorResponseWriter.write(
          exchange,
          HttpStatus.BAD_REQUEST,
          ErrorCode.INVALID_WORKSPACE_ID,
          "X-Workspace-Id must be a valid UUID");
    }

    return exchange
        .getPrincipal()
        .cast(JwtAuthenticationToken.class)
        .map(authentication -> mutateAuthenticatedRequest(exchange, authentication, workspaceId))
        .defaultIfEmpty(mutatePublicRequest(exchange, workspaceId))
        .flatMap(mutated -> chain.filter(mutated));
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 20;
  }

  private ServerWebExchange mutateAuthenticatedRequest(
      ServerWebExchange exchange, JwtAuthenticationToken authentication, String workspaceId) {
    ServerHttpRequest request =
        exchange
            .getRequest()
            .mutate()
            .headers(
                headers -> {
                  removeSpoofableHeaders(headers);
                  headers.set(GatewayHeaders.USER_ID, authentication.getToken().getSubject());
                  String email = authentication.getToken().getClaimAsString("email");
                  if (email != null && !email.isBlank()) {
                    headers.set(GatewayHeaders.USER_EMAIL, email);
                  }
                  if (workspaceId != null && !workspaceId.isBlank()) {
                    headers.set(GatewayHeaders.WORKSPACE_ID, workspaceId);
                  } else {
                    headers.remove(GatewayHeaders.WORKSPACE_ID);
                  }
                })
            .build();
    return exchange.mutate().request(request).build();
  }

  private ServerWebExchange mutatePublicRequest(ServerWebExchange exchange, String workspaceId) {
    ServerHttpRequest request =
        exchange
            .getRequest()
            .mutate()
            .headers(
                headers -> {
                  removeSpoofableHeaders(headers);
                  if (workspaceId != null && !workspaceId.isBlank()) {
                    headers.set(GatewayHeaders.WORKSPACE_ID, workspaceId);
                  } else {
                    headers.remove(GatewayHeaders.WORKSPACE_ID);
                  }
                })
            .build();
    return exchange.mutate().request(request).build();
  }

  private void removeSpoofableHeaders(org.springframework.http.HttpHeaders headers) {
    headers.remove(GatewayHeaders.USER_ID);
    headers.remove(GatewayHeaders.USER_EMAIL);
    headers.remove(GatewayHeaders.USER_ROLES);
    headers.remove(GatewayHeaders.WORKSPACE_ROLE);
  }

  private boolean isValidUuid(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
