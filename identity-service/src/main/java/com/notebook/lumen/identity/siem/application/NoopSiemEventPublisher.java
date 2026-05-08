package com.notebook.lumen.identity.siem.application;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NoopSiemEventPublisher implements SiemEventPublisher {
  @Override
  public SiemPublishResult publish(List<SiemEventPayload> events) {
    return SiemPublishResult.ok();
  }
}
