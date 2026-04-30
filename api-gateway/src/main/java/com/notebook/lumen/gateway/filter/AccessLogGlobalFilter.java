package com.notebook.lumen.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AccessLogGlobalFilter implements GlobalFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(AccessLogGlobalFilter.class);

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    long startNanos = System.nanoTime();
    return chain.filter(exchange).doFinally(signalType -> logRequest(exchange, startNanos));
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }

  private void logRequest(ServerWebExchange exchange, long startNanos) {
    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
    Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
    HttpStatusCode status = exchange.getResponse().getStatusCode();
    String requestId = exchange.getResponse().getHeaders().getFirst(GatewayHeaders.REQUEST_ID);

    try {
      MDC.put("requestId", requestId);
      MDC.put("routeId", route == null ? "-" : route.getId());
      MDC.put("method", exchange.getRequest().getMethod().name());
      MDC.put("path", exchange.getRequest().getPath().value());
      MDC.put("status", status == null ? "-" : String.valueOf(status.value()));
      MDC.put("durationMs", String.valueOf(durationMs));
      log.info("gateway_request");
    } finally {
      MDC.remove("routeId");
      MDC.remove("method");
      MDC.remove("path");
      MDC.remove("status");
      MDC.remove("durationMs");
    }
  }
}
