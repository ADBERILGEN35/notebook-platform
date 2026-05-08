package com.notebook.lumen.identity.siem.application;

import java.util.List;

public interface SiemEventPublisher {
  SiemPublishResult publish(List<SiemEventPayload> events);
}
