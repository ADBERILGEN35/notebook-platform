package com.notebook.lumen.notification.email.application;

import com.notebook.lumen.notification.analytics.NotificationWorkerRunTimestamps;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationDigestWorker {
  private final NotificationDigestService digestService;
  private final NotificationWorkerRunTimestamps workerRunTimestamps;

  public NotificationDigestWorker(
      NotificationDigestService digestService, NotificationWorkerRunTimestamps workerRunTimestamps) {
    this.digestService = digestService;
    this.workerRunTimestamps = workerRunTimestamps;
  }

  @Scheduled(fixedDelayString = "${notification.digest.poll-interval-seconds:60}000")
  public void poll() {
    try {
      digestService.processDueDigestItems();
    } finally {
      workerRunTimestamps.markDigestRun(Instant.now());
    }
  }
}
