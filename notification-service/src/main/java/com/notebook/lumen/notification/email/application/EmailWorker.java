package com.notebook.lumen.notification.email.application;

import com.notebook.lumen.notification.analytics.NotificationWorkerRunTimestamps;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmailWorker {
  private final EmailNotificationService service;
  private final NotificationWorkerRunTimestamps workerRunTimestamps;
  private final AtomicBoolean acceptingClaims = new AtomicBoolean(true);

  public EmailWorker(
      EmailNotificationService service, NotificationWorkerRunTimestamps workerRunTimestamps) {
    this.service = service;
    this.workerRunTimestamps = workerRunTimestamps;
  }

  @Scheduled(fixedDelayString = "${notification.email.worker-fixed-delay-ms:5000}")
  void process() {
    if (!acceptingClaims.get()) {
      return;
    }
    try {
      service.processDueNotifications();
    } finally {
      workerRunTimestamps.markEmailRun(Instant.now());
    }
  }

  @jakarta.annotation.PreDestroy
  void stopAcceptingClaims() {
    acceptingClaims.set(false);
  }
}
