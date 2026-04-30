package com.notebook.lumen.gateway.filter;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class RequestIdGlobalFilter implements WebFilter, Ordered {

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String requestId = resolveRequestId(exchange);
    ServerHttpRequest request =
        exchange
            .getRequest()
            .mutate()
            .headers(headers -> headers.set(GatewayHeaders.REQUEST_ID, requestId))
            .build();

    exchange.getResponse().getHeaders().set(GatewayHeaders.REQUEST_ID, requestId);
    return chain
        .filter(exchange.mutate().request(request).build())
        .contextWrite(context -> context.put(GatewayHeaders.REQUEST_ID, requestId))
        .doFirst(() -> MDC.put(GatewayHeaders.REQUEST_ID, requestId))
        .doFinally(signalType -> MDC.remove(GatewayHeaders.REQUEST_ID));
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  private String resolveRequestId(ServerWebExchange exchange) {
    String incoming = exchange.getRequest().getHeaders().getFirst(GatewayHeaders.REQUEST_ID);
    if (incoming != null && isValidUuid(incoming)) {
      return incoming;
    }
    return UUID.randomUUID().toString();
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
