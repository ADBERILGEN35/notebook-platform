package com.notebook.lumen.notification.email.application;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmailWorker {
  private final EmailNotificationService service;
  private final AtomicBoolean acceptingClaims = new AtomicBoolean(true);

  public EmailWorker(EmailNotificationService service) {
    this.service = service;
  }

  @Scheduled(fixedDelayString = "${notification.email.worker-fixed-delay-ms:5000}")
  void process() {
    if (!acceptingClaims.get()) {
      return;
    }
    service.processDueNotifications();
  }

  @jakarta.annotation.PreDestroy
  void stopAcceptingClaims() {
    acceptingClaims.set(false);
  }
}
