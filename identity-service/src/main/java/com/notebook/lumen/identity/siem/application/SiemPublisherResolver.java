package com.notebook.lumen.identity.siem.application;

import com.notebook.lumen.identity.siem.SiemProperties;
import org.springframework.stereotype.Component;

@Component
public class SiemPublisherResolver {
  private final SiemProperties properties;
  private final NoopSiemEventPublisher noopPublisher;
  private final LogSiemEventPublisher logPublisher;
  private final GenericHttpSiemEventPublisher httpPublisher;

  public SiemPublisherResolver(
      SiemProperties properties,
      NoopSiemEventPublisher noopPublisher,
      LogSiemEventPublisher logPublisher,
      GenericHttpSiemEventPublisher httpPublisher) {
    this.properties = properties;
    this.noopPublisher = noopPublisher;
    this.logPublisher = logPublisher;
    this.httpPublisher = httpPublisher;
  }

  public SiemEventPublisher resolve() {
    return switch (properties.effectiveProvider()) {
      case "log" -> logPublisher;
      case "generic-http" -> httpPublisher;
      default -> noopPublisher;
    };
  }
}
