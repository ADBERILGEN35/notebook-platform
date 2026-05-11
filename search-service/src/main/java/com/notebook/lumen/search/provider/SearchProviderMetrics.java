package com.notebook.lumen.search.provider;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class SearchProviderMetrics {
  private final MeterRegistry meterRegistry;

  public SearchProviderMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public <T> T record(
      SearchProviderType provider, SearchOperation operation, Supplier<T> supplier) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      T result = supplier.get();
      meterRegistry
          .counter(
              "search_provider_requests_total",
              "provider",
              provider.name().toLowerCase(),
              "operation",
              operation.name().toLowerCase())
          .increment();
      return result;
    } catch (RuntimeException e) {
      meterRegistry
          .counter(
              "search_provider_failures_total",
              "provider",
              provider.name().toLowerCase(),
              "operation",
              operation.name().toLowerCase())
          .increment();
      throw e;
    } finally {
      sample.stop(
          meterRegistry.timer(
              "search_provider_latency",
              "provider",
              provider.name().toLowerCase(),
              "operation",
              operation.name().toLowerCase()));
    }
  }

  public void fallback(SearchOperation operation) {
    meterRegistry
        .counter("search_provider_fallback_total", "operation", operation.name().toLowerCase())
        .increment();
  }
}
