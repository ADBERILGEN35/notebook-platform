package com.notebook.lumen.gateway.security;

import com.notebook.lumen.gateway.config.GatewayAuthProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 25)
public class CookieAccessTokenWebFilter implements WebFilter {
  private final GatewayAuthProperties authProperties;

  public CookieAccessTokenWebFilter(GatewayAuthProperties authProperties) {
    this.authProperties = authProperties;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (!authProperties.cookieTransportEnabled()) {
      return chain.filter(exchange);
    }
    String existing = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (existing != null && !existing.isBlank()) {
      return chain.filter(exchange);
    }

    HttpHeaders headers = new HttpHeaders();
    headers.putAll(exchange.getRequest().getHeaders());
    var cookie =
        exchange.getRequest().getCookies().getFirst(authProperties.effectiveAccessCookieName());
    if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
      return chain.filter(exchange);
    }
    headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + cookie.getValue());
    ServerHttpRequest decorated =
        new ServerHttpRequestDecorator(exchange.getRequest()) {
          @Override
          public HttpHeaders getHeaders() {
            return headers;
          }
        };
    return chain.filter(exchange.mutate().request(decorated).build());
  }
}
