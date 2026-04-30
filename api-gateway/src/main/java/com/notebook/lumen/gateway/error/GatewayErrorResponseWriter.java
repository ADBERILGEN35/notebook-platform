package com.notebook.lumen.gateway.error;

import java.time.Instant;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GatewayErrorResponseWriter {

  private final ObjectMapper objectMapper;

  public GatewayErrorResponseWriter() {
    this.objectMapper = JsonMapper.builder().findAndAddModules().build();
  }

  public Mono<Void> write(
      ServerWebExchange exchange, HttpStatus status, ErrorCode errorCode, String message) {
    ServerHttpResponse response = exchange.getResponse();
    if (response.isCommitted()) {
      return Mono.empty();
    }

    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    ErrorResponse body =
        new ErrorResponse(
            Instant.now(),
            status.value(),
            errorCode.name(),
            message,
            exchange.getRequest().getPath().value(),
            exchange.getResponse().getHeaders().getFirst("X-Request-Id"));

    byte[] bytes;
    try {
      bytes = objectMapper.writeValueAsBytes(body);
    } catch (Exception e) {
      bytes = fallbackBody(status, errorCode, message, exchange);
    }
    DataBuffer buffer = response.bufferFactory().wrap(bytes);
    return response.writeWith(Mono.just(buffer));
  }

  private byte[] fallbackBody(
      HttpStatus status, ErrorCode errorCode, String message, ServerWebExchange exchange) {
    String json =
        """
            {"timestamp":"%s","status":%d,"errorCode":"%s","message":"%s","path":"%s","requestId":"%s"}\
            """
            .formatted(
                Instant.now(),
                status.value(),
                errorCode.name(),
                escape(message),
                escape(exchange.getRequest().getPath().value()),
                escape(exchange.getResponse().getHeaders().getFirst("X-Request-Id")));
    return json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  private String escape(String value) {
    return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
