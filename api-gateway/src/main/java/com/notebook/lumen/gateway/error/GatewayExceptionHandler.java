package com.notebook.lumen.gateway.error;

import java.net.ConnectException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class GatewayExceptionHandler implements WebExceptionHandler {

  private final GatewayErrorResponseWriter errorResponseWriter;

  public GatewayExceptionHandler(GatewayErrorResponseWriter errorResponseWriter) {
    this.errorResponseWriter = errorResponseWriter;
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    if (exchange.getResponse().isCommitted()) {
      return Mono.error(ex);
    }

    if (isRouteUnavailable(ex)) {
      return errorResponseWriter.write(
          exchange,
          HttpStatus.SERVICE_UNAVAILABLE,
          ErrorCode.ROUTE_UNAVAILABLE,
          "Route unavailable");
    }

    if (ex instanceof ResponseStatusException statusException) {
      return errorResponseWriter.write(
          exchange,
          HttpStatus.valueOf(statusException.getStatusCode().value()),
          ErrorCode.INTERNAL_GATEWAY_ERROR,
          "Gateway request failed");
    }

    return errorResponseWriter.write(
        exchange,
        HttpStatus.INTERNAL_SERVER_ERROR,
        ErrorCode.INTERNAL_GATEWAY_ERROR,
        "Unexpected gateway error");
  }

  private boolean isRouteUnavailable(Throwable ex) {
    Throwable current = ex;
    while (current != null) {
      if (current instanceof ConnectException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
