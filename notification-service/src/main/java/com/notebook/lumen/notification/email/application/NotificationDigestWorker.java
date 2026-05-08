package com.notebook.lumen.notification.email.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationDigestWorker {
  private final NotificationDigestService digestService;

  public NotificationDigestWorker(NotificationDigestService digestService) {
    this.digestService = digestService;
  }

  @Scheduled(fixedDelayString = "${notification.digest.poll-interval-seconds:60}000")
  public void poll() {
    digestService.processDueDigestItems();
  }
}
