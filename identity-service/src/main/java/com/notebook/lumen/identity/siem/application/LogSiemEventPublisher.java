package com.notebook.lumen.identity.siem.application;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LogSiemEventPublisher implements SiemEventPublisher {
  private static final Logger log = LoggerFactory.getLogger(LogSiemEventPublisher.class);

  @Override
  public SiemPublishResult publish(List<SiemEventPayload> events) {
    for (SiemEventPayload event : events) {
      log.info(
          "siem_event_log id={} type={} category={} severity={} requestId={}",
          event.id(),
          event.eventType(),
          event.category(),
          event.severity(),
          event.requestId());
    }
    return SiemPublishResult.ok();
  }
}
