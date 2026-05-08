package com.notebook.lumen.notification.user.fanout;

import com.notebook.lumen.notification.shared.config.NotificationProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationFanoutOutboxWorker {
  private final NotificationFanoutOutboxProcessor processor;
  private final NotificationFanoutOutboxRepository repository;
  private final NotificationProperties properties;
  private final MeterRegistry meterRegistry;

  public NotificationFanoutOutboxWorker(
      NotificationFanoutOutboxProcessor processor,
      NotificationFanoutOutboxRepository repository,
      NotificationProperties properties,
      MeterRegistry meterRegistry) {
    this.processor = processor;
    this.repository = repository;
    this.properties = properties;
    this.meterRegistry = meterRegistry;
  }

  @PostConstruct
  void registerGauges() {
    Gauge.builder(
            "notifications_fanout_outbox_pending",
            repository,
            r -> r.countByStatus(NotificationFanoutOutboxStatus.PENDING))
        .register(meterRegistry);
    Gauge.builder(
            "notifications_fanout_outbox_dead",
            repository,
            r -> r.countByStatus(NotificationFanoutOutboxStatus.DEAD))
        .register(meterRegistry);
  }

  @Scheduled(fixedDelayString = "${notification.fanout.poll-interval-seconds:5}000")
  public void poll() {
    processor.processDue();
  }
}
