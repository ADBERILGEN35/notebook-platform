package com.notebook.lumen.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class BreakGlassGatewayMetricsTest {

  @Test
  void recordRevokedTokenDenied_incrementsCounter() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    BreakGlassGatewayMetrics metrics = new BreakGlassGatewayMetrics(registry);
    metrics.recordRevokedTokenDenied();
    metrics.recordRevokedTokenDenied();
    assertThat(registry.get("break_glass_revoked_token_denied_total").counter().count())
        .isEqualTo(2.0);
  }
}
