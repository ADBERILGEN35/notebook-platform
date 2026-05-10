package com.notebook.lumen.notification.admin.retention;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationRetentionWorker {

  private final NotificationRetentionAdminService retentionAdminService;

  public NotificationRetentionWorker(NotificationRetentionAdminService retentionAdminService) {
    this.retentionAdminService = retentionAdminService;
  }

  @Scheduled(fixedDelayString = "${notification.retention.poll-interval-ms:86400000}")
  public void tick() {
    retentionAdminService.workerTick();
  }
}
