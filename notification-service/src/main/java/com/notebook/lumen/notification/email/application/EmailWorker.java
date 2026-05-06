package com.notebook.lumen.notification.email.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmailWorker {
  private final EmailNotificationService service;

  public EmailWorker(EmailNotificationService service) {
    this.service = service;
  }

  @Scheduled(fixedDelayString = "${notification.email.worker-fixed-delay-ms:5000}")
  void process() {
    service.processDueNotifications();
  }
}
