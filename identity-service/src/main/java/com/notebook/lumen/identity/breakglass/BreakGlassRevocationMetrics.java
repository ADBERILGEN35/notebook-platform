package com.notebook.lumen.identity.breakglass;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class BreakGlassRevocationMetrics {
  private final AtomicLong activeSessions = new AtomicLong(0);
  private final MeterRegistry meterRegistry;

  public BreakGlassRevocationMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    Gauge.builder("break_glass_active_sessions_total", activeSessions, AtomicLong::get)
        .description("Active non-expired break-glass sessions with token jti")
        .register(meterRegistry);
  }

  public void setActiveSessions(long count) {
    activeSessions.set(count);
  }

  public void recordRevocation(String source, String result) {
    Counter.builder("break_glass_revocations_total")
        .tag("source", source == null ? "unknown" : source)
        .tag("result", result == null ? "unknown" : result)
        .register(meterRegistry)
        .increment();
  }

  public void recordRevokeAll(String result) {
    Counter.builder("break_glass_revoke_all_total")
        .tag("result", result == null ? "unknown" : result)
        .register(meterRegistry)
        .increment();
  }
}
