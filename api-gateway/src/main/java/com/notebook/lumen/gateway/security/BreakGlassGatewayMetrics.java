package com.notebook.lumen.gateway.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BreakGlassGatewayMetrics {
  private final MeterRegistry meterRegistry;

  public BreakGlassGatewayMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordRevokedTokenDenied() {
    Counter.builder("break_glass_revoked_token_denied_total")
        .description("Break-glass requests rejected because JWT jti is revoked")
        .register(meterRegistry)
        .increment();
  }
}
